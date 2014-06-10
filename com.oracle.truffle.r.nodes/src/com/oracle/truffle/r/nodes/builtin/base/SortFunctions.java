/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.r.nodes.builtin.RBuiltinKind.*;

import java.util.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

/**
 * Temporary minimal implementation for b25 benchmarks. Eventually this should be combined with
 * {@link Order} and made consistent with {@code sort.R}.
 *
 */
public class SortFunctions {

    @RBuiltin(name = "sort.list", kind = SUBSTITUTE)
    // TODO Implement in R
    public abstract static class SortList extends RBuiltinNode {
        @Specialization
        public RDoubleVector sortList(RDoubleVector vec, byte decreasing) {
            controlVisibility();
            double[] data = vec.getDataCopy();
            Arrays.sort(data);
            if (decreasing == RRuntime.LOGICAL_TRUE) {
                double[] rdata = new double[data.length];
                for (int i = 0; i < data.length; i++) {
                    rdata[i] = data[data.length - (i + 1)];
                }
                data = rdata;
            }
            return RDataFactory.createDoubleVector(data, RDataFactory.COMPLETE_VECTOR);
        }
    }

    @RBuiltin(name = "qsort", kind = INTERNAL)
    // TODO full implementation in Java handling NAs
    public abstract static class QSort extends RBuiltinNode {
        @Specialization
        public RDoubleVector qsort(RAbstractDoubleVector vec, @SuppressWarnings("unused") Object indexReturn) {
            double[] data = vec.materialize().getDataCopy();
            Arrays.sort(data);
            return RDataFactory.createDoubleVector(data, vec.isComplete());
        }

        @Specialization
        public RIntVector qsort(RAbstractIntVector vec, @SuppressWarnings("unused") Object indexReturn) {
            int[] data = vec.materialize().getDataCopy();
            Arrays.sort(data);
            return RDataFactory.createIntVector(data, vec.isComplete());
        }
    }

}
