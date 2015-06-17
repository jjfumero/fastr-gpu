/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.runtime.RBuiltinKind.*;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.access.variables.*;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode.ReadKind;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.function.*;
import com.oracle.truffle.r.nodes.function.PromiseHelperNode.PromiseCheckHelperNode;
import com.oracle.truffle.r.nodes.function.S3FunctionLookupNode.Result;
import com.oracle.truffle.r.nodes.function.signature.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.RArguments.S3Args;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

public abstract class S3DispatchFunctions extends RBuiltinNode {

    @Child private S3FunctionLookupNode methodLookup;
    @Child private CallMatcherNode callMatcher;

    private final ConditionProfile callerFrameSlowPath = ConditionProfile.createBinaryProfile();
    private final ConditionProfile topLevelFrameProfile = ConditionProfile.createBinaryProfile();

    protected S3DispatchFunctions(boolean nextMethod) {
        methodLookup = S3FunctionLookupNode.create(true, nextMethod);
        callMatcher = CallMatcherNode.create(nextMethod, false);
    }

    protected MaterializedFrame getCallerFrame(VirtualFrame frame) {
        MaterializedFrame funFrame = RArguments.getCallerFrame(frame);
        if (callerFrameSlowPath.profile(funFrame == null)) {
            funFrame = Utils.getCallerFrame(frame, FrameAccess.MATERIALIZE).materialize();
            RError.performanceWarning("slow caller frame access in UseMethod dispatch");
        }
        // S3 method can be dispatched from top-level where there is no caller frame
        return topLevelFrameProfile.profile(funFrame == null) ? frame.materialize() : funFrame;
    }

    protected Object dispatch(VirtualFrame frame, String generic, RStringVector type, String group, MaterializedFrame callerFrame, MaterializedFrame genericDefFrame,
                    ArgumentsSignature suppliedSignature, Object[] suppliedArguments) {
        Result lookupResult = methodLookup.execute(frame, generic, type, group, callerFrame, genericDefFrame);

        S3Args s3Args = new S3Args(lookupResult.generic, lookupResult.clazz, lookupResult.targetFunctionName, callerFrame, genericDefFrame, group);
        Object result = callMatcher.execute(frame, suppliedSignature, suppliedArguments, lookupResult.function, s3Args);
        return result;
    }

    @RBuiltin(name = "UseMethod", kind = PRIMITIVE, parameterNames = {"generic", "object"})
    public abstract static class UseMethod extends S3DispatchFunctions {

        /*
         * TODO: If more than two parameters are passed to UseMethod the extra parameters are
         * ignored and a warning is generated.
         */

        @Child private ClassHierarchyNode classHierarchyNode = ClassHierarchyNodeGen.create(true);
        @Child private PromiseCheckHelperNode promiseCheckHelper;

        private final BranchProfile errorProfile = BranchProfile.create();
        private final ConditionProfile argMissingProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile argsValueAndNamesProfile = ConditionProfile.createBinaryProfile();

        protected UseMethod() {
            super(false);
        }

        @Specialization
        protected Object execute(VirtualFrame frame, String generic, Object arg) {
            controlVisibility();

            Object dispatchedObject;
            if (argMissingProfile.profile(arg == RMissing.instance)) {
                // For S3Dispatch, we have to evaluate the the first argument
                dispatchedObject = getEnclosingArg(frame, generic);
            } else {
                dispatchedObject = arg;
            }

            RStringVector type = classHierarchyNode.execute(dispatchedObject);
            MaterializedFrame callerFrame = getCallerFrame(frame);
            MaterializedFrame genericDefFrame = RArguments.getEnclosingFrame(frame);

            ArgumentsSignature suppliedSignature = RArguments.getSignature(frame);
            Object[] suppliedArguments = RArguments.getArguments(frame);
            Object result = dispatch(frame, generic, type, null, callerFrame, genericDefFrame, suppliedSignature, suppliedArguments);
            throw new ReturnException(result, null);
        }

        /**
         * Get the first (logical) argument in the frame, and handle {@link RPromise}s and
         * {@link RArgsValuesAndNames}.
         */
        private Object getEnclosingArg(VirtualFrame frame, String generic) {
            if (RArguments.getArgumentsLength(frame) == 0 || RArguments.getArgument(frame, 0) == null) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.UNKNOWN_FUNCTION_USE_METHOD, generic, RRuntime.toString(RNull.instance));
            }
            Object enclosingArg = RArguments.getArgument(frame, 0);
            if (argsValueAndNamesProfile.profile(enclosingArg instanceof RArgsValuesAndNames)) {
                // The GnuR "1. argument" might be hidden inside a "..."! Unwrap for proper dispatch
                RArgsValuesAndNames varArgs = (RArgsValuesAndNames) enclosingArg;
                if (varArgs.isEmpty()) {
                    errorProfile.enter();
                    throw RError.error(getEncapsulatingSourceSection(), RError.Message.UNKNOWN_FUNCTION_USE_METHOD, generic, RRuntime.toString(RNull.instance));
                }
                enclosingArg = varArgs.getArgument(0);
            }
            if (promiseCheckHelper == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                promiseCheckHelper = insert(new PromiseCheckHelperNode());
            }
            return promiseCheckHelper.checkEvaluate(frame, enclosingArg);
        }
    }

    @RBuiltin(name = "NextMethod", kind = SUBSTITUTE, parameterNames = {"generic", "object", "..."})
    public abstract static class NextMethod extends S3DispatchFunctions {

        @Child private ReadVariableNode rvnGroup = ReadVariableNode.create(RRuntime.RDotGroup, RType.Character, ReadKind.SilentLocal);
        @Child private ReadVariableNode rvnClass = ReadVariableNode.create(RRuntime.RDotClass, RType.Character, ReadKind.SilentLocal);
        @Child private ReadVariableNode rvnGeneric = ReadVariableNode.create(RRuntime.RDotGeneric, RType.Character, ReadKind.SilentLocal);
        @Child private ReadVariableNode rvnCall = ReadVariableNode.create(RRuntime.RDotGenericCallEnv, RType.Any, ReadKind.SilentLocal);
        @Child private ReadVariableNode rvnDef = ReadVariableNode.create(RRuntime.RDotGenericDefEnv, RType.Any, ReadKind.SilentLocal);

        @Child private CombineSignaturesNode combineSignatures;
        @Child private CollectArgumentsNode collectArguments = CollectArgumentsNodeGen.create();

        @CompilationFinal private RAttributeProfiles attrProfiles;
        @Child private PromiseHelperNode promiseHelper;

        private final BranchProfile errorProfile = BranchProfile.create();
        private final ConditionProfile emptyArgsProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile genericCallFrameNullProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile genericDefFrameNullProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile alternateClassHeaderProfile = ConditionProfile.createBinaryProfile();

        private final ValueProfile parameterSignatureProfile = ValueProfile.createIdentityProfile();

        protected NextMethod() {
            super(true);
        }

        @Override
        public Object[] getDefaultParameterValues() {
            return new Object[]{RNull.instance, RNull.instance, RArgsValuesAndNames.EMPTY};
        }

        @Specialization
        protected Object nextMethod(VirtualFrame frame, @SuppressWarnings("unused") RNull nullGeneric, Object obj, RArgsValuesAndNames args) {
            String generic = (String) rvnGeneric.execute(frame);
            if (generic == null || generic.isEmpty()) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.GEN_FUNCTION_NOT_SPECIFIED);
            }
            return nextMethod(frame, generic, obj, args);
        }

        @SuppressWarnings("unused")
        @Specialization
        protected Object nextMethod(VirtualFrame frame, String generic, Object obj, RArgsValuesAndNames args) {
            controlVisibility();
            MaterializedFrame genericCallFrame = getCallFrame(frame);
            MaterializedFrame genericDefFrame = getDefFrame(frame);
            String group = (String) rvnGroup.execute(frame);

            ArgumentsSignature suppliedSignature;
            ArgumentsSignature parameterSignature = parameterSignatureProfile.profile(RArguments.getSignature(frame));
            Object[] suppliedArguments = collectArguments.execute(frame, parameterSignature);
            if (emptyArgsProfile.profile(args == RArgsValuesAndNames.EMPTY)) {
                suppliedSignature = parameterSignature;
            } else {
                if (combineSignatures == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    combineSignatures = insert(CombineSignaturesNodeGen.create());
                }
                suppliedSignature = combineSignatures.execute(parameterSignature, args.getSignature());

                suppliedArguments = Arrays.copyOf(suppliedArguments, suppliedSignature.getLength());
                System.arraycopy(args.getArguments(), 0, suppliedArguments, parameterSignature.getLength(), suppliedSignature.getLength() - parameterSignature.getLength());
            }
            return dispatch(frame, generic, readType(frame), group, genericCallFrame, genericDefFrame, suppliedSignature, suppliedArguments);
        }

        private MaterializedFrame getDefFrame(VirtualFrame frame) {
            MaterializedFrame genericDefFrame = (MaterializedFrame) rvnDef.execute(frame);
            if (genericDefFrameNullProfile.profile(genericDefFrame == null)) {
                genericDefFrame = RArguments.getEnclosingFrame(frame);
            }
            return genericDefFrame;
        }

        private MaterializedFrame getCallFrame(VirtualFrame frame) {
            MaterializedFrame genericCallFrame = (MaterializedFrame) rvnCall.execute(frame);
            if (genericCallFrameNullProfile.profile(genericCallFrame == null)) {
                genericCallFrame = frame.materialize();
            }
            return genericCallFrame;
        }

        private RStringVector readType(VirtualFrame frame) {
            Object storedClass = rvnClass.execute(frame);
            if (alternateClassHeaderProfile.profile(storedClass == null || storedClass == RNull.instance)) {
                return getAlternateClassHr(frame);
            } else {
                return (RStringVector) storedClass;
            }
        }

        private RStringVector getAlternateClassHr(VirtualFrame frame) {
            if (promiseHelper == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                attrProfiles = RAttributeProfiles.create();
                promiseHelper = insert(new PromiseHelperNode());
            }
            if (RArguments.getArgumentsLength(frame) == 0 || RArguments.getArgument(frame, 0) == null ||
                            (!(RArguments.getArgument(frame, 0) instanceof RAbstractContainer) && !(RArguments.getArgument(frame, 0) instanceof RPromise))) {
                errorProfile.enter();
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.OBJECT_NOT_SPECIFIED);
            }
            Object arg = RArguments.getArgument(frame, 0);
            if (arg instanceof RPromise) {
                arg = promiseHelper.evaluate(frame, (RPromise) arg);
            }
            RAbstractContainer enclosingArg = (RAbstractContainer) arg;
            return enclosingArg.getClassHierarchy();
        }
    }
}
