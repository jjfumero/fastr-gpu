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
package com.oracle.truffle.r.nodes.access.array.write;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.access.array.*;
import com.oracle.truffle.r.nodes.access.array.ArrayPositionCast.OperatorConverterNode;
import com.oracle.truffle.r.nodes.access.array.ArrayPositionCastNodeGen.OperatorConverterNodeGen;
import com.oracle.truffle.r.nodes.access.array.read.*;
import com.oracle.truffle.r.nodes.control.*;
import com.oracle.truffle.r.nodes.function.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.RDeparse.State;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.gnur.*;
import com.oracle.truffle.r.runtime.nodes.*;
import com.oracle.truffle.r.runtime.ops.na.*;

@SuppressWarnings("unused")
@NodeChildren({@NodeChild(value = "v", type = RNode.class), @NodeChild(value = "newValue", type = RNode.class),
                @NodeChild(value = "positions", type = PositionsArrayNodeValue.class, executeWith = {"v", "newValue"}),
                @NodeChild(value = "vector", type = CoerceVector.class, executeWith = {"newValue", "v", "positions"})})
public abstract class UpdateArrayHelperNode extends RNode {

    protected final boolean isSubset;

    private final NACheck elementNACheck = NACheck.create();
    private final NACheck posNACheck = NACheck.create();
    private final NACheck namesNACheck = NACheck.create();

    protected abstract CoerceVector getVector();

    protected abstract PositionsArrayNodeValue getPositions();

    protected abstract RNode getNewValue();

    public abstract Object executeUpdate(Object v, Object value, Object positions, Object vector);

    @CompilationFinal private boolean recursiveIsSubset;

    @Child private UpdateArrayHelperNode updateRecursive;
    @Child private UpdateArrayHelperNode updateDelegate;
    @Child private CastComplexNode castComplex;
    @Child private CastDoubleNode castDouble;
    @Child private CastIntegerNode castInteger;
    @Child private CastStringNode castString;
    @Child private CoerceVector coerceVector;
    @Child private ArrayPositionCast castPosition;
    @Child private OperatorConverterNode operatorConverter;
    @Child private SetMultiDimDataNode setMultiDimData;

    private final BranchProfile error = BranchProfile.create();
    private final BranchProfile warning = BranchProfile.create();
    private final ConditionProfile negativePosProfile = ConditionProfile.createBinaryProfile();

    private final BranchProfile vectorShared = BranchProfile.create();
    private final BranchProfile vectorTooShort = BranchProfile.create();
    private final BranchProfile vectorNoDims = BranchProfile.create();

    private final ConditionProfile noResultNames = ConditionProfile.createBinaryProfile();
    private final ConditionProfile multiPosProfile = ConditionProfile.createBinaryProfile();

    private final ConditionProfile twoPosProfile = ConditionProfile.createBinaryProfile();

    private final RAttributeProfiles attrProfiles = RAttributeProfiles.create();
    protected final int recLevel;

    protected UpdateArrayHelperNode(boolean isSubset, int recLevel) {
        this.isSubset = isSubset;
        this.recursiveIsSubset = isSubset;
        this.recLevel = recLevel;
    }

    private RIntVector updateVector(RAbstractIntVector value, RAbstractIntVector vector, Object[] positions) {
        int replacementLength = getReplacementLength(positions, value, false);
        RIntVector resultVector = vector.materialize();
        if (replacementLength == 0) {
            return resultVector;
        }
        if (resultVector.isShared()) {
            vectorShared.enter();
            resultVector = (RIntVector) vector.copy();
        }
        if (!FastROptions.NewStateTransition.getBooleanValue()) {
            resultVector.markNonTemporary();
        } else if (resultVector.isTemporary()) {
            resultVector.incRefCount();
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        elementNACheck.enable(value);
        posNACheck.enable(p);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (posNACheck.check(pos)) {
                handleNaMultiDim(value, false, isSubset, this);
                continue;
            }
            int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
            setMultiDimData(value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
        }
        return resultVector;
    }

    private Object updateDelegate(Object v, Object value, Object vector, Object operand) {
        if (updateDelegate == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            updateDelegate = insert(UpdateArrayHelperNodeGen.create(isSubset, recLevel, null, null, null, null));
        }
        return updateDelegate.executeUpdate(v, value, operand, vector);
    }

    private Object updateRecursive(Object v, Object value, Object vector, Object operand) {
        if (updateRecursive == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            updateRecursive = insert(UpdateArrayHelperNodeGen.create(isSubset, recLevel + 1, null, null, null, null));
        }
        return updateRecursive.executeUpdate(v, value, operand, vector);
    }

    private final ConditionProfile needsCastProfile = ConditionProfile.createBinaryProfile();

    private RComplexVector castComplex(Object operand) {
        if (needsCastProfile.profile(!(operand instanceof RComplexVector))) {
            if (castComplex == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castComplex = insert(CastComplexNodeGen.create(true, true, false));
            }
            return (RComplexVector) castComplex.execute(operand);
        } else {
            return (RComplexVector) operand;
        }
    }

    private RDoubleVector castDouble(Object operand) {
        if (needsCastProfile.profile(!(operand instanceof RDoubleVector))) {
            if (castDouble == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castDouble = insert(CastDoubleNodeGen.create(true, true, false));
            }
            return (RDoubleVector) castDouble.execute(operand);
        } else {
            return (RDoubleVector) operand;
        }
    }

    private RIntVector castInteger(Object operand) {
        if (needsCastProfile.profile(!(operand instanceof RIntVector))) {
            if (castInteger == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castInteger = insert(CastIntegerNodeGen.create(true, true, false));
            }
            return (RIntVector) castInteger.execute(operand);
        } else {
            return (RIntVector) operand;
        }
    }

    private RStringVector castString(Object operand) {
        if (needsCastProfile.profile(!(operand instanceof RStringVector))) {
            if (castString == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castString = insert(CastStringNodeGen.create(true, true, false, true));
            }
            return (RStringVector) castString.execute(operand);
        } else {
            return (RStringVector) operand;
        }
    }

    private Object coerceVector(Object vector, Object value, Object operand) {
        if (coerceVector == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            coerceVector = insert(CoerceVectorNodeGen.create(null, null, null));
        }
        return coerceVector.executeEvaluated(value, vector, operand);
    }

    private Object castPosition(Object vector, Object operand) {
        if (castPosition == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castPosition = insert(ArrayPositionCastNodeGen.create(0, 1, true, false, null, null));
        }
        return castPosition.executeArg(vector, operand);
    }

    private void initOperatorConvert() {
        if (operatorConverter == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            operatorConverter = insert(OperatorConverterNodeGen.create(0, 1, true, false, null, null, null));
        }
    }

    private Object convertOperand(Object vector, int operand) {
        initOperatorConvert();
        return operatorConverter.executeConvert(vector, operand, RRuntime.LOGICAL_TRUE);
    }

    private Object convertOperand(Object vector, String operand) {
        initOperatorConvert();
        return operatorConverter.executeConvert(vector, operand, RRuntime.LOGICAL_TRUE);
    }

    private Object setMultiDimData(RAbstractContainer value, RAbstractVector vector, Object[] positions, int currentDimLevel, int srcArrayBase, int dstArrayBase, int accSrcDimensions,
                    int accDstDimensions, NACheck posNACheck, NACheck elementNACheck) {
        if (setMultiDimData == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            setMultiDimData = insert(SetMultiDimDataNodeGen.create(posNACheck, elementNACheck, this.isSubset));
        }
        return setMultiDimData.executeMultiDimDataSet(value, vector, positions, currentDimLevel, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions);
    }

    @CreateCast({"newValue"})
    public RNode createCastValue(RNode child) {
        return new ApplyCastNode(CastToContainerNodeGen.create(false, false, false), child);
    }

    @Specialization
    protected Object update(Object v, RAbstractVector value, Object positions, RFactor factor) {
        return updateDelegate(v, value, factor.getVector(), positions);
    }

    @Specialization
    protected Object update(Object v, Object value, Object positions, RDataFrame dataFrame) {
        return updateDelegate(v, value, dataFrame.getVector(), positions);
    }

    @Specialization(guards = "isSubset")
    protected Object update(Object v, RFactor value, Object positions, Object vector) {
        return updateDelegate(v, value.getVector(), vector, positions);
    }

    @Specialization(guards = {"emptyValue(value)"})
    protected RAbstractVector update(Object v, RAbstractVector value, Object[] positions, RAbstractVector vector) {
        if (isSubset) {
            int replacementLength = getReplacementLength(positions, value, false);
            if (replacementLength == 0) {
                return vector;
            }
        }
        throw RError.error(this, RError.Message.REPLACEMENT_0);
    }

    @TruffleBoundary
    @Specialization
    protected RNull accessFunction(Object v, Object value, Object position, RFunction vector) {
        throw RError.error(this, RError.Message.OBJECT_NOT_SUBSETTABLE, "closure");
    }

    @TruffleBoundary
    @Specialization
    protected RAbstractVector update(Object v, RNull value, Object[] positions, RList vector) {
        if (isSubset) {
            throw RError.error(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        } else {
            throw RError.error(this, RError.Message.SUBSCRIPT_TYPES, "NULL", "list");
        }
    }

    @Specialization(guards = {"isPosZero(position)"})
    protected RAbstractVector updateNAOrZero(Object v, RNull value, int position, RList vector) {
        if (!isSubset) {
            throw RError.error(this, RError.Message.SELECT_LESS_1);
        } else {
            return vector;
        }
    }

    @TruffleBoundary
    @Specialization
    protected RAbstractVector update(Object v, RNull value, Object[] positions, RAbstractVector vector) {
        if (isSubset) {
            throw RError.error(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        } else {
            throw RError.error(this, RError.Message.MORE_SUPPLIED_REPLACE);
        }
    }

    @TruffleBoundary
    @Specialization(guards = {"emptyValue(value)", "!isPosZero(position)", "!isPosNA(position)", "!isVectorList(vector)"})
    protected RAbstractVector update(Object v, RAbstractVector value, int position, RAbstractVector vector) {
        throw RError.error(this, RError.Message.REPLACEMENT_0);
    }

    @TruffleBoundary
    @Specialization
    protected RAbstractVector updateVectorLongerThanOne(Object v, RNull value, RNull position, RList vector) {
        throw RError.error(this, vector.getLength() > 1 ? RError.Message.SELECT_MORE_1 : RError.Message.SELECT_LESS_1);
    }

    @TruffleBoundary
    @Specialization
    protected RAbstractVector update(Object v, RAbstractVector value, RNull position, RAbstractVector vector) {
        throw RError.error(this, RError.Message.SELECT_MORE_1);
    }

    @Specialization(guards = {"isPosNA(position)", "isValueLengthOne(value)"})
    protected RAbstractVector updateNAValueLengthOneLongVector(Object v, RAbstractVector value, int position, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(this, vector.getLength() > 1 ? RError.Message.SELECT_MORE_1 : RError.Message.SELECT_LESS_1);
        } else {
            return vector;
        }
    }

    @TruffleBoundary
    @Specialization(guards = {"isPosNA(position)", "!isValueLengthOne(value)"})
    protected RAbstractVector updateNA(Object v, RAbstractVector value, int position, RAbstractVector vector) {
        if (isSubset) {
            throw RError.error(this, RError.Message.NA_SUBSCRIPTED);
        } else {
            throw RError.error(this, RError.Message.MORE_SUPPLIED_REPLACE);
        }
    }

    private static RError.Message getErrorForValueLength(RAbstractVector value) {
        CompilerAsserts.neverPartOfCompilation();
        if (value.getLength() == 0) {
            return RError.Message.REPLACEMENT_0;
        } else if (value.getLength() == 1) {
            return RError.Message.SELECT_LESS_1;
        } else {
            return RError.Message.MORE_SUPPLIED_REPLACE;
        }
    }

    @Specialization(guards = {"isPosZero(position)"})
    protected RAbstractVector updatePosZero(Object v, RAbstractVector value, int position, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(this, getErrorForValueLength(value));
        }
        return vector;
    }

    private int getSrcArrayBase(int pos, int accSrcDimensions) {
        if (posNACheck.check(pos)) {
            throw RError.error(this, RError.Message.NA_SUBSCRIPTED);
        } else {
            return accSrcDimensions * (pos - 1);
        }
    }

    private final BranchProfile handleNa = BranchProfile.create();

    private int getReplacementLength(Object[] positions, RAbstractContainer value, boolean isList) {
        int valueLength = value.getLength();
        int length = 1;
        boolean seenNA = false;
        for (int i = 0; i < positions.length; i++) {
            RIntVector p = (RIntVector) positions[i];
            int len = p.getLength();
            posNACheck.enable(p);
            boolean allZeros = true;
            for (int j = 0; j < len; j++) {
                int pos = p.getDataAt(j);
                if (pos != 0) {
                    allZeros = false;
                    if (posNACheck.check(pos)) {
                        if (len == 1) {
                            handleNaMultiDim(value, isList, isSubset, this);
                        } else {
                            seenNA = true;
                        }
                    }
                }
            }
            length = allZeros ? 0 : length * p.getLength();
        }
        if (valueLength != 0 && length != 0 && length % valueLength != 0) {
            error.enter();
            throw RError.error(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        } else if (seenNA) {
            handleNa.enter();
            handleNaMultiDim(value, isList, isSubset, this);
        }
        return length;
    }

    private int getHighestPos(RIntVector positions) {
        int highestPos = 0;
        posNACheck.enable(positions);
        int numNAs = 0;
        for (int i = 0; i < positions.getLength(); i++) {
            int pos = positions.getDataAt(i);
            if (posNACheck.check(pos)) {
                // ignore
                numNAs++;
            } else if (pos < 0) {
                if (-pos > highestPos) {
                    highestPos = -pos;
                }
            } else if (pos > highestPos) {
                highestPos = pos;
            }
        }
        return numNAs == positions.getLength() ? numNAs : highestPos;
    }

    private RStringVector getNamesVector(RVector resultVector) {
        if (noResultNames.profile(resultVector.getNames(attrProfiles) == null)) {
            String[] namesData = new String[resultVector.getLength()];
            Arrays.fill(namesData, RRuntime.NAMES_ATTR_EMPTY_VALUE);
            RStringVector names = RDataFactory.createStringVector(namesData, RDataFactory.COMPLETE_VECTOR);
            resultVector.setNames(names);
            return names;
        } else {
            return resultVector.getNames(attrProfiles);
        }
    }

    private final BranchProfile posNames = BranchProfile.create();

    private void updateNames(RVector resultVector, RIntVector positions) {
        if (positions.getNames(attrProfiles) != null) {
            posNames.enter();
            RStringVector names = getNamesVector(resultVector);
            if (names.isShared()) {
                names = (RStringVector) names.copy();
            }
            RStringVector newNames = positions.getNames(attrProfiles);
            namesNACheck.enable(newNames);
            for (int i = 0; i < positions.getLength(); i++) {
                int p = positions.getDataAt(i);
                names.updateDataAt(p - 1, newNames.getDataAt(i), namesNACheck);
            }
        }
    }

    // null

    @Specialization
    protected RNull updateWrongDimensions(Object v, RNull value, Object[] positions, RNull vector) {
        return vector;
    }

    @Specialization(guards = {"!wrongDimensionsMatrix(positions, vector)", "!wrongDimensions(positions, vector)"})
    protected RNull updateWrongDimensions(Object v, RAbstractVector value, Object[] positions, RNull vector) {
        return vector;
    }

    @Specialization(guards = "emptyValue(value)")
    protected RNull updatePosZero(Object v, RAbstractVector value, int position, RNull vector) {
        if (!isSubset) {
            throw RError.error(this, RError.Message.REPLACEMENT_0);
        }
        return vector;
    }

    @Specialization(guards = "emptyValue(value)")
    protected RNull updatePosZero(Object v, RAbstractVector value, RIntVector positions, RNull vector) {
        if (!isSubset) {
            throw RError.error(this, RError.Message.REPLACEMENT_0);
        }
        return vector;
    }

    @Specialization(guards = "!emptyValue(value)")
    protected RIntVector update(Object v, RAbstractIntVector value, RIntVector positions, RNull vector) {
        int highestPos = getHighestPos(positions);
        int[] data = new int[highestPos];
        Arrays.fill(data, RRuntime.INT_NA);
        return updateSingleDimVector(value, 0, RDataFactory.createIntVector(data, RDataFactory.INCOMPLETE_VECTOR), positions);
    }

    @Specialization(guards = {"!emptyValue(value)", "!isPosNA(position)", "!isPosZero(position)"})
    protected RIntVector update(Object v, RAbstractIntVector value, int position, RNull vector) {
        if (multiPosProfile.profile(position > 1)) {
            int[] data = new int[position];
            Arrays.fill(data, RRuntime.INT_NA);
            return updateSingleDim(value, RDataFactory.createIntVector(data, RDataFactory.INCOMPLETE_VECTOR), position);
        } else {
            return updateSingleDim(value, RDataFactory.createIntVector(position), position);
        }
    }

    @Specialization(guards = "!emptyValue(value)")
    protected RDoubleVector update(Object v, RAbstractDoubleVector value, RIntVector positions, RNull vector) {
        int highestPos = getHighestPos(positions);
        double[] data = new double[highestPos];
        Arrays.fill(data, RRuntime.DOUBLE_NA);
        return updateSingleDimVector(value, 0, RDataFactory.createDoubleVector(data, RDataFactory.INCOMPLETE_VECTOR), positions);
    }

    @Specialization(guards = {"!emptyValue(value)", "!isPosNA(position)", "!isPosZero(position)"})
    protected RDoubleVector update(Object v, RAbstractDoubleVector value, int position, RNull vector) {
        if (multiPosProfile.profile(position > 1)) {
            double[] data = new double[position];
            Arrays.fill(data, RRuntime.DOUBLE_NA);
            return updateSingleDim(value, RDataFactory.createDoubleVector(data, RDataFactory.INCOMPLETE_VECTOR), position);
        } else {
            return updateSingleDim(value, RDataFactory.createDoubleVector(position), position);
        }
    }

    @Specialization(guards = "!emptyValue(value)")
    protected RLogicalVector update(Object v, RAbstractLogicalVector value, RIntVector positions, RNull vector) {
        int highestPos = getHighestPos(positions);
        byte[] data = new byte[highestPos];
        Arrays.fill(data, RRuntime.LOGICAL_NA);
        return updateSingleDimVector(value, 0, RDataFactory.createLogicalVector(data, RDataFactory.INCOMPLETE_VECTOR), positions);
    }

    @Specialization(guards = {"!emptyValue(value)", "!isPosNA(position)", "!isPosZero(position)"})
    protected RLogicalVector update(Object v, RAbstractLogicalVector value, int position, RNull vector) {
        if (multiPosProfile.profile(position > 1)) {
            byte[] data = new byte[position];
            Arrays.fill(data, RRuntime.LOGICAL_NA);
            return updateSingleDim(value, RDataFactory.createLogicalVector(data, RDataFactory.INCOMPLETE_VECTOR), position);
        } else {
            return updateSingleDim(value, RDataFactory.createLogicalVector(position), position);
        }
    }

    @Specialization(guards = "!emptyValue(value)")
    protected RStringVector update(Object v, RAbstractStringVector value, RIntVector positions, RNull vector) {
        int highestPos = getHighestPos(positions);
        String[] data = new String[highestPos];
        Arrays.fill(data, RRuntime.STRING_NA);
        return updateSingleDimVector(value, 0, RDataFactory.createStringVector(data, RDataFactory.INCOMPLETE_VECTOR), positions);
    }

    @Specialization(guards = {"!emptyValue(value)", "!isPosNA(position)", "!isPosZero(position)"})
    protected RStringVector update(Object v, RAbstractStringVector value, int position, RNull vector) {
        if (multiPosProfile.profile(position > 1)) {
            String[] data = new String[position];
            Arrays.fill(data, RRuntime.STRING_NA);
            return updateSingleDim(value, RDataFactory.createStringVector(data, RDataFactory.INCOMPLETE_VECTOR), position);
        } else {
            return updateSingleDim(value, RDataFactory.createStringVector(position), position);
        }
    }

    @Specialization(guards = "!emptyValue(value)")
    protected RComplexVector update(Object v, RAbstractComplexVector value, RIntVector positions, RNull vector) {
        int highestPos = getHighestPos(positions);
        double[] data = new double[highestPos << 1];
        int ind = 0;
        for (int i = 0; i < highestPos; i++) {
            data[ind++] = RRuntime.COMPLEX_NA_REAL_PART;
            data[ind++] = RRuntime.COMPLEX_NA_IMAGINARY_PART;
        }
        return updateSingleDimVector(value, 0, RDataFactory.createComplexVector(data, RDataFactory.INCOMPLETE_VECTOR), positions);
    }

    @Specialization(guards = {"!emptyValue(value)", "!isPosNA(position)", "!isPosZero(position)"})
    protected RComplexVector update(Object v, RAbstractComplexVector value, int position, RNull vector) {
        if (multiPosProfile.profile(position > 1)) {
            double[] data = new double[position << 1];
            int ind = 0;
            for (int i = 0; i < position; i++) {
                data[ind++] = RRuntime.COMPLEX_NA_REAL_PART;
                data[ind++] = RRuntime.COMPLEX_NA_IMAGINARY_PART;
            }
            return updateSingleDim(value, RDataFactory.createComplexVector(data, RDataFactory.INCOMPLETE_VECTOR), position);
        } else {
            return updateSingleDim(value, RDataFactory.createComplexVector(position), position);
        }
    }

    @Specialization(guards = "!emptyValue(value)")
    protected RRawVector update(Object v, RAbstractRawVector value, RIntVector positions, RNull vector) {
        return updateSingleDimVector(value, 0, RDataFactory.createRawVector(getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!emptyValue(value)", "!isPosNA(position)", "!isPosZero(position)"})
    protected RRawVector update(Object v, RAbstractRawVector value, int position, RNull vector) {
        return updateSingleDim(value, RDataFactory.createRawVector(position), position);
    }

    @TruffleBoundary
    @Specialization(guards = {"!isPosNA(position)", "isPositionNegative(position)", "!isVectorList(vector)"})
    protected RList updateNegativeNull(Object v, RNull value, int position, RAbstractVector vector) {
        throw RError.error(this, RError.Message.MORE_SUPPLIED_REPLACE);
    }

    @TruffleBoundary
    @Specialization(guards = {"!isPosNA(position)", "isPositionNegative(position)"})
    protected RList updateNegativeNull(Object v, Object value, int position, RAbstractVector vector) {
        throw RError.error(this, -position <= vector.getLength() || vector.getLength() != 1 ? RError.Message.SELECT_MORE_1 : RError.Message.SELECT_LESS_1);
    }

    // list

    private RList updateVector(RAbstractContainer value, RList vector, Object[] positions) {
        int replacementLength = getReplacementLength(positions, value, true);
        RList resultVector = vector;
        if (replacementLength == 0) {
            return resultVector;
        }
        if (vector.isShared()) {
            vectorShared.enter();
            resultVector = (RList) vector.copy();
            if (!FastROptions.NewStateTransition.getBooleanValue()) {
                resultVector.markNonTemporary();
            } else if (resultVector.isTemporary()) {
                resultVector.incRefCount();
            }
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        posNACheck.enable(p);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (posNACheck.check(pos)) {
                handleNaMultiDim(value, true, isSubset, this);
                continue;
            }
            int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
            setMultiDimData(value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
        }
        return resultVector;
    }

    private RList getResultVector(RList vector, int highestPos, boolean resetDims) {
        RList resultVector = vector;
        if (resultVector.getLength() < highestPos) {
            vectorTooShort.enter();
            int orgLength = resultVector.getLength();
            resultVector = (RList) resultVector.resize(highestPos, false);
            for (int i = orgLength; i < highestPos; i++) {
                resultVector.updateDataAt(i, RNull.instance, null);
            }
        } else if (resultVector.isShared()) {
            vectorShared.enter();
            resultVector = (RList) vector.copy();
        }
        if (!FastROptions.NewStateTransition.getBooleanValue()) {
            resultVector.markNonTemporary();
        } else if (resultVector.isTemporary()) {
            resultVector.incRefCount();
        }
        if (resetDims) {
            vectorNoDims.enter();
            resultVector.setDimensions(null);
            resultVector.setDimNames(null);
        }
        return resultVector;
    }

    private int getPositionInRecursion(RList vector, int position, boolean lastPos) {
        if (RRuntime.isNA(position)) {
            error.enter();
            if (lastPos && recLevel > 0) {
                throw RError.error(this, RError.Message.SELECT_LESS_1);
            } else if (recLevel == 0) {
                throw RError.error(this, RError.Message.SELECT_MORE_1);
            } else {
                throw RError.error(this, RError.Message.NO_SUCH_INDEX, recLevel + 1);
            }
        } else if (!lastPos && position > vector.getLength()) {
            error.enter();
            throw RError.error(this, RError.Message.NO_SUCH_INDEX, recLevel + 1);
        } else if (position < 0) {
            error.enter();
            return AccessArrayNode.getPositionFromNegative(vector, position, this, error);
        } else if (position == 0) {
            error.enter();
            throw RError.error(this, RError.Message.SELECT_LESS_1);
        }
        return position;
    }

    private static RList updateSingleDim(RAbstractContainer value, RList resultVector, int position) {
        resultVector.updateDataAt(position - 1, value.getDataAtAsObject(0), null);
        return resultVector;
    }

    private final BranchProfile valShared = BranchProfile.create();
    private final BranchProfile valNonTmp = BranchProfile.create();
    private final BranchProfile valTmp = BranchProfile.create();
    private final ConditionProfile isShared = ConditionProfile.createBinaryProfile();

    // this is similar to what happens on "regular" (non-vector) assignment - the state has to
    // change to avoid erroneous sharing
    private RShareable adjustRhsStateOnAssignment(RAbstractContainer value) {
        RShareable val = value.materializeToShareable();
        if (FastROptions.NewStateTransition.getBooleanValue()) {
            if (isShared.profile(val.isShared())) {
                val = val.copy();
            } else {
                val.incRefCount();
            }
        } else {
            if (val.isShared()) {
                valShared.enter();
                val = val.copy();
            } else if (!val.isTemporary()) {
                valNonTmp.enter();
                val.makeShared();
            } else {
                assert val.isTemporary();
                valTmp.enter();
                val.markNonTemporary();
            }
        }
        return val;
    }

    private RList updateSingleDimRec(RAbstractContainer value, RList resultVector, RIntVector p) {
        int position = getPositionInRecursion(resultVector, p.getDataAt(0), true);
        resultVector.updateDataAt(position - 1, adjustRhsStateOnAssignment(value), null);
        updateNames(resultVector, p);
        return resultVector;
    }

    private RList updateSingleDimVector(RAbstractContainer value, int orgVectorLength, RList resultVector, RIntVector positions) {
        if (positions.getLength() == 1 && value.getLength() > 1) {
            error.enter();
            throw RError.error(this, RError.Message.MORE_SUPPLIED_REPLACE);
        }
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNaOrNegative(p, value)) {
                continue;
            }
            if (negativePosProfile.profile(p < 0 && -(p + 1) >= orgVectorLength)) {
                resultVector.updateDataAt(-(p + 1), RNull.instance, null);
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAtAsObject(i % value.getLength()), null);
            }
        }
        if (positions.getLength() % value.getLength() != 0) {
            warning.enter();
            RError.warning(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    private Object updateListRecursive(Object v, Object value, RList vector, RStringVector p) {
        int position = AccessArrayNode.getPositionInRecursion(vector, p.getDataAt(0), recLevel, this, error, attrProfiles);
        if (p.getLength() == 2 && RRuntime.isNA(p.getDataAt(1))) {
            // catch it here, otherwise it will get caught at lower level of recursion resulting in
            // a different message
            error.enter();
            throw RError.error(this, RError.Message.SELECT_LESS_1);
        }
        Object el;
        RList resultList = vector;
        if (recLevel == 0) {
            // TODO: does it matter to make it smarter (mark nested lists as shared during copy?)
            resultList = (RList) vector.deepCopy();
        }
        if (twoPosProfile.profile(p.getLength() == 2)) {
            Object finalVector = coerceVector(resultList.getDataAt(position - 1), value, p);
            Object lastPosition = castPosition(finalVector, convertOperand(finalVector, p.getDataAt(1)));
            el = updateRecursive(v, value, finalVector, lastPosition);
        } else {
            RStringVector newP = AccessArrayNode.popHead(p, posNACheck);
            el = updateRecursive(v, value, resultList.getDataAt(position - 1), newP);
        }

        resultList.updateDataAt(position - 1, el, null);
        return resultList;
    }

    @Specialization(guards = {"multiDim(vector)", "!wrongDimensionsMatrix(positions, vector)", "!wrongDimensions(positions, vector)"})
    protected RList update(Object v, RAbstractContainer value, Object[] positions, RList vector) {
        return updateVector(value, vector, positions);
    }

    @Specialization
    protected Object updateString(Object v, RNull value, RStringVector positions, RList vector) {
        return updateListRecursive(v, value, vector, positions);
    }

    @Specialization
    protected Object updateString(Object v, RAbstractContainer value, RStringVector positions, RList vector) {
        return updateListRecursive(v, value, vector, positions);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "multiPos(positions)"})
    protected RList update(Object v, RAbstractContainer value, RIntVector positions, RList vector) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions), false), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "onePosition(positions)"})
    protected Object updateOne(Object v, RAbstractContainer value, RIntVector positions, RList vector) {
        return updateDelegate(v, value, vector, positions.getDataAt(0));
    }

    @Specialization(guards = {"isSubset", "posNames(positions)"})
    protected RList updateNames(Object v, RAbstractContainer value, RIntVector positions, RList vector) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions), false), positions);
    }

    @Specialization(guards = {"!isValueLengthOne(value)", "isSubset", "!isPosNA(position)", "!isPosZero(position)", "!isPositionNegative(position)"})
    protected RList updateTooManyValuesSubset(Object v, RAbstractContainer value, int position, RList vector) {
        RError.warning(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(value, getResultVector(vector, position, false), position);
    }

    @Specialization(guards = {"isValueLengthOne(value)", "isSubset", "!isPosNA(position)", "!isPosZero(position)", "!isPositionNegative(position)"})
    protected RList update(Object v, RAbstractContainer value, int position, RList vector) {
        return updateSingleDim(value, getResultVector(vector, position, false), position);
    }

    @Specialization(guards = {"!isSubset", "!isPosNA(position)", "!isPosZero(position)", "!isPositionNegative(position)"})
    protected RList updateTooManyValuesSubscript(Object v, RAbstractContainer value, int position, RList vector) {
        RList resultVector = getResultVector(vector, position, false);
        resultVector.updateDataAt(position - 1, adjustRhsStateOnAssignment(value), null);
        return resultVector;
    }

    @Specialization(guards = {"isPosNA(position)"})
    protected RList updateListNullValue(Object v, RNull value, int position, RList vector) {
        return vector;
    }

    @Specialization(guards = {"!isPosZero(position)", "emptyList(vector)", "!isPosNA(position)", "!isPositionNegative(position)"})
    protected RList updateEmptyList(Object v, RNull value, int position, RList vector) {
        return vector;
    }

    private RList removeElement(RList vector, int position, boolean inRecursion, boolean resetDims) {
        if (position > vector.getLength()) {
            vectorTooShort.enter();
            if (inRecursion || !isSubset) {
                // simply return the vector unchanged
                return vector;
            } else {
                // this is equivalent to extending the vector to appropriate length and then
                // removing the last element
                return getResultVector(vector, position - 1, resetDims);
            }
        }
        Object[] data = new Object[vector.getLength() - 1];
        RStringVector orgNames = null;
        String[] namesData = null;
        if (vector.getNames(attrProfiles) != null) {
            namesData = new String[vector.getLength() - 1];
            orgNames = vector.getNames(attrProfiles);
        }

        int ind = 0;
        for (int i = 0; i < vector.getLength(); i++) {
            if (i != (position - 1)) {
                data[ind] = vector.getDataAt(i);
                if (orgNames != null) {
                    namesData[ind] = orgNames.getDataAt(i);
                }
                ind++;
            }
        }

        RList result = RDataFactory.createList(data, orgNames == null ? null : RDataFactory.createStringVector(namesData, vector.isComplete()));
        result.copyRegAttributesFrom(vector);
        return result;
    }

    @Specialization(guards = {"!isPosZero(position)", "!emptyList(vector)", "!isPosNA(position)", "!isPositionNegative(position)"})
    protected RList update(Object v, RNull value, int position, RList vector) {
        return removeElement(vector, position, false, isSubset);
    }

    private static final Object DELETE_MARKER = new Object();

    @Specialization(guards = {"isSubset", "noPosition(positions)"})
    protected RList updateEmptyPos(Object v, RNull value, RIntVector positions, RList vector) {
        return vector;
    }

    @Specialization(guards = {"isSubset", "!noPosition(positions)"})
    protected RList update(Object v, RNull value, RIntVector positions, RList vector) {
        if (!isSubset) {
            throw RError.error(this, RError.Message.MORE_SUPPLIED_REPLACE);
        }

        RList list = vector;
        if (list.isShared()) {
            vectorShared.enter();
            list = (RList) vector.copy();
        }
        if (!FastROptions.NewStateTransition.getBooleanValue()) {
            list.markNonTemporary();
        } else if (list.isTemporary()) {
            list.incRefCount();
        }
        int highestPos = getHighestPos(positions);
        if (list.getLength() < highestPos) {
            vectorTooShort.enter();
            // to mark duplicate deleted elements with positions > vector length
            list = list.copyResized(highestPos, false);
        }
        int posDeleted = 0;
        for (int i = 0; i < positions.getLength(); i++) {
            int pos = positions.getDataAt(i);
            if (RRuntime.isNA(pos) || pos < 0) {
                continue;
            }
            if (list.getDataAt(pos - 1) != DELETE_MARKER) {
                list.updateDataAt(pos - 1, DELETE_MARKER, null);
                // count each position only once
                posDeleted++;
            }
        }
        int resultVectorLength = highestPos > list.getLength() ? highestPos - posDeleted : list.getLength() - posDeleted;
        Object[] data = new Object[resultVectorLength];

        RList result;
        if (noResultNames.profile(vector.getNames(attrProfiles) == null)) {
            int ind = 0;
            for (int i = 0; i < vector.getLength(); i++) {
                Object el = list.getDataAt(i);
                if (el != DELETE_MARKER) {
                    data[ind] = el;
                    ind++;
                }
            }
            Arrays.fill(data, ind, data.length, RNull.instance);
            result = RDataFactory.createList(data);
        } else {
            String[] namesData = new String[resultVectorLength];
            RStringVector orgNames = vector.getNames(attrProfiles);
            int ind = 0;
            for (int i = 0; i < vector.getLength(); i++) {
                Object el = list.getDataAt(i);
                if (el != DELETE_MARKER) {
                    data[ind] = el;
                    namesData[ind] = orgNames.getDataAt(i);
                    ind++;
                }
            }
            Arrays.fill(data, ind, data.length, RNull.instance);
            Arrays.fill(namesData, ind, data.length, RRuntime.NAMES_ATTR_EMPTY_VALUE);
            result = RDataFactory.createList(data, RDataFactory.createStringVector(namesData, orgNames.isComplete()));
        }
        result.copyRegAttributesFrom(vector);
        return result;
    }

    private Object updateListRecursive(Object v, Object value, RList vector, RIntVector p) {
        int position = getPositionInRecursion(vector, p.getDataAt(0), false);
        if (p.getLength() == 2 && RRuntime.isNA(p.getDataAt(1))) {
            // catch it here, otherwise it will get caught at lower level of recursion resulting in
            // a different message
            error.enter();
            throw RError.error(this, RError.Message.SELECT_LESS_1);
        }
        RList resultList = vector;
        if (recLevel == 0) {
            // TODO: does it matter to make it smarter (mark nested lists as shared during copy?)
            resultList = (RList) vector.deepCopy();
        }
        Object el;
        if (twoPosProfile.profile(p.getLength() == 2)) {
            Object finalVector = coerceVector(resultList.getDataAt(position - 1), value, p);
            Object lastPosition = castPosition(finalVector, convertOperand(finalVector, p.getDataAt(1)));
            el = updateRecursive(v, value, finalVector, lastPosition);
        } else {
            RIntVector newP = AccessArrayNode.popHead(p, posNACheck);
            el = updateRecursive(v, value, resultList.getDataAt(position - 1), newP);
        }

        resultList.updateDataAt(position - 1, el, null);
        return resultList;
    }

    @Specialization(guards = {"!isSubset", "multiPos(positions)"})
    protected Object access(Object v, RNull value, RIntVector positions, RList vector) {
        return updateListRecursive(v, value, vector, positions);
    }

    @Specialization(guards = {"!isSubset", "multiPos(positions)"})
    protected Object access(Object v, RAbstractContainer value, RIntVector positions, RList vector) {
        return updateListRecursive(v, value, vector, positions);
    }

    @Specialization(guards = {"!isSubset", "recLevel > 0", "multiPos(positions)"})
    protected Object accessRecFailed(Object v, RAbstractContainer value, RIntVector positions, RAbstractVector vector) {
        throw RError.error(this, RError.Message.RECURSIVE_INDEXING_FAILED, recLevel + 1);
    }

    @Specialization(guards = {"!isSubset", "!multiPos(positions)"})
    protected Object accessSubscriptListValue(Object v, RList value, RIntVector positions, RList vector) {
        int position = getPositionInRecursion(vector, positions.getDataAt(0), true);
        return updateSingleDimRec(value, getResultVector(vector, position, false), positions);
    }

    @Specialization(guards = {"!isSubset", "recLevel > 0", "!multiPos(positions)"})
    protected Object accessSubscriptNullValueInRecursion(Object v, RNull value, RIntVector positions, RList vector) {
        int position = getPositionInRecursion(vector, positions.getDataAt(0), true);
        return removeElement(vector, position, true, false);
    }

    @Specialization(guards = {"!isSubset", "recLevel == 0", "!multiPos(positions)"})
    protected Object accessSubscriptNullValue(Object v, RNull value, RIntVector positions, RList vector) {
        int position = getPositionInRecursion(vector, positions.getDataAt(0), true);
        return removeElement(vector, position, false, false);
    }

    @Specialization(guards = {"!isSubset", "!multiPos(positions)"})
    protected Object accessSubscript(Object v, RAbstractContainer value, RIntVector positions, RList vector) {
        int position = getPositionInRecursion(vector, positions.getDataAt(0), true);
        return updateSingleDimRec(value, getResultVector(vector, position, false), positions);
    }

    @TruffleBoundary
    @Specialization(guards = {"!isValueLengthOne(value)", "!emptyValue(value)", "!isSubset", "!isPosNA(position)", "!isPosZero(position)"})
    protected RAbstractVector updateTooManyValues(Object v, RAbstractContainer value, int position, RAbstractVector vector) {
        throw RError.error(this, RError.Message.MORE_SUPPLIED_REPLACE);
    }

    // null value (with vectors)

    @Specialization(guards = {"isPosZero(position)", "!isVectorList(vector)"})
    protected RAbstractVector updatePosZero(Object v, RNull value, int position, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(this, RError.Message.REPLACEMENT_0);
        }
        return vector;
    }

    @TruffleBoundary
    @Specialization(guards = {"!isPosZero(position)", "!isPosNA(position)", "!isVectorList(vector)"})
    protected RAbstractVector update(Object v, RNull value, int position, RAbstractVector vector) {
        if (!isSubset) {
            throw RError.error(this, RError.Message.MORE_SUPPLIED_REPLACE);
        } else {
            throw RError.error(this, RError.Message.REPLACEMENT_0);
        }
    }

    @Specialization(guards = {"isSubset", "!isVectorList(vector)", "noPosition(positions)"})
    protected RAbstractVector updateNullSubsetNoPos(Object v, RNull value, RIntVector positions, RAbstractVector vector) {
        return vector;
    }

    @TruffleBoundary
    @Specialization(guards = {"isSubset", "!isVectorList(vector)", "!noPosition(positions)"})
    protected RAbstractVector updateNullSubset(Object v, RNull value, RIntVector positions, RAbstractVector vector) {
        throw RError.error(this, RError.Message.REPLACEMENT_0);
    }

    @TruffleBoundary
    @Specialization(guards = {"!isSubset", "!isVectorList(vector)"})
    protected RAbstractVector updateNullNoPos(Object v, RNull value, RIntVector positions, RAbstractVector vector) {
        RError.Message message = positions.getLength() <= 1 ? RError.Message.MORE_SUPPLIED_REPLACE : (positions.getLength() == 2 && positions.getDataAt(0) == 0) ? RError.Message.SELECT_LESS_1
                        : RError.Message.SELECT_MORE_1;
        throw RError.error(this, message);
    }

    // int vector

    @TruffleBoundary
    @Specialization(guards = {"!isSubset", "!isVectorList(vector)", "!posNames(positions)", "multiPos(positions)"})
    protected Object update(Object v, RAbstractVector value, RIntVector positions, RAbstractVector vector) {
        throw RError.error(this, positions.getLength() == 2 && positions.getDataAt(0) == 0 ? RError.Message.SELECT_LESS_1 : RError.Message.SELECT_MORE_1);
    }

    private RIntVector getResultVector(RAbstractIntVector vector, int highestPos) {
        RIntVector resultVector = vector.materialize();
        if (resultVector.getLength() < highestPos) {
            vectorTooShort.enter();
            resultVector = (RIntVector) resultVector.resize(highestPos, false);
        } else if (resultVector.isShared()) {
            vectorShared.enter();
            resultVector = (RIntVector) vector.copy();
        }
        if (!FastROptions.NewStateTransition.getBooleanValue()) {
            resultVector.markNonTemporary();
        } else if (resultVector.isTemporary()) {
            resultVector.incRefCount();
        }
        return resultVector;
    }

    private RIntVector updateSingleDim(RAbstractIntVector value, RIntVector resultVector, int position) {
        elementNACheck.enable(value);
        resultVector.updateDataAt(position - 1, value.getDataAt(0), elementNACheck);
        return resultVector;
    }

    private final ConditionProfile naOrNegativeProfile = ConditionProfile.createBinaryProfile();

    private boolean seenNaOrNegative(int p, RAbstractContainer value) {
        if (naOrNegativeProfile.profile(posNACheck.check(p) || p < 0)) {
            if (value.getLength() == 1) {
                return true;
            } else {
                throw RError.error(this, RError.Message.NA_SUBSCRIPTED);
            }
        } else {
            return false;
        }
    }

    protected static void handleNaMultiDim(RAbstractContainer value, boolean isList, boolean isSubset, RBaseNode invokingNode) {
        if (value.getLength() == 1) {
            if (!isSubset) {
                throw RError.error(invokingNode, RError.Message.SUBSCRIPT_BOUNDS_SUB);
            }
        } else {
            if (!isSubset) {
                if (isList) {
                    throw RError.error(invokingNode, RError.Message.SUBSCRIPT_BOUNDS_SUB);
                } else {
                    throw RError.error(invokingNode, RError.Message.MORE_SUPPLIED_REPLACE);
                }
            } else {
                throw RError.error(invokingNode, RError.Message.NA_SUBSCRIPTED);
            }
        }
    }

    private RIntVector updateSingleDimVector(RAbstractIntVector value, int orgVectorLength, RIntVector resultVector, RIntVector positions) {
        if (positions.getLength() == 1 && value.getLength() > 1) {
            error.enter();
            throw RError.error(this, RError.Message.MORE_SUPPLIED_REPLACE);
        }
        elementNACheck.enable(value);
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNaOrNegative(p, value)) {
                continue;
            }
            if (negativePosProfile.profile(p < 0 && -(p + 1) >= orgVectorLength)) {
                resultVector.updateDataAt(-(p + 1), RRuntime.INT_NA, elementNACheck);
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAt(i % value.getLength()), elementNACheck);
            }
        }
        if (positions.getLength() % value.getLength() != 0) {
            warning.enter();
            RError.warning(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    @Specialization(guards = {"multiDim(vector)", "!wrongDimensionsMatrix(positions, vector)", "!wrongDimensions(positions, vector)"})
    protected RIntVector update(Object v, RAbstractIntVector value, Object[] positions, RAbstractIntVector vector) {
        return updateVector(value, vector, positions);
    }

    @Specialization(guards = {"multiDim(vector)", "!wrongDimensionsMatrix(positions, vector)", "!wrongDimensions(positions, vector)"})
    protected RIntVector update(Object v, RAbstractLogicalVector value, Object[] positions, RAbstractIntVector vector) {
        return updateVector(castInteger(value), vector, positions);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "multiPos(positions)"})
    protected RAbstractIntVector updateSubset(Object v, RAbstractIntVector value, RIntVector positions, RAbstractIntVector vector) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "multiPos(positions)"})
    protected RAbstractIntVector updateSubset(Object v, RAbstractLogicalVector value, RIntVector positions, RAbstractIntVector vector) {
        return updateSubset(v, castInteger(value), positions, vector);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "multiPos(positions)"})
    protected RAbstractIntVector updateSubset(Object v, RAbstractDoubleVector value, RIntVector positions, RAbstractIntVector vector) {
        return updateSubset(v, castInteger(value), positions, vector);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "onePosition(positions)"})
    protected Object updateSubsetOne(Object v, RAbstractIntVector value, RIntVector positions, RAbstractIntVector vector) {
        return updateDelegate(v, value, vector, positions.getDataAt(0));
    }

    @Specialization(guards = {"posNames(positions)"})
    protected RAbstractIntVector updateNames(Object v, RAbstractIntVector value, RIntVector positions, RAbstractIntVector vector) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"posNames(positions)"})
    protected RAbstractIntVector updateNames(Object v, RAbstractLogicalVector value, RIntVector positions, RAbstractIntVector vector) {
        return updateNames(v, castInteger(value), positions, vector);
    }

    @Specialization(guards = {"!isValueLengthOne(value)", "isSubset", "!isPosNA(position)", "!isPosZero(position)"})
    protected RIntVector updateTooManyValuesSubset(Object v, RAbstractIntVector value, int position, RAbstractIntVector vector) {
        RError.warning(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(guards = {"!isValueLengthOne(value)", "isSubset", "!isPosNA(position)", "!isPosZero(position)"})
    protected RIntVector updateTooManyValuesSubset(Object v, RAbstractLogicalVector value, int position, RAbstractIntVector vector) {
        return updateTooManyValuesSubset(v, castInteger(value), position, vector);
    }

    @Specialization(guards = {"isValueLengthOne(value)", "!isPosNA(position)", "!isPosZero(position)"})
    protected RIntVector update(Object v, RAbstractIntVector value, int position, RAbstractIntVector vector) {
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "onePosition(positions)"})
    protected Object updateSubsetOne(Object v, RAbstractLogicalVector value, RIntVector positions, RAbstractIntVector vector) {
        return updateDelegate(v, value, vector, positions.getDataAt(0));
    }

    @Specialization(guards = {"isValueLengthOne(value)", "!isPosNA(position)", "!isPosZero(position)"})
    protected RIntVector update(Object v, RAbstractLogicalVector value, int position, RAbstractIntVector vector) {
        return updateSingleDim(castInteger(value), getResultVector(vector, position), position);
    }

    // double vector

    private RDoubleVector updateVector(RAbstractDoubleVector value, RAbstractDoubleVector vector, Object[] positions) {
        int replacementLength = getReplacementLength(positions, value, false);
        RDoubleVector resultVector = vector.materialize();
        if (replacementLength == 0) {
            return resultVector;
        }
        if (resultVector.isShared()) {
            vectorShared.enter();
            resultVector = (RDoubleVector) vector.copy();
        }
        if (!FastROptions.NewStateTransition.getBooleanValue()) {
            resultVector.markNonTemporary();
        } else if (resultVector.isTemporary()) {
            resultVector.incRefCount();
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        elementNACheck.enable(value);
        posNACheck.enable(p);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (posNACheck.check(pos)) {
                handleNaMultiDim(value, false, isSubset, this);
                continue;
            }
            int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
            setMultiDimData(value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
        }
        return resultVector;
    }

    private RDoubleVector getResultVector(RAbstractDoubleVector vector, int highestPos) {
        RDoubleVector resultVector = vector.materialize();
        if (resultVector.getLength() < highestPos) {
            vectorTooShort.enter();
            resultVector = (RDoubleVector) resultVector.resize(highestPos, false);
        } else if (resultVector.isShared()) {
            vectorShared.enter();
            resultVector = (RDoubleVector) vector.copy();
        }
        if (!FastROptions.NewStateTransition.getBooleanValue()) {
            resultVector.markNonTemporary();
        } else if (resultVector.isTemporary()) {
            resultVector.incRefCount();
        }
        return resultVector;
    }

    private RDoubleVector updateSingleDim(RAbstractDoubleVector value, RDoubleVector resultVector, int position) {
        elementNACheck.enable(value);
        resultVector.updateDataAt(position - 1, value.getDataAt(0), elementNACheck);
        return resultVector;
    }

    private RDoubleVector updateSingleDimVector(RAbstractDoubleVector value, int orgVectorLength, RDoubleVector resultVector, RIntVector positions) {
        if (positions.getLength() == 1 && value.getLength() > 1) {
            error.enter();
            throw RError.error(this, RError.Message.MORE_SUPPLIED_REPLACE);
        }
        elementNACheck.enable(value);
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNaOrNegative(p, value)) {
                continue;
            }
            if (negativePosProfile.profile(p < 0 && -(p + 1) >= orgVectorLength)) {
                resultVector.updateDataAt(-(p + 1), RRuntime.DOUBLE_NA, elementNACheck);
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAt(i % value.getLength()), elementNACheck);
            }
        }
        if (value.getLength() == 0) {
            throw RInternalError.unimplemented();
        }
        if (positions.getLength() % value.getLength() != 0) {
            warning.enter();
            RError.warning(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    @Specialization(guards = {"multiDim(vector)", "!wrongDimensionsMatrix(positions, vector)", "!wrongDimensions(positions, vector)"})
    protected RDoubleVector update(Object v, RAbstractIntVector value, Object[] positions, RAbstractDoubleVector vector) {
        return updateVector(castDouble(value), vector, positions);
    }

    @Specialization(guards = {"multiDim(vector)", "!wrongDimensionsMatrix(positions, vector)", "!wrongDimensions(positions, vector)"})
    protected RDoubleVector update(Object v, RAbstractDoubleVector value, Object[] positions, RAbstractDoubleVector vector) {
        return updateVector(value, vector, positions);
    }

    @Specialization(guards = {"multiDim(vector)", "!wrongDimensionsMatrix(positions, vector)", "!wrongDimensions(positions, vector)"})
    protected RDoubleVector update(Object v, RAbstractLogicalVector value, Object[] positions, RAbstractDoubleVector vector) {
        return updateVector(castDouble(value), vector, positions);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "multiPos(positions)"})
    protected RAbstractDoubleVector updateSubset(Object v, RAbstractIntVector value, RIntVector positions, RAbstractDoubleVector vector) {
        return updateSingleDimVector(castDouble(value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "onePosition(positions)"})
    protected Object updateSubsetOne(Object v, RAbstractIntVector value, RIntVector positions, RAbstractDoubleVector vector) {
        return updateDelegate(v, value, vector, positions.getDataAt(0));
    }

    @Specialization(guards = {"posNames(positions)"})
    protected RAbstractDoubleVector updateNames(Object v, RAbstractIntVector value, RIntVector positions, RAbstractDoubleVector vector) {
        return updateSingleDimVector(castDouble(value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne(value)", "isSubset", "!isPosNA(position)", "!isPosZero(position)"})
    protected RDoubleVector updateTooManyValuesSubset(Object v, RAbstractIntVector value, int position, RAbstractDoubleVector vector) {
        RError.warning(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(castDouble(value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne(value)", "!isPosNA(position)", "!isPosZero(position)"})
    protected RDoubleVector update(Object v, RAbstractIntVector value, int position, RAbstractDoubleVector vector) {
        return updateSingleDim(castDouble(value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "multiPos(positions)"})
    protected RAbstractDoubleVector updateSubset(Object v, RAbstractDoubleVector value, RIntVector positions, RAbstractDoubleVector vector) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "onePosition(positions)"})
    protected Object updateSubsetOne(Object v, RAbstractDoubleVector value, RIntVector positions, RAbstractDoubleVector vector) {
        return updateDelegate(v, value, vector, positions.getDataAt(0));
    }

    @Specialization(guards = {"posNames(positions)"})
    protected RAbstractDoubleVector updateNames(Object v, RAbstractDoubleVector value, RIntVector positions, RAbstractDoubleVector vector) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne(value)", "isSubset", "!isPosNA(position)", "!isPosZero(position)"})
    protected RDoubleVector updateTooManyValuesSubset(Object v, RAbstractDoubleVector value, int position, RAbstractDoubleVector vector) {
        RError.warning(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne(value)", "!isPosNA(position)", "!isPosZero(position)"})
    protected RDoubleVector update(Object v, RAbstractDoubleVector value, int position, RAbstractDoubleVector vector) {
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "multiPos(positions)"})
    protected RAbstractDoubleVector updateSubset(Object v, RAbstractLogicalVector value, RIntVector positions, RAbstractDoubleVector vector) {
        return updateSingleDimVector(castDouble(value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "onePosition(positions)"})
    protected Object updateSubsetOne(Object v, RAbstractLogicalVector value, RIntVector positions, RAbstractDoubleVector vector) {
        return updateDelegate(v, value, vector, positions.getDataAt(0));
    }

    @Specialization(guards = {"posNames(positions)"})
    protected RAbstractDoubleVector updateNames(Object v, RAbstractLogicalVector value, RIntVector positions, RAbstractDoubleVector vector) {
        return updateSingleDimVector(castDouble(value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne(value)", "isSubset", "!isPosNA(position)", "!isPosZero(position)"})
    protected RDoubleVector updateTooManyValuesSubset(Object v, RAbstractLogicalVector value, int position, RAbstractDoubleVector vector) {
        RError.warning(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(castDouble(value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne(value)", "!isPosNA(position)", "!isPosZero(position)"})
    protected RDoubleVector update(Object v, RAbstractLogicalVector value, int position, RAbstractDoubleVector vector) {
        return updateSingleDim(castDouble(value), getResultVector(vector, position), position);
    }

    // logical vector

    private RLogicalVector updateVector(RAbstractLogicalVector value, RLogicalVector vector, Object[] positions) {
        int replacementLength = getReplacementLength(positions, value, false);
        RLogicalVector resultVector = vector;
        if (replacementLength == 0) {
            return resultVector;
        }
        if (vector.isShared()) {
            vectorShared.enter();
            resultVector = (RLogicalVector) vector.copy();
            if (!FastROptions.NewStateTransition.getBooleanValue()) {
                resultVector.markNonTemporary();
            } else if (resultVector.isTemporary()) {
                resultVector.incRefCount();
            }
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        elementNACheck.enable(value);
        posNACheck.enable(p);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (posNACheck.check(pos)) {
                handleNaMultiDim(value, false, isSubset, this);
                continue;
            }
            int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
            setMultiDimData(value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
        }
        return resultVector;
    }

    private RLogicalVector getResultVector(RLogicalVector vector, int highestPos) {
        RLogicalVector resultVector = vector;
        if (resultVector.getLength() < highestPos) {
            vectorTooShort.enter();
            resultVector = (RLogicalVector) resultVector.resize(highestPos, false);
        } else if (vector.isShared()) {
            vectorShared.enter();
            resultVector = (RLogicalVector) vector.copy();
        }
        if (!FastROptions.NewStateTransition.getBooleanValue()) {
            resultVector.markNonTemporary();
        } else if (resultVector.isTemporary()) {
            resultVector.incRefCount();
        }
        return resultVector;
    }

    private RLogicalVector updateSingleDim(RAbstractLogicalVector value, RLogicalVector resultVector, int position) {
        elementNACheck.enable(value);
        resultVector.updateDataAt(position - 1, value.getDataAt(0), elementNACheck);
        return resultVector;
    }

    private RLogicalVector updateSingleDimVector(RAbstractLogicalVector value, int orgVectorLength, RLogicalVector resultVector, RIntVector positions) {
        if (positions.getLength() == 1 && value.getLength() > 1) {
            error.enter();
            throw RError.error(this, RError.Message.MORE_SUPPLIED_REPLACE);
        }
        elementNACheck.enable(value);
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNaOrNegative(p, value)) {
                continue;
            }
            if (negativePosProfile.profile(p < 0 && -(p + 1) >= orgVectorLength)) {
                resultVector.updateDataAt(-(p + 1), RRuntime.LOGICAL_NA, elementNACheck);
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAt(i % value.getLength()), elementNACheck);
            }
        }
        if (positions.getLength() % value.getLength() != 0) {
            warning.enter();
            RError.warning(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    @Specialization(guards = {"multiDim(vector)", "!wrongDimensionsMatrix(positions, vector)", "!wrongDimensions(positions, vector)"})
    protected RLogicalVector update(Object v, RAbstractLogicalVector value, Object[] positions, RLogicalVector vector) {
        return updateVector(value, vector, positions);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "multiPos(positions)"})
    protected RAbstractLogicalVector updateSubset(Object v, RAbstractLogicalVector value, RIntVector positions, RLogicalVector vector) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "onePosition(positions)"})
    protected Object updateSubsetOne(Object v, RAbstractLogicalVector value, RIntVector positions, RLogicalVector vector) {
        return updateDelegate(v, value, vector, positions.getDataAt(0));
    }

    @Specialization(guards = {"posNames(positions)"})
    protected RAbstractLogicalVector updateNames(Object v, RAbstractLogicalVector value, RIntVector positions, RLogicalVector vector) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne(value)", "isSubset", "!isPosNA(position)", "!isPosZero(position)"})
    protected RLogicalVector updateTooManyValuesSubset(Object v, RAbstractLogicalVector value, int position, RLogicalVector vector) {
        RError.warning(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne(value)", "!isPosNA(position)", "!isPosZero(position)"})
    protected RLogicalVector update(Object v, RAbstractLogicalVector value, int position, RLogicalVector vector) {
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    // string vector

    private RStringVector updateVector(RAbstractStringVector value, RStringVector vector, Object[] positions) {
        int replacementLength = getReplacementLength(positions, value, false);
        RStringVector resultVector = vector;
        if (replacementLength == 0) {
            return resultVector;
        }
        if (vector.isShared()) {
            vectorShared.enter();
            resultVector = (RStringVector) vector.copy();
            if (!FastROptions.NewStateTransition.getBooleanValue()) {
                resultVector.markNonTemporary();
            } else if (resultVector.isTemporary()) {
                resultVector.incRefCount();
            }
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        elementNACheck.enable(value);
        posNACheck.enable(p);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (posNACheck.check(pos)) {
                handleNaMultiDim(value, false, isSubset, this);
                continue;
            }
            int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
            setMultiDimData(value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
        }
        return resultVector;
    }

    private RStringVector getResultVector(RStringVector vector, int highestPos) {
        RStringVector resultVector = vector;
        if (resultVector.getLength() < highestPos) {
            vectorTooShort.enter();
            resultVector = (RStringVector) resultVector.resize(highestPos, false);
        } else if (vector.isShared()) {
            vectorShared.enter();
            resultVector = (RStringVector) vector.copy();
        }
        if (!FastROptions.NewStateTransition.getBooleanValue()) {
            resultVector.markNonTemporary();
        } else if (resultVector.isTemporary()) {
            resultVector.incRefCount();
        }
        return resultVector;
    }

    private RStringVector updateSingleDim(RAbstractStringVector value, RStringVector resultVector, int position) {
        elementNACheck.enable(value);
        resultVector.updateDataAt(position - 1, value.getDataAt(0), elementNACheck);
        return resultVector;
    }

    private RStringVector updateSingleDimVector(RAbstractStringVector value, int orgVectorLength, RStringVector resultVector, RIntVector positions) {
        if (positions.getLength() == 1 && value.getLength() > 1) {
            error.enter();
            throw RError.error(this, RError.Message.MORE_SUPPLIED_REPLACE);
        }
        elementNACheck.enable(value);
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNaOrNegative(p, value)) {
                continue;
            }
            if (negativePosProfile.profile(p < 0 && -(p + 1) >= orgVectorLength)) {
                resultVector.updateDataAt(-(p + 1), RRuntime.STRING_NA, elementNACheck);
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAt(i % value.getLength()), elementNACheck);
            }
        }
        if (positions.getLength() % value.getLength() != 0) {
            warning.enter();
            RError.warning(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    @Specialization(guards = {"multiDim(vector)", "!wrongDimensionsMatrix(positions, vector)", "!wrongDimensions(positions, vector)"})
    protected RStringVector update(Object v, RAbstractStringVector value, Object[] positions, RStringVector vector) {
        return updateVector(value, vector, positions);
    }

    @Specialization(guards = {"multiDim(vector)", "!wrongDimensionsMatrix(positions, vector)", "!wrongDimensions(positions, vector)"})
    protected RStringVector update(Object v, RAbstractVector value, Object[] positions, RStringVector vector) {
        return updateVector(castString(value), vector, positions);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "multiPos(positions)"})
    protected RAbstractStringVector updateSubset(Object v, RAbstractStringVector value, RIntVector positions, RStringVector vector) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "onePosition(positions)"})
    protected Object updateSubsetOne(Object v, RAbstractStringVector value, RIntVector positions, RStringVector vector) {
        return updateDelegate(v, value, vector, positions.getDataAt(0));
    }

    @Specialization(guards = {"posNames(positions)"})
    protected RAbstractStringVector updateNames(Object v, RAbstractStringVector value, RIntVector positions, RStringVector vector) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne(value)", "isSubset", "!isPosNA(position)", "!isPosZero(position)"})
    protected RStringVector updateTooManyValuesSubset(Object v, RAbstractStringVector value, int position, RStringVector vector) {
        RError.warning(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne(value)", "!isPosNA(position)", "!isPosZero(position)"})
    protected RStringVector update(Object v, RAbstractStringVector value, int position, RStringVector vector) {
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "multiPos(positions)"})
    protected RAbstractStringVector updateSubset(Object v, RAbstractVector value, RIntVector positions, RStringVector vector) {
        return updateSingleDimVector(castString(value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "onePosition(positions)"})
    protected Object updateSubsetOne(Object v, RAbstractVector value, RIntVector positions, RStringVector vector) {
        return updateDelegate(v, value, vector, positions.getDataAt(0));
    }

    @Specialization(guards = {"posNames(positions)"})
    protected RAbstractStringVector updateSubsetNames(Object v, RAbstractVector value, RIntVector positions, RStringVector vector) {
        return updateSingleDimVector(castString(value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne(value)", "isSubset", "!isPosNA(position)", "!isPosZero(position)"})
    protected RStringVector updateTooManyValuesSubset(Object v, RAbstractVector value, int position, RStringVector vector) {
        RError.warning(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(castString(value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne(value)", "!isPosNA(position)", "!isPosZero(position)"})
    protected RStringVector update(Object v, RAbstractVector value, int position, RStringVector vector) {
        return updateSingleDim(castString(value), getResultVector(vector, position), position);
    }

    // complex vector

    private RComplexVector updateVector(RAbstractComplexVector value, RComplexVector vector, Object[] positions) {
        int replacementLength = getReplacementLength(positions, value, false);
        RComplexVector resultVector = vector;
        if (replacementLength == 0) {
            return resultVector;
        }
        if (vector.isShared()) {
            vectorShared.enter();
            resultVector = (RComplexVector) vector.copy();
            if (!FastROptions.NewStateTransition.getBooleanValue()) {
                resultVector.markNonTemporary();
            } else if (resultVector.isTemporary()) {
                resultVector.incRefCount();
            }
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        elementNACheck.enable(value);
        posNACheck.enable(p);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (posNACheck.check(pos)) {
                handleNaMultiDim(value, false, isSubset, this);
                continue;
            }
            int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
            setMultiDimData(value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
        }
        return resultVector;
    }

    private RComplexVector getResultVector(RComplexVector vector, int highestPos) {
        RComplexVector resultVector = vector;
        if (resultVector.getLength() < highestPos) {
            vectorTooShort.enter();
            resultVector = (RComplexVector) resultVector.resize(highestPos, false);
        } else if (vector.isShared()) {
            vectorShared.enter();
            resultVector = (RComplexVector) vector.copy();
        }
        if (!FastROptions.NewStateTransition.getBooleanValue()) {
            resultVector.markNonTemporary();
        } else if (resultVector.isTemporary()) {
            resultVector.incRefCount();
        }
        return resultVector;
    }

    private RComplexVector updateSingleDim(RAbstractComplexVector value, RComplexVector resultVector, int position) {
        elementNACheck.enable(value);
        resultVector.updateDataAt(position - 1, value.getDataAt(0), elementNACheck);
        return resultVector;
    }

    private RComplexVector updateSingleDimVector(RAbstractComplexVector value, int orgVectorLength, RComplexVector resultVector, RIntVector positions) {
        if (positions.getLength() == 1 && value.getLength() > 1) {
            error.enter();
            throw RError.error(this, RError.Message.MORE_SUPPLIED_REPLACE);
        }
        elementNACheck.enable(value);
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNaOrNegative(p, value)) {
                continue;
            }
            if (negativePosProfile.profile(p < 0 && -(p + 1) >= orgVectorLength)) {
                resultVector.updateDataAt(-(p + 1), RRuntime.createComplexNA(), elementNACheck);
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAt(i % value.getLength()), elementNACheck);
            }
        }
        if (positions.getLength() % value.getLength() != 0) {
            warning.enter();
            RError.warning(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    @Specialization(guards = {"multiDim(vector)", "!wrongDimensionsMatrix(positions, vector)", "!wrongDimensions(positions, vector)"})
    protected RComplexVector update(Object v, RAbstractIntVector value, Object[] positions, RComplexVector vector) {
        return updateVector(castComplex(value), vector, positions);
    }

    @Specialization(guards = {"multiDim(vector)", "!wrongDimensionsMatrix(positions, vector)", "!wrongDimensions(positions, vector)"})
    protected RComplexVector update(Object v, RAbstractDoubleVector value, Object[] positions, RComplexVector vector) {
        return updateVector(castComplex(value), vector, positions);
    }

    @Specialization(guards = {"multiDim(vector)", "!wrongDimensionsMatrix(positions, vector)", "!wrongDimensions(positions, vector)"})
    protected RComplexVector update(Object v, RAbstractLogicalVector value, Object[] positions, RComplexVector vector) {
        return updateVector(castComplex(value), vector, positions);
    }

    @Specialization(guards = {"multiDim(vector)", "!wrongDimensionsMatrix(positions, vector)", "!wrongDimensions(positions, vector)"})
    protected RComplexVector update(Object v, RAbstractComplexVector value, Object[] positions, RComplexVector vector) {
        return updateVector(value, vector, positions);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "multiPos(positions)"})
    protected RAbstractComplexVector updateSubset(Object v, RAbstractIntVector value, RIntVector positions, RComplexVector vector) {
        return updateSingleDimVector(castComplex(value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "onePosition(positions)"})
    protected Object updateSubsetOne(Object v, RAbstractIntVector value, RIntVector positions, RComplexVector vector) {
        return updateDelegate(v, value, vector, positions.getDataAt(0));
    }

    @Specialization(guards = {"posNames(positions)"})
    protected RAbstractComplexVector updateNames(Object v, RAbstractIntVector value, RIntVector positions, RComplexVector vector) {
        return updateSingleDimVector(castComplex(value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne(value)", "isSubset", "!isPosNA(position)", "!isPosZero(position)"})
    protected RComplexVector updateTooManyValuesSubset(Object v, RAbstractIntVector value, int position, RComplexVector vector) {
        RError.warning(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(castComplex(value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne(value)", "!isPosNA(position)", "!isPosZero(position)"})
    protected RComplexVector update(Object v, RAbstractIntVector value, int position, RComplexVector vector) {
        return updateSingleDim(castComplex(value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "multiPos(positions)"})
    protected RAbstractComplexVector updateSubset(Object v, RAbstractDoubleVector value, RIntVector positions, RComplexVector vector) {
        return updateSingleDimVector(castComplex(value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "onePosition(positions)"})
    protected Object updateSubsetOne(Object v, RAbstractDoubleVector value, RIntVector positions, RComplexVector vector) {
        return updateDelegate(v, value, vector, positions.getDataAt(0));
    }

    @Specialization(guards = {"posNames(positions)"})
    protected RAbstractComplexVector updateNames(Object v, RAbstractDoubleVector value, RIntVector positions, RComplexVector vector) {
        return updateSingleDimVector(castComplex(value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne(value)", "isSubset", "!isPosNA(position)", "!isPosZero(position)"})
    protected RComplexVector updateTooManyValuesSubset(Object v, RAbstractDoubleVector value, int position, RComplexVector vector) {
        RError.warning(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(castComplex(value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne(value)", "!isPosNA(position)", "!isPosZero(position)"})
    protected RComplexVector update(Object v, RAbstractDoubleVector value, int position, RComplexVector vector) {
        return updateSingleDim(castComplex(value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "multiPos(positions)"})
    protected RAbstractComplexVector updateSubset(Object v, RAbstractLogicalVector value, RIntVector positions, RComplexVector vector) {
        return updateSingleDimVector(castComplex(value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "onePosition(positions)"})
    protected Object updateSubsetOne(Object v, RAbstractLogicalVector value, RIntVector positions, RComplexVector vector) {
        return updateDelegate(v, value, vector, positions.getDataAt(0));
    }

    @Specialization(guards = {"posNames(positions)"})
    protected RAbstractComplexVector updateNames(Object v, RAbstractLogicalVector value, RIntVector positions, RComplexVector vector) {
        return updateSingleDimVector(castComplex(value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne(value)", "isSubset", "!isPosNA(position)", "!isPosZero(position)"})
    protected RComplexVector updateTooManyValuesSubset(Object v, RAbstractLogicalVector value, int position, RComplexVector vector) {
        RError.warning(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(castComplex(value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne(value)", "!isPosNA(position)", "!isPosZero(position)"})
    protected RComplexVector update(Object v, RAbstractLogicalVector value, int position, RComplexVector vector) {
        return updateSingleDim(castComplex(value), getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "multiPos(positions)"})
    protected RAbstractComplexVector updateSubset(Object v, RAbstractComplexVector value, RIntVector positions, RComplexVector vector) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "onePosition(positions)"})
    protected Object updateSubsetOne(Object v, RAbstractComplexVector value, RIntVector positions, RComplexVector vector) {
        return updateDelegate(v, value, vector, positions.getDataAt(0));
    }

    @Specialization(guards = {"posNames(positions)"})
    protected RAbstractComplexVector updateNames(Object v, RAbstractComplexVector value, RIntVector positions, RComplexVector vector) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne(value)", "isSubset", "!isPosNA(position)", "!isPosZero(position)"})
    protected RComplexVector updateTooManyValuesSubset(Object v, RAbstractComplexVector value, int position, RComplexVector vector) {
        RError.warning(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne(value)", "!isPosNA(position)", "!isPosZero(position)"})
    protected RComplexVector update(Object v, RAbstractComplexVector value, int position, RComplexVector vector) {
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    // raw vector

    private RRawVector updateVector(RAbstractRawVector value, RRawVector vector, Object[] positions) {
        int replacementLength = getReplacementLength(positions, value, false);
        RRawVector resultVector = vector;
        if (replacementLength == 0) {
            return resultVector;
        }
        if (vector.isShared()) {
            vectorShared.enter();
            resultVector = (RRawVector) vector.copy();
            if (!FastROptions.NewStateTransition.getBooleanValue()) {
                resultVector.markNonTemporary();
            } else if (resultVector.isTemporary()) {
                resultVector.incRefCount();
            }
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        posNACheck.enable(p);
        elementNACheck.enable(value);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (posNACheck.check(pos)) {
                handleNaMultiDim(value, false, isSubset, this);
                continue;
            }
            int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
            setMultiDimData(value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions, posNACheck, elementNACheck);
        }
        return resultVector;
    }

    private RRawVector getResultVector(RRawVector vector, int highestPos) {
        RRawVector resultVector = vector;
        if (resultVector.getLength() < highestPos) {
            vectorTooShort.enter();
            resultVector = (RRawVector) resultVector.resize(highestPos, false);
        } else if (vector.isShared()) {
            vectorShared.enter();
            resultVector = (RRawVector) vector.copy();
        }
        if (!FastROptions.NewStateTransition.getBooleanValue()) {
            resultVector.markNonTemporary();
        } else if (resultVector.isTemporary()) {
            resultVector.incRefCount();
        }
        return resultVector;
    }

    private static RRawVector updateSingleDim(RAbstractRawVector value, RRawVector resultVector, int position) {
        resultVector.updateDataAt(position - 1, value.getDataAt(0));
        return resultVector;
    }

    private RRawVector updateSingleDimVector(RAbstractRawVector value, int orgVectorLength, RRawVector resultVector, RIntVector positions) {
        if (positions.getLength() == 1 && value.getLength() > 1) {
            error.enter();
            throw RError.error(this, RError.Message.MORE_SUPPLIED_REPLACE);
        }
        elementNACheck.enable(value);
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNaOrNegative(p, value)) {
                continue;
            }
            if (negativePosProfile.profile(p < 0 && -(p + 1) >= orgVectorLength)) {
                resultVector.updateDataAt(-(p + 1), RDataFactory.createRaw((byte) 0));
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAt(i % value.getLength()));
            }
        }
        if (positions.getLength() % value.getLength() != 0) {
            warning.enter();
            RError.warning(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    @Specialization(guards = {"multiDim(vector)", "!wrongDimensionsMatrix(positions, vector)", "!wrongDimensions(positions, vector)"})
    protected RRawVector update(Object v, RAbstractRawVector value, Object[] positions, RRawVector vector) {
        return updateVector(value, vector, positions);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "multiPos(positions)"})
    protected RAbstractRawVector updateSubset(Object v, RAbstractRawVector value, RIntVector positions, RRawVector vector) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"isSubset", "!posNames(positions)", "onePosition(positions)"})
    protected Object updateSubsetOne(Object v, RAbstractRawVector value, RIntVector positions, RRawVector vector) {
        return updateDelegate(v, value, vector, positions.getDataAt(0));
    }

    @Specialization(guards = {"posNames(positions)"})
    protected RAbstractRawVector updateSubsetNames(Object v, RAbstractRawVector value, RIntVector positions, RRawVector vector) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(guards = {"!isValueLengthOne(value)", "isSubset", "!isPosNA(position)", "!isPosZero(position)"})
    protected RRawVector updateTooManyValuesSubset(Object v, RAbstractRawVector value, int position, RRawVector vector) {
        RError.warning(this, RError.Message.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(guards = {"isValueLengthOne(value)", "!isPosNA(position)", "!isPosZero(position)"})
    protected RRawVector update(Object v, RAbstractRawVector value, int position, RRawVector vector) {
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    private static int getValueLength(Object value) {
        CompilerAsserts.neverPartOfCompilation();
        return value instanceof RNull ? 0 : ((RAbstractVector) RRuntime.asAbstractVector(value)).getLength();
    }

    @TruffleBoundary
    @Specialization
    protected Object accessListPosition(Object v, Object value, RList positions, RAbstractVector vector) {
        if (!isSubset) {
            if (vector instanceof RList) {
                if (positions.getLength() == 0) {
                    throw RError.error(this, RError.Message.SELECT_LESS_1);
                }
            } else {
                if (positions.getLength() <= 1 && getValueLength(value) == 0 && !(value instanceof RNull)) {
                    throw RError.error(this, RError.Message.REPLACEMENT_0);
                }
                if (positions.getLength() == 0 && (getValueLength(value) == 1 || value instanceof RNull)) {
                    throw RError.error(this, RError.Message.SELECT_LESS_1);
                }
                if (positions.getLength() <= 1 && (getValueLength(value) > 1 || value instanceof RNull)) {
                    throw RError.error(this, RError.Message.MORE_SUPPLIED_REPLACE);
                }
                if (positions.getLength() > 2 && getValueLength(value) != positions.getLength()) {
                    throw RError.error(this, RError.Message.SELECT_MORE_1);
                }
            }
        }
        throw RError.error(this, RError.Message.INVALID_SUBSCRIPT_TYPE, "list");
    }

    @TruffleBoundary
    @Specialization
    protected Object accessComplexPosition(Object v, Object value, RComplex position, RAbstractVector vector) {
        if (isSubset || vector instanceof RList || getValueLength(value) == 1) {
            throw RError.error(this, RError.Message.INVALID_SUBSCRIPT_TYPE, "complex");
        } else {
            throw RError.error(this, getValueLength(value) != 0 || value instanceof RNull ? RError.Message.MORE_SUPPLIED_REPLACE : RError.Message.REPLACEMENT_0);
        }
    }

    @TruffleBoundary
    @Specialization
    protected Object accessRawPosition(Object v, Object value, RRaw position, RAbstractVector vector) {
        if (isSubset || vector instanceof RList || getValueLength(value) == 1) {
            throw RError.error(this, RError.Message.INVALID_SUBSCRIPT_TYPE, "raw");
        } else {
            throw RError.error(this, getValueLength(value) != 0 || value instanceof RNull ? RError.Message.MORE_SUPPLIED_REPLACE : RError.Message.REPLACEMENT_0);
        }
    }

    protected boolean firstPosZero(RIntVector positions) {
        return positions.getDataAt(0) == 0;
    }

    protected boolean outOfBoundsNegative(int position, RAbstractVector vector) {
        return -position > vector.getLength();
    }

    protected boolean oneElemVector(RAbstractVector vector) {
        return vector.getLength() == 1;
    }

    protected boolean posNames(RIntVector positions) {
        return positions.getNames(attrProfiles) != null;
    }

    protected boolean isPositionNegative(int position) {
        return position < 0;
    }

    protected boolean isVectorList(RAbstractVector vector) {
        return vector instanceof RList;
    }

    protected boolean isVectorLongerThanOne(RAbstractVector vector) {
        return vector.getLength() > 1;
    }

    protected boolean emptyValue(RAbstractContainer value) {
        return value.getLength() == 0;
    }

    protected boolean valueLengthOne(RAbstractContainer value) {
        return value.getLength() == 1;
    }

    protected boolean valueLongerThanOne(RAbstractContainer value) {
        return value.getLength() > 1;
    }

    protected boolean wrongDimensionsMatrix(Object[] positions, RAbstractVector vector) {
        if (positions.length == 2 && (vector.getDimensions() == null || vector.getDimensions().length != positions.length)) {
            error.enter();
            if (isSubset) {
                throw RError.error(this, RError.Message.INCORRECT_SUBSCRIPTS_MATRIX);
            } else {
                throw RError.error(this, RError.Message.IMPROPER_SUBSCRIPT);
            }
        }
        return false;
    }

    protected boolean wrongDimensionsMatrix(Object[] positions, RNull vector) {
        if (positions.length == 2) {
            error.enter();
            if (isSubset) {
                throw RError.error(this, RError.Message.INCORRECT_SUBSCRIPTS_MATRIX);
            } else {
                throw RError.error(this, RError.Message.IMPROPER_SUBSCRIPT);
            }
        }
        return false;
    }

    protected boolean wrongDimensions(Object[] positions, RAbstractVector vector) {
        if (!((vector.getDimensions() == null && positions.length == 1) || vector.getDimensions().length == positions.length)) {
            error.enter();
            if (isSubset) {
                throw RError.error(this, RError.Message.INCORRECT_SUBSCRIPTS);
            } else {
                throw RError.error(this, RError.Message.IMPROPER_SUBSCRIPT);
            }
        }
        return false;
    }

    protected boolean wrongDimensions(Object[] positions, RNull vector) {
        if (positions.length > 2) {
            error.enter();
            if (isSubset) {
                throw RError.error(this, RError.Message.INCORRECT_SUBSCRIPTS);
            } else {
                throw RError.error(this, RError.Message.IMPROPER_SUBSCRIPT);
            }
        }
        return false;
    }

    protected boolean multiDim(RAbstractVector vector) {
        return vector.getDimensions() != null && vector.getDimensions().length > 1;
    }

    protected boolean wrongLength(RAbstractContainer value, RIntVector positions, RAbstractVector vector) {
        int valLength = value.getLength();
        int posLength = positions.getLength();
        return valLength > posLength || (posLength % valLength != 0);
    }

    protected boolean isPosNA(int position) {
        return RRuntime.isNA(position);
    }

    protected boolean isPosZero(int position) {
        return position == 0;
    }

    protected boolean isValueLengthOne(RAbstractContainer value) {
        return value.getLength() == 1;
    }

    protected boolean twoPositions(RAbstractVector position) {
        return position.getLength() == 2;
    }

    protected boolean onePosition(RAbstractVector position) {
        return position.getLength() == 1;
    }

    protected boolean noPosition(RAbstractVector position) {
        return position.getLength() == 0;
    }

    protected boolean multiPos(RIntVector positions) {
        return positions.getLength() > 1;
    }

    protected boolean moreThanTwoPos(RList positions) {
        return positions.getLength() > 2;
    }

    protected boolean emptyList(RList vector) {
        return vector.getLength() == 0;
    }
}
