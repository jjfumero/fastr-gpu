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

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.function.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ops.na.*;

public abstract class Bind extends RPrecedenceBuiltinNode {

    @Child private CastToVectorNode castVector;
    @Child private UseMethodInternalNode dcn;

    private final ConditionProfile nullNamesProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile emptyVectorProfile = ConditionProfile.createBinaryProfile();
    private final BranchProfile nonNullNames = BranchProfile.create();
    private final NACheck naCheck = NACheck.create();
    private final RAttributeProfiles attrProfiles = RAttributeProfiles.create();

    protected String getBindType() {
        // this method should be abstract but due to annotation processor problem it does not work
        RInternalError.unimplemented("getBindType() method must be overridden in a subclass");
        return null;
    }

    protected RAbstractVector castVector(Object value) {
        if (castVector == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castVector = insert(CastToVectorNodeGen.create(null, false, false, false, false));
        }
        return (RAbstractVector) castVector.executeObject(value);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isNullPrecedence(args)")
    protected RNull allNull(VirtualFrame frame, Object deparseLevelObj, RArgsValuesAndNames args) {
        controlVisibility();
        return RNull.instance;
    }

    private static final ArgumentsSignature SIGNATURE = ArgumentsSignature.get("deparse.level", "...");

    @Specialization(guards = {"!oneElement(args)", "isDataFrame(args)"})
    protected Object allDataFrame(VirtualFrame frame, Object deparseLevel, RArgsValuesAndNames args) {
        if (dcn == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            dcn = insert(new UseMethodInternalNode(getBindType(), SIGNATURE, false));
        }
        try {
            return dcn.execute(frame, (RDataFrame) args.getArgument(0), new Object[]{deparseLevel, args});
        } catch (S3FunctionLookupNode.NoGenericMethodException e) {
            throw RInternalError.shouldNotReachHere();
        }
    }

    private Object bindInternal(VirtualFrame frame, Object deparseLevel, RArgsValuesAndNames args, CastNode castNode, boolean needsVectorCast) {
        controlVisibility();
        Object[] array = args.getArguments();
        ArgumentsSignature signature = args.getSignature();
        String[] vecNames = nullNamesProfile.profile(signature.getNonNullCount() == 0) ? null : new String[signature.getLength()];
        RAbstractVector[] vectors = new RAbstractVector[args.getLength()];
        boolean complete = true;
        int ind = 0;
        naCheck.enable(true);
        for (int i = 0; i < array.length; i++) {
            if (vecNames != null) {
                nonNullNames.enter();
                vecNames[ind] = signature.getName(i);
                naCheck.check(vecNames[ind]);
            }
            Object result = castNode.executeCast(array[i]);
            RAbstractVector vector;
            if (needsVectorCast) {
                vector = castVector(result);
            } else {
                vector = (RAbstractVector) result;
            }
            if (emptyVectorProfile.profile(vector.getLength() == 0)) {
                // nothing to do
            } else {
                vectors[ind] = vector;
                complete &= vector.isComplete();
                ind++;
            }
        }
        if (emptyVectorProfile.profile(ind < array.length)) {
            if (vecNames != null) {
                nonNullNames.enter();
                vecNames = Arrays.copyOf(vecNames, ind);
            }
            vectors = Arrays.copyOf(vectors, ind);
        }
        return genericBind(frame, vectors, complete, vecNames, naCheck.neverSeenNA(), deparseLevel);
    }

    @Specialization(guards = {"isIntegerPrecedence(args)", "!oneElement(args)", "!isDataFrame(args)"})
    protected Object allInt(VirtualFrame frame, Object deparseLevel, RArgsValuesAndNames args, @Cached("create()") CastIntegerNode cast) {
        return bindInternal(frame, deparseLevel, args, cast, true);
    }

    @Specialization(guards = {"isDoublePrecedence( args)", "!oneElement(args)", "!isDataFrame(args)"})
    protected Object allDouble(VirtualFrame frame, Object deparseLevel, RArgsValuesAndNames args, @Cached("create()") CastDoubleNode cast) {
        return bindInternal(frame, deparseLevel, args, cast, true);
    }

    @Specialization(guards = {"isStringPrecedence( args)", "!oneElement(args)", "!isDataFrame(args)"})
    protected Object allString(VirtualFrame frame, Object deparseLevel, RArgsValuesAndNames args, @Cached("create()") CastStringNode cast) {
        return bindInternal(frame, deparseLevel, args, cast, true);
    }

    @Specialization(guards = {"isComplexPrecedence( args)", "!oneElement(args)", "!isDataFrame(args)"})
    protected Object allComplex(VirtualFrame frame, Object deparseLevel, RArgsValuesAndNames args, @Cached("create()") CastComplexNode cast) {
        return bindInternal(frame, deparseLevel, args, cast, true);
    }

    @Specialization(guards = {"isListPrecedence( args)", "!oneElement(args)", "!isDataFrame(args)"})
    protected Object allList(VirtualFrame frame, Object deparseLevel, RArgsValuesAndNames args, @Cached("create()") CastListNode cast) {
        return bindInternal(frame, deparseLevel, args, cast, false);
    }

    protected Object allOneElem(VirtualFrame frame, Object deparseLevelObj, RArgsValuesAndNames args, boolean cbind) {
        RAbstractVector vec = castVector(args.getArgument(0));
        if (vec.isMatrix()) {
            return vec;
        }
        int[] dims = getDimensions(vec, cbind);
        // for cbind dimNamesA is names for the 1st dim and dimNamesB is names for 2nd dim; for
        // rbind the other way around
        Object dimNamesA = vec.getNames(attrProfiles) == null ? RNull.instance : vec.getNames(attrProfiles);
        Object dimNamesB;

        ArgumentsSignature signature = args.getSignature();
        if (signature.getNonNullCount() == 0) {
            int deparseLevel = deparseLevel(deparseLevelObj);
            if (deparseLevel == 0) {
                dimNamesB = RNull.instance;
            } else {
                // var arg is at the first position - as in the R bind call
                RArgsValuesAndNames varArg = (RArgsValuesAndNames) RArguments.getArgument(frame, 0);
                String deparsedName = deparseArgName(varArg, deparseLevel, 0);
                dimNamesB = deparsedName == RRuntime.NAMES_ATTR_EMPTY_VALUE ? RNull.instance : RDataFactory.createStringVector(deparsedName);
            }
        } else {
            String[] names = new String[signature.getLength()];
            for (int i = 0; i < names.length; i++) {
                names[i] = signature.getName(i);
            }
            dimNamesB = RDataFactory.createStringVector(names, RDataFactory.COMPLETE_VECTOR);
        }

        RVector res = (RVector) vec.copyWithNewDimensions(dims);
        res.setDimNames(RDataFactory.createList(cbind ? new Object[]{dimNamesA, dimNamesB} : new Object[]{dimNamesB, dimNamesA}));
        res.copyRegAttributesFrom(vec);
        return res;
    }

    /**
     * Compute dimnames for rows (cbind) or columns (rbind) from names of elements of combined
     * vectors.
     *
     * @param vec
     * @param dimLength
     * @return dimnames
     */
    protected Object getDimResultNamesFromElements(RAbstractVector vec, int dimLength, int dimInd) {
        Object firstDimResultNames = RNull.instance;
        Object firstDimNames = RNull.instance;
        if (vec.isMatrix()) {
            RList vecDimNames = vec.getDimNames(attrProfiles);
            if (vecDimNames != null) {
                firstDimNames = vecDimNames.getDataAt(dimInd);
            }
        } else if (!vec.isArray() || vec.getDimensions().length == 1) {
            RStringVector names = vec.getNames(attrProfiles);
            firstDimNames = names == null ? RNull.instance : names;
        } else {
            RInternalError.unimplemented("binding multi-dimensional arrays is not supported");
        }
        if (firstDimNames != RNull.instance) {
            RStringVector names = (RStringVector) firstDimNames;
            if (names != null && names.getLength() == dimLength) {
                firstDimResultNames = names;
            }
        }
        return firstDimResultNames;
    }

    /**
     * Compute dimnames for columns (cbind) or rows (rbind) from names of vectors being combined or
     * by deparsing.
     *
     * @param frame
     * @param vec
     * @param argNames
     * @param resDim
     * @param oldInd
     * @param vecInd
     * @param deparseLevelObj
     * @param dimNamesArray
     * @return dimnames
     */
    protected int getDimResultNamesFromVectors(VirtualFrame frame, RAbstractVector vec, String[] argNames, int resDim, int oldInd, int vecInd, Object deparseLevelObj, String[] dimNamesArray,
                    int dimNamesInd) {
        int ind = oldInd;
        if (vec.isMatrix()) {
            RList vecDimNames = vec.getDimNames(attrProfiles);
            if (vecDimNames != null) {
                Object resDimNames = vecDimNames.getDataAt(dimNamesInd);
                if (resDimNames != RNull.instance) {
                    RStringVector names = (RStringVector) resDimNames;
                    assert names.getLength() == resDim;
                    for (int i = 0; i < names.getLength(); i++) {
                        dimNamesArray[ind++] = names.getDataAt(i);
                    }
                    return ind;
                }
            }
            for (int i = 0; i < resDim; i++) {
                dimNamesArray[ind++] = RRuntime.NAMES_ATTR_EMPTY_VALUE;
            }
            return -ind;
        } else if (!vec.isArray() || vec.getDimensions().length == 1) {
            if (argNames == null) {
                int deparseLevel = deparseLevel(deparseLevelObj);
                if (deparseLevel == 0) {
                    dimNamesArray[ind++] = RRuntime.NAMES_ATTR_EMPTY_VALUE;
                    return -ind;
                } else {
                    RArgsValuesAndNames varArg = (RArgsValuesAndNames) RArguments.getArgument(frame, 0);
                    String deparsedName = deparseArgName(varArg, deparseLevel, vecInd);
                    dimNamesArray[ind++] = deparsedName;
                    return deparsedName == RRuntime.NAMES_ATTR_EMPTY_VALUE ? -ind : ind;
                }
            } else {
                if (argNames[vecInd] == null) {
                    dimNamesArray[ind++] = RRuntime.NAMES_ATTR_EMPTY_VALUE;
                    return -ind;
                } else {
                    dimNamesArray[ind++] = argNames[vecInd];
                    return ind;
                }
            }
        } else {
            RInternalError.unimplemented("binding multi-dimensional arrays is not supported");
            return 0;
        }
    }

    @SuppressWarnings("unused")
    protected RVector genericBind(VirtualFrame frame, RAbstractVector[] vectors, boolean complete, String[] vacNames, boolean vecNamesComplete, Object deparseLevel) {
        // this method should be abstract but due to annotation processor problem it does not work
        RInternalError.unimplemented("genericBind() method must be overridden in a subclass");
        return null;
    }

    /**
     *
     * @param vectors vectors to be combined
     * @param res result dims
     * @param bindDims columns dim (cbind) or rows dim (rbind)
     * @param cbind to be used for cbind function (true) or rbind function (false)
     * @return whether number of rows (cbind) or columns (rbind) in vectors is the same
     */
    protected static boolean getResultDimensions(RAbstractVector[] vectors, int[] res, int[] bindDims, boolean cbind) {
        int srcDim1Ind = cbind ? 0 : 1;
        int srcDim2Ind = cbind ? 1 : 0;
        assert vectors.length > 0;
        int[] dim = getDimensions(vectors[0], cbind);
        assert dim.length == 2;
        bindDims[0] = dim[srcDim2Ind];
        res[srcDim1Ind] = dim[srcDim1Ind];
        res[srcDim2Ind] = dim[srcDim2Ind];
        boolean notEqualDims = false;
        for (int i = 1; i < vectors.length; i++) {
            int[] dims = getDimensions(vectors[i], cbind);
            assert dims.length == 2;
            bindDims[i] = dims[srcDim2Ind];
            if (dims[srcDim1Ind] != res[srcDim1Ind]) {
                notEqualDims = true;
                if (dims[srcDim1Ind] > res[srcDim1Ind]) {
                    res[srcDim1Ind] = dims[srcDim1Ind];
                }
            }
            res[srcDim2Ind] += dims[srcDim2Ind];
        }
        return notEqualDims;
    }

    protected int deparseLevel(Object deparseLevelObj) {
        RAbstractLogicalVector v = (RAbstractLogicalVector) castLogical(castVector(deparseLevelObj), true);
        if (v.getLength() == 0 || v.getDataAt(0) == 0) {
            return 0;
        } else {
            return v.getDataAt(0);
        }
    }

    protected static int[] getDimensions(RAbstractVector vector, boolean cbind) {
        int[] dimensions = vector.getDimensions();
        if (dimensions == null || dimensions.length != 2) {
            return cbind ? new int[]{vector.getLength(), 1} : new int[]{1, vector.getLength()};
        } else {
            assert dimensions.length == 2;
            return dimensions;
        }
    }

    @TruffleBoundary
    protected static String deparseArgName(RArgsValuesAndNames varArg, int deparseLevel, int argInd) {
        assert varArg.getLength() >= argInd;
        Object argValue = varArg.getArgument(argInd);
        if (argValue instanceof RPromise) {
            RPromise p = (RPromise) argValue;
            Object node = RASTUtils.createLanguageElement(RASTUtils.unwrap(p.getRep()));
            if (deparseLevel == 1 && node instanceof RSymbol) {
                return ((RSymbol) node).toString();
            } // else - TODO handle deparseLevel > 1
        }
        // else - TODO handle non-promise arg (particularly a problem with the bind function
        // execuded via do.call

        return RRuntime.NAMES_ATTR_EMPTY_VALUE;

    }

    protected boolean oneElement(RArgsValuesAndNames args) {
        return args.getLength() == 1;
    }

    protected boolean isDataFrame(RArgsValuesAndNames args) {
        return args.getArgument(0) instanceof RDataFrame;
    }

    @RBuiltin(name = "cbind", kind = INTERNAL, parameterNames = {"deparse.level", "..."})
    public abstract static class CbindInternal extends Bind {
        private final BranchProfile everSeenNotEqualRows = BranchProfile.create();

        @Override
        public String getBindType() {
            return "cbind";
        }

        @Specialization(guards = {"!isNullPrecedence(args)", "oneElement(args)"})
        protected Object allOneElem(VirtualFrame frame, Object deparseLevelObj, RArgsValuesAndNames args) {
            controlVisibility();
            return allOneElem(frame, deparseLevelObj, args, true);
        }

        @Override
        public RVector genericBind(VirtualFrame frame, RAbstractVector[] vectors, boolean complete, String[] vecNames, boolean vecNamesComplete, Object deparseLevel) {

            int[] resultDimensions = new int[2];
            int[] secondDims = new int[vectors.length];
            boolean notEqualRows = getResultDimensions(vectors, resultDimensions, secondDims, true);
            RVector result = vectors[0].createEmptySameType(resultDimensions[0] * resultDimensions[1], complete);

            int ind = 0;
            Object rowDimResultNames = RNull.instance;
            String[] colDimNamesArray = new String[resultDimensions[1]];
            int colInd = 0;
            boolean allColDimNamesNull = true;
            for (int i = 0; i < vectors.length; i++) {
                RAbstractVector vec = vectors[i];
                if (rowDimResultNames == RNull.instance) {
                    // get the first valid names value
                    rowDimResultNames = getDimResultNamesFromElements(vec, resultDimensions[0], 0);
                }

                // compute dimnames for the second dimension
                int newColInd = getDimResultNamesFromVectors(frame, vec, vecNames, secondDims[i], colInd, i, deparseLevel, colDimNamesArray, 1);
                if (newColInd < 0) {
                    colInd = -newColInd;
                } else {
                    allColDimNamesNull = false;
                    colInd = newColInd;
                }

                // compute result vector values
                int vecLength = vec.getLength();
                for (int j = 0; j < vecLength; j++) {
                    result.transferElementSameType(ind++, vec, j);
                }
                if (notEqualRows) {
                    everSeenNotEqualRows.enter();
                    if (vecLength < resultDimensions[0]) {
                        // re-use vector elements
                        int k = 0;
                        for (int j = 0; j < resultDimensions[0] - vecLength; j++, k = Utils.incMod(k, vecLength)) {
                            result.transferElementSameType(ind++, vectors[i], k);
                        }

                        if (k != 0) {
                            RError.warning(RError.Message.ROWS_NOT_MULTIPLE, i + 1);
                        }
                    }
                }

            }
            Object colDimResultNames = allColDimNamesNull ? RNull.instance : RDataFactory.createStringVector(colDimNamesArray, vecNamesComplete);
            result.setDimensions(resultDimensions);
            result.setDimNames(RDataFactory.createList(new Object[]{rowDimResultNames, colDimResultNames}));
            return result;
        }

    }

    @RBuiltin(name = "rbind", kind = INTERNAL, parameterNames = {"deparse.level", "..."})
    public abstract static class RbindInternal extends Bind {
        private final BranchProfile everSeenNotEqualColumns = BranchProfile.create();

        @Override
        public String getBindType() {
            return "rbind";
        }

        @Specialization(guards = {"!isNullPrecedence(args)", "oneElement(args)"})
        protected Object allOneElem(VirtualFrame frame, Object deparseLevelObj, RArgsValuesAndNames args) {
            controlVisibility();
            return allOneElem(frame, deparseLevelObj, args, false);
        }

        @Override
        public RVector genericBind(VirtualFrame frame, RAbstractVector[] vectors, boolean complete, String[] vecNames, boolean vecNamesComplete, Object deparseLevel) {

            int[] resultDimensions = new int[2];
            int[] firstDims = new int[vectors.length];
            boolean notEqualColumns = getResultDimensions(vectors, resultDimensions, firstDims, false);
            RVector result = vectors[0].createEmptySameType(resultDimensions[0] * resultDimensions[1], complete);

            Object colDimResultNames = RNull.instance;
            String[] rowDimNamesArray = new String[resultDimensions[0]];
            int rowInd = 0;
            boolean allRowDimNamesNull = true;
            int dstRowInd = 0;
            for (int i = 0; i < vectors.length; i++) {
                RAbstractVector vec = vectors[i];
                if (colDimResultNames == RNull.instance) {
                    // get the first valid names value
                    colDimResultNames = getDimResultNamesFromElements(vec, resultDimensions[1], 1);
                }

                // compute dimnames for the second dimension
                int newRowInd = getDimResultNamesFromVectors(frame, vec, vecNames, firstDims[i], rowInd, i, deparseLevel, rowDimNamesArray, 0);
                if (newRowInd < 0) {
                    rowInd = -newRowInd;
                } else {
                    allRowDimNamesNull = false;
                    rowInd = newRowInd;
                }

                // compute result vector values
                int vecLength = vec.getLength();
                int srcInd = 0;
                int j = 0;
                for (; j < vecLength / firstDims[i]; j++) {
                    for (int k = dstRowInd; k < dstRowInd + firstDims[i]; k++) {
                        result.transferElementSameType(j * resultDimensions[0] + k, vec, srcInd++);
                    }
                }
                if (notEqualColumns) {
                    everSeenNotEqualColumns.enter();
                    if (j < resultDimensions[1]) {
                        // re-use vector elements
                        int k = 0;
                        for (; j < resultDimensions[1]; j++, k = Utils.incMod(k, vecLength % resultDimensions[1])) {
                            result.transferElementSameType(j * resultDimensions[0] + (dstRowInd + firstDims[i] - 1), vectors[i], k);
                        }

                        if (k != 0) {
                            RError.warning(RError.Message.COLUMNS_NOT_MULTIPLE, i + 1);
                        }
                    }
                }
                dstRowInd += firstDims[i];

            }
            Object rowDimResultNames = allRowDimNamesNull ? RNull.instance : RDataFactory.createStringVector(rowDimNamesArray, vecNamesComplete);
            result.setDimensions(resultDimensions);
            result.setDimNames(RDataFactory.createList(new Object[]{rowDimResultNames, colDimResultNames}));
            return result;
        }

    }

}
