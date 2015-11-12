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
package com.oracle.truffle.r.nodes.function;

import static com.oracle.truffle.r.nodes.function.opt.EagerEvalHelper.getOptimizableConstant;
import static com.oracle.truffle.r.nodes.function.opt.EagerEvalHelper.isOptimizableVariable;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.utilities.BranchProfile;
import com.oracle.truffle.api.utilities.ConditionProfile;
import com.oracle.truffle.r.nodes.RASTUtils;
import com.oracle.truffle.r.nodes.access.ConstantNode;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode.ReadKind;
import com.oracle.truffle.r.nodes.function.PromiseHelperNode.PromiseCheckHelperNode;
import com.oracle.truffle.r.nodes.function.opt.OptConstantPromiseNode;
import com.oracle.truffle.r.nodes.function.opt.OptForcedEagerPromiseNode;
import com.oracle.truffle.r.nodes.function.opt.OptVariablePromiseBaseNode;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RDeparse;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RSerialize.State;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.data.RPromise.Closure;
import com.oracle.truffle.r.runtime.data.RPromise.PromiseType;
import com.oracle.truffle.r.runtime.data.RPromise.RPromiseFactory;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

/**
 * These {@link RNode} implementations are used as a factory-nodes for {@link RPromise}s.<br/>
 * All these classes are created during/after argument matching and get cached afterwards, so they
 * get (and need to get) called every repeated call to a function with the same arguments.
 *
 * TODO Certain subclasses used to override {@code deparse}. Since a {@link PromiseNode} is not a
 * {@link RSyntaxNode} this is no longer possible. So we probably need wrappers.
 */
public abstract class PromiseNode extends RNode {
    /**
     * The {@link RPromiseFactory} which holds all information necessary to construct a proper
     * {@link RPromise} for every case that might occur.
     */
    protected final RPromiseFactory factory;

    /**
     * @param factory {@link #factory}
     */
    protected PromiseNode(RPromiseFactory factory) {
        this.factory = factory;
    }

    /**
     * Returns the {@link RSyntaxNode} that is the expression associated with the promise.
     */
    public abstract RSyntaxNode getPromiseExpr();

    public static RNode createInlined(RNode expression, Object defaultValue, boolean unwrap) {
        CompilerAsserts.neverPartOfCompilation();
        RNode clonedExpression = NodeUtil.cloneNode(expression);
        RNode pn = clonedExpression instanceof ConstantNode ? clonedExpression : new InlinedSuppliedArgumentNode(clonedExpression, defaultValue, unwrap);
        return pn;
    }

    /**
     * @param factory {@link #factory}
     * @return Depending on {@link RPromiseFactory#getType()}, the proper {@link PromiseNode}
     *         implementation
     */
    @TruffleBoundary
    public static RNode create(RPromiseFactory factory, boolean noOpt, boolean forcedEager) {
        assert factory.getType() != PromiseType.NO_ARG;

        // For ARG_DEFAULT, expr == defaultExpr!
        RNode arg = (RNode) factory.getExpr();
        RNode expr = (RNode) RASTUtils.unwrap(arg);
        int wrapIndex = ArgumentStatePush.INVALID_INDEX;
        if (arg instanceof WrapArgumentNode && ((WrapArgumentNode) arg).modeChange()) {
            wrapIndex = ((WrapArgumentNode) arg).getIndex();
        }
        if (forcedEager) {
            return new OptForcedEagerPromiseNode(factory, wrapIndex);
        } else {
            Object optimizableConstant = getOptimizableConstant(expr);
            if (optimizableConstant != null) {
                // As Constants don't care where they are evaluated, we don't need to
                // distinguish between ARG_DEFAULT and ARG_SUPPLIED
                return new OptConstantPromiseNode(factory.getType(), expr, optimizableConstant);
            } else if (factory.getType() == PromiseType.ARG_SUPPLIED) {
                if (isVararg(expr)) {
                    return expr;
                } else if (!noOpt && isOptimizableVariable(expr)) {
                    return new OptVariableSuppliedPromiseNode(factory, (ReadVariableNode) expr, wrapIndex);
                }
            }
            return new PromisedNode(factory);
        }
    }

    /**
     * This method checks whether to apply optimizations to RNodes created for single "..."
     * elements.
     *
     * @param expr
     * @return Whether the given {@link RNode} is a {@link VarArgNode}
     */
    private static boolean isVararg(RNode expr) {
        return expr instanceof VarArgNode;
    }

    /**
     * @return Creates a {@link VarArgNode} for the given
     */
    public static VarArgNode createVarArg(int varArgIndex) {
        return new VarArgNode(varArgIndex);
    }

    /**
     * A {@link PromiseNode} for supplied arguments.
     */
    private static final class PromisedNode extends PromiseNode {
        private PromisedNode(RPromiseFactory factory) {
            super(factory);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            MaterializedFrame execFrame = factory.getType() == PromiseType.ARG_SUPPLIED ? frame.materialize() : null;
            return factory.createPromise(execFrame);
        }

        @Override
        public RSyntaxNode getRSyntaxNode() {
            return getPromiseExpr();
        }

        @Override
        public RSyntaxNode getPromiseExpr() {
            return ((RNode) factory.getExpr()).asRSyntaxNode();
        }
    }

    /**
     * TODO Expand!
     */
    private static final class OptVariableSuppliedPromiseNode extends OptVariablePromiseBaseNode {

        public OptVariableSuppliedPromiseNode(RPromiseFactory factory, ReadVariableNode rvn, int wrapIndex) {
            super(factory, rvn, wrapIndex);
        }

        @Override
        protected RNode createFallback() {
            return new PromisedNode(factory);
        }

// @TruffleBoundary
        public void onSuccess(RPromise promise) {
// System.err.println("Opt SUCCESS: " + promise.getOptType());
        }

// @TruffleBoundary
        public void onFailure(RPromise promise) {
// System.err.println("Opt FAILURE: " + promise.getOptType());
            rewriteToFallback();
        }
    }

    /**
     * This class is meant for supplied arguments (which have to be evaluated in the caller frame)
     * which are supposed to be evaluated inline: This means we can simply evaluate it here, and not
     * create a promise.
     */
    private static final class InlinedSuppliedArgumentNode extends RNode {
        @Child private RNode expression;
        @Child private RNode defaultExpressionCache;
        private final Object defaultValue;
        private final boolean unwrap;

        @Child private PromiseHelperNode promiseHelper;

        private final BranchProfile isVarArgProfile = BranchProfile.create();
        private final ConditionProfile isPromiseProfile = ConditionProfile.createBinaryProfile();

        public InlinedSuppliedArgumentNode(RNode expression, Object defaultValue, boolean unwrap) {
            // TODO assert RSyntaxNode?
            this.expression = expression;
            this.defaultValue = defaultValue;
            this.unwrap = unwrap;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            // builtin.inline: We do re-evaluation every execute inside the caller frame, as we
            // know that the evaluation of default values has no side effects (has to be assured by
            // builtin implementations)
            Object obj;
            if (unwrap && expression instanceof WrapArgumentNode) {
                obj = ((WrapArgumentNode) expression).getOperand().execute(frame);
            } else {
                obj = expression.execute(frame);
            }
            if (obj == RMissing.instance) {
                if (defaultExpressionCache == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    defaultExpressionCache = insert(ConstantNode.create(defaultValue));
                }
                return defaultExpressionCache.execute(frame);
            } else if (obj instanceof RArgsValuesAndNames) {
                isVarArgProfile.enter();
                if (promiseHelper == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    promiseHelper = insert(new PromiseHelperNode());
                }
                return checkEvaluateArgs(frame, (RArgsValuesAndNames) obj);
            } else {
                if (isPromiseProfile.profile(obj instanceof RPromise)) {
                    if (promiseHelper == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        promiseHelper = insert(new PromiseHelperNode());
                    }
                    return promiseHelper.evaluate(frame, (RPromise) obj);
                } else {
                    return obj;
                }
            }
        }

        public RArgsValuesAndNames checkEvaluateArgs(VirtualFrame frame, RArgsValuesAndNames args) {
            Object[] values = args.getArguments();
            Object[] newValues = new Object[values.length];
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (isPromiseProfile.profile(value instanceof RPromise)) {
                    value = promiseHelper.evaluate(frame, (RPromise) value);
                }
                newValues[i] = value;
            }
            return new RArgsValuesAndNames(newValues, args.getSignature());
        }

        @Override
        public RSyntaxNode getRSyntaxNode() {
            return expression.asRSyntaxNode();
        }

    }

    /**
     * This {@link RNode} is used to evaluate the expression given in a {@link RPromise} formerly
     * wrapped into a "..." after unrolling.<br/>
     * In a certain sense this is the class corresponding class for GNU R's PROMSXP (AST equivalent
     * of RPromise, only needed for varargs in FastR TODO Move to separate package together with
     * other varargs classes) N.B. This implements {@link RSyntaxNode} because it can be stored as
     * an argument in a {@link RCallNode} where the arguments are statically typed as
     * {@link RSyntaxNode}.
     */
    public static final class VarArgNode extends RNode implements RSyntaxNode {

        @Child private ReadVariableNode lookupVarArgs;

        private final int index;

        private VarArgNode(int index) {
            this.index = index;
        }

        public RArgsValuesAndNames getVarargsAndNames(VirtualFrame frame) {
            if (lookupVarArgs == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupVarArgs = insert(ReadVariableNode.create(ArgumentsSignature.VARARG_NAME, RType.Any, ReadKind.Silent));
            }
            RArgsValuesAndNames varArgsAndNames;
            try {
                varArgsAndNames = lookupVarArgs.executeRArgsValuesAndNames(frame);
            } catch (UnexpectedResultException e) {
                throw RInternalError.shouldNotReachHere(e, "'...' should always be represented by RArgsValuesAndNames");
            }
            return varArgsAndNames;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return getVarargsAndNames(frame).getArgument(index);
        }

        @Override
        public RSyntaxNode substituteImpl(REnvironment env) {
            Object obj = ((RArgsValuesAndNames) env.get(ArgumentsSignature.VARARG_NAME)).getArgument(index);
            return obj instanceof RPromise ? (RSyntaxNode) ((RPromise) obj).getRep() : ConstantNode.create(obj);
        }

        public int getIndex() {
            return index;
        }

        @Override
        public void deparseImpl(RDeparse.State state) {
            int num = index + 1;
            state.startNodeDeparse(this);
            state.append((num < 10 ? ".." : ".") + num);
            state.endNodeDeparse(this);
        }

        public void serializeImpl(State state) {
            throw RInternalError.unimplemented();
        }

        public int getRlengthImpl() {
            throw RInternalError.unimplemented();
        }

        @Override
        public Object getRelementImpl(@SuppressWarnings("hiding") int index) {
            throw RInternalError.unimplemented();
        }

        @Override
        public boolean getRequalsImpl(RSyntaxNode other) {
            throw RInternalError.unimplemented();
        }
    }

    @TruffleBoundary
    public static RNode createVarArgsInlined(RNode[] nodes, ArgumentsSignature signature) {
        return new InlineVarArgsNode(nodes, signature);
    }

    @TruffleBoundary
    public static RNode createVarArgs(RNode[] nodes, ArgumentsSignature signature, ClosureCache closureCache, boolean forcedEager) {
        return new VarArgsPromiseNode(nodes, signature, closureCache, forcedEager);
    }

    /**
     * This class is used for wrapping arguments into "..." ({@link RArgsValuesAndNames}).
     */
    public static final class VarArgsPromiseNode extends RNode {
        @Children private final RNode[] promised;
        private final Closure[] closures;
        private final ArgumentsSignature signature;

        public VarArgsPromiseNode(RNode[] nodes, ArgumentsSignature signature, ClosureCache closureCache, boolean forcedEager) {
            this.promised = new RNode[nodes.length];
            this.closures = new Closure[nodes.length];
            for (int i = 0; i < nodes.length; i++) {
                Closure closure = closureCache.getOrCreateClosure(nodes[i]);
                this.closures[i] = closure;
                this.promised[i] = PromisedNode.create(RPromiseFactory.create(PromiseType.ARG_SUPPLIED, closure), false, forcedEager);
            }
            this.signature = signature;
        }

        @Override
        @ExplodeLoop
        public Object execute(VirtualFrame frame) {
            Object[] promises = new Object[promised.length];
            for (int i = 0; i < promised.length; i++) {
                promises[i] = promised[i].execute(frame);
            }
            return new RArgsValuesAndNames(promises, signature);
        }

        public Closure[] getClosures() {
            return closures;
        }

        public ArgumentsSignature getSignature() {
            return signature;
        }
    }

    /**
     * The inlined counterpart of {@link VarArgsPromiseNode}: This gets a bit more complicated, as
     * "..." might include values from an outer "...", which might resolve to an empty argument
     * list.
     */
    public static final class InlineVarArgsNode extends RNode {
        @Children private final RNode[] varargs;
        protected final ArgumentsSignature signature;

        @Child private PromiseCheckHelperNode promiseCheckHelper = new PromiseCheckHelperNode();
        private final ConditionProfile argsValueAndNamesProfile = ConditionProfile.createBinaryProfile();

        public InlineVarArgsNode(RNode[] nodes, ArgumentsSignature signature) {
            this.varargs = nodes;
            this.signature = signature;
            assert varargs.length == signature.getLength();
        }

        public RNode[] getVarargs() {
            return varargs;
        }

        public ArgumentsSignature getSignature() {
            return signature;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (varargs.length == 0) {
                // No need to create an extra object, already have one
                return RArgsValuesAndNames.EMPTY;
            }
            Object[] evaluatedArgs = new Object[varargs.length];
            Object[] flattenedArgs = evaluateArguments(frame, evaluatedArgs);

            if (flattenedArgs == evaluatedArgs) {
                // no vararg parameters
                return new RArgsValuesAndNames(evaluatedArgs, signature);
            } else {
                // vararg parameters
                int pos = 0;
                for (int i = 0; i < varargs.length; i++) {
                    Object argValue = evaluatedArgs[i];
                    if (argsValueAndNamesProfile.profile(argValue instanceof RArgsValuesAndNames)) {
                        RArgsValuesAndNames argsValuesAndNames = (RArgsValuesAndNames) argValue;
                        Object[] varargValues = argsValuesAndNames.getArguments();
                        copyVarargValues(frame, flattenedArgs, pos, varargValues);
                        pos += varargValues.length;
                    } else {
                        flattenedArgs[pos++] = evaluatedArgs[i];
                    }
                }
                assert pos == flattenedArgs.length;

                // if there was only one vararg argument, we can reuse the signature
                ArgumentsSignature finalSignature = evaluatedArgs.length == 1 ? ((RArgsValuesAndNames) evaluatedArgs[0]).getSignature() : createSignature(evaluatedArgs, flattenedArgs.length);
                return new RArgsValuesAndNames(flattenedArgs, finalSignature);
            }
        }

        private void copyVarargValues(VirtualFrame frame, Object[] flattenedArgs, int pos, Object[] varargValues) {
            for (int j = 0; j < varargValues.length; j++) {
                flattenedArgs[pos + j] = promiseCheckHelper.checkEvaluate(frame, varargValues[j]);
            }
        }

        @ExplodeLoop
        private ArgumentsSignature createSignature(Object[] evaluatedArgs, int size) {
            String[] names = new String[size];
            int pos = 0;
            for (int i = 0; i < varargs.length; i++) {
                Object argValue = evaluatedArgs[i];
                if (argsValueAndNamesProfile.profile(argValue instanceof RArgsValuesAndNames)) {
                    RArgsValuesAndNames argsValuesAndNames = (RArgsValuesAndNames) argValue;
                    Object[] varargValues = argsValuesAndNames.getArguments();
                    copyVarargNames(names, pos, argsValuesAndNames, varargValues);
                    pos += varargValues.length;
                } else {
                    names[pos++] = signature.getName(i);
                }
            }
            assert pos == size;
            return ArgumentsSignature.get(names);
        }

        private static void copyVarargNames(String[] names, int pos, RArgsValuesAndNames argsValuesAndNames, Object[] varargValues) {
            for (int j = 0; j < varargValues.length; j++) {
                names[pos + j] = argsValuesAndNames.getSignature().getName(j);
            }
        }

        @ExplodeLoop
        private Object[] evaluateArguments(VirtualFrame frame, Object[] evaluatedArgs) {
            int size = 0;
            boolean containsVarargs = false;
            for (int i = 0; i < varargs.length; i++) {
                Object argValue = varargs[i].execute(frame);
                if (argsValueAndNamesProfile.profile(argValue instanceof RArgsValuesAndNames)) {
                    containsVarargs = true;
                    size += ((RArgsValuesAndNames) argValue).getLength();
                    evaluatedArgs[i] = argValue;
                } else {
                    size++;
                    evaluatedArgs[i] = promiseCheckHelper.checkEvaluate(frame, argValue);
                }
            }
            if (containsVarargs) {
                return new Object[size];
            } else {
                return evaluatedArgs;
            }
        }
    }
}
