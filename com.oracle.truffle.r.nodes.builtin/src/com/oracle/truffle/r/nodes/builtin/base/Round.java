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

import static com.oracle.truffle.r.runtime.RBuiltinKind.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ops.*;
import com.oracle.truffle.r.runtime.ops.na.*;

@RBuiltin(name = "round", kind = PRIMITIVE, parameterNames = {"x", "digits"})
public abstract class Round extends RBuiltinNode {

    @Child private UnaryArithmetic roundOp = UnaryArithmetic.ROUND.create();

    private final NACheck check = NACheck.create();
    private final RAttributeProfiles attrProfiles = RAttributeProfiles.create();

    @Override
    public Object[] getDefaultParameterValues() {
        return new Object[]{RMissing.instance, 0};
    }

    @CreateCast("arguments")
    protected RNode[] castStatusArgument(RNode[] arguments) {
        // digits argument is at index 1
        arguments[1] = CastIntegerNodeGen.create(arguments[1], true, false, false);
        return arguments;
    }

    @Specialization
    protected int round(int x, @SuppressWarnings("unused") int digits) {
        controlVisibility();
        return roundOp.op(x);
    }

    @Specialization(guards = "digits == 0")
    protected double round(double x, @SuppressWarnings("unused") int digits) {
        controlVisibility();
        return roundOp.op(x);
    }

    @Specialization(guards = "digits != 0")
    protected double roundDigits(double x, int digits) {
        controlVisibility();
        return roundOp.opd(x, digits);
    }

    @Specialization(guards = "digits == 0")
    protected RDoubleVector round(RAbstractDoubleVector x, int digits) {
        controlVisibility();
        double[] result = new double[x.getLength()];
        check.enable(x);
        for (int i = 0; i < x.getLength(); ++i) {
            result[i] = round(x.getDataAt(i), digits);
            check.check(result[i]);
        }
        RDoubleVector ret = RDataFactory.createDoubleVector(result, check.neverSeenNA());
        ret.copyAttributesFrom(attrProfiles, x);
        return ret;
    }

    @Specialization(guards = "digits != 0")
    protected RDoubleVector roundDigits(RAbstractDoubleVector x, int digits) {
        controlVisibility();
        double[] result = new double[x.getLength()];
        check.enable(x);
        for (int i = 0; i < x.getLength(); ++i) {
            result[i] = roundDigits(x.getDataAt(i), digits);
            check.check(result[i]);
        }
        RDoubleVector ret = RDataFactory.createDoubleVector(result, check.neverSeenNA());
        ret.copyAttributesFrom(attrProfiles, x);
        return ret;
    }

    @Specialization(guards = "digits == 0")
    protected RComplex round(RComplex x, @SuppressWarnings("unused") int digits) {
        controlVisibility();
        return roundOp.op(x.getRealPart(), x.getImaginaryPart());
    }

    @Specialization(guards = "digits != 0")
    protected RComplex roundDigits(RComplex x, int digits) {
        controlVisibility();
        return roundOp.opd(x.getRealPart(), x.getImaginaryPart(), digits);
    }

    @Specialization(guards = "digits == 0")
    protected RComplexVector round(RComplexVector x, int digits) {
        controlVisibility();
        double[] result = new double[x.getLength() << 1];
        check.enable(x);
        for (int i = 0; i < x.getLength(); ++i) {
            RComplex z = x.getDataAt(i);
            RComplex r = round(z, digits);
            result[2 * i] = r.getRealPart();
            result[2 * i + 1] = r.getImaginaryPart();
            check.check(r);
        }
        RComplexVector ret = RDataFactory.createComplexVector(result, check.neverSeenNA());
        ret.copyAttributesFrom(attrProfiles, x);
        return ret;
    }

    @Specialization(guards = "digits != 0")
    protected RComplexVector roundDigits(RComplexVector x, int digits) {
        controlVisibility();
        double[] result = new double[x.getLength() << 1];
        check.enable(x);
        for (int i = 0; i < x.getLength(); ++i) {
            RComplex z = x.getDataAt(i);
            RComplex r = roundDigits(z, digits);
            result[2 * i] = r.getRealPart();
            result[2 * i + 1] = r.getImaginaryPart();
            check.check(r);
        }
        RComplexVector ret = RDataFactory.createComplexVector(result, check.neverSeenNA());
        ret.copyAttributesFrom(attrProfiles, x);
        return ret;
    }

}
