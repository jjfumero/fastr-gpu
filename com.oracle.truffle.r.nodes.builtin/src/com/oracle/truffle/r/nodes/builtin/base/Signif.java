/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.runtime.RBuiltinKind.*;

import java.math.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ops.na.*;

@RBuiltin(name = "signif", kind = PRIMITIVE, parameterNames = {"x", "digits"})
public abstract class Signif extends RBuiltinNode {

    @Override
    public Object[] getDefaultParameterValues() {
        return new Object[]{RMissing.instance, 6};
    }

    private final NACheck naCheck = NACheck.create();
    private final BranchProfile identity = BranchProfile.create();
    private final ConditionProfile infProfile = ConditionProfile.createBinaryProfile();
    private final RAttributeProfiles attrProfiles = RAttributeProfiles.create();

    @Override
    protected void createCasts(CastBuilder casts) {
        casts.toInteger(1);
    }

    // TODO: consider porting signif implementation from GNU R

    @Specialization(guards = "digitsVec.getLength() == 1")
    protected RAbstractDoubleVector signif(RAbstractDoubleVector x, RAbstractIntVector digitsVec) {
        controlVisibility();
        int digits = digitsVec.getDataAt(0) <= 0 ? 1 : digitsVec.getDataAt(0);
        if (digits > 22) {
            identity.enter();
            return x;
        }
        double[] data = new double[x.getLength()];
        naCheck.enable(x);
        for (int i = 0; i < x.getLength(); i++) {
            double val = x.getDataAt(i);
            if (naCheck.check(val)) {
                data[i] = RRuntime.DOUBLE_NA;
            } else {
                if (infProfile.profile(Double.isInfinite(val))) {
                    data[i] = Double.POSITIVE_INFINITY;
                } else {
                    BigDecimal bigDecimalVal = new BigDecimal(val, new MathContext(digits, RoundingMode.HALF_UP));
                    data[i] = bigDecimalVal.doubleValue();
                }
            }
        }
        RDoubleVector ret = RDataFactory.createDoubleVector(data, naCheck.neverSeenNA());
        ret.copyAttributesFrom(attrProfiles, x);
        return ret;
    }

    @Specialization(guards = "digits.getLength() == 1")
    protected RAbstractIntVector roundDigits(RAbstractIntVector x, @SuppressWarnings("unused") RAbstractIntVector digits) {
        controlVisibility();
        return x;
    }

    // TODO: add support for digit vectors of length different than 1

}
