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
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.binary.BinaryBooleanScalarNodeGen.LogicalScalarCastNodeGen;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.function.PromiseHelperNode.PromiseCheckHelperNode;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ops.*;
import com.oracle.truffle.r.runtime.ops.na.*;

public abstract class BinaryBooleanScalarNode extends RBuiltinNode {

    /*
     * As the execution of right depends on the left value and the right node might be arbitrarily
     * expensive we use a more expensive counting condition profile to inject more accurate profile
     * here.
     */
    private final ConditionProfile profile = ConditionProfile.createCountingProfile();

    @Child private BinaryMapBooleanFunctionNode logic;
    @Child private LogicalScalarCastNode leftCast;
    @Child private BoxPrimitiveNode leftBox;
    @Child private LogicalScalarCastNode rightCast;
    @Child private BoxPrimitiveNode rightBox;
    @Child private PromiseCheckHelperNode promiseHelper;

    private final BooleanOperation booleanLogic;

    public BinaryBooleanScalarNode(BooleanOperationFactory factory) {
        this.booleanLogic = factory.create();
    }

    @Specialization
    protected byte binary(VirtualFrame frame, Object leftValue, Object rightValue) {
        if (leftCast == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            logic = insert(new BinaryMapBooleanFunctionNode(booleanLogic));
            leftCast = insert(LogicalScalarCastNodeGen.create(booleanLogic.opName(), "x", logic.getLeftNACheck()));
            leftBox = insert(BoxPrimitiveNodeGen.create());
        }
        byte left = leftCast.executeCast(leftBox.execute(leftValue));
        if (profile.profile(logic.requiresRightOperand(left))) {
            if (rightCast == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                rightCast = insert(LogicalScalarCastNodeGen.create(booleanLogic.opName(), "y", logic.getRightNACheck()));
                rightBox = insert(BoxPrimitiveNodeGen.create());
                promiseHelper = insert(new PromiseCheckHelperNode());
            }
            byte right = rightCast.executeCast(rightBox.execute(promiseHelper.checkEvaluate(frame, rightValue)));
            return logic.applyLogical(left, right);
        }
        return left;
    }

    protected abstract static class LogicalScalarCastNode extends Node {

        protected static final int CACHE_LIMIT = 3;

        public abstract byte executeCast(Object o);

        private final String opName;
        private final String argumentName;

        private final NACheck check;
        private final BranchProfile seenEmpty = BranchProfile.create();

        public LogicalScalarCastNode(String opName, String argumentName, NACheck check) {
            this.opName = opName;
            this.argumentName = argumentName;
            this.check = check;
        }

        @Specialization(limit = "CACHE_LIMIT", guards = {"cachedClass != null", "operand.getClass() == cachedClass"})
        protected byte doCached(Object operand, @Cached("getNumericVectorClass(operand)") Class<? extends RAbstractVector> cachedClass) {
            return castImpl(cachedClass.cast(operand));
        }

        @Specialization(contains = "doCached", guards = {"operand.getRType().isNumeric()"})
        @TruffleBoundary
        protected byte doGeneric(RAbstractVector operand) {
            return castImpl(operand);
        }

        private byte castImpl(RAbstractVector vector) {
            this.check.enable(!vector.isComplete());
            if (vector.getLength() == 0) {
                seenEmpty.enter();
                return RRuntime.LOGICAL_NA;
            }
            RType type = vector.getRType();
            CompilerAsserts.compilationConstant(type);
            switch (type) {
                case Logical:
                    return (byte) vector.getDataAtAsObject(0);
                case Integer:
                    return check.convertIntToLogical((int) vector.getDataAtAsObject(0));
                case Double:
                    return check.convertDoubleToLogical((double) vector.getDataAtAsObject(0));
                case Complex:
                    return check.convertComplexToLogical((RComplex) vector.getDataAtAsObject(0));
                default:
                    throw RInternalError.shouldNotReachHere();
            }
        }

        protected static Class<? extends RAbstractVector> getNumericVectorClass(Object value) {
            if (value instanceof RAbstractVector) {
                RAbstractVector castVector = (RAbstractVector) value;
                if (castVector.getRType().isNumeric()) {
                    return castVector.getClass();
                }
            }
            return null;
        }

        @Fallback
        protected byte doFallback(@SuppressWarnings("unused") Object operand) {
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.INVALID_TYPE_IN, argumentName, opName);
        }
    }
}
