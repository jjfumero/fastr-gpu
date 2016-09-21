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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.runtime.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.base.ReNodeGen.ReInternalNodeGen;
import com.oracle.truffle.r.nodes.unary.UnaryNode;
import com.oracle.truffle.r.runtime.RBuiltin;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractComplexVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractDoubleVector;
import com.oracle.truffle.r.runtime.ops.na.NACheck;

@RBuiltin(name = "Re", kind = PRIMITIVE, parameterNames = {"z"})
public abstract class Re extends RBuiltinNode {

    @Child private ReInternalNode re = ReInternalNodeGen.create();

    @Specialization
    protected Object re(Object value) {
        return re.execute(value);
    }

    public abstract static class ReInternalNode extends UnaryNode {

        public abstract RDoubleVector executeRDoubleVector(Object value);

        private NACheck check = NACheck.create();

        @Specialization
        protected RDoubleVector re(RAbstractComplexVector vector) {
            double[] result = new double[vector.getLength()];
            check.enable(vector);
            for (int i = 0; i < vector.getLength(); i++) {
                result[i] = vector.getDataAt(i).getRealPart();
                check.check(result[i]);
            }
            return RDataFactory.createDoubleVector(result, check.neverSeenNA());
        }

        @Specialization
        protected RDoubleVector re(RAbstractDoubleVector vector) {
            return (RDoubleVector) vector.copy();
        }
    }
}
