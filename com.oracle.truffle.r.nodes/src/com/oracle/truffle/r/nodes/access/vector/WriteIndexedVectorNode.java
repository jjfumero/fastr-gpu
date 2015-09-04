/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.access.vector;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.profile.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ops.na.*;

/**
 * Primitive indexed N-dimensional vector write node. It can be used for vector replaces and
 * extracts. The only difference is that replace indexes the left vector and extract indexes the
 * right vector. The index direction is indicated with the boolean flag
 * {@link #positionsApplyToRight}.
 */
abstract class WriteIndexedVectorNode extends Node {

    private final int dimensionIndex;
    private final int totalDimensions;

    /**
     * Indicates if the position vectors index into the left or the right vector. This enables us to
     * share the same node for vector replaces and vector extracts.
     */
    private final boolean positionsApplyToRight;
    /**
     * If skipNA is true then no action should be invoked for NA values and its indexed
     * subdimensions.
     */
    private final boolean skipNA;

    private final VectorLengthProfile positionLengthProfile = VectorLengthProfile.create();
    private final VectorLengthProfile positionOffsetProfile = VectorLengthProfile.create();
    private final VectorLengthProfile dimensionValueProfile = VectorLengthProfile.create();
    private final ValueProfile positionClassProfile = ValueProfile.createClassProfile();
    private final NACheck positionNACheck = NACheck.create();

    @Child private WriteIndexedScalarNode<RAbstractVector, RAbstractContainer> scalarNode;
    @Child private WriteIndexedVectorNode innerVectorNode;

    @SuppressWarnings("unchecked")
    protected WriteIndexedVectorNode(RType vectorType, int totalDimensions, int dimensionIndex, boolean positionAppliesToRight, boolean skipNA, boolean setListElementAsObject) {
        this.scalarNode = (WriteIndexedScalarNode<RAbstractVector, RAbstractContainer>) createIndexedAction(vectorType, setListElementAsObject);
        this.dimensionIndex = dimensionIndex;
        this.totalDimensions = totalDimensions;
        this.positionsApplyToRight = positionAppliesToRight;
        this.skipNA = skipNA;
        if (dimensionIndex > 0) {
            innerVectorNode = WriteIndexedVectorNodeGen.create(vectorType, totalDimensions, dimensionIndex - 1, positionAppliesToRight, skipNA, setListElementAsObject);
        }
    }

    public static WriteIndexedVectorNode create(RType vectorType, int totalDimensions, boolean positionAppliesToValue, boolean skipNA, boolean setListElementAsObject) {
        return WriteIndexedVectorNodeGen.create(vectorType, totalDimensions, totalDimensions - 1, positionAppliesToValue, skipNA, setListElementAsObject);
    }

    public NACheck getValueNACheck() {
        return scalarNode.valueNACheck;
    }

    public void enableValueNACheck(RAbstractContainer vector) {
        getValueNACheck().enable(vector);
        if (innerVectorNode != null) {
            innerVectorNode.enableValueNACheck(vector);
        }
    }

    public boolean neverSeenNAInValue() {
        if (getValueNACheck().neverSeenNA()) {
            if (innerVectorNode == null || innerVectorNode.neverSeenNAInValue()) {
                return true;
            }
        }
        return false;
    }

    public final void apply(RAbstractVector left, int leftLength, //
                    Object[] positions, RAbstractContainer right, int rightLength, int[] positionTargetDimensions) {
        assert left.getLength() == leftLength;
        assert right.getLength() == rightLength;
        assert totalDimensions == positions.length : "totalDimensions must be constant per vector write node";

        Object leftStore = left.getInternalStore();
        Object rightStore = right.getInternalStore();

        int initialPositionOffset;
        if (positionsApplyToRight) {
            initialPositionOffset = rightLength;
        } else {
            initialPositionOffset = leftLength;
        }

        int firstTargetDimension;
        if (totalDimensions == 0 || positionTargetDimensions == null) {
            // no dimensions
            firstTargetDimension = initialPositionOffset;
        } else {
            firstTargetDimension = dimensionValueProfile.profile(positionTargetDimensions[dimensionIndex]);
        }

        applyImpl(left, leftStore, 0, leftLength, positionTargetDimensions, firstTargetDimension, //
                        positions, initialPositionOffset, //
                        right, rightStore, 0, rightLength, false);
    }

    private final ConditionProfile positionMatchesTargetDimensionsProfile = ConditionProfile.createBinaryProfile();

    private int applyImpl(//
                    RAbstractVector left, Object leftStore, int leftBase, int leftLength, Object targetDimensions, int targetDimension, //
                    Object[] positions, int positionOffset, //
                    RAbstractContainer right, Object rightStore, int rightBase, int rightLength, boolean parentNA) {

        Object position = positionClassProfile.profile(positions[dimensionIndex]);

        int positionLength = getPositionLength(position);
        int newPositionOffset;
        if (positionMatchesTargetDimensionsProfile.profile(positionOffset == targetDimension)) {
            newPositionOffset = 1;
        } else {
            newPositionOffset = positionOffsetProfile.profile(positionOffset / targetDimension);
        }
        return execute(left, leftStore, leftBase, leftLength, targetDimensions, targetDimension, //
                        positions, position, newPositionOffset, positionLength, //
                        right, rightStore, rightBase, rightLength, parentNA);
    }

    public int getPositionLength(Object position) {
        if (position instanceof RAbstractVector) {
            return positionLengthProfile.profile(((RAbstractVector) position).getLength());
        } else {
            return -1;
        }
    }

    protected abstract int execute(RAbstractVector left, Object leftStore, int storeBase, int storeLength, Object targetDimensions, int targetDimension, //
                    Object[] positions, Object position, int positionOffset, int positionLength, //
                    RAbstractContainer right, Object rightStore, int valueBase, int valueLength, boolean parentNA);

    @SuppressWarnings("unused")
    @Specialization
    protected int doMissing(RAbstractVector left, Object leftStore, int leftBase, int leftLength, Object targetDimensions, int targetDimension, //
                    Object[] positions, RMissing position, int positionOffset, int positionLength, //
                    RAbstractContainer right, Object rightStore, int rightBase, int rightLength, boolean parentNA) {
        initRightIndexCheck(rightBase, targetDimension, leftLength, rightLength);

        int rightIndex = rightBase;
        for (int positionValue = 0; positionValue < targetDimension; positionValue += 1) {
            rightIndex = applyInner(//
                            left, leftStore, leftBase, leftLength, targetDimensions, //
                            positions, positionOffset, positionValue, //
                            right, rightStore, rightLength, rightIndex, parentNA);
        }
        return rightIndex;
    }

    @Specialization
    protected int doLogicalPosition(RAbstractVector left, Object leftStore, int leftBase, int leftLength, Object targetDimensions, int targetDimension, //
                    Object[] positions, RAbstractLogicalVector position, int positionOffset, int positionLength, //
                    RAbstractContainer right, Object rightStore, int rightBase, int rightLength, boolean parentNA, //
                    @Cached("create()") BranchProfile wasTrue, @Cached("create()") BranchProfile outOfBounds) {
        positionNACheck.enable(!skipNA && !position.isComplete());

        int length = targetDimension;
        if (positionLength > targetDimension) {
            outOfBounds.enter();
            length = positionLength;
        }

        int rightIndex = rightBase;
        if (positionLength > 0) {

            initRightIndexCheck(rightBase, length, leftLength, rightLength);

            int positionIndex = 0;
            for (int i = 0; i < length; i++) {
                byte positionValue = position.getDataAt(positionIndex);
                boolean isNA = positionNACheck.check(positionValue);
                if (isNA || positionValue == RRuntime.LOGICAL_TRUE) {
                    wasTrue.enter();
                    if (outOfBounds.isVisited() && i >= targetDimension) {
                        isNA = true;
                    }
                    rightIndex = applyInner(//
                                    left, leftStore, leftBase, leftLength, targetDimensions, //
                                    positions, positionOffset, i, //
                                    right, rightStore, rightLength, rightIndex, isNA || parentNA);
                }
                positionIndex = Utils.incMod(positionIndex, positionLength);
            }
        }
        return rightIndex;
    }

    @CompilationFinal private boolean needsRightIndexCheck = false;

    /**
     * For integer sequences we need to make sure that start and stride is profiled.
     *
     * @throws SlowPathException
     */
    @Specialization(rewriteOn = SlowPathException.class)
    protected int doIntegerSequencePosition(RAbstractVector left, Object leftStore, int leftBase, int leftLength, Object targetDimensions, @SuppressWarnings("unused") int targetDimension, //
                    Object[] positions, RIntSequence position, int positionOffset, int positionLength, //
                    RAbstractContainer right, Object rightStore, int rightBase, int rightLength, boolean parentNA, //
                    @Cached("create()") IntValueProfile startProfile, //
                    @Cached("create()") IntValueProfile strideProfile, //
                    @Cached("createBinaryProfile()") ConditionProfile conditionProfile) throws SlowPathException {
        // skip NA check. sequences never contain NA values.
        int rightIndex = rightBase;
        int start = startProfile.profile(position.getStart() - 1);
        int stride = strideProfile.profile(position.getStride());
        int end = start + positionLength * stride;

        if (start < 0 || end <= 0) {
            throw new SlowPathException("rewrite to doIntegerPosition");
        }

        initRightIndexCheck(rightBase, positionLength, leftLength, rightLength);

        boolean ascending = conditionProfile.profile(start < end);
        for (int positionValue = start; ascending ? positionValue < end : positionValue > end; positionValue += stride) {
            rightIndex = applyInner(//
                            left, leftStore, leftBase, leftLength, targetDimensions, //
                            positions, positionOffset, positionValue, //
                            right, rightStore, rightLength, rightIndex, parentNA);
        }
        return rightIndex;
    }

    private void initRightIndexCheck(int rightBase, int positionLength, int leftLength, int rightLength) {
        if (!needsRightIndexCheck) {
            int actionRightMod = positionsApplyToRight ? leftLength : rightLength;
            if (rightBase + positionLength > actionRightMod) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                System.out.println("needs right index check");
                needsRightIndexCheck = true;
            }
        }
    }

    /**
     * Integer vectors iterate over the number of positions because we assume that the number of
     * positions in an integer vector is significantly lower than the number of elements in the
     * store. This might not be always true and could benefit from more investigation.
     */
    @Specialization(contains = "doIntegerSequencePosition")
    protected int doIntegerPosition(RAbstractVector left, Object leftStore, int leftBase, int leftLength, Object targetDimensions, @SuppressWarnings("unused") int targetDimension, //
                    Object[] positions, RAbstractIntVector position, int positionOffset, int positionLength, //
                    RAbstractContainer right, Object rightStore, int rightBase, int rightLength, boolean parentNA, //
                    @Cached("create()") CountedLoopConditionProfile lengthProfile) {
        positionNACheck.enable(position);
        int rightIndex = rightBase;

        initRightIndexCheck(rightBase, positionLength, leftLength, rightLength);

        lengthProfile.profileLength(positionLength);
        for (int i = 0; lengthProfile.inject(i < positionLength); i++) {
            int positionValue = position.getDataAt(i);
            boolean isNA = positionNACheck.check(positionValue);
            if (isNA) {
                if (skipNA) {
                    continue;
                }
            }
            rightIndex = applyInner(//
                            left, leftStore, leftBase, leftLength, targetDimensions, //
                            positions, positionOffset, positionValue - 1, //
                            right, rightStore, rightLength, rightIndex, isNA || parentNA);
        }
        return rightIndex;
    }

    private int applyInner(//
                    RAbstractVector left, Object leftStore, int leftBase, int leftLength, Object targetDimensions, //
                    Object[] positions, int positionOffset, int positionValue, //
                    RAbstractContainer right, Object rightStore, int rightLength, int rightIndex, boolean isNA) {
        int newTargetIndex = leftBase + positionValue * positionOffset;
        if (dimensionIndex == 0) {
            // for-loops leaf for innermost dimension

            // if position indexes value we just need to switch indices
            int actionLeftIndex;
            int actionRightIndex;
            int actionRightMod;
            if (positionsApplyToRight) {
                actionLeftIndex = rightIndex;
                actionRightIndex = newTargetIndex;
                actionRightMod = leftLength;
            } else {
                actionLeftIndex = newTargetIndex;
                actionRightIndex = rightIndex;
                actionRightMod = rightLength;
            }
            if (isNA) {
                left.setNA(leftStore, actionLeftIndex);
                getValueNACheck().seenNA();
            } else {
                scalarNode.apply(left, leftStore, actionLeftIndex, right, rightStore, actionRightIndex);
            }

            int result = rightIndex + 1;
            if (needsRightIndexCheck && result == actionRightMod) {
                return 0;
            }
            return result;
        } else {
            // generate another for-loop for other dimensions
            int nextTargetDimension = innerVectorNode.dimensionValueProfile.profile(((int[]) targetDimensions)[innerVectorNode.dimensionIndex]);
            return innerVectorNode.applyImpl(//
                            left, leftStore, newTargetIndex, leftLength, targetDimensions, nextTargetDimension, //
                            positions, positionOffset, //
                            right, rightStore, rightIndex, rightLength, isNA);
        }
    }

    private static WriteIndexedScalarNode<? extends RAbstractVector, ? extends RAbstractContainer> createIndexedAction(RType type, boolean setListElementAsObject) {
        switch (type) {
            case Logical:
                return new WriteLogicalAction();
            case Integer:
                return new WriteIntegerAction();
            case Double:
                return new WriteDoubleAction();
            case Complex:
                return new WriteComplexAction();
            case Character:
                return new WriteCharacterAction();
            case Raw:
                return new WriteRawAction();
            case Language:
            case DataFrame:
            case Expression:
            case PairList:
            case List:
                return new WriteListAction(setListElementAsObject);
            default:
                throw RInternalError.shouldNotReachHere();
        }
    }

    private abstract static class WriteIndexedScalarNode<A extends RAbstractVector, V extends RAbstractContainer> extends Node {

        final NACheck valueNACheck = NACheck.create();

        abstract void apply(A leftAccess, Object leftStore, int leftIndex, V rightAccess, Object rightStore, int rightIndex);

    }

    private static final class WriteLogicalAction extends WriteIndexedScalarNode<RAbstractLogicalVector, RAbstractLogicalVector> {

        @Override
        void apply(RAbstractLogicalVector leftAccess, Object leftStore, int leftIndex, RAbstractLogicalVector rightAccess, Object rightStore, int rightIndex) {
            byte value = rightAccess.getDataAt(rightStore, rightIndex);
            leftAccess.setDataAt(leftStore, leftIndex, value);
            valueNACheck.check(value);
        }
    }

    private static final class WriteIntegerAction extends WriteIndexedScalarNode<RAbstractIntVector, RAbstractIntVector> {

        @Override
        void apply(RAbstractIntVector leftAccess, Object leftStore, int leftIndex, RAbstractIntVector rightAccess, Object rightStore, int rightIndex) {
            int value = rightAccess.getDataAt(rightStore, rightIndex);
            leftAccess.setDataAt(leftStore, leftIndex, value);
            valueNACheck.check(value);
        }
    }

    private static final class WriteDoubleAction extends WriteIndexedScalarNode<RAbstractDoubleVector, RAbstractDoubleVector> {

        @Override
        void apply(RAbstractDoubleVector leftAccess, Object leftStore, int leftIndex, RAbstractDoubleVector rightAccess, Object rightStore, int rightIndex) {
            double value = rightAccess.getDataAt(rightStore, rightIndex);
            leftAccess.setDataAt(leftStore, leftIndex, value);
            valueNACheck.check(value);
        }
    }

    private static final class WriteComplexAction extends WriteIndexedScalarNode<RAbstractComplexVector, RAbstractComplexVector> {

        @Override
        void apply(RAbstractComplexVector leftAccess, Object leftStore, int leftIndex, RAbstractComplexVector rightAccess, Object rightStore, int rightIndex) {
            RComplex value = rightAccess.getDataAt(rightStore, rightIndex);
            leftAccess.setDataAt(leftStore, leftIndex, value);
            valueNACheck.check(value);
        }
    }

    private static final class WriteCharacterAction extends WriteIndexedScalarNode<RAbstractStringVector, RAbstractStringVector> {

        @Override
        void apply(RAbstractStringVector leftAccess, Object leftStore, int leftIndex, RAbstractStringVector rightAccess, Object rightStore, int rightIndex) {
            String value = rightAccess.getDataAt(rightStore, rightIndex);
            leftAccess.setDataAt(leftStore, leftIndex, value);
            valueNACheck.check(value);
        }
    }

    private static final class WriteRawAction extends WriteIndexedScalarNode<RAbstractRawVector, RAbstractRawVector> {

        @Override
        void apply(RAbstractRawVector leftAccess, Object leftStore, int leftIndex, RAbstractRawVector rightAccess, Object rightStore, int rightIndex) {
            byte value = rightAccess.getRawDataAt(rightStore, rightIndex);
            leftAccess.setRawDataAt(leftStore, leftIndex, value);
            valueNACheck.check(value);
        }
    }

    private static final class WriteListAction extends WriteIndexedScalarNode<RAbstractListVector, RAbstractContainer> {

        private final boolean setListElementAsObject;

        public WriteListAction(boolean setListElementAsObject) {
            this.setListElementAsObject = setListElementAsObject;
        }

        @Override
        void apply(RAbstractListVector leftAccess, Object leftStore, int leftIndex, RAbstractContainer rightAccess, Object rightStore, int rightIndex) {
            Object rightValue;
            if (setListElementAsObject) {
                rightValue = rightAccess;
                if (rightValue instanceof RAbstractVector) {
                    // TODO we should unbox instead of materialize here
                    rightValue = ((RAbstractVector) rightValue).materialize();
                }
            } else {
                rightValue = rightAccess.getDataAtAsObject(rightStore, rightIndex);
            }
            leftAccess.setDataAt(leftStore, leftIndex, rightValue);
            valueNACheck.checkListElement(rightValue);
        }
    }

}
