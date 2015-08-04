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
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.control.*;
import com.oracle.truffle.r.nodes.primitive.*;
import com.oracle.truffle.r.nodes.profile.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.nodes.*;
import com.oracle.truffle.r.runtime.ops.*;

public abstract class BinaryArithmeticNode extends RBuiltinNode {

    protected static final int CACHE_LIMIT = 5;

    protected final BinaryArithmeticFactory binary;
    protected final UnaryArithmeticFactory unary;

    public BinaryArithmeticNode(BinaryArithmeticFactory binaryFactory, UnaryArithmeticFactory unaryFactory) {
        this.binary = binaryFactory;
        this.unary = unaryFactory;
    }

    @Override
    protected void createCasts(CastBuilder casts) {
        casts.boxPrimitive(0).boxPrimitive(1);
    }

    public abstract Object execute(Object left, Object right);

    public static BinaryArithmeticNode create(BinaryArithmeticFactory binary, UnaryArithmeticFactory unary) {
        return BinaryArithmeticNodeGen.create(binary, unary, new RNode[]{null, null}, null, null);
    }

    @Specialization(limit = "CACHE_LIMIT", guards = {"cached != null", "cached.isSupported(left, right)"})
    protected Object doNumericVectorCached(Object left, Object right, //
                    @Cached("createFastCached(left, right)") BinaryMapNode cached) {
        return cached.apply(left, right);
    }

    @Specialization(contains = "doNumericVectorCached", guards = {"isNumericVector(left)", "isNumericVector(right)"})
    @TruffleBoundary
    protected Object doNumericVectorGeneric(Object left, Object right, //
                    @Cached("binary.create()") BinaryArithmetic arithmetic, //
                    @Cached("new(createCached(arithmetic, left, right))") GenericNumericVectorNode generic) {
        RAbstractVector leftVector = (RAbstractVector) left;
        RAbstractVector rightVector = (RAbstractVector) right;
        return generic.get(arithmetic, leftVector, rightVector).apply(leftVector, rightVector);
    }

    protected BinaryMapNode createFastCached(Object left, Object right) {
        if (isNumericVector(left) && isNumericVector(right)) {
            return createCached(binary.create(), left, right);
        }
        return null;
    }

    protected static boolean isNumericVector(Object value) {
        return value instanceof RAbstractIntVector || value instanceof RAbstractDoubleVector || value instanceof RAbstractComplexVector || value instanceof RAbstractLogicalVector;
    }

    @Specialization
    @SuppressWarnings("unused")
    protected Object doUnary(Object left, RMissing right, //
                    @Cached("createUnaryArithmeticNode()") UnaryArithmeticNode unaryNode) {
        return unaryNode.execute(left);
    }

    protected final UnaryArithmeticNode createUnaryArithmeticNode() {
        if (unary == null) {
            throw RError.error(this, RError.Message.ARGUMENT_EMPTY, 2);
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return UnaryArithmeticNodeGen.create(unary, RError.Message.INVALID_ARG_TYPE_UNARY);
        }
    }

    protected static boolean isFactor(Object value) {
        return value instanceof RFactor;
    }

    @Specialization(guards = "isFactor(left) || isFactor(right)")
    @TruffleBoundary
    protected Object doFactor(Object left, Object right, @Cached("create()") RLengthNode lengthNode) {
        Message warning;
        if (left instanceof RFactor) {
            warning = getFactorWarning((RFactor) left);
        } else {
            warning = getFactorWarning((RFactor) right);
        }
        RError.warning(this, warning, binary.create().opName());
        return RDataFactory.createNAVector(Math.max(lengthNode.executeInteger(left), lengthNode.executeInteger(right)));
    }

    private static Message getFactorWarning(RFactor factor) {
        return factor.isOrdered() ? Message.NOT_MEANINGFUL_FOR_ORDERED_FACTORS : Message.NOT_MEANINGFUL_FOR_FACTORS;
    }

    @Specialization
    @SuppressWarnings("unused")
    protected static Object doBothNull(RNull left, RNull right) {
        return RType.Double.getEmpty();
    }

    @Specialization(guards = "isNumericVector(right)")
    protected static Object doLeftNull(@SuppressWarnings("unused") RNull left, Object right, //
                    @Cached("createClassProfile()") ValueProfile classProfile) {
        if (((RAbstractVector) classProfile.profile(right)).getRType() == RType.Complex) {
            return RDataFactory.createEmptyComplexVector();
        } else {
            return RDataFactory.createEmptyDoubleVector();
        }
    }

    @Specialization(guards = "isNumericVector(left)")
    protected static Object doRightNull(Object left, RNull right, //
                    @Cached("createClassProfile()") ValueProfile classProfile) {
        return doLeftNull(right, left, classProfile);
    }

    @SuppressWarnings("unused")
    @Fallback
    protected Object doInvalidType(Object left, Object right) {
        throw RError.error(this, Message.NON_NUMERIC_BINARY);
    }

    protected static BinaryMapNode createCached(BinaryArithmetic innerArithmetic, Object left, Object right) {
        RAbstractVector leftVector = (RAbstractVector) left;
        RAbstractVector rightVector = (RAbstractVector) right;

        RType argumentType = RType.maxPrecedence(RType.Integer, RType.maxPrecedence(leftVector.getRType(), rightVector.getRType()));
        RType resultType = argumentType;
        if (resultType == RType.Integer && !innerArithmetic.isSupportsIntResult()) {
            resultType = RType.Double;
        }

        return BinaryMapNode.create(new BinaryMapArithmeticFunctionNode(innerArithmetic), leftVector, rightVector, argumentType, resultType, true);
    }

    protected static final class GenericNumericVectorNode extends TruffleBoundaryNode {

        @Child private BinaryMapNode cached;

        public GenericNumericVectorNode(BinaryMapNode cachedOperation) {
            this.cached = insert(cachedOperation);
        }

        public BinaryMapNode get(BinaryArithmetic arithmetic, RAbstractVector left, RAbstractVector right) {
            CompilerAsserts.neverPartOfCompilation();
            if (!cached.isSupported(left, right)) {
                cached = cached.replace(createCached(arithmetic, left, right));
            }
            return cached;
        }

    }

}
