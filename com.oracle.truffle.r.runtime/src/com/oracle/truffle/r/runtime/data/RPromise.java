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
package com.oracle.truffle.r.runtime.data;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.r.runtime.*;

/**
 * Denotes an R {@code promise}. Its child classes - namely {@link EagerPromise} and
 * {@link VarargPromise} - are only present for documentation reasons: Because Truffle cannot do
 * proper function dispatch based on inheritance, an additional {@link #optType} is introduced,
 * which is used for manual method dispatch.
 */
@ValueType
public class RPromise extends RLanguageRep implements RTypedValue {

    /**
     * Different to GNU R, FastR has no additional binding information (a "origin" where the binding
     * is coming from). This enum is meant to substitute this information.
     */
    public enum PromiseType {
        /**
         * This promise is created for an argument that has been supplied to the function call and
         * thus has to be evaluated inside the caller frame.
         */
        ARG_SUPPLIED,

        /**
         * This promise is created for an argument that was 'missing' at the function call and thus
         * contains it's default value and has to be evaluated inside the _callee_ frame.
         */
        ARG_DEFAULT,

        /**
         * This promise is not a function argument at all. (Created by 'delayedAssign', for
         * example).
         */
        NO_ARG;
    }

    /**
     * As Truffle cannot inline virtual methods properly, this type was introduced to tell all
     * {@link RPromise} classes apart and to implement "virtual" method dispatch.
     *
     * @see RPromise
     */
    public enum OptType {
        DEFAULT,
        EAGER,
        VARARG,
        PROMISED
    }

    public static final String CLOSURE_WRAPPER_NAME = new String("<promise>");

    /**
     * @see PromiseType
     */
    private final PromiseType type;

    private final OptType optType;

    /**
     * @see #getFrame()
     * @see EagerPromise#materialize()
     */
    protected MaterializedFrame execFrame;

    /**
     * Might not be <code>null</code>.
     */
    private final Closure closure;

    /**
     * When {@code null} the promise has not been evaluated.
     */
    private Object value = null;

    /**
     * A flag which is necessary to avoid cyclic evaluation. Manipulated by
     * {@link #setUnderEvaluation(boolean)} and can by checked via {@link #isUnderEvaluation()}.
     */
    private boolean underEvaluation = false;

    /**
     * This creates a new tuple (expr, env, closure, value=null), which may later be evaluated.
     */
    RPromise(PromiseType type, OptType optType, MaterializedFrame execFrame, Closure closure) {
        super(closure.getExpr());
        assert type != PromiseType.ARG_DEFAULT || execFrame != null;
        this.type = type;
        this.optType = optType;
        this.execFrame = execFrame;
        this.closure = closure;
    }

    /**
     * This creates a new tuple (expr, null, null, value), which is already evaluated.
     */
    RPromise(PromiseType type, OptType optType, Object expr, Object value) {
        super(expr);
        assert value != null;
        this.type = type;
        this.optType = optType;
        this.value = value;
        // Not needed as already evaluated:
        this.execFrame = null;
        this.closure = null;
    }

    /**
     * This creates a new tuple (isEvaluated=false, expr, null, null, value=null). Meant to be
     * called via {@link VarargPromise#VarargPromise(PromiseType, RPromise, Closure)} only!
     */
    private RPromise(PromiseType type, OptType optType, Object expr) {
        super(expr);
        this.type = type;
        this.optType = optType;
        // Not needed as already evaluated:
        this.execFrame = null;
        this.closure = null;
    }

    public RType getRType() {
        return RType.Promise;
    }

    /**
     * @return Whether this promise is of {@link #type} {@link PromiseType#ARG_DEFAULT}.
     */
    public final boolean isDefault() {
        return type == PromiseType.ARG_DEFAULT;
    }

    public final boolean isNonArgument() {
        return type == PromiseType.NO_ARG;
    }

    public final boolean isNullFrame() {
        return execFrame == null;
    }

    /**
     * Used in case the {@link RPromise} is evaluated outside.
     *
     * @param newValue
     */
    public final void setValue(Object newValue) {
        assert newValue != null;
        this.value = newValue;
    }

    /**
     * @param frame
     * @return Whether the given {@link RPromise} is in its origin context and thus can be resolved
     *         directly inside the AST.
     */
    public final boolean isInOriginFrame(VirtualFrame frame) {
        if (isDefault() && isNullFrame()) {
            return true;
        }

        if (frame == null) {
            return false;
        }
        return frame == execFrame;
    }

    /**
     * @return The representation of expression (a RNode). May contain <code>null</code> if no expr
     *         is provided!
     */
    @Override
    public final Object getRep() {
        return super.getRep();
    }

    /**
     * @return {@link #closure}
     */
    public final Closure getClosure() {
        return closure;
    }

    /**
     * @return {@link #execFrame}. This might be <code>null</code>! Materialize before.
     *
     * @see #execFrame
     */
    public final MaterializedFrame getFrame() {
        return execFrame;
    }

    /**
     * @return The raw {@link #value}.
     */
    public final Object getValue() {
        assert value != null;
        return value;
    }

    /**
     * Returns {@code true} if this promise has been evaluated?
     */
    public final boolean isEvaluated() {
        return value != null;
    }

    /**
     * @return This instance's {@link OptType}
     */
    public final OptType getOptType() {
        return optType;
    }

    /**
     * Used to manipulate {@link #underEvaluation}.
     *
     * @param underEvaluation The new value to set
     */
    public final void setUnderEvaluation(boolean underEvaluation) {
        this.underEvaluation = underEvaluation;
    }

    /**
     * @return The state of the {@link #underEvaluation} flag.
     */
    public final boolean isUnderEvaluation() {
        return underEvaluation;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "[" + type + ", " + optType + ", " + execFrame + ", expr=" + getRep() + ", " + value + "]";
    }

    /**
     * This is a {@link RPromise} implementation that performs two optimizations:
     * <ul>
     * <li>1. It does not carry a {@link MaterializedFrame} ({@link RPromise#execFrame}) but knows
     * how to retrieve the correct one if needed</li>
     * <li>2. It carries a pre-evaluated value of the symbol-expression it is supposed to evaluate
     * on first read</li>
     * </ul>
     * The 1. optimization is only possible if the {@link EagerPromise} does not leave the stack it
     * was created in, e.g. by the means of "sys.frame", "function" or similar. If it needs to be
     * present for any reason, {@link #materialize()} is called.<br/>
     * The 2. optimization is only possible as long it can be guaranteed that the symbol it was
     * originally read from has not been altered in the mean time. If this cannot be guaranteed for
     * any reason, a Promise gets {@link #deoptimize()} (which includes {@link #materialize()}ion).
     */
    public static final class EagerPromise extends RPromise {
        protected final Object eagerValue;

        private final Assumption notChangedNonLocally;
        private final int frameId;
        private final EagerFeedback feedback;
        private final boolean wrap;

        /**
         * Set to <code>true</code> by {@link #deoptimize()}. If this is true, the
         * {@link RPromise#execFrame} is guaranteed to be set.
         */
        private boolean deoptimized = false;

        EagerPromise(PromiseType type, OptType optType, Closure closure, Object eagerValue, Assumption notChangedNonLocally, int nFrameId, EagerFeedback feedback, boolean wrap) {
            super(type, optType, (MaterializedFrame) null, closure);
            assert type != PromiseType.NO_ARG;
            this.eagerValue = eagerValue;
            this.notChangedNonLocally = notChangedNonLocally;
            this.frameId = nFrameId;
            this.feedback = feedback;
            this.wrap = wrap;
        }

        /**
         * @return Whether the promise has been deoptimized before
         */
        public boolean deoptimize() {
            if (!deoptimized) {
                deoptimized = true;
                feedback.onFailure(this);
                materialize();
                return false;
            }
            return true;
        }

        @TruffleBoundary
        public void materialize() {
            if (execFrame == null) {
                this.execFrame = Utils.getStackFrame(FrameAccess.MATERIALIZE, frameId).materialize();
            }
        }

        public Object getEagerValue() {
            return eagerValue;
        }

        public boolean isDeoptimized() {
            return deoptimized;
        }

        public Assumption getIsValidAssumption() {
            return notChangedNonLocally;
        }

        public boolean isValid() {
            return notChangedNonLocally.isValid();
        }

        public void notifySuccess() {
            feedback.onSuccess(this);
        }

        public void notifyFailure() {
            feedback.onFailure(this);
        }

        public boolean shouldWrap() {
            return wrap;
        }
    }

    /**
     * A {@link RPromise} implementation that knows that it holds a Promise itself (that it has to
     * respect by evaluating it on it's evaluation).
     */
    public static final class VarargPromise extends RPromise {
        private final RPromise vararg;

        VarargPromise(PromiseType type, RPromise vararg, Closure exprClosure) {
            super(type, OptType.VARARG, exprClosure.getExpr());
            this.vararg = vararg;
        }

        public RPromise getVararg() {
            return vararg;
        }
    }

    /**
     * Used to allow feedback on {@link EagerPromise} evaluation.
     */
    public interface EagerFeedback {
        /**
         * Called whenever an optimized {@link EagerPromise} has been evaluated successfully.
         *
         * @param promise
         */
        void onSuccess(RPromise promise);

        /**
         * Whenever an optimized {@link EagerPromise} has been deoptimized, or it's assumption did
         * not hold until evaluation.
         *
         * @param promise
         */
        void onFailure(RPromise promise);
    }

    /**
     * A factory which produces instances of {@link RPromise}.
     */
    public static final class RPromiseFactory {
        private final Closure exprClosure;
        private final PromiseType type;

        /**
         * Create the promise with a representation that allows evaluation later in the "current"
         * frame. The frame may need to be set if the promise is passed as an argument to another
         * function.
         */
        public static RPromiseFactory create(PromiseType type, Closure suppliedClosure) {
            return new RPromiseFactory(type, suppliedClosure);
        }

        private RPromiseFactory(PromiseType type, Closure suppliedClosure) {
            this.type = type;
            this.exprClosure = suppliedClosure;
        }

        /**
         * @return A {@link RPromise} from the given parameters
         */
        public RPromise createPromise(MaterializedFrame frame) {
            return RDataFactory.createPromise(type, frame, exprClosure);
        }

        /**
         * @param eagerValue The eagerly evaluated value
         * @param notChangedNonLocally The {@link Assumption} that eagerValue is still valid
         * @param feedback The {@link EagerFeedback} to notify whether the {@link Assumption} hold
         *            until evaluation
         * @return An {@link EagerPromise}
         */
        public RPromise createEagerSuppliedPromise(Object eagerValue, Assumption notChangedNonLocally, int nFrameId, EagerFeedback feedback, boolean wrap) {
            return RDataFactory.createEagerPromise(type, OptType.EAGER, exprClosure, eagerValue, notChangedNonLocally, nFrameId, feedback, wrap);
        }

        public RPromise createPromisedPromise(RPromise promisedPromise, Assumption notChangedNonLocally, int nFrameId, EagerFeedback feedback) {
            return RDataFactory.createEagerPromise(type, OptType.PROMISED, exprClosure, promisedPromise, notChangedNonLocally, nFrameId, feedback, false);
        }

        public RPromise createVarargPromise(RPromise promisedVararg) {
            return RDataFactory.createVarargPromise(type, promisedVararg, exprClosure);
        }

        public Object getExpr() {
            if (exprClosure == null) {
                return null;
            }
            return exprClosure.getExpr();
        }

        public PromiseType getType() {
            return type;
        }
    }

    public static final class Closure {
        private RootCallTarget callTarget;
        private final Object expr;

        private Closure(Object expr) {
            this.expr = expr;
        }

        public static Closure create(Object expr) {
            return new Closure(expr);
        }

        public RootCallTarget getCallTarget() {
            if (callTarget == null) {
                // Create lazily, as it is not needed at all for INLINED promises!
                callTarget = generateCallTarget(expr);
            }
            return callTarget;
        }

        @TruffleBoundary
        private static RootCallTarget generateCallTarget(Object expr) {
            return RContext.getEngine().makePromiseCallTarget(expr, CLOSURE_WRAPPER_NAME);
        }

        public Object getExpr() {
            return expr;
        }
    }
}
