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
package com.oracle.truffle.r.nodes.access;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.access.CoerceBinaryNodeGen.VectorUpdateValueCastNodeGen;
import com.oracle.truffle.r.nodes.access.CoerceBinaryNodeGen.VectorUpdateVectorCastNodeGen;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.closures.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ops.na.*;

@SuppressWarnings({"unused"})
@NodeChildren({@NodeChild("left"), @NodeChild("right")})
public abstract class CoerceBinaryNode extends RNode {

    private final NACheck leftNACheck;
    private final NACheck rightNACheck;

    @Child private CoercedBinaryOperationNode updateNode = null;

    protected CoerceBinaryNode(CoercedBinaryOperationNode operationNode) {
        this.updateNode = operationNode;
        leftNACheck = new NACheck();
        rightNACheck = new NACheck();
    }

    protected CoerceBinaryNode(CoerceBinaryNode op) {
        this.updateNode = op.updateNode;
        this.leftNACheck = op.leftNACheck;
        this.rightNACheck = op.rightNACheck;
    }

    @CreateCast({"left"})
    protected RNode createCastLeft(RNode child) {
        return VectorUpdateVectorCastNodeGen.create(child, leftNACheck);
    }

    @CreateCast({"right"})
    protected RNode createCastRight(RNode child) {
        return VectorUpdateValueCastNodeGen.create(child, leftNACheck);
    }

    public abstract RNode getLeft();

    // Scalar assigned to vector

    private RLogicalVector doLogical(VirtualFrame frame, RLogicalVector vector, byte right) {
        try {
            return updateNode.executeEvaluated(frame, vector, right);
        } catch (UnexpectedResultException e) {
            throw illegal();
        }
    }

    private static RuntimeException illegal() {
        throw RInternalError.shouldNotReachHere();
    }

    private RIntVector doInt(VirtualFrame frame, RIntVector vector, int right) {
        try {
            return updateNode.executeEvaluated(frame, vector, right);
        } catch (UnexpectedResultException e) {
            throw illegal();
        }
    }

    private RDoubleVector doDouble(VirtualFrame frame, RDoubleVector vector, double right) {
        try {
            return updateNode.executeEvaluated(frame, vector, right);
        } catch (UnexpectedResultException e) {
            throw illegal();
        }
    }

    private RComplexVector doComplex(VirtualFrame frame, RComplexVector vector, RComplex right) {
        try {
            return updateNode.executeEvaluated(frame, vector, right);
        } catch (UnexpectedResultException e) {
            throw illegal();
        }
    }

    private RStringVector doString(VirtualFrame frame, RStringVector vector, String right) {
        try {
            return updateNode.executeEvaluated(frame, vector, right);
        } catch (UnexpectedResultException e) {
            throw illegal();
        }
    }

    // Vector assigned to vector

    private RLogicalVector doLogical(VirtualFrame frame, RLogicalVector vector, RLogicalVector right) {
        try {
            return updateNode.executeEvaluated(frame, vector, right);
        } catch (UnexpectedResultException e) {
            throw illegal();
        }
    }

    private RIntVector doInt(VirtualFrame frame, RIntVector vector, RIntVector right) {
        try {
            return updateNode.executeEvaluated(frame, vector, right);
        } catch (UnexpectedResultException e) {
            throw illegal();
        }
    }

    private RDoubleVector doDouble(VirtualFrame frame, RDoubleVector vector, RDoubleVector right) {
        try {
            return updateNode.executeEvaluated(frame, vector, right);
        } catch (UnexpectedResultException e) {
            throw illegal();
        }
    }

    private RComplexVector doComplex(VirtualFrame frame, RComplexVector vector, RComplexVector right) {
        try {
            return updateNode.executeEvaluated(frame, vector, right);
        } catch (UnexpectedResultException e) {
            throw illegal();
        }
    }

    private RStringVector doString(VirtualFrame frame, RStringVector vector, RStringVector right) {
        try {
            return updateNode.executeEvaluated(frame, vector, right);
        } catch (UnexpectedResultException e) {
            throw illegal();
        }
    }

    // list

    private RAbstractVector doList(VirtualFrame frame, RList list, RAbstractVector right) {
        try {
            return updateNode.executeEvaluated(frame, list, right);
        } catch (UnexpectedResultException e) {
            throw illegal();
        }
    }

    private RAbstractVector doList(VirtualFrame frame, RList list, RNull right) {
        try {
            return updateNode.executeEvaluated(frame, list, right);
        } catch (UnexpectedResultException e) {
            throw illegal();
        }
    }

    // Left side is RNull

    @Specialization
    protected RNull access(VirtualFrame frame, RNull left, RNull right) {
        return left;
    }

    @Specialization
    protected RLogicalVector access(VirtualFrame frame, RNull left, byte right) {
        return doLogical(frame, RDataFactory.createEmptyLogicalVector(), right);
    }

    @Specialization
    protected RIntVector access(VirtualFrame frame, RNull left, int right) {
        return doInt(frame, RDataFactory.createEmptyIntVector(), right);
    }

    @Specialization
    protected RDoubleVector access(VirtualFrame frame, RNull left, double right) {
        return doDouble(frame, RDataFactory.createEmptyDoubleVector(), right);
    }

    @Specialization
    protected RComplexVector access(VirtualFrame frame, RNull left, RComplex right) {
        return doComplex(frame, RDataFactory.createEmptyComplexVector(), right);
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RNull left, String right) {
        return doString(frame, RDataFactory.createEmptyStringVector(), right);
    }

    @Specialization
    protected RLogicalVector access(VirtualFrame frame, RNull left, RLogicalVector right) {
        return doLogical(frame, RDataFactory.createEmptyLogicalVector(), right);
    }

    @Specialization
    protected RIntVector access(VirtualFrame frame, RNull left, RIntVector right) {
        return doInt(frame, RDataFactory.createEmptyIntVector(), right);
    }

    @Specialization
    protected RDoubleVector access(VirtualFrame frame, RNull left, RDoubleVector right) {
        return doDouble(frame, RDataFactory.createEmptyDoubleVector(), right);
    }

    @Specialization
    protected RComplexVector access(VirtualFrame frame, RNull left, RComplexVector right) {
        return doComplex(frame, RDataFactory.createEmptyComplexVector(), right);
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RNull left, RStringVector right) {
        return doString(frame, RDataFactory.createEmptyStringVector(), right);
    }

    // Left side is RLogicalVector

    @Specialization
    protected RLogicalVector access(VirtualFrame frame, RLogicalVector left, RNull right) {
        return doLogical(frame, left, RDataFactory.createEmptyLogicalVector());
    }

    @Specialization
    protected RLogicalVector access(VirtualFrame frame, RLogicalVector left, byte right) {
        return doLogical(frame, left, right);
    }

    @Specialization
    protected RIntVector access(VirtualFrame frame, RLogicalVector left, int right) {
        leftNACheck.enable(left);
        return doInt(frame, RClosures.createLogicalToIntVector(left).materialize(), right);
    }

    @Specialization
    protected RDoubleVector access(VirtualFrame frame, RLogicalVector left, double right) {
        leftNACheck.enable(left);
        return doDouble(frame, RClosures.createLogicalToDoubleVector(left).materialize(), right);
    }

    @Specialization
    protected RComplexVector access(VirtualFrame frame, RLogicalVector left, RComplex right) {
        leftNACheck.enable(left);
        return doComplex(frame, RClosures.createLogicalToComplexVector(left).materialize(), right);
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RLogicalVector left, String right) {
        return doString(frame, left.toStringVector(), right);
    }

    @Specialization
    protected RLogicalVector access(VirtualFrame frame, RLogicalVector left, RLogicalVector right) {
        return doLogical(frame, left, right);
    }

    @Specialization
    protected RIntVector access(VirtualFrame frame, RLogicalVector left, RIntVector right) {
        leftNACheck.enable(left);
        return doInt(frame, RClosures.createLogicalToIntVector(left).materialize(), right);
    }

    @Specialization
    protected RDoubleVector access(VirtualFrame frame, RLogicalVector left, RDoubleVector right) {
        leftNACheck.enable(left);
        return doDouble(frame, RClosures.createLogicalToDoubleVector(left).materialize(), right);
    }

    @Specialization
    protected RComplexVector access(VirtualFrame frame, RLogicalVector left, RComplexVector right) {
        leftNACheck.enable(left);
        return doComplex(frame, RClosures.createLogicalToComplexVector(left).materialize(), right);
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RLogicalVector left, RStringVector right) {
        return doString(frame, left.toStringVector(), right);
    }

    // Left side is RIntVector

    @Specialization
    protected RIntVector access(VirtualFrame frame, RIntVector left, RNull right) {
        return doInt(frame, left, RDataFactory.createEmptyIntVector());
    }

    @Specialization
    protected RIntVector access(VirtualFrame frame, RIntVector left, byte right) {
        return doInt(frame, left, rightNACheck.convertLogicalToInt(right));
    }

    @Specialization
    protected RIntVector access(VirtualFrame frame, RIntVector left, int right) {
        return doInt(frame, left, right);
    }

    @Specialization
    protected RDoubleVector access(VirtualFrame frame, RIntVector left, double right) {
        return doDouble(frame, RClosures.createIntToDoubleVector(left).materialize(), right);
    }

    @Specialization
    protected RComplexVector access(VirtualFrame frame, RIntVector left, RComplex right) {
        return doComplex(frame, RClosures.createIntToComplexVector(left).materialize(), right);
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RIntVector left, String right) {
        return doString(frame, left.toStringVector(), right);
    }

    @Specialization
    protected RIntVector access(VirtualFrame frame, RIntVector left, RLogicalVector right) {
        rightNACheck.enable(right);
        return doInt(frame, left, RClosures.createLogicalToIntVector(right).materialize());
    }

    @Specialization
    protected RIntVector access(VirtualFrame frame, RIntVector left, RIntVector right) {
        return doInt(frame, left, right);
    }

    @Specialization
    protected RDoubleVector access(VirtualFrame frame, RIntVector left, RDoubleVector right) {
        return doDouble(frame, RClosures.createIntToDoubleVector(left).materialize(), right);
    }

    @Specialization
    protected RComplexVector access(VirtualFrame frame, RIntVector left, RComplexVector right) {
        return doComplex(frame, RClosures.createIntToComplexVector(left).materialize(), right);
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RIntVector left, RStringVector right) {
        return doString(frame, left.toStringVector(), right);
    }

    // Left side is RDoubleVector

    @Specialization
    protected RDoubleVector access(VirtualFrame frame, RDoubleVector left, RNull right) {
        return doDouble(frame, left, RDataFactory.createEmptyDoubleVector());
    }

    @Specialization
    protected RDoubleVector access(VirtualFrame frame, RDoubleVector left, byte right) {
        return doDouble(frame, left, rightNACheck.convertLogicalToDouble(right));
    }

    @Specialization
    protected RDoubleVector access(VirtualFrame frame, RDoubleVector left, int right) {
        return doDouble(frame, left, rightNACheck.convertIntToDouble(right));
    }

    @Specialization
    protected RDoubleVector access(VirtualFrame frame, RDoubleVector left, double right) {
        return doDouble(frame, left, right);
    }

    @Specialization
    protected RComplexVector access(VirtualFrame frame, RDoubleVector left, RComplex right) {
        return doComplex(frame, RClosures.createDoubleToComplexVector(left).materialize(), right);
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RDoubleVector left, String right) {
        return doString(frame, left.toStringVector(), right);
    }

    @Specialization
    protected RDoubleVector access(VirtualFrame frame, RDoubleVector left, RLogicalVector right) {
        return doDouble(frame, left, RClosures.createLogicalToDoubleVector(right).materialize());
    }

    @Specialization
    protected RDoubleVector access(VirtualFrame frame, RDoubleVector left, RIntVector right) {
        return doDouble(frame, left, RClosures.createIntToDoubleVector(right).materialize());
    }

    @Specialization
    protected RDoubleVector access(VirtualFrame frame, RDoubleVector left, RDoubleVector right) {
        return doDouble(frame, left, right);
    }

    @Specialization
    protected RComplexVector access(VirtualFrame frame, RDoubleVector left, RComplexVector right) {
        return doComplex(frame, RClosures.createDoubleToComplexVector(left).materialize(), right);
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RDoubleVector left, RStringVector right) {
        return doString(frame, left.toStringVector(), right);
    }

    // Left side is RComplexVector

    @Specialization
    protected RComplexVector access(VirtualFrame frame, RComplexVector left, RNull right) {
        return doComplex(frame, left, RDataFactory.createEmptyComplexVector());
    }

    @Specialization
    protected RComplexVector access(VirtualFrame frame, RComplexVector left, byte right) {
        return doComplex(frame, left, rightNACheck.convertLogicalToComplex(right));
    }

    @Specialization
    protected RComplexVector access(VirtualFrame frame, RComplexVector left, int right) {
        return doComplex(frame, left, rightNACheck.convertIntToComplex(right));
    }

    @Specialization
    protected RComplexVector access(VirtualFrame frame, RComplexVector left, double right) {
        return doComplex(frame, left, rightNACheck.convertDoubleToComplex(right));
    }

    @Specialization
    protected RComplexVector access(VirtualFrame frame, RComplexVector left, RComplex right) {
        return doComplex(frame, left, right);
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RComplexVector left, String right) {
        return doString(frame, left.toStringVector(), right);
    }

    @Specialization
    protected RComplexVector access(VirtualFrame frame, RComplexVector left, RLogicalVector right) {
        return doComplex(frame, left, RClosures.createLogicalToComplexVector(right).materialize());
    }

    @Specialization
    protected RComplexVector access(VirtualFrame frame, RComplexVector left, RIntVector right) {
        return doComplex(frame, left, RClosures.createIntToComplexVector(right).materialize());
    }

    @Specialization
    protected RComplexVector access(VirtualFrame frame, RComplexVector left, RDoubleVector right) {
        return doComplex(frame, left, RClosures.createDoubleToComplexVector(right).materialize());
    }

    @Specialization
    protected RComplexVector access(VirtualFrame frame, RComplexVector left, RComplexVector right) {
        return doComplex(frame, left, right);
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RComplexVector left, RStringVector right) {
        return doString(frame, left.toStringVector(), right);
    }

    // Left side is RStringVector

    @Specialization
    protected RStringVector access(VirtualFrame frame, RStringVector left, RNull right) {
        return doString(frame, left, RDataFactory.createEmptyStringVector());
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RStringVector left, byte right) {
        return doString(frame, left, rightNACheck.convertLogicalToString(right));
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RStringVector left, int right) {
        return doString(frame, left, rightNACheck.convertIntToString(right));
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RStringVector left, double right) {
        return doString(frame, left, rightNACheck.convertDoubleToString(right));
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RStringVector left, RComplex right) {
        return doString(frame, left, rightNACheck.convertComplexToString(right));
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RStringVector left, String right) {
        return doString(frame, left.toStringVector(), right);
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RStringVector left, RLogicalVector right) {
        return doString(frame, left, right.toStringVector());
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RStringVector left, RIntVector right) {
        return doString(frame, left, right.toStringVector());
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RStringVector left, RDoubleVector right) {
        return doString(frame, left, right.toStringVector());
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RStringVector left, RComplexVector right) {
        return doString(frame, left, right.toStringVector());
    }

    @Specialization
    protected RStringVector access(VirtualFrame frame, RStringVector left, RStringVector right) {
        return doString(frame, left, right);
    }

    // left side is RList

    @Specialization
    protected RAbstractVector access(VirtualFrame frame, RList left, RAbstractVector right) {
        return doList(frame, left, right);
    }

    @Specialization
    protected RAbstractVector access(VirtualFrame frame, RList left, RNull right) {
        return doList(frame, left, right);
    }

    @NodeField(name = "NACheck", type = NACheck.class)
    @NodeChild("operand")
    public abstract static class VectorUpdateValueCast extends RNode {

        protected abstract NACheck getNACheck();

        @Specialization
        protected int doInt(int operand) {
            getNACheck().enable(operand);
            return operand;
        }

        @Specialization
        protected double doDouble(double operand) {
            getNACheck().enable(operand);
            return operand;
        }

        @Specialization
        protected RComplex doComplex(RComplex operand) {
            getNACheck().enable(operand);
            return operand;
        }

        @Specialization
        protected byte doBoolean(byte operand) {
            getNACheck().enable(operand);
            return operand;
        }

        @Specialization
        protected String doString(String operand) {
            getNACheck().enable(operand);
            return operand;
        }

        @Specialization
        protected RIntVector doIntVector(RIntSequence operand) {
            // NACheck may keep disabled.
            return (RIntVector) operand.createVector();
        }

        @Specialization
        protected RIntVector doIntVector(RIntVector operand) {
            getNACheck().enable(operand);
            return operand;
        }

        @Specialization
        protected RDoubleVector doDoubleVector(RDoubleVector operand) {
            getNACheck().enable(operand);
            return operand;
        }

        @Specialization
        protected RComplexVector doComplexVector(RComplexVector operand) {
            getNACheck().enable(operand);
            return operand;
        }

        @Specialization
        protected RLogicalVector doLogicalVector(RLogicalVector operand) {
            getNACheck().enable(operand);
            return operand;
        }

        @Specialization
        protected RStringVector doStringVector(RStringVector operand) {
            getNACheck().enable(operand);
            return operand;
        }

        @Specialization
        protected RList doList(RList operand) {
            getNACheck().enable(operand);
            return operand;
        }

        @Specialization
        protected RNull doNull(RNull operand) {
            return operand;
        }
    }

    @NodeField(name = "NACheck", type = NACheck.class)
    @NodeChild("operand")
    public abstract static class VectorUpdateVectorCast extends RNode {

        protected abstract NACheck getNACheck();

        public abstract RNode getOperand();

        private final BranchProfile seenShared = BranchProfile.create();

        @Specialization
        protected RNull doNull(RNull operand) {
            return operand;
        }

        @Specialization
        protected RIntVector doInt(int operand) {
            getNACheck().enable(operand);
            return RDataFactory.createIntVectorFromScalar(operand);
        }

        @Specialization
        protected RDoubleVector doDouble(double operand) {
            getNACheck().enable(operand);
            return RDataFactory.createDoubleVectorFromScalar(operand);
        }

        @Specialization
        protected RComplexVector doComplex(RComplex operand) {
            getNACheck().enable(operand);
            return RDataFactory.createComplexVectorFromScalar(operand);
        }

        @Specialization
        protected RLogicalVector doBoolean(byte operand) {
            getNACheck().enable(operand);
            return RDataFactory.createLogicalVector(operand);
        }

        @Specialization
        protected RStringVector doString(String operand) {
            getNACheck().enable(operand);
            return RDataFactory.createStringVector(operand);
        }

        @Specialization
        protected RDoubleVector doDoubleVector(RDoubleSequence operand) {
            // NACheck may keep disabled.
            return (RDoubleVector) operand.createVector();
        }

        @Specialization
        protected RIntVector doIntVector(RIntSequence operand) {
            // NACheck may keep disabled.
            return (RIntVector) operand.createVector();
        }

        @Specialization
        protected RIntVector doIntVector(RIntVector operand) {
            getNACheck().enable(operand);
            return doVector(operand);
        }

        @Specialization
        protected RDoubleVector doDoubleVector(RDoubleVector operand) {
            getNACheck().enable(operand);
            return doVector(operand);
        }

        @Specialization
        protected RComplexVector doComplexVector(RComplexVector operand) {
            getNACheck().enable(operand);
            return doVector(operand);
        }

        @Specialization
        protected RLogicalVector doLogicalVector(RLogicalVector operand) {
            getNACheck().enable(operand);
            return doVector(operand);
        }

        @Specialization
        protected RStringVector doStringVector(RStringVector operand) {
            getNACheck().enable(operand);
            return doVector(operand);
        }

        @Specialization
        protected RList doList(RList operand) {
            getNACheck().enable(operand);
            return doVector(operand);
        }

        @SuppressWarnings("unchecked")
        private <T extends RVector> T doVector(T operand) {
            if (operand.isShared()) {
                seenShared.enter();
                return (T) operand.copy();
            }
            return operand;
        }
    }
}
