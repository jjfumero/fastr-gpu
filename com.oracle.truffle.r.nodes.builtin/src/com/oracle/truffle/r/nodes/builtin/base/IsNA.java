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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

@SuppressWarnings("unused")
@RBuiltin(name = "is.na", kind = PRIMITIVE, parameterNames = {"x"})
public abstract class IsNA extends RBuiltinNode {

    @Child private IsNA recursiveIsNA;

    private final RAttributeProfiles attrProfiles = RAttributeProfiles.create();

    private Object isNARecursive(VirtualFrame frame, Object o) {
        if (recursiveIsNA == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            recursiveIsNA = insert(IsNAFactory.create(new RNode[1], getBuiltin(), getSuppliedSignature()));
        }
        return recursiveIsNA.execute(frame, o);
    }

    public abstract Object execute(VirtualFrame frame, Object o);

    @Specialization
    protected byte isNA(int value) {
        controlVisibility();
        return RRuntime.asLogical(RRuntime.isNA(value));
    }

    @Specialization
    protected RLogicalVector isNA(RAbstractIntVector vector) {
        controlVisibility();
        byte[] resultVector = new byte[vector.getLength()];
        for (int i = 0; i < vector.getLength(); i++) {
            resultVector[i] = RRuntime.asLogical(RRuntime.isNA(vector.getDataAt(i)));
        }
        return RDataFactory.createLogicalVector(resultVector, RDataFactory.COMPLETE_VECTOR, vector.getDimensions(), vector.getNames(attrProfiles));
    }

    @Specialization
    protected byte isNA(double value) {
        controlVisibility();
        return RRuntime.asLogical(RRuntime.isNAorNaN(value));
    }

    @Specialization
    protected RLogicalVector isNA(RAbstractDoubleVector vector) {
        controlVisibility();
        byte[] resultVector = new byte[vector.getLength()];
        for (int i = 0; i < vector.getLength(); i++) {
            resultVector[i] = RRuntime.asLogical(RRuntime.isNAorNaN(vector.getDataAt(i)));
        }
        return RDataFactory.createLogicalVector(resultVector, RDataFactory.COMPLETE_VECTOR, vector.getDimensions(), vector.getNames(attrProfiles));
    }

    @Specialization
    protected RLogicalVector isNA(RComplexVector vector) {
        controlVisibility();
        byte[] resultVector = new byte[vector.getLength()];
        for (int i = 0; i < vector.getLength(); i++) {
            RComplex complex = vector.getDataAt(i);
            resultVector[i] = RRuntime.asLogical(RRuntime.isNAorNaN(complex.getRealPart()) || RRuntime.isNAorNaN(complex.getImaginaryPart()));
        }
        return RDataFactory.createLogicalVector(resultVector, RDataFactory.COMPLETE_VECTOR, vector.getDimensions(), vector.getNames(attrProfiles));
    }

    @Specialization
    protected byte isNA(String value) {
        controlVisibility();
        return RRuntime.asLogical(RRuntime.isNA(value));
    }

    @Specialization
    protected RLogicalVector isNA(RStringVector vector) {
        controlVisibility();
        byte[] resultVector = new byte[vector.getLength()];
        for (int i = 0; i < vector.getLength(); i++) {
            resultVector[i] = RRuntime.asLogical(RRuntime.isNA(vector.getDataAt(i)));
        }
        return RDataFactory.createLogicalVector(resultVector, RDataFactory.COMPLETE_VECTOR, vector.getDimensions(), vector.getNames(attrProfiles));
    }

    @Specialization
    protected RLogicalVector isNA(VirtualFrame frame, RList list) {
        controlVisibility();
        byte[] resultVector = new byte[list.getLength()];
        for (int i = 0; i < list.getLength(); i++) {
            Object result = isNARecursive(frame, list.getDataAt(i));
            byte isNAResult;
            if (result instanceof Byte) {
                isNAResult = (Byte) result;
            } else if (result instanceof RLogicalVector) {
                RLogicalVector vector = (RLogicalVector) result;
                // result is false unless that element is a length-one atomic vector
                // and the single element of that vector is regarded as NA
                isNAResult = (vector.getLength() == 1) ? vector.getDataAt(0) : RRuntime.LOGICAL_FALSE;
            } else {
                throw fail("unhandled return type in isNA(list)");
            }
            resultVector[i] = isNAResult;
        }
        return RDataFactory.createLogicalVector(resultVector, RDataFactory.COMPLETE_VECTOR);
    }

    @TruffleBoundary
    private static UnsupportedOperationException fail(String message) {
        throw new UnsupportedOperationException(message);
    }

    @Specialization
    protected byte isNA(byte value) {
        controlVisibility();
        return RRuntime.asLogical(RRuntime.isNA(value));
    }

    @Specialization
    protected RLogicalVector isNA(RLogicalVector vector) {
        controlVisibility();
        byte[] resultVector = new byte[vector.getLength()];
        for (int i = 0; i < vector.getLength(); i++) {
            resultVector[i] = (RRuntime.isNA(vector.getDataAt(i)) ? RRuntime.LOGICAL_TRUE : RRuntime.LOGICAL_FALSE);
        }
        return RDataFactory.createLogicalVector(resultVector, RDataFactory.COMPLETE_VECTOR, vector.getDimensions(), vector.getNames(attrProfiles));
    }

    @Specialization
    protected byte isNA(RComplex value) {
        controlVisibility();
        return RRuntime.asLogical(RRuntime.isNA(value));
    }

    @Specialization
    protected byte isNA(RNull value) {
        controlVisibility();
        return RRuntime.LOGICAL_FALSE;
    }

    @Specialization
    protected RLogicalVector isNA(RFactor value) {
        return isNA(value.getVector());
    }

    @Specialization
    protected byte isNA(RRaw value) {
        controlVisibility();
        return RRuntime.LOGICAL_FALSE;
    }

    @Specialization
    protected RLogicalVector isNA(RRawVector vector) {
        controlVisibility();
        byte[] resultVector = new byte[vector.getLength()];
        for (int i = 0; i < vector.getLength(); i++) {
            resultVector[i] = RRuntime.LOGICAL_FALSE;
        }
        return RDataFactory.createLogicalVector(resultVector, RDataFactory.COMPLETE_VECTOR, vector.getDimensions(), vector.getNames(attrProfiles));
    }
}
