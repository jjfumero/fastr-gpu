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

import static com.oracle.truffle.r.runtime.RBuiltinKind.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.r.nodes.binary.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.closures.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ops.*;
import com.oracle.truffle.r.runtime.ops.na.*;

@RBuiltin(name = "%*%", kind = PRIMITIVE)
public abstract class MatMult extends RBuiltinNode {

    @Child protected BinaryArithmeticExperimentalNode mult = BinaryArithmeticExperimentalNode.create(BinaryArithmetic.MULTIPLY);
    @Child protected BinaryArithmeticExperimentalNode add = BinaryArithmeticExperimentalNode.create(BinaryArithmetic.ADD);

    protected abstract Object executeObject(VirtualFrame frame, Object a, Object b);

    protected final NACheck na;

    public MatMult() {
        this.na = NACheck.create();
    }

    public MatMult(MatMult prev) {
        this.na = prev.na;
    }

    @Specialization(order = 1, guards = "bothZeroDim")
    public RDoubleVector both0Dim(RAbstractDoubleVector a, RAbstractDoubleVector b) {
        controlVisibility();
        int r = b.getDimensions()[1];
        int c = a.getDimensions()[0];
        RDoubleVector result = RDataFactory.createDoubleVector(r * c);
        result.setDimensions(new int[]{r, c});
        return result;
    }

    @Specialization(order = 2, guards = "leftHasZeroDim")
    public RAbstractVector left0Dim(RAbstractVector a, RAbstractVector b) {
        controlVisibility();
        int[] dim = a.getDimensions()[0] == 0 ? new int[]{0, b.getDimensions()[1]} : new int[]{b.getDimensions()[0], 0};
        return a.copyWithNewDimensions(dim);
    }

    @Specialization(order = 3, guards = "rightHasZeroDim")
    public RAbstractVector right0Dim(RAbstractVector a, RAbstractVector b) {
        controlVisibility();
        int[] dim = b.getDimensions()[0] == 0 ? new int[]{0, a.getDimensions()[1]} : new int[]{a.getDimensions()[0], 0};
        return b.copyWithNewDimensions(dim);
    }

    // double-double

    @Specialization(order = 10, guards = "matmat")
    public RDoubleVector matmatmult(RAbstractDoubleVector a, RAbstractDoubleVector b) {
        controlVisibility();
        final int aCols = a.getDimensions()[1];
        final int bRows = b.getDimensions()[0];
        if (aCols != bRows) {
            throw RError.error(this.getEncapsulatingSourceSection(), RError.Message.NON_CONFORMABLE_ARGS);
        }
        final int aRows = a.getDimensions()[0];
        final int bCols = b.getDimensions()[1];
        double[] result = new double[aRows * bCols];
        na.enable(true);
        for (int row = 0; row < aRows; ++row) {
            for (int col = 0; col < bCols; ++col) {
                double x = 0.0;
                for (int k = 0; k < aCols; ++k) {
                    x = add.doDouble(x, mult.doDouble(a.getDataAt(k * aRows + row), b.getDataAt(col * bRows + k)));
                    na.check(x);
                }
                result[col * aRows + row] = x;
            }
        }
        return RDataFactory.createDoubleVector(result, na.neverSeenNA(), new int[]{aRows, bCols});
    }

    @Specialization(order = 11, guards = "vecvec")
    public RDoubleVector vecvecmult(RAbstractDoubleVector a, RAbstractDoubleVector b) {
        controlVisibility();
        if (a.getLength() != b.getLength()) {
            throw RError.error(this.getEncapsulatingSourceSection(), RError.Message.NON_CONFORMABLE_ARGS);
        }
        double result = 0.0;
        na.enable(true);
        for (int k = 0; k < a.getLength(); ++k) {
            result = add.doDouble(result, mult.doDouble(a.getDataAt(k), b.getDataAt(k)));
            na.check(result);
        }
        return RDataFactory.createDoubleVector(new double[]{result}, na.neverSeenNA(), new int[]{1, 1});
    }

    @Specialization(order = 12, guards = "matvec")
    public RDoubleVector matvecmult(RAbstractDoubleVector a, RAbstractDoubleVector b) {
        controlVisibility();
        final int aCols = a.getDimensions()[1];
        final int aRows = a.getDimensions()[0];
        if (aCols != 1 && aCols != b.getLength()) {
            throw RError.error(this.getEncapsulatingSourceSection(), RError.Message.NON_CONFORMABLE_ARGS);
        }
        na.enable(true);
        if (aCols != 1) {
            double[] result = new double[aRows];
            for (int row = 0; row < aRows; ++row) {
                double x = 0;
                for (int k = 0; k < b.getLength(); ++k) {
                    x = add.doDouble(x, mult.doDouble(a.getDataAt(k * aRows + row), b.getDataAt(k)));
                    na.check(x);
                }
                result[row] = x;
            }
            return RDataFactory.createDoubleVector(result, na.neverSeenNA(), new int[]{aRows, 1});
        } else {
            double[] result = new double[aRows * b.getLength()];
            for (int row = 0; row < aRows; ++row) {
                for (int k = 0; k < b.getLength(); ++k) {
                    double x = mult.doDouble(a.getDataAt(row), b.getDataAt(k));
                    na.check(x);
                    result[k * aRows + row] = x;
                }
            }
            return RDataFactory.createDoubleVector(result, na.neverSeenNA(), new int[]{aRows, b.getLength()});
        }
    }

    @Specialization(order = 13, guards = "vecmat")
    public RDoubleVector vecmatmult(RAbstractDoubleVector a, RAbstractDoubleVector b) {
        controlVisibility();
        final int bCols = b.getDimensions()[1];
        final int bRows = b.getDimensions()[0];
        if (bRows != 1 && bRows != a.getLength()) {
            throw RError.error(this.getEncapsulatingSourceSection(), RError.Message.NON_CONFORMABLE_ARGS);
        }
        na.enable(true);
        if (bRows != 1) {
            double[] result = new double[bCols];
            for (int k = 0; k < bCols; ++k) {
                double x = 0.0;
                for (int row = 0; row < a.getLength(); ++row) {
                    x = add.doDouble(x, mult.doDouble(a.getDataAt(row), b.getDataAt(k * a.getLength() + row)));
                    na.check(x);
                }
                result[k] = x;
            }
            return RDataFactory.createDoubleVector(result, na.neverSeenNA(), new int[]{1, bCols});
        } else {
            double[] result = new double[bCols * a.getLength()];
            for (int row = 0; row < a.getLength(); ++row) {
                for (int k = 0; k < bCols; ++k) {
                    double x = mult.doDouble(a.getDataAt(row), b.getDataAt(k));
                    na.check(x);
                    result[k * a.getLength() + row] = x;
                }
            }
            return RDataFactory.createDoubleVector(result, na.neverSeenNA(), new int[]{a.getLength(), bCols});
        }
    }

    // complex-complex

    @Specialization(order = 20, guards = "matmat")
    public RComplexVector matmatmult(RAbstractComplexVector a, RAbstractComplexVector b) {
        controlVisibility();
        final int aCols = a.getDimensions()[1];
        final int bRows = b.getDimensions()[0];
        if (aCols != bRows) {
            throw RError.error(this.getEncapsulatingSourceSection(), RError.Message.NON_CONFORMABLE_ARGS);
        }
        final int aRows = a.getDimensions()[0];
        final int bCols = b.getDimensions()[1];
        double[] result = new double[(aRows * bCols) << 1];
        na.enable(true);
        for (int row = 0; row < aRows; ++row) {
            for (int col = 0; col < bCols; ++col) {
                RComplex x = RDataFactory.createComplexZero();
                for (int k = 0; k < aCols; ++k) {
                    x = add.doComplex(x, mult.doComplex(a.getDataAt(k * aRows + row), b.getDataAt(col * bRows + k)));
                    na.check(x);
                }
                final int index = 2 * (col * aRows + row);
                result[index] = x.getRealPart();
                result[index + 1] = x.getImaginaryPart();
            }
        }
        return RDataFactory.createComplexVector(result, na.neverSeenNA(), new int[]{aRows, bCols});
    }

    @Specialization(order = 21, guards = "vecvec")
    public RComplexVector vecvecmult(RAbstractComplexVector a, RAbstractComplexVector b) {
        controlVisibility();
        if (a.getLength() != b.getLength()) {
            throw RError.error(this.getEncapsulatingSourceSection(), RError.Message.NON_CONFORMABLE_ARGS);
        }
        RComplex result = RDataFactory.createComplexZero();
        na.enable(true);
        for (int k = 0; k < a.getLength(); ++k) {
            result = add.doComplex(result, mult.doComplex(a.getDataAt(k), b.getDataAt(k)));
            na.check(result);
        }
        return RDataFactory.createComplexVector(new double[]{result.getRealPart(), result.getImaginaryPart()}, na.neverSeenNA(), new int[]{1, 1});
    }

    @Specialization(order = 22, guards = "matvec")
    public RComplexVector matvecmult(RAbstractComplexVector a, RAbstractComplexVector b) {
        controlVisibility();
        final int aCols = a.getDimensions()[1];
        final int aRows = a.getDimensions()[0];
        if (aCols != 1 && aCols != b.getLength()) {
            throw RError.error(this.getEncapsulatingSourceSection(), RError.Message.NON_CONFORMABLE_ARGS);
        }
        na.enable(true);
        if (aCols != 1) {
            double[] result = new double[aRows << 1];
            for (int row = 0; row < aRows; ++row) {
                RComplex x = RDataFactory.createComplexZero();
                for (int k = 0; k < b.getLength(); ++k) {
                    x = add.doComplex(x, mult.doComplex(a.getDataAt(k * aRows + row), b.getDataAt(k)));
                    na.check(x);
                }
                result[row << 1] = x.getRealPart();
                result[row << 1 + 1] = x.getImaginaryPart();
            }
            return RDataFactory.createComplexVector(result, na.neverSeenNA(), new int[]{aRows, 1});
        } else {
            double[] result = new double[aRows * b.getLength() << 1];
            for (int row = 0; row < aRows; ++row) {
                for (int k = 0; k < b.getLength(); ++k) {
                    RComplex x = mult.doComplex(a.getDataAt(row), b.getDataAt(k));
                    na.check(x);
                    result[(k * aRows + row) << 1] = x.getRealPart();
                    result[(k * aRows + row) << 1 + 1] = x.getRealPart();
                }
            }
            return RDataFactory.createComplexVector(result, na.neverSeenNA(), new int[]{aRows, b.getLength()});
        }
    }

    @Specialization(order = 23, guards = "vecmat")
    public RComplexVector vecmatmult(RAbstractComplexVector a, RAbstractComplexVector b) {
        controlVisibility();
        final int bRows = b.getDimensions()[0];
        final int bCols = b.getDimensions()[1];
        if (bRows != 1 && bRows != a.getLength()) {
            throw RError.error(this.getEncapsulatingSourceSection(), RError.Message.NON_CONFORMABLE_ARGS);
        }
        na.enable(true);
        if (bRows != 1) {
            double[] result = new double[bCols << 1];
            for (int k = 0; k < bCols; ++k) {
                RComplex x = RDataFactory.createComplexZero();
                for (int row = 0; row < a.getLength(); ++row) {
                    x = add.doComplex(x, mult.doComplex(a.getDataAt(row), b.getDataAt(k * a.getLength() + row)));
                    na.check(x);
                }
                result[k << 1] = x.getRealPart();
                result[k << 1 + 1] = x.getImaginaryPart();
            }
            return RDataFactory.createComplexVector(result, na.neverSeenNA(), new int[]{1, bCols});
        } else {
            double[] result = new double[(bCols * a.getLength()) << 1];
            for (int row = 0; row < a.getLength(); ++row) {
                for (int k = 0; k < bCols; ++k) {
                    RComplex x = mult.doComplex(a.getDataAt(row), b.getDataAt(k));
                    na.check(x);
                    result[(k * a.getLength() + row) << 1] = x.getRealPart();
                    result[(k * a.getLength() + row) << 1 + 1] = x.getImaginaryPart();
                }
            }
            return RDataFactory.createComplexVector(result, na.neverSeenNA(), new int[]{a.getLength(), bCols});
        }
    }

    // int-int

    @Specialization(order = 30, guards = "matmat")
    public RIntVector matmatmult(RAbstractIntVector a, RAbstractIntVector b) {
        controlVisibility();
        final int aCols = a.getDimensions()[1];
        final int bRows = b.getDimensions()[0];
        if (aCols != bRows) {
            throw RError.error(this.getEncapsulatingSourceSection(), RError.Message.NON_CONFORMABLE_ARGS);
        }
        final int aRows = a.getDimensions()[0];
        final int bCols = b.getDimensions()[1];
        int[] result = new int[aRows * bCols];
        na.enable(true);
        for (int row = 0; row < aRows; ++row) {
            for (int col = 0; col < bCols; ++col) {
                int x = 0;
                for (int k = 0; k < aCols; ++k) {
                    x = add.doInt(x, mult.doInt(a.getDataAt(k * aRows + row), b.getDataAt(col * bRows + k)));
                    na.check(x);
                }
                result[col * aRows + row] = x;
            }
        }
        return RDataFactory.createIntVector(result, na.neverSeenNA(), new int[]{aRows, bCols});
    }

    @Specialization(order = 31, guards = "vecvec")
    public RIntVector vecvecmult(RAbstractIntVector a, RAbstractIntVector b) {
        controlVisibility();
        if (a.getLength() != b.getLength()) {
            throw RError.error(this.getEncapsulatingSourceSection(), RError.Message.NON_CONFORMABLE_ARGS);
        }
        int result = 0;
        na.enable(result);
        for (int k = 0; k < a.getLength(); ++k) {
            result = add.doInt(result, mult.doInt(a.getDataAt(k), b.getDataAt(k)));
            na.check(result);
        }
        return RDataFactory.createIntVector(new int[]{result}, na.neverSeenNA(), new int[]{1, 1});
    }

    @Specialization(order = 32, guards = "matvec")
    public RIntVector matvecmult(RAbstractIntVector a, RAbstractIntVector b) {
        controlVisibility();
        final int aCols = a.getDimensions()[1];
        final int aRows = a.getDimensions()[0];
        if (aCols != 1 && aCols != b.getLength()) {
            throw RError.error(this.getEncapsulatingSourceSection(), RError.Message.NON_CONFORMABLE_ARGS);
        }
        na.enable(true);
        if (aCols != 1) {
            int[] result = new int[aRows];
            for (int row = 0; row < aRows; ++row) {
                int x = 0;
                for (int k = 0; k < b.getLength(); ++k) {
                    x = add.doInt(x, mult.doInt(a.getDataAt(k * aRows + row), b.getDataAt(k)));
                    na.check(x);
                }
                result[row] = x;
            }
            return RDataFactory.createIntVector(result, na.neverSeenNA(), new int[]{aRows, 1});
        } else {
            int[] result = new int[aRows * b.getLength()];
            for (int row = 0; row < aRows; ++row) {
                for (int k = 0; k < b.getLength(); ++k) {
                    int x = mult.doInt(a.getDataAt(row), b.getDataAt(k));
                    na.check(x);
                    result[k * aRows + row] = x;
                }
            }
            return RDataFactory.createIntVector(result, na.neverSeenNA(), new int[]{aRows, b.getLength()});
        }
    }

    @Specialization(order = 33, guards = "vecmat")
    public RIntVector vecmatmult(RAbstractIntVector a, RAbstractIntVector b) {
        controlVisibility();
        final int bCols = b.getDimensions()[1];
        final int bRows = b.getDimensions()[0];
        if (bRows != 1 && bRows != a.getLength()) {
            throw RError.error(this.getEncapsulatingSourceSection(), RError.Message.NON_CONFORMABLE_ARGS);
        }
        na.enable(true);
        if (bRows != 1) {
            int[] result = new int[bCols];
            for (int k = 0; k < bCols; ++k) {
                int x = 0;
                for (int row = 0; row < a.getLength(); ++row) {
                    x = add.doInt(x, mult.doInt(a.getDataAt(row), b.getDataAt(k * a.getLength() + row)));
                    na.check(x);
                }
                result[k] = x;
            }
            return RDataFactory.createIntVector(result, na.neverSeenNA(), new int[]{1, bCols});
        } else {
            int[] result = new int[bCols * a.getLength()];
            for (int row = 0; row < a.getLength(); ++row) {
                for (int k = 0; k < bCols; ++k) {
                    int x = mult.doInt(a.getDataAt(row), b.getDataAt(k));
                    na.check(x);
                    result[k * a.getLength() + row] = x;
                }
            }
            return RDataFactory.createIntVector(result, na.neverSeenNA(), new int[]{a.getLength(), bCols});
        }
    }

    // logical-logical

    @Specialization(order = 40, guards = "matmat")
    public RIntVector matmatmult(RAbstractLogicalVector a, RAbstractLogicalVector b) {
        return matmatmult(RClosures.createLogicalToIntVector(a, na), RClosures.createLogicalToIntVector(b, na));
    }

    @Specialization(order = 41, guards = "vecvec")
    public RIntVector vecvecmult(RAbstractLogicalVector a, RAbstractLogicalVector b) {
        return vecvecmult(RClosures.createLogicalToIntVector(a, na), RClosures.createLogicalToIntVector(b, na));
    }

    @Specialization(order = 42, guards = "matvec")
    public RIntVector matvecmult(RAbstractLogicalVector a, RAbstractLogicalVector b) {
        return matvecmult(RClosures.createLogicalToIntVector(a, na), RClosures.createLogicalToIntVector(b, na));
    }

    @Specialization(order = 43, guards = "vecmat")
    public RIntVector vecmatmult(RAbstractLogicalVector a, RAbstractLogicalVector b) {
        return vecmatmult(RClosures.createLogicalToIntVector(a, na), RClosures.createLogicalToIntVector(b, na));
    }

    // to int

    @Specialization(order = 50, guards = "matmat")
    public RIntVector matmatmult(RAbstractLogicalVector a, RAbstractIntVector b) {
        return matmatmult(RClosures.createLogicalToIntVector(a, na), b);
    }

    @Specialization(order = 51, guards = "vecvec")
    public RIntVector vecvecmult(RAbstractLogicalVector a, RAbstractIntVector b) {
        return vecvecmult(RClosures.createLogicalToIntVector(a, na), b);
    }

    @Specialization(order = 52, guards = "matvec")
    public RIntVector matvecmult(RAbstractLogicalVector a, RAbstractIntVector b) {
        return matvecmult(RClosures.createLogicalToIntVector(a, na), b);
    }

    @Specialization(order = 53, guards = "vecmat")
    public RIntVector vecmatmult(RAbstractLogicalVector a, RAbstractIntVector b) {
        return vecmatmult(RClosures.createLogicalToIntVector(a, na), b);
    }

    @Specialization(order = 54, guards = "matmat")
    public RIntVector matmatmult(RAbstractIntVector a, RAbstractLogicalVector b) {
        return matmatmult(a, RClosures.createLogicalToIntVector(b, na));
    }

    @Specialization(order = 55, guards = "vecvec")
    public RIntVector vecvecmult(RAbstractIntVector a, RAbstractLogicalVector b) {
        return vecvecmult(a, RClosures.createLogicalToIntVector(b, na));
    }

    @Specialization(order = 56, guards = "matvec")
    public RIntVector matvecmult(RAbstractIntVector a, RAbstractLogicalVector b) {
        return matvecmult(a, RClosures.createLogicalToIntVector(b, na));
    }

    @Specialization(order = 57, guards = "vecmat")
    public RIntVector vecmatmult(RAbstractIntVector a, RAbstractLogicalVector b) {
        return vecmatmult(a, RClosures.createLogicalToIntVector(b, na));
    }

    // to complex

    @Specialization(order = 60, guards = "matmat")
    public RComplexVector matmatmult(RAbstractIntVector a, RAbstractComplexVector b) {
        return matmatmult(RClosures.createIntToComplexVector(a, na), b);
    }

    @Specialization(order = 61, guards = "vecvec")
    public RComplexVector vecvecmult(RAbstractIntVector a, RAbstractComplexVector b) {
        return vecvecmult(RClosures.createIntToComplexVector(a, na), b);
    }

    @Specialization(order = 62, guards = "matvec")
    public RComplexVector matvecmult(RAbstractIntVector a, RAbstractComplexVector b) {
        return matvecmult(RClosures.createIntToComplexVector(a, na), b);
    }

    @Specialization(order = 63, guards = "vecmat")
    public RComplexVector vecmatmult(RAbstractIntVector a, RAbstractComplexVector b) {
        return vecmatmult(RClosures.createIntToComplexVector(a, na), b);
    }

    @Specialization(order = 65, guards = "matmat")
    public RComplexVector matmatmult(RAbstractComplexVector a, RAbstractIntVector b) {
        return matmatmult(a, RClosures.createIntToComplexVector(b, na));
    }

    @Specialization(order = 66, guards = "vecvec")
    public RComplexVector vecvecmult(RAbstractComplexVector a, RAbstractIntVector b) {
        return vecvecmult(a, RClosures.createIntToComplexVector(b, na));
    }

    @Specialization(order = 67, guards = "matvec")
    public RComplexVector matvecmult(RAbstractComplexVector a, RAbstractIntVector b) {
        return matvecmult(a, RClosures.createIntToComplexVector(b, na));
    }

    @Specialization(order = 68, guards = "vecmat")
    public RComplexVector vecmatmult(RAbstractComplexVector a, RAbstractIntVector b) {
        return vecmatmult(a, RClosures.createIntToComplexVector(b, na));
    }

    @Specialization(order = 70, guards = "matmat")
    public RComplexVector matmatmult(RAbstractLogicalVector a, RAbstractComplexVector b) {
        return matmatmult(RClosures.createLogicalToComplexVector(a, na), b);
    }

    @Specialization(order = 71, guards = "vecvec")
    public RComplexVector vecvecmult(RAbstractLogicalVector a, RAbstractComplexVector b) {
        return vecvecmult(RClosures.createLogicalToComplexVector(a, na), b);
    }

    @Specialization(order = 72, guards = "matvec")
    public RComplexVector matvecmult(RAbstractLogicalVector a, RAbstractComplexVector b) {
        return matvecmult(RClosures.createLogicalToComplexVector(a, na), b);
    }

    @Specialization(order = 73, guards = "vecmat")
    public RComplexVector vecmatmult(RAbstractLogicalVector a, RAbstractComplexVector b) {
        return vecmatmult(RClosures.createLogicalToComplexVector(a, na), b);
    }

    @Specialization(order = 75, guards = "matmat")
    public RComplexVector matmatmult(RAbstractComplexVector a, RAbstractLogicalVector b) {
        return matmatmult(a, RClosures.createLogicalToComplexVector(b, na));
    }

    @Specialization(order = 76, guards = "vecvec")
    public RComplexVector vecvecmult(RAbstractComplexVector a, RAbstractLogicalVector b) {
        return vecvecmult(a, RClosures.createLogicalToComplexVector(b, na));
    }

    @Specialization(order = 77, guards = "matvec")
    public RComplexVector matvecmult(RAbstractComplexVector a, RAbstractLogicalVector b) {
        return matvecmult(a, RClosures.createLogicalToComplexVector(b, na));
    }

    @Specialization(order = 78, guards = "vecmat")
    public RComplexVector vecmatmult(RAbstractComplexVector a, RAbstractLogicalVector b) {
        return vecmatmult(a, RClosures.createLogicalToComplexVector(b, na));
    }

    @Specialization(order = 80, guards = "matmat")
    public RComplexVector matmatmult(RAbstractDoubleVector a, RAbstractComplexVector b) {
        return matmatmult(RClosures.createDoubleToComplexVector(a, na), b);
    }

    @Specialization(order = 81, guards = "vecvec")
    public RComplexVector vecvecmult(RAbstractDoubleVector a, RAbstractComplexVector b) {
        return vecvecmult(RClosures.createDoubleToComplexVector(a, na), b);
    }

    @Specialization(order = 82, guards = "matvec")
    public RComplexVector matvecmult(RAbstractDoubleVector a, RAbstractComplexVector b) {
        return matvecmult(RClosures.createDoubleToComplexVector(a, na), b);
    }

    @Specialization(order = 83, guards = "vecmat")
    public RComplexVector vecmatmult(RAbstractDoubleVector a, RAbstractComplexVector b) {
        return vecmatmult(RClosures.createDoubleToComplexVector(a, na), b);
    }

    @Specialization(order = 85, guards = "matmat")
    public RComplexVector matmatmult(RAbstractComplexVector a, RAbstractDoubleVector b) {
        return matmatmult(a, RClosures.createDoubleToComplexVector(b, na));
    }

    @Specialization(order = 86, guards = "vecvec")
    public RComplexVector vecvecmult(RAbstractComplexVector a, RAbstractDoubleVector b) {
        return vecvecmult(a, RClosures.createDoubleToComplexVector(b, na));
    }

    @Specialization(order = 87, guards = "matvec")
    public RComplexVector matvecmult(RAbstractComplexVector a, RAbstractDoubleVector b) {
        return matvecmult(a, RClosures.createDoubleToComplexVector(b, na));
    }

    @Specialization(order = 88, guards = "vecmat")
    public RComplexVector vecmatmult(RAbstractComplexVector a, RAbstractDoubleVector b) {
        return vecmatmult(a, RClosures.createDoubleToComplexVector(b, na));
    }

    // to double

    @Specialization(order = 90, guards = "matmat")
    public RDoubleVector matmatmult(RAbstractIntVector a, RAbstractDoubleVector b) {
        return matmatmult(RClosures.createIntToDoubleVector(a, na), b);
    }

    @Specialization(order = 91, guards = "vecvec")
    public RDoubleVector vecvecmult(RAbstractIntVector a, RAbstractDoubleVector b) {
        return vecvecmult(RClosures.createIntToDoubleVector(a, na), b);
    }

    @Specialization(order = 92, guards = "matvec")
    public RDoubleVector matvecmult(RAbstractIntVector a, RAbstractDoubleVector b) {
        return matvecmult(RClosures.createIntToDoubleVector(a, na), b);
    }

    @Specialization(order = 93, guards = "vecmat")
    public RDoubleVector vecmatmult(RAbstractIntVector a, RAbstractDoubleVector b) {
        return vecmatmult(RClosures.createIntToDoubleVector(a, na), b);
    }

    @Specialization(order = 95, guards = "matmat")
    public RDoubleVector matmatmult(RAbstractDoubleVector a, RAbstractIntVector b) {
        return matmatmult(a, RClosures.createIntToDoubleVector(b, na));
    }

    @Specialization(order = 96, guards = "vecvec")
    public RDoubleVector vecvecmult(RAbstractDoubleVector a, RAbstractIntVector b) {
        return vecvecmult(a, RClosures.createIntToDoubleVector(b, na));
    }

    @Specialization(order = 97, guards = "matvec")
    public RDoubleVector matvecmult(RAbstractDoubleVector a, RAbstractIntVector b) {
        return matvecmult(a, RClosures.createIntToDoubleVector(b, na));
    }

    @Specialization(order = 98, guards = "vecmat")
    public RDoubleVector vecmatmult(RAbstractDoubleVector a, RAbstractIntVector b) {
        return vecmatmult(a, RClosures.createIntToDoubleVector(b, na));
    }

    @Specialization(order = 100, guards = "matmat")
    public RDoubleVector matmatmult(RAbstractLogicalVector a, RAbstractDoubleVector b) {
        return matmatmult(RClosures.createLogicalToDoubleVector(a, na), b);
    }

    @Specialization(order = 101, guards = "vecvec")
    public RDoubleVector vecvecmult(RAbstractLogicalVector a, RAbstractDoubleVector b) {
        return vecvecmult(RClosures.createLogicalToDoubleVector(a, na), b);
    }

    @Specialization(order = 102, guards = "matvec")
    public RDoubleVector matvecmult(RAbstractLogicalVector a, RAbstractDoubleVector b) {
        return matvecmult(RClosures.createLogicalToDoubleVector(a, na), b);
    }

    @Specialization(order = 103, guards = "vecmat")
    public RDoubleVector vecmatmult(RAbstractLogicalVector a, RAbstractDoubleVector b) {
        return vecmatmult(RClosures.createLogicalToDoubleVector(a, na), b);
    }

    @Specialization(order = 105, guards = "matmat")
    public RDoubleVector matmatmult(RAbstractDoubleVector a, RAbstractLogicalVector b) {
        return matmatmult(a, RClosures.createLogicalToDoubleVector(b, na));
    }

    @Specialization(order = 106, guards = "vecvec")
    public RDoubleVector vecvecmult(RAbstractDoubleVector a, RAbstractLogicalVector b) {
        return vecvecmult(a, RClosures.createLogicalToDoubleVector(b, na));
    }

    @Specialization(order = 107, guards = "matvec")
    public RDoubleVector matvecmult(RAbstractDoubleVector a, RAbstractLogicalVector b) {
        return matvecmult(a, RClosures.createLogicalToDoubleVector(b, na));
    }

    @Specialization(order = 108, guards = "vecmat")
    public RDoubleVector vecmatmult(RAbstractDoubleVector a, RAbstractLogicalVector b) {
        return vecmatmult(a, RClosures.createLogicalToDoubleVector(b, na));
    }

    // errors

    @SuppressWarnings("unused")
    @Specialization(order = 200)
    public RDoubleVector doRaw(RAbstractRawVector a, Object b) {
        throw RError.error(this.getEncapsulatingSourceSection(), RError.Message.NUMERIC_COMPLEX_MATRIX_VECTOR);
    }

    @SuppressWarnings("unused")
    @Specialization(order = 201)
    public RDoubleVector doRaw(Object a, RAbstractRawVector b) {
        throw RError.error(this.getEncapsulatingSourceSection(), RError.Message.NUMERIC_COMPLEX_MATRIX_VECTOR);
    }

    @SuppressWarnings("unused")
    @Specialization(order = 205)
    public RDoubleVector doString(RAbstractStringVector a, Object b) {
        throw RError.error(this.getEncapsulatingSourceSection(), RError.Message.NUMERIC_COMPLEX_MATRIX_VECTOR);
    }

    @SuppressWarnings("unused")
    @Specialization(order = 206)
    public RDoubleVector doString(Object a, RAbstractStringVector b) {
        throw RError.error(this.getEncapsulatingSourceSection(), RError.Message.NUMERIC_COMPLEX_MATRIX_VECTOR);
    }

    // guards

    protected static boolean matmat(RAbstractVector a, RAbstractVector b) {
        return a.isMatrix() && b.isMatrix();
    }

    protected static boolean vecvec(RAbstractVector a, RAbstractVector b) {
        return !a.isMatrix() && !b.isMatrix();
    }

    protected static boolean matvec(RAbstractVector a, RAbstractVector b) {
        return a.isMatrix() && !b.isMatrix();
    }

    protected static boolean vecmat(RAbstractVector a, RAbstractVector b) {
        return !a.isMatrix() && b.isMatrix();
    }

    protected static boolean leftHasZeroDim(RAbstractVector a, @SuppressWarnings("unused") RAbstractVector b) {
        return hasZeroDim(a);
    }

    protected static boolean rightHasZeroDim(@SuppressWarnings("unused") RAbstractVector a, RAbstractVector b) {
        return hasZeroDim(b);
    }

    protected static boolean bothZeroDim(RAbstractVector a, RAbstractVector b) {
        return hasZeroDim(a) && hasZeroDim(b);
    }

    protected static boolean hasZeroDim(RAbstractVector v) {
        if (!v.hasDimensions()) {
            return false;
        }
        for (int d : v.getDimensions()) {
            if (d == 0) {
                return true;
            }
        }
        return false;
    }

}
