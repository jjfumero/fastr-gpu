/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.library.stats;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDouble;

/**
 * TODO GnuR checks/updates {@code .Random.seed} across this call.
 */
public abstract class Runif extends RExternalBuiltinNode.Arg3 {

    // @Child private RandomNumberNode random = new RandomNumberNode();

    private int I1 = 1234;
    private int I2 = 5678;

    private double unif_rand() {
        I1 = 36969 * (I1 & 0177777) + (I1 >> 16);
        I2 = 18000 * (I2 & 0177777) + (I2 >> 16);
        return ((I1 << 16) ^ (I2 & 0177777)) * 2.328306437080797e-10; /* in [0,1) */
    }

    public double runif(double a, double b) {
        // if (!R_finite(a) || !R_finite(b) || b < a) {
        if (b < a) {
            return RDouble.NA.getDataAt(0);
        }

        if (a == b) {
            return a;
        }
        else {
            double u;
            /*
             * This is true of all builtin generators, but protect against user-supplied ones
             */
            do {
                u = unif_rand();
            } while (u <= 0 || u >= 1);
            return a + (b - a) * u;
        }
    }

    public double[] randomNumbers(int nInt) {
        double[] v = new double[nInt];
        for (int i = 0; i < v.length; i++) {
            v[i] = runif(0, 1);
        }
        return v;
    }

    @Specialization
    protected Object doRunif(Object n, Object min, Object max) {
        // TODO full error checks
        int nInt = castInt(castVector(n));
        double minDouble = castDouble(castVector(min)).getDataAt(0);
        double maxDouble = castDouble(castVector(max)).getDataAt(0);
        double delta = maxDouble - minDouble;

        // double[] result = random.executeDouble(nInt);
        double[] result = randomNumbers(nInt);

        if (minDouble < 0 || maxDouble >= 1.0) {
            for (int i = 0; i < nInt; i++) {
                result[i] = minDouble + result[i] * delta;
            }
        }

        return RDataFactory.createDoubleVector(result, RDataFactory.COMPLETE_VECTOR);
        // return RDataFactory.createDoubleVector(result, false);
    }
}
