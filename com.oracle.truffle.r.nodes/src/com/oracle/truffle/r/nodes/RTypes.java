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
package com.oracle.truffle.r.nodes;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.conn.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.env.*;

/**
 * Whenever you add a type {@code T} to the list below, make sure a corresponding {@code executeT()}
 * method is added to {@link RNode}, a {@code typeof} method is added to {@code TypeoNode} and a
 * {@code print} method added to {code PrettyPrinterNode}.
 *
 * @see RNode
 */
@TypeSystem({boolean.class, byte.class, int.class, double.class, RRaw.class, RComplex.class, String.class, RIntSequence.class, RDoubleSequence.class, RIntVector.class, RDoubleVector.class,
                RRawVector.class, RComplexVector.class, RStringVector.class, RLogicalVector.class, RFunction.class, RNull.class, RMissing.class, REnvironment.class, RExpression.class,
                RConnection.class, MaterializedFrame.class, FrameSlot.class, RAbstractIntVector.class, RAbstractDoubleVector.class, RAbstractLogicalVector.class, RAbstractComplexVector.class,
                RAbstractStringVector.class, RAbstractRawVector.class, RList.class, RAbstractVector.class, RDataFrame.class, RFactor.class, RSymbol.class, RPromise.class, RLanguage.class,
                RPairList.class, RFormula.class, RExternalPtr.class, RS4Object.class, RAbstractContainer.class, RAttributable.class, RTypedValue.class, RArgsValuesAndNames.class, RType.class,
                Object[].class})
public class RTypes {

    @TypeCheck(RNull.class)
    public static boolean isRNull(Object value) {
        return value == RNull.instance;
    }

    @TypeCast(RNull.class)
    @SuppressWarnings("unused")
    public static RNull asRNull(Object value) {
        return RNull.instance;
    }

    @TypeCheck(RMissing.class)
    public static boolean isRMissing(Object value) {
        return value == RMissing.instance;
    }

    @TypeCast(RMissing.class)
    @SuppressWarnings("unused")
    public static RMissing asRMissing(Object value) {
        return RMissing.instance;
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(int value) {
        return RDataFactory.createIntVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(double value) {
        return RDataFactory.createDoubleVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(RRaw value) {
        return RDataFactory.createRawVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(byte value) {
        return RDataFactory.createLogicalVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(RComplex value) {
        return RDataFactory.createComplexVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(String value) {
        return RDataFactory.createStringVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(RIntVector vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(RDoubleVector vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(RLogicalVector vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(RComplexVector vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(RRawVector vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(RStringVector vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(RIntSequence vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(RDoubleSequence vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(RList vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(RDataFrame vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractContainer toAbstractContainer(RAbstractVector vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(int value) {
        return RDataFactory.createIntVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(double value) {
        return RDataFactory.createDoubleVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(RRaw value) {
        return RDataFactory.createRawVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(byte value) {
        return RDataFactory.createLogicalVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(RComplex value) {
        return RDataFactory.createComplexVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(String value) {
        return RDataFactory.createStringVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(RIntVector vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(RDoubleVector vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(RLogicalVector vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(RComplexVector vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(RRawVector vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(RStringVector vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(RIntSequence vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(RDoubleSequence vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractVector toAbstractVector(RList vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractIntVector toAbstractIntVector(int value) {
        return RDataFactory.createIntVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractIntVector toAbstractIntVector(RIntSequence vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractIntVector toAbstractIntVector(RIntVector vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractDoubleVector toAbstractDoubleVector(double value) {
        return RDataFactory.createDoubleVectorFromScalar(value);
    }

    @ImplicitCast
    public static RAbstractDoubleVector toAbstractDoubleVector(RDoubleSequence vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractDoubleVector toAbstractDoubleVector(RDoubleVector vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractComplexVector toAbstractComplexVector(RComplexVector vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractComplexVector toAbstractComplexVector(RComplex vector) {
        return RDataFactory.createComplexVectorFromScalar(vector);
    }

    @ImplicitCast
    public static RAbstractLogicalVector toAbstractLogicalVector(byte vector) {
        return RDataFactory.createLogicalVectorFromScalar(vector);
    }

    @ImplicitCast
    public static RAbstractLogicalVector toAbstractLogicalVector(RLogicalVector vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractRawVector toAbstractRawVector(RRaw vector) {
        return RDataFactory.createRawVectorFromScalar(vector);
    }

    @ImplicitCast
    public static RAbstractRawVector toAbstractRawVector(RRawVector vector) {
        return vector;
    }

    @ImplicitCast
    public static RAbstractStringVector toAbstractStringVector(String vector) {
        return RDataFactory.createStringVectorFromScalar(vector);
    }

    @ImplicitCast
    public static RAbstractStringVector toAbstractStringVector(RStringVector vector) {
        return vector;
    }

}
