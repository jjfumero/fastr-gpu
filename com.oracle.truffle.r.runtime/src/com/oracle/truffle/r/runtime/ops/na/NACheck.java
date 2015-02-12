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
package com.oracle.truffle.r.runtime.ops.na;

import static com.oracle.truffle.r.runtime.RRuntime.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

public final class NACheck {

    private static final int NO_CHECK = 0;
    private static final int CHECK_DEOPT = 1;
    private static final int CHECK = 2;

    private final BranchProfile conversionOverflowReached = BranchProfile.create();

    @CompilationFinal int state;
    @CompilationFinal boolean seenNaN;

    public static NACheck create() {
        return new NACheck();
    }

    public void enable(boolean value) {
        if (state == NO_CHECK && value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            state = CHECK_DEOPT;
        }
    }

    public void enable(byte logical) {
        if (state == NO_CHECK) {
            enable(RRuntime.isNA(logical));
        }
    }

    public void enable(int value) {
        if (state == NO_CHECK) {
            enable(RRuntime.isNA(value));
        }
    }

    public void enable(double value) {
        if (state == NO_CHECK) {
            enable(RRuntime.isNA(value));
        }
    }

    public void enable(RComplex value) {
        if (state == NO_CHECK) {
            enable(value.isNA());
        }
    }

    public void enable(RAbstractVector value) {
        if (state == NO_CHECK) {
            enable(!value.isComplete());
        }
    }

    public void enable(String operand) {
        if (state == NO_CHECK) {
            enable(RRuntime.isNA(operand));
        }
    }

    public boolean check(double value) {
        if (state != NO_CHECK && isNA(value)) {
            if (state == CHECK_DEOPT) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                state = CHECK;
            }
            return true;
        }
        return false;
    }

    public boolean check(RComplex value) {
        if (state != NO_CHECK && value.isNA()) {
            if (state == CHECK_DEOPT) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                state = CHECK;
            }
            return true;
        }
        return false;
    }

    public boolean check(int value) {
        if (state != NO_CHECK && isNA(value)) {
            if (state == CHECK_DEOPT) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                state = CHECK;
            }
            return true;
        }
        return false;
    }

    public boolean check(String value) {
        if (state != NO_CHECK && isNA(value)) {
            if (state == CHECK_DEOPT) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                state = CHECK;
            }
            return true;
        }
        return false;
    }

    public boolean check(byte value) {
        if (state != NO_CHECK && isNA(value)) {
            if (state == CHECK_DEOPT) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                state = CHECK;
            }
            return true;
        }
        return false;
    }

    public int convertLogicalToInt(byte value) {
        if (check(value)) {
            return RRuntime.INT_NA;
        }
        return value;
    }

    public RComplex convertLogicalToComplex(byte value) {
        if (check(value)) {
            return RRuntime.createComplexNA();
        }
        return RDataFactory.createComplex(value, 0);
    }

    public double convertIntToDouble(int value) {
        if (check(value)) {
            return RRuntime.DOUBLE_NA;
        }
        return value;
    }

    public RComplex convertDoubleToComplex(double value) {
        if (checkNAorNaN(value)) {
            // Special case here NaN does not enable the NA check.
            this.enable(true);
            return RRuntime.createComplexNA();
        }
        return RDataFactory.createComplex(value, 0);
    }

    public RComplex convertIntToComplex(int value) {
        if (check(value)) {
            return RRuntime.createComplexNA();
        }
        return RDataFactory.createComplex(value, 0);
    }

    public boolean neverSeenNA() {
        // need to check for both NA and NaN (the latter used for double to int conversions)
        return state != CHECK && !seenNaN;
    }

    public boolean hasNeverBeenTrue() {
        return neverSeenNA();
    }

    public double convertLogicalToDouble(byte value) {
        if (check(value)) {
            return RRuntime.DOUBLE_NA;
        }
        return RRuntime.logical2doubleNoCheck(value);
    }

    public String convertLogicalToString(byte right) {
        if (check(right)) {
            return RRuntime.STRING_NA;
        }
        return RRuntime.logicalToStringNoCheck(right);
    }

    public String convertIntToString(int right) {
        if (check(right)) {
            return RRuntime.STRING_NA;
        }
        return RRuntime.intToStringNoCheck(right);
    }

    @TruffleBoundary
    public double convertStringToDouble(String value) {
        if (check(value)) {
            return RRuntime.DOUBLE_NA;
        }
        double result = RRuntime.string2doubleNoCheck(value);
        check(result); // can be NA
        return result;
    }

    public RComplex convertStringToComplex(String value) {
        if (check(value)) {
            return RRuntime.createComplexNA();
        }
        RComplex result = RRuntime.string2complexNoCheck(value);
        check(result); // can be NA
        return result;
    }

    public int convertStringToInt(String value) {
        if (check(value)) {
            return RRuntime.INT_NA;
        }
        return RRuntime.string2intNoCheck(value);
    }

    public String convertDoubleToString(double value) {
        if (check(value)) {
            return RRuntime.STRING_NA;
        }
        return RRuntime.doubleToStringNoCheck(value);
    }

    public String convertComplexToString(RComplex value) {
        if (check(value)) {
            return RRuntime.STRING_NA;
        }
        return RRuntime.complexToStringNoCheck(value);
    }

    public double convertComplexToDouble(RComplex value) {
        return convertComplexToDouble(value, false);
    }

    public double convertComplexToDouble(RComplex value, boolean warning) {
        if (check(value)) {
            return RRuntime.DOUBLE_NA;
        }
        if (warning) {
            RError.warning(RError.Message.IMAGINARY_PARTS_DISCARDED_IN_COERCION);
        }
        return RRuntime.complex2doubleNoCheck(value);
    }

    public byte convertComplexToLogical(RComplex value) {
        if (check(value)) {
            return RRuntime.LOGICAL_NA;
        }
        return RRuntime.complex2logicalNoCheck(value);
    }

    public int convertComplexToInt(RComplex right) {
        return convertComplexToInt(right, true);
    }

    public int convertComplexToInt(RComplex right, boolean warning) {
        if (check(right)) {
            return RRuntime.INT_NA;
        }
        if (warning) {
            RError.warning(RError.Message.IMAGINARY_PARTS_DISCARDED_IN_COERCION);
        }
        return RRuntime.complex2intNoCheck(right);
    }

    public boolean checkNAorNaN(double value) {
        if (Double.isNaN(value)) {
            if (!this.seenNaN) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.seenNaN = true;
            }
            return true;
        }
        return false;
    }

    public int convertDoubleToInt(double value) {
        if (checkNAorNaN(value)) {
            return RRuntime.INT_NA;
        }
        int result = (int) value;
        if (result == Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            conversionOverflowReached.enter();
            RError.warning(RError.Message.NA_INTRODUCED_COERCION);
            check(RRuntime.INT_NA); // na encountered
            return RRuntime.INT_NA;
        }
        return result;
    }

    public int[] convertDoubleVectorToIntData(RDoubleVector vector) {
        int length = vector.getLength();
        int[] result = new int[length];
        boolean warning = false;
        for (int i = 0; i < length; i++) {
            double value = vector.getDataAt(i);
            if (checkNAorNaN(value)) {
                result[i] = RRuntime.INT_NA;
            } else {
                int intValue = (int) value;
                if (intValue == Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                    conversionOverflowReached.enter();
                    warning = true;
                    check(RRuntime.INT_NA); // NA encountered
                    intValue = RRuntime.INT_NA;
                }
                result[i] = intValue;
            }
            if (warning) {
                RError.warning(RError.Message.NA_INTRODUCED_COERCION);
            }
        }
        return result;
    }

    public byte convertIntToLogical(int value) {
        if (check(value)) {
            return RRuntime.LOGICAL_NA;
        }
        return RRuntime.int2logicalNoCheck(value);
    }

    public byte convertDoubleToLogical(double value) {
        if (check(value)) {
            return RRuntime.LOGICAL_NA;
        }
        return RRuntime.double2logicalNoCheck(value);
    }

    public byte convertStringToLogical(String value) {
        if (check(value)) {
            return RRuntime.LOGICAL_NA;
        }
        return RRuntime.string2logicalNoCheck(value);
    }

}
