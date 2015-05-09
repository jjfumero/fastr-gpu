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

import static com.oracle.truffle.r.nodes.function.opt.EagerEvalHelper.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.access.variables.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.function.*;
import com.oracle.truffle.r.nodes.function.opt.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.RPromise.Closure;
import com.oracle.truffle.r.runtime.data.RPromise.PromiseType;
import com.oracle.truffle.r.runtime.data.RPromise.RPromiseFactory;

/**
 * This {@link RNode} returns a function's argument specified by its formal index. It is used to
 * populate a function's new frame right after the actual function call and before the function's
 * actual body is executed.
 */
public final class AccessArgumentNode extends RNode {

    @Child private ReadArgumentNode readArgNode;

    @Child private PromiseHelperNode promiseHelper;

    /**
     * The formal index of this argument.
     */
    private final int index;

    public ReadArgumentNode getReadArgNode() {
        return readArgNode;
    }

    /**
     * Used to cache {@link RPromise} evaluations.
     */
    @Child private RNode optDefaultArgNode;
    @CompilationFinal private FormalArguments formals;
    @CompilationFinal private boolean hasDefaultArg;
    @CompilationFinal private RPromiseFactory factory;
    @CompilationFinal private boolean deoptimized;
    @CompilationFinal private boolean defaultArgCanBeOptimized = EagerEvalHelper.optConsts() || EagerEvalHelper.optDefault() || EagerEvalHelper.optExprs();

    private final ConditionProfile isMissingProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile isEmptyProfile = ConditionProfile.createBinaryProfile();

    protected AccessArgumentNode(int index) {
        this.index = index;
        this.readArgNode = new ReadArgumentNode(index);
    }

    public void setFormals(FormalArguments formals) {
        CompilerAsserts.neverPartOfCompilation();
        assert this.formals == null;
        this.formals = formals;
        hasDefaultArg = formals.hasDefaultArgument(index);
    }

    public static AccessArgumentNode create(int index) {
        return new AccessArgumentNode(index);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return doArgument(frame, readArgNode.execute(frame));
    }

    @Override
    public NodeCost getCost() {
        return hasDefaultArg ? NodeCost.MONOMORPHIC : NodeCost.NONE;
    }

    private Object doArgumentInternal(VirtualFrame frame) {
        assert !(getRootNode() instanceof RBuiltinRootNode) : getRootNode();
        // Insert default value
        checkPromiseFactory();
        Object result;
        if (canBeOptimized()) {
            if (checkInsertOptDefaultArg()) {
                result = optDefaultArgNode.execute(frame);
                // Update RArguments for S3 dispatch to work
                RArguments.setArgument(frame, index, result);
                return result;
            } else {
                /*
                 * Default arg cannot be optimized: Rewrite to default and assure that we don't take
                 * this path again
                 */
                CompilerDirectives.transferToInterpreterAndInvalidate();
                defaultArgCanBeOptimized = false;
            }
        }
        // Insert default value
        result = factory.createPromise(frame.materialize());
        // Update RArguments for S3 dispatch to work
        RArguments.setArgument(frame, index, result);
        return result;
    }

    protected Object doArgument(VirtualFrame frame, Object arg) {
        if (hasDefaultArg) {
            if (isMissingProfile.profile(arg == RMissing.instance)) {
                return doArgumentInternal(frame);
            }
            if (isEmptyProfile.profile(arg == REmpty.instance)) {
                return doArgumentInternal(frame);
            }
        }
        return arg;
    }

    private boolean canBeOptimized() {
        return !deoptimized && defaultArgCanBeOptimized;
    }

    private void checkPromiseFactory() {
        if (factory == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Closure defaultClosure = formals.getOrCreateClosure(formals.getDefaultArgument(index));
            factory = RPromiseFactory.create(PromiseType.ARG_DEFAULT, defaultClosure);
        }
    }

    private boolean checkInsertOptDefaultArg() {
        if (optDefaultArgNode == null) {
            RNode defaultArg = formals.getDefaultArgument(index);
            RNode arg = EagerEvalHelper.unfold(defaultArg);

            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (isOptimizableDefault(arg)) {
                optDefaultArgNode = new OptVariableDefaultPromiseNode(factory, (ReadVariableNode) NodeUtil.cloneNode(arg));
            } else if (isOptimizableConstant(arg)) {
                optDefaultArgNode = new OptConstantPromiseNode(factory.getType(), (ConstantNode) arg);
            }
            // else if (isOptimizableExpression(arg)) {
            // System.err.println(" >>> DEF " + arg.getSourceSection().getCode());
            // }
            if (optDefaultArgNode == null) {
                // No success: Rewrite to default
                return false;
            }
            insert(optDefaultArgNode);
        }
        return true;
    }

    protected final class OptVariableDefaultPromiseNode extends OptVariablePromiseBaseNode {

        public OptVariableDefaultPromiseNode(RPromiseFactory factory, ReadVariableNode rvn) {
            super(factory, rvn);
        }

        public void onSuccess(RPromise promise) {
        }

        public void onFailure(RPromise promise) {
            // Assure that no further eager promises are created
            if (!deoptimized) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                deoptimized = true;
            }
        }

        @Override
        protected Object rewriteToAndExecuteFallback(VirtualFrame frame) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            deoptimized = true;
            return doArgument(frame, RMissing.instance);
        }

        @Override
        protected Object executeFallback(VirtualFrame frame) {
            return doArgument(frame, RMissing.instance);
        }

        @Override
        protected RNode createFallback() {
            throw RInternalError.shouldNotReachHere();
        }
    }
}
