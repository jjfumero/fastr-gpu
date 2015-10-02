/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.nodes.function;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.ConditionProfile;
import com.oracle.truffle.r.nodes.RASTUtils;
import com.oracle.truffle.r.nodes.access.ConstantNode;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode.ReadKind;
import com.oracle.truffle.r.nodes.function.S3FunctionLookupNode.NoGenericMethodException;
import com.oracle.truffle.r.nodes.function.S3FunctionLookupNode.Result;
import com.oracle.truffle.r.nodes.runtime.RASTDeparse;
import com.oracle.truffle.r.runtime.Arguments;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RArguments.S3Args;
import com.oracle.truffle.r.runtime.RDeparse;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RGroupGenerics;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RSerialize;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

public final class GroupDispatchNode extends RNode implements RSyntaxNode {

    @Child private CallArgumentsNode callArgsNode;
    @Child private S3FunctionLookupNode functionLookupL;
    @Child private S3FunctionLookupNode functionLookupR;
    @Child private ClassHierarchyNode classHierarchyL;
    @Child private ClassHierarchyNode classHierarchyR;
    @Child private CallMatcherNode callMatcher = CallMatcherNode.create(false, true);
    @Child private ReadVariableNode lookupVarArgs;

    private final String fixedGenericName;
    private final RGroupGenerics fixedGroup;
    private final RFunction fixedBuiltinFunction;

    private final ConditionProfile mismatchProfile = ConditionProfile.createBinaryProfile();

    @CompilationFinal private boolean dynamicLookup;
    private final ConditionProfile exactEqualsProfile = ConditionProfile.createBinaryProfile();

    private GroupDispatchNode(String genericName, CallArgumentsNode callArgNode, RFunction builtinFunction) {
        this.fixedGenericName = genericName.intern();
        this.fixedGroup = RGroupGenerics.getGroup(genericName);
        this.callArgsNode = callArgNode;
        this.fixedBuiltinFunction = builtinFunction;
    }

    public static GroupDispatchNode create(String genericName, SourceSection callSrc, ArgumentsSignature signature, RSyntaxNode... arguments) {
        CallArgumentsNode callArgNode = CallArgumentsNode.create(false, true, Arrays.copyOf(arguments, arguments.length, RNode[].class), signature);
        GroupDispatchNode gdcn = new GroupDispatchNode(genericName, callArgNode, RContext.lookupBuiltin(genericName));
        gdcn.assignSourceSection(callSrc);
        return gdcn;
    }

    public static GroupDispatchNode create(String genericName, CallArgumentsNode callArgNode, RFunction builtinFunction, SourceSection callSrc) {
        GroupDispatchNode gdcn = new GroupDispatchNode(genericName, callArgNode, builtinFunction);
        gdcn.assignSourceSection(callSrc);
        return gdcn;
    }

    public Arguments<RSyntaxNode> getArguments() {
        return new Arguments<>(callArgsNode.getSyntaxArguments(), callArgsNode.getSignature());
    }

    public String getGenericName() {
        return fixedGenericName;
    }

    public SourceSection getCallSrc() {
        return getSourceSection();
    }

    @Override
    public void deparseImpl(RDeparse.State state) {
        String name = getGenericName();
        RDeparse.Func func = RDeparse.getFunc(name);
        state.startNodeDeparse(this);
        if (func != null) {
            // infix operator
            RASTDeparse.deparseInfixOperator(state, this, func);
        } else {
            state.append(name);
            RCallNode.deparseArguments(state, callArgsNode.getSyntaxArguments(), callArgsNode.signature);
        }
        state.endNodeDeparse(this);
    }

    @Override
    public void serializeImpl(RSerialize.State state) {
        String name = getGenericName();
        state.setAsBuiltin(name);
        RCallNode.serializeArguments(state, callArgsNode.getSyntaxArguments(), callArgsNode.signature);
    }

    @Override
    public RSyntaxNode substituteImpl(REnvironment env) {
        // TODO substitute aDispatchNode
        Arguments<RSyntaxNode> substituteArguments = RCallNode.substituteArguments(env, callArgsNode.getSyntaxArguments(), callArgsNode.signature);
        return RASTUtils.createCall(this, false, substituteArguments.getSignature(), substituteArguments.getArguments());
    }

    public int getRlengthImpl() {
        return 1 + getArguments().getLength();
    }

    @Override
    public Object getRelementImpl(int index) {
        if (index == 0) {
            if (RASTUtils.isNamedFunctionNode(this)) {
                return RASTUtils.findFunctionName(this);
            } else {
                RNode functionNode = RASTUtils.getFunctionNode(this);
                if (functionNode instanceof ConstantNode && ((ConstantNode) functionNode).getValue() instanceof RSymbol) {
                    return ((ConstantNode) functionNode).getValue();
                } else {
                    return RDataFactory.createLanguage(functionNode);
                }
            }
        } else {
            Arguments<RSyntaxNode> args = RASTUtils.findCallArguments(this);
            return RASTUtils.createLanguageElement(args, index - 1);
        }
    }

    @Override
    public boolean getRequalsImpl(RSyntaxNode other) {
        throw RInternalError.unimplemented();
    }

    @Override
    public Object execute(VirtualFrame frame) {
        RArgsValuesAndNames varArgs = null;
        if (callArgsNode.containsVarArgsSymbol()) {
            if (lookupVarArgs == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupVarArgs = insert(ReadVariableNode.create(ArgumentsSignature.VARARG_NAME, RType.Any, ReadKind.Silent));
            }
            try {
                varArgs = lookupVarArgs.executeRArgsValuesAndNames(frame);
            } catch (UnexpectedResultException e) {
                throw RInternalError.shouldNotReachHere(e, "'...' should always be represented by RArgsValuesAndNames");
            }
        }
        RArgsValuesAndNames argAndNames = callArgsNode.evaluateFlatten(frame, varArgs);
        return executeInternal(frame, argAndNames, fixedGenericName, fixedGroup, fixedBuiltinFunction);
    }

    public Object executeDynamic(VirtualFrame frame, RArgsValuesAndNames argAndNames, String genericName, RGroupGenerics group, RFunction builtinFunction) {
        if (!dynamicLookup) {
            if (builtinFunction == fixedBuiltinFunction && (exactEqualsProfile.profile(fixedGenericName == genericName) || fixedGenericName.equals(genericName))) {
                return executeInternal(frame, argAndNames, fixedGenericName, fixedGroup, fixedBuiltinFunction);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            dynamicLookup = true;
        }
        return executeInternal(frame, argAndNames, genericName, group, builtinFunction);
    }

    private Object executeInternal(VirtualFrame frame, RArgsValuesAndNames argAndNames, String genericName, RGroupGenerics group, RFunction builtinFunction) {
        Object[] evaluatedArgs = argAndNames.getArguments();

        if (classHierarchyL == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            classHierarchyL = insert(ClassHierarchyNodeGen.create(false));
        }
        RStringVector typeL = evaluatedArgs.length == 0 ? null : classHierarchyL.execute(evaluatedArgs[0]);

        Result resultL = null;
        if (typeL != null) {
            try {
                if (functionLookupL == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    functionLookupL = insert(S3FunctionLookupNode.create(false, false));
                }
                resultL = functionLookupL.execute(frame, genericName, typeL, group.getName(), frame.materialize(), null);
            } catch (NoGenericMethodException e) {
                // fall-through
            }
        }
        Result resultR = null;
        if (group == RGroupGenerics.Ops && argAndNames.getSignature().getLength() >= 2) {
            if (classHierarchyR == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                classHierarchyR = insert(ClassHierarchyNodeGen.create(false));
            }
            RStringVector typeR = classHierarchyR.execute(evaluatedArgs[1]);
            if (typeR != null) {
                try {
                    if (functionLookupR == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        functionLookupR = insert(S3FunctionLookupNode.create(false, false));
                    }
                    resultR = functionLookupR.execute(frame, genericName, typeR, group.getName(), frame.materialize(), null);
                } catch (NoGenericMethodException e) {
                    // fall-through
                }
            }
        }

        Result result;
        RStringVector dotMethod;
        if (resultL == null) {
            if (resultR == null) {
                result = null;
                dotMethod = null;
            } else {
                result = resultR;
                dotMethod = RDataFactory.createStringVector(new String[]{"", result.targetFunctionName}, true);
            }
        } else {
            if (resultR == null) {
                result = resultL;
                dotMethod = RDataFactory.createStringVector(new String[]{result.targetFunctionName, ""}, true);
            } else {
                if (mismatchProfile.profile(resultL.function != resultR.function)) {
                    RError.warning(this, RError.Message.INCOMPATIBLE_METHODS, resultL.targetFunctionName, resultR.targetFunctionName, genericName);
                    result = null;
                    dotMethod = null;
                } else {
                    result = resultL;
                    dotMethod = RDataFactory.createStringVector(new String[]{result.targetFunctionName, result.targetFunctionName}, true);
                }
            }
        }
        ArgumentsSignature signature = argAndNames.getSignature();
        S3Args s3Args;
        RFunction function;
        if (result == null) {
            s3Args = null;
            function = builtinFunction;
        } else {
            s3Args = new S3Args(genericName, result.clazz, dotMethod, frame.materialize(), null, result.groupMatch ? group.getName() : null);
            function = result.function;
        }
        if (function == null) {
            CompilerDirectives.transferToInterpreter();
            throw RError.nyi(this, "missing builtin function '" + genericName + "'");
        }
        return callMatcher.execute(frame, signature, evaluatedArgs, function, s3Args);
    }
}
