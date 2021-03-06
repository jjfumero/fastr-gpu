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
package com.oracle.truffle.r.nodes.unary;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.primitive.UnaryMapNode;
import com.oracle.truffle.r.nodes.profile.TruffleBoundaryNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.model.RAbstractComplexVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractDoubleVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractIntVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractLogicalVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.ops.UnaryArithmetic;
import com.oracle.truffle.r.runtime.ops.UnaryArithmeticFactory;

public abstract class UnaryArithmeticNode extends UnaryNode {

    protected final UnaryArithmeticFactory unary;
    private final Message error;

    public UnaryArithmeticNode(UnaryArithmeticFactory factory, Message error) {
        this.unary = factory;
        this.error = error;
    }

    @Specialization(guards = {"cachedNode != null", "cachedNode.isSupported(operand)"})
    protected Object doCached(Object operand, @Cached("createCachedFast(operand)") UnaryMapNode cachedNode) {
        return cachedNode.apply(operand);
    }

    protected UnaryMapNode createCachedFast(Object operand) {
        if (isNumericVector(operand)) {
            return createCached(unary.create(), operand);
        }
        return null;
    }

    protected static UnaryMapNode createCached(UnaryArithmetic arithmetic, Object operand) {
        if (operand instanceof RAbstractVector) {
            RAbstractVector castOperand = (RAbstractVector) operand;
            RType operandType = castOperand.getRType();
            if (operandType.isNumeric()) {
                RType type = RType.maxPrecedence(operandType, RType.Integer);
                return UnaryMapNode.create(new ScalarUnaryArithmeticNode(arithmetic), castOperand, type, type);
            }
        }
        return null;
    }

    protected static boolean isNumericVector(Object value) {
        return value instanceof RAbstractIntVector || value instanceof RAbstractDoubleVector || value instanceof RAbstractComplexVector || value instanceof RAbstractLogicalVector;
    }

    @Specialization(contains = "doCached", guards = {"isNumericVector(operand)"})
    @TruffleBoundary
    protected Object doGeneric(Object operand, //
                    @Cached("unary.create()") UnaryArithmetic arithmetic, //
                    @Cached("new(createCached(arithmetic, operand))") GenericNumericVectorNode generic) {
        RAbstractVector operandVector = (RAbstractVector) operand;
        return generic.get(arithmetic, operandVector).apply(operandVector);
    }

    @Fallback
    protected Object invalidArgType(@SuppressWarnings("unused") Object operand) {
        throw RError.error(this, error);
    }

    protected static final class GenericNumericVectorNode extends TruffleBoundaryNode {

        @Child private UnaryMapNode cached;

        public UnaryMapNode get(UnaryArithmetic arithmetic, RAbstractVector operand) {
            UnaryMapNode next = cached;
            if (!next.isSupported(operand)) {
                next = cached.replace(createCached(arithmetic, operand));
            }
            return next;
        }

        public GenericNumericVectorNode(UnaryMapNode cachedOperation) {
            this.cached = cachedOperation;
        }

    }

}
