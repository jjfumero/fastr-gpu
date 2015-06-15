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
package com.oracle.truffle.r.nodes.binary;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.control.*;
import com.oracle.truffle.r.nodes.primitive.*;
import com.oracle.truffle.r.nodes.profile.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.conn.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.closures.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ops.*;
import com.oracle.truffle.r.runtime.ops.BinaryLogic.And;
import com.oracle.truffle.r.runtime.ops.BinaryLogic.Or;

public abstract class BinaryBooleanNode extends RBuiltinNode {

    protected static final int CACHE_LIMIT = 5;

    protected final BooleanOperationFactory factory;

    public BinaryBooleanNode(BooleanOperationFactory factory) {
        this.factory = factory;
    }

    @Override
    protected void createCasts(CastBuilder casts) {
        casts.boxPrimitive(0).boxPrimitive(1);
    }

    private static boolean isLogicOp(BooleanOperation op) {
        return op instanceof And || op instanceof Or;
    }

    private static boolean isLogicOp(BooleanOperationFactory factory) {
        return factory == BinaryLogic.AND || factory == BinaryLogic.OR;
    }

    public abstract Object execute(Object left, Object right);

    public static BinaryBooleanNode create(BooleanOperationFactory factory) {
        return BinaryBooleanNodeGen.create(factory, new RNode[]{null, null}, null, null);
    }

    @Specialization(limit = "CACHE_LIMIT", guards = {"cached != null", "cached.isSupported(left, right)"})
    protected Object doNumericVectorCached(Object left, Object right, //
                    @Cached("createFastCached(left, right)") BinaryMapNode cached) {
        return cached.apply(left, right);
    }

    @Specialization(contains = "doNumericVectorCached", guards = "isSupported(left, right)")
    @TruffleBoundary
    protected Object doNumericVectorGeneric(Object left, Object right, //
                    @Cached("factory.create()") BooleanOperation operation, //
                    @Cached("new(createCached(operation, left, right))") GenericNumericVectorNode generic) {
        RAbstractVector leftVector = (RAbstractVector) left;
        RAbstractVector rightVector = (RAbstractVector) right;
        return generic.get(operation, leftVector, rightVector).apply(leftVector, rightVector);
    }

    protected BinaryMapNode createFastCached(Object left, Object right) {
        if (isSupported(left, right)) {
            return createCached(factory.create(), left, right);
        }
        return null;
    }

    protected boolean isSupported(Object left, Object right) {
        if (isLogicOp(factory) && left instanceof RAbstractRawVector && right instanceof RAbstractRawVector) {
            // for logic ops only both raw vectors are supported
            return true;
        } else if (isSupportedVector(left) && isSupportedVector(right)) {
            return true;
        }
        return false;
    }

    protected boolean isSupportedVector(Object value) {
        return value instanceof RAbstractIntVector || value instanceof RAbstractDoubleVector || value instanceof RAbstractComplexVector || value instanceof RAbstractLogicalVector ||
                        (!isLogicOp(factory) && (value instanceof RAbstractStringVector || value instanceof RAbstractRawVector));
    }

    protected static boolean isFactor(Object value) {
        return value instanceof RFactor;
    }

    @Specialization(guards = {"isRConnection(left) || isRConnection(right)"})
    protected Object doConnection(Object left, Object right, //
                    @Cached("createRecursive()") BinaryBooleanNode recursive) {
        Object recursiveLeft = left;
        if (recursiveLeft instanceof RConnection) {
            recursiveLeft = RInteger.valueOf(((RConnection) recursiveLeft).getDescriptor());
        }
        Object recursiveRight = right;
        if (recursiveRight instanceof RConnection) {
            recursiveRight = RInteger.valueOf(((RConnection) recursiveRight).getDescriptor());
        }
        return recursive.execute(recursiveLeft, recursiveRight);
    }

    @Specialization(guards = {"isFactor(left) || isFactor(right)", "meaningfulFactorOp(left, right)"})
    protected Object doFactorMeaningful(Object left, Object right, //
                    @Cached("createRecursive()") BinaryBooleanNode recursive, //
                    @Cached("create()") RAttributeProfiles attrProfiles) {
        Object recursiveLeft = left;
        if (recursiveLeft instanceof RFactor) {
            recursiveLeft = RClosures.createFactorToVector((RFactor) recursiveLeft, false, attrProfiles);
        }
        Object recursiveRight = right;
        if (recursiveRight instanceof RFactor) {
            recursiveRight = RClosures.createFactorToVector((RFactor) recursiveRight, false, attrProfiles);
        }
        return recursive.execute(recursiveLeft, recursiveRight);
    }

    protected BinaryBooleanNode createRecursive() {
        return BinaryBooleanNodeGen.create(factory);
    }

    @Specialization(guards = {"isFactor(left) || isFactor(right)", "!meaningfulFactorOp(left, right)"})
    @TruffleBoundary
    protected Object doFactorNotMeaniningful(Object left, Object right, @Cached("create()") RLengthNode lengthNode) {
        Message warning;
        if (left instanceof RFactor) {
            warning = getFactorWarning((RFactor) left);
        } else {
            warning = getFactorWarning((RFactor) right);
        }
        RError.warning(getSourceSection(), warning, factory.create().opName());
        return RDataFactory.createNAVector(Math.max(lengthNode.executeInteger(left), lengthNode.executeInteger(right)));
    }

    protected boolean meaningfulFactorOp(Object left, Object right) {
        if (factory == BinaryCompare.EQUAL || factory == BinaryCompare.NOT_EQUAL) {
            return true;
        } else if (left instanceof RFactor) {
            boolean ordered = ((RFactor) left).isOrdered();
            if (right instanceof RFactor) {
                return ordered && ((RFactor) right).isOrdered();
            }
            return ordered;
        } else {
            assert right instanceof RFactor;
            return ((RFactor) right).isOrdered();
        }
    }

    private static Message getFactorWarning(RFactor factor) {
        return factor.isOrdered() ? Message.NOT_MEANINGFUL_FOR_ORDERED_FACTORS : Message.NOT_MEANINGFUL_FOR_FACTORS;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"(isRNull(left) || isEmpty(left)) || (isRNull(right) || isEmpty(right))"})
    protected static Object doEmptyOrNull(Object left, Object right) {
        return RType.Logical.getEmpty();
    }

    protected static boolean isEmpty(Object value) {
        return (isRAbstractVector(value) && ((RAbstractVector) value).getLength() == 0);
    }

    @SuppressWarnings("unused")
    @Fallback
    protected Object doInvalidType(Object left, Object right) {
        throw RError.error(getSourceSection(), Message.OPERATIONS_NUMERIC_LOGICAL_COMPLEX);
    }

    protected static BinaryMapNode createCached(BooleanOperation operation, Object left, Object right) {
        RAbstractVector leftVector = (RAbstractVector) left;
        RAbstractVector rightVector = (RAbstractVector) right;

        RType argumentType = RType.maxPrecedence(leftVector.getRType(), rightVector.getRType());
        RType resultType = RType.Logical;
        if (isLogicOp(operation) && argumentType == RType.Raw) {
            resultType = RType.Raw;
        } else {
            resultType = RType.Logical;
        }

        return BinaryMapNode.create(new BinaryMapBooleanFunctionNode(operation), leftVector, rightVector, argumentType, resultType, false);
    }

    protected static final class GenericNumericVectorNode extends TruffleBoundaryNode {

        @Child private BinaryMapNode cached;

        public GenericNumericVectorNode(BinaryMapNode cachedOperation) {
            this.cached = insert(cachedOperation);
        }

        public BinaryMapNode get(BooleanOperation arithmetic, RAbstractVector left, RAbstractVector right) {
            CompilerAsserts.neverPartOfCompilation();
            if (!cached.isSupported(left, right)) {
                cached = cached.replace(createCached(arithmetic, left, right));
            }
            return cached;
        }

    }

}
