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
package com.oracle.truffle.r.nodes.function;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.access.*;
import com.oracle.truffle.r.nodes.access.variables.*;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode.ReadKind;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.function.MatchedArguments.MatchedArgumentsNode;
import com.oracle.truffle.r.nodes.function.S3FunctionLookupNode.Result;
import com.oracle.truffle.r.nodes.function.signature.*;
import com.oracle.truffle.r.nodes.runtime.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.RArguments.S3Args;
import com.oracle.truffle.r.runtime.RDeparse.Func;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.env.*;
import com.oracle.truffle.r.runtime.gnur.*;
import com.oracle.truffle.r.runtime.nodes.*;

/**
 * This class denotes a call site to a function,e.g.:
 *
 * <pre>
 * f(a, b, c)
 * </pre>
 * <p>
 * To avoid repeated work on each call, {@link CachedCallNode} class together with
 * {@link UninitializedCallNode} forms the basis for the polymorphic inline cache (PIC) used to
 * cache argument nodes on the call site of a function call.
 * </p>
 * There are two problems we're facing which are the reason for the caching:
 * <ol>
 * <li>on some call sites the function may change between executions</li>
 * <li>for call sites taking "..." as an argument the actual argument list passed into the function
 * may change each call (note that this is totally independent of the problem which function is to
 * be called)</li>
 * </ol>
 * <p>
 * Problem 1 can be tackled by a the use of an {@link IndirectCallNode} instead of a
 * {@link DirectCallNode} which is not as performant as the latter but has no further disadvantages.
 * But as the function changed its formal parameters changed, too, so a re-match has to be done as
 * well, which involves the creation of nodes and thus must happen on the {@link TruffleBoundary}.<br/>
 * Problem 2 is not that easy, too: It is solved by reading the values associated with "..." (which
 * are Promises) and wrapping them in newly created {@link RNode}s. These nodes get inserted into
 * the arguments list ({@link CallArgumentsNode#executeFlatten(Frame)}) - which needs to be be
 * matched against the formal parameters again, as theses arguments may carry names as well which
 * may have an impact on argument order. As matching involves node creation, it has to happen on the
 * {@link TruffleBoundary}.
 * </p>
 * To avoid repeated node creations as much as possible by caching two interwoven PICs are
 * implemented. The caches are constructed using the following classes:
 *
 * <pre>
 *  U = {@link UninitializedCallNode}: Forms the uninitialized end of the function PIC
 *  D = {@link DispatchedCallNode}: Function fixed, no varargs
 *  G = {@link GenericCallNode}: Function arbitrary
 * 
 *  UV = {@link UninitializedCallNode} with varargs,
 *  UVC = {@link UninitializedVarArgsCacheCallNode} with varargs, for varargs cache
 *  DV = {@link DispatchedVarArgsCallNode}: Function fixed, with cached varargs
 *  DGV = {@link DispatchedGenericVarArgsCallNode}: Function fixed, with arbitrary varargs (generic case)
 * 
 * (RB = {@link RBuiltinNode}: individual functions that are builtins are represented by this node
 * which is not aware of caching). Due to {@link CachedCallNode} (see below) this is transparent to
 * the cache and just behaves like a D/DGV)
 * </pre>
 *
 * As the property "takes varargs as argument" is static for each call site, we effectively end up
 * with two separate cache structures. Some examples of each are depicted below:
 *
 * <pre>
 * non varargs, max depth:
 * |
 * D-D-D-U
 * 
 * no varargs, generic (if max depth is exceeded):
 * |
 * D-D-D-D-G
 * 
 * varargs:
 * |
 * DV-DV-UV         <- function call target identity level cache
 *    |
 *    DV
 *    |
 *    UVC           <- varargs signature level cache
 * 
 * varargs, max varargs depth exceeded:
 * |
 * DV-DV-UV
 *    |
 *    DV
 *    |
 *    DV
 *    |
 *    DV
 *    |
 *    DGV
 * 
 * varargs, max function depth exceeded:
 * |
 * DV-DV-DV-DV-GV
 * </pre>
 * <p>
 * In the diagrams above every horizontal connection "-" is in fact established by a separate node:
 * {@link CachedCallNode}, which encapsulates the check for function call target identity. So the 1.
 * example in fact looks like this:
 *
 * <pre>
 * |
 * C-C-C-U
 * | | |
 * D D D
 * </pre>
 *
 * This is useful as it avoids repetition and allows child nodes to be more concise. It is also
 * needed as there are {@link RCallNode} implementations that are not aware of caching, e.g.
 * {@link RBuiltinNode}, which may be part of the cache. Note that varargs cache nodes (
 * {@link DispatchedVarArgsCallNode}, the nodes connected by "|") handle the check for varargs value
 * identity by itself.
 * </p>
 *
 * As the two problems of changing arguments and changing functions are totally independent as
 * described above, it would be possible to use two totally separate caching infrastructures. The
 * reason for the current choice is the expectation that R users will try to call same functions
 * with the same arguments, and there will very rarely be the case that the same arguments get
 * passed via "..." to the same functions.
 *
 * TODO Many of the classes here do not really need to implement {@link RSyntaxNode}.
 */
@NodeInfo(cost = NodeCost.NONE)
public final class RCallNode extends RNode implements RSyntaxNode {

    private static final int FUNCTION_INLINE_CACHE_SIZE = 4;
    private static final int VARARGS_INLINE_CACHE_SIZE = 4;

    private static final Object[] defaultTempIdentifiers = new Object[]{new Object(), new Object(), new Object(), new Object()};

    @Child private RNode functionNode;
    @Child private PromiseHelperNode promiseHelper;
    @Child private RootCallNode call;
    @Child private RootCallNode internalDispatchCall;
    @Child private FrameSlotNode dispatchTempSlot;
    @Child private RNode dispatchArgument;
    @Child private S3FunctionLookupNode dispatchLookup;
    @Child private ClassHierarchyNode classHierarchyNode;
    @Child private GroupDispatchNode groupDispatchNode;

    /**
     * The {@link #arguments} field must be cloned by {@link NodeUtil#cloneNode(Node)}.
     */
    private static class SyntaxArguments extends NodeCloneable {
        private final RSyntaxNode[] v;

        SyntaxArguments(RSyntaxNode[] arguments) {
            this.v = arguments;
        }

        @Override
        protected Object clone() {
            SyntaxArguments sa = new SyntaxArguments(new RSyntaxNode[v.length]);
            for (int i = 0; i < v.length; i++) {
                if (v[i] != null) {
                    RSyntaxNode viClone = (RSyntaxNode) NodeUtil.cloneNode(v[i].asRNode());
                    sa.v[i] = viClone;
                }
            }
            return sa;
        }

    }

    private final SyntaxArguments arguments;
    private final ArgumentsSignature signature;

    private final ValueProfile builtinProfile = ValueProfile.createIdentityProfile();
    private final ConditionProfile implicitTypeProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile resultIsBuiltinProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile isPromiseProfile = ConditionProfile.createBinaryProfile();
    private final BranchProfile normalDispatchProfile = BranchProfile.create();
    private final BranchProfile errorProfile = BranchProfile.create();
    private final ConditionProfile isRFunctionProfile = ConditionProfile.createBinaryProfile();

    private int tempIdentifier;

    @Child private Node foreignCall;
    @CompilationFinal private int foreignCallArgCount;
    @Child private CallArgumentsNode foreignCallArguments;

    public RCallNode(RNode function, RSyntaxNode[] arguments, ArgumentsSignature signature) {
        this.functionNode = function;
        this.arguments = new SyntaxArguments(arguments);
        this.signature = signature;
    }

    public Arguments<RSyntaxNode> getArguments() {
        return new Arguments<>(arguments.v, signature);
    }

    public int getArgumentCount() {
        return arguments.v.length;
    }

    public RSyntaxNode getArgument(int index) {
        return arguments.v[index];
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return execute(frame, executeFunctionNode(frame));
    }

    public Object execute(VirtualFrame frame, Object functionObject) {
        RFunction function;
        if (isRFunctionProfile.profile(functionObject instanceof RFunction)) {
            function = (RFunction) functionObject;
            // fall through
        } else if (functionObject instanceof TruffleObject && !(functionObject instanceof RTypedValue)) {
            if (foreignCallArguments == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                foreignCallArguments = insert(createArguments(null, true, true));
            }
            Object[] argumentsArray = foreignCallArguments.evaluateFlattenObjects(frame);
            if (foreignCall == null || foreignCallArgCount != argumentsArray.length) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                foreignCall = insert(Message.createExecute(argumentsArray.length).createNode());
                foreignCallArgCount = argumentsArray.length;
            }
            try {
                return ForeignAccess.execute(foreignCall, frame, (TruffleObject) functionObject, argumentsArray);
            } catch (Throwable e) {
                errorProfile.enter();
                throw RError.error(this, RError.Message.GENERIC, "Foreign function failed: " + e.getMessage() != null ? e.getMessage() : e.toString());
            }
        } else {
            errorProfile.enter();
            throw RError.error(this, RError.Message.APPLY_NON_FUNCTION);
        }

        if (!signature.isEmpty() && function.getRBuiltin() != null) {
            RBuiltinDescriptor builtin = builtinProfile.profile(function.getRBuiltin());
            if (builtin.getDispatch() == RDispatch.INTERNAL_GENERIC) {
                if (internalDispatchCall == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    dispatchTempSlot = insert(FrameSlotNode.createInitialized(frame.getFrameDescriptor(), defaultTempIdentifiers[0], true));
                    internalDispatchCall = insert(new UninitializedCallNode(this, defaultTempIdentifiers[0]));
                    dispatchArgument = insert(NodeUtil.cloneNode(arguments.v[0].asRNode()));
                    dispatchLookup = insert(S3FunctionLookupNode.create(true, false));
                    classHierarchyNode = insert(ClassHierarchyNodeGen.create(false));
                }
                FrameSlot slot = dispatchTempSlot.executeFrameSlot(frame);
                try {
                    if (frame.isObject(slot) && frame.getObject(slot) != null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        // keep the complete loop in the slow path
                        do {
                            tempIdentifier++;
                            Object identifier = tempIdentifier < defaultTempIdentifiers.length ? defaultTempIdentifiers[tempIdentifier] : new Object();
                            dispatchTempSlot.replace(FrameSlotNode.createInitialized(frame.getFrameDescriptor(), identifier, true));
                            internalDispatchCall.replace(new UninitializedCallNode(this, identifier));
                            slot = dispatchTempSlot.executeFrameSlot(frame);
                        } while (frame.isObject(slot) && frame.getObject(slot) != null);
                    }
                } catch (FrameSlotTypeException e) {
                    throw RInternalError.shouldNotReachHere();
                }
                try {
                    Object dispatch = dispatchArgument.execute(frame);
                    frame.setObject(slot, dispatch);
                    RStringVector type = classHierarchyNode.execute(dispatch);
                    S3Args s3Args;
                    RFunction resultFunction;
                    if (implicitTypeProfile.profile(type != null)) {
                        Result result = dispatchLookup.execute(frame, builtin.getName(), type, null, frame.materialize(), null);
                        if (resultIsBuiltinProfile.profile(result.function.isBuiltin())) {
                            s3Args = null;
                        } else {
                            s3Args = new S3Args(result.generic, result.clazz, result.targetFunctionName, frame.materialize(), null, null);
                        }
                        resultFunction = result.function;
                    } else {
                        s3Args = null;
                        resultFunction = function;
                    }
                    return internalDispatchCall.execute(frame, resultFunction, s3Args);
                } finally {
                    frame.setObject(slot, null);
                }
            }
        }
        normalDispatchProfile.enter();
        if (call == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            call = insert(new UninitializedCallNode(this, null));
        }
        return call.execute(frame, function, null);
    }

    private Object executeFunctionNode(VirtualFrame frame) {
        /**
         * Expressions such as "pkg:::f" can result in (delayedAssign) promises that must be
         * explicitly resolved.
         */
        Object value = functionNode.execute(frame);
        if (isPromiseProfile.profile(value instanceof RPromise)) {
            if (promiseHelper == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                promiseHelper = insert(new PromiseHelperNode());
            }
            value = promiseHelper.evaluate(frame, (RPromise) value);
        }
        return value;
    }

    public RNode getFunctionNode() {
        if (functionNode != null) {
            // Only the very 1. RootCallNode on the top level contains the one-and-only function
            // node
            return functionNode;
        }
        return getParentCallNode().getFunctionNode();
    }

    public CallArgumentsNode createArguments(Object dispatchTempIdentifier, boolean modeChange, boolean modeChangeAppliesToAll) {
        RNode[] args = new RNode[arguments.v.length];
        for (int i = 0; i < arguments.v.length; i++) {
            if (i == 0 && dispatchTempIdentifier != null) {
                args[0] = new GetTempNode(dispatchTempIdentifier, arguments.v[0]);
            } else {
                args[i] = arguments.v[i] == null ? null : NodeUtil.cloneNode(arguments.v[i].asRNode());
            }
        }
        return CallArgumentsNode.create(modeChange, modeChangeAppliesToAll, args, signature);
    }

    @Override
    public void deparseImpl(RDeparse.State state) {
        Object fname = RASTUtils.findFunctionName(this);
        Func func = RASTDeparse.isInfixOperator(fname);
        try {
            state.startNodeDeparse(this);
            if (func != null && arguments.v.length > 0) {
                RASTDeparse.deparseInfixOperator(state, this, func);
            } else {
                if (fname instanceof RSymbol) {
                    String sfname = ((RSymbol) fname).getName();
                    if (sfname.equals(":::") || sfname.equals("::")) {
                        // special infix, could be a:::b() or a:::b
                        RNode fn = getFunctionNode().unwrap();
                        RSyntaxNode[] argValues;
                        if (fn instanceof RCallNode) {
                            argValues = ((RCallNode) fn).arguments.v;
                        } else {
                            argValues = arguments.v;
                        }
                        argValues[0].deparseImpl(state);
                        state.append(sfname);
                        argValues[1].deparseImpl(state);
                        if (!(fn instanceof RCallNode)) {
                            return;
                        }
                    } else if (sfname.equals("[<-") || sfname.equals("[[<-")) {
                        boolean isSubset = sfname.equals("[<-");
                        arguments.v[0].deparseImpl(state);
                        state.append(isSubset ? "[" : "[[");
                        for (int i = 1; i < arguments.v.length - 1; i++) {
                            if (signature.getName(i) != null && !signature.getName(i).isEmpty()) {
                                state.append(signature.getName(i));
                                state.append('=');
                            }
                            arguments.v[i].deparseImpl(state);
                            if (i != arguments.v.length - 2) {
                                state.append(", ");
                            }
                        }
                        state.append(isSubset ? "]" : "]]");
                        state.append(" <- ");
                        arguments.v[arguments.v.length - 1].deparseImpl(state);
                        return;
                    }
                }
                getFunctionNode().deparse(state);

                deparseArguments(state, arguments.v, signature);
            }
        } finally {
            state.endNodeDeparse(this);
        }
    }

    public static void deparseArguments(RDeparse.State state, RSyntaxNode[] arguments, ArgumentsSignature signature) {
        state.append('(');
        for (int i = 0; i < arguments.length; i++) {
            RSyntaxNode argument = arguments[i];
            String name = signature.getName(i);
            if (name != null) {
                state.append(name);
                state.append(" = ");
            }
            if (argument != null) {
                // e.g. not f(, foo)
                argument.deparseImpl(state);
            }
            if (i != arguments.length - 1) {
                state.append(", ");
            }
        }
        state.append(')');
    }

    @Override
    public void serializeImpl(RSerialize.State state) {
        state.setAsLangType();
        state.serializeNodeSetCar(functionNode);
        if (isColon(functionNode)) {
            // special case, have to translate Strings to Symbols
            state.openPairList();
            state.setCarAsSymbol((String) ((ConstantNode) arguments.v[0]).getValue());
            state.openPairList();
            state.setCarAsSymbol((String) ((ConstantNode) arguments.v[1]).getValue());
            state.linkPairList(2);
            state.setCdr(state.closePairList());
        } else {
            serializeArguments(state, arguments.v, signature);
        }
    }

    public static void serializeArguments(RSerialize.State state, RSyntaxNode[] arguments, ArgumentsSignature signature) {
        state.openPairList(SEXPTYPE.LISTSXP);
        if (arguments.length == 0) {
            state.setNull();
        } else {
            for (int i = 0; i < arguments.length; i++) {
                RSyntaxNode argument = arguments[i];
                String name = signature.getName(i);
                if (name != null) {
                    state.setTagAsSymbol(name);
                }
                if (argument == null) {
                    state.setCarMissing();
                } else {
                    state.serializeNodeSetCar(argument);
                }
                if (i != arguments.length - 1) {
                    state.openPairList();
                }

            }
            state.linkPairList(arguments.length);
        }
        state.setCdr(state.closePairList());
    }

    public int getRlengthImpl() {
        return 1 + getArgumentCount();
    }

    @Override
    public Object getRelementImpl(int index) {
        if (index == 0) {
            if (RASTUtils.isNamedFunctionNode(this)) {
                return RASTUtils.findFunctionName(this);
            } else {
                if (functionNode instanceof ConstantNode && ((ConstantNode) functionNode).getValue() instanceof RSymbol) {
                    return ((ConstantNode) functionNode).getValue();
                } else {
                    return RDataFactory.createLanguage(functionNode);
                }
            }
        } else {
            Arguments<RSyntaxNode> args = getArguments();
            RSyntaxNode argNode = args.getArguments()[index - 1];
            /*
             * If this argument is of form x=y, must pass both back. We use an RArgsValuesAndNames
             * for convenience, which is interpreted by our caller.
             */
            if (argNode instanceof ReadVariableNode && signature.getName(index - 1) != null) {
                return new RArgsValuesAndNames(new Object[]{RASTUtils.createRSymbol(argNode.asRNode())}, ArgumentsSignature.get(signature.getName(index - 1)));
            } else {
                return RASTUtils.createLanguageElement(args, index - 1);
            }
        }
    }

    @Override
    public boolean getRequalsImpl(RSyntaxNode other) {
        if (!(other instanceof RCallNode)) {
            return false;
        }
        RCallNode otherCN = (RCallNode) other;
        if (!getFunctionNode().getRequals(otherCN.getFunctionNode().asRSyntaxNode())) {
            return false;
        }
        return getRequalsImplArgs(arguments.v, otherCN.arguments.v);
    }

    public static boolean getRequalsImplArgs(RSyntaxNode[] arguments, RSyntaxNode[] otherArguments) {
        if (arguments.length != otherArguments.length) {
            return false;
        }
        for (int i = 0; i < arguments.length; i++) {
            if (!arguments[i].getRequalsImpl(otherArguments[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isColon(RNode node) {
        if (node instanceof ReadVariableNode) {
            ReadVariableNode rvn = (ReadVariableNode) node;
            String name = rvn.getIdentifier();
            return name.equals("::") || name.equals(":::");
        }
        return false;
    }

    @Override
    public RSyntaxNode substituteImpl(REnvironment env) {
        RNode functionSub = getFunctionNode().substitute(env).asRNode();

        Arguments<RSyntaxNode> argsSub = substituteArguments(env, arguments.v, signature);
        return RASTUtils.createCall(functionSub, false, argsSub.getSignature(), argsSub.getArguments());
    }

    public static Arguments<RSyntaxNode> substituteArguments(REnvironment env, RSyntaxNode[] arguments, ArgumentsSignature signature) {
        ArrayList<RSyntaxNode> newArguments = new ArrayList<>();
        ArrayList<String> newNames = new ArrayList<>();
        for (int i = 0; i < arguments.length; i++) {
            RSyntaxNode argNodeSubs = arguments[i].substituteImpl(env);
            if (argNodeSubs instanceof RASTUtils.MissingDotsNode) {
                // nothing to do
            } else if (argNodeSubs instanceof RASTUtils.ExpandedDotsNode) {
                RASTUtils.ExpandedDotsNode expandedDotsNode = (RASTUtils.ExpandedDotsNode) argNodeSubs;
                newArguments.addAll(Arrays.asList(expandedDotsNode.nodes));
                for (int j = 0; j < expandedDotsNode.nodes.length; j++) {
                    newNames.add(null);
                }
            } else {
                newArguments.add(argNodeSubs);
                newNames.add(signature.getName(i));
            }
        }
        RSyntaxNode[] newArgumentsArray = newArguments.stream().toArray(RSyntaxNode[]::new);
        String[] newNamesArray = newNames.stream().toArray(String[]::new);
        return new Arguments<>(newArgumentsArray, ArgumentsSignature.get(newNamesArray));
    }

    public static RCallNode createOpCall(SourceSection src, SourceSection opNameSrc, String function, RSyntaxNode... arguments) {
        return createCall(src, ReadVariableNode.createFunctionLookup(opNameSrc, function), ArgumentsSignature.empty(arguments.length), arguments);
    }

    /**
     * Creates a call to a resolved {@link RBuiltinKind#INTERNAL} that will be used to replace the
     * original call.
     *
     * @param frame The frame to create the inlined builtins in
     * @param src source section to use (from original call)
     * @param internalCallArg the {@link UninitializedCallNode} corresponding to the argument to the
     *            {code .Internal}.
     * @param function the resolved {@link RFunction}.
     * @param name The name of the function
     */
    public static LeafCallNode createInternalCall(VirtualFrame frame, SourceSection src, RCallNode internalCallArg, RFunction function, String name) {
        CompilerAsserts.neverPartOfCompilation();
        return UninitializedCallNode.createCacheNode(frame, internalCallArg.createArguments(null, false, true), internalCallArg, function);
    }

    /**
     * Creates a modified call in which the first N arguments are replaced by
     * {@code replacementArgs}. This is, for example, to support
     * {@code HiddenInternalFunctions.MakeLazy}, and condition handling.
     */
    @TruffleBoundary
    public static RCallNode createCloneReplacingArgs(RCallNode call, RSyntaxNode... replacementArgs) {
        RSyntaxNode[] args = new RSyntaxNode[call.arguments.v.length];
        for (int i = 0; i < args.length; i++) {
            args[i] = i < replacementArgs.length ? replacementArgs[i] : call.arguments.v[i];
        }
        return new RCallNode(NodeUtil.cloneNode(call.functionNode), args, call.signature);
    }

    /**
     * The standard way to create a call to {@code function} with given arguments. If
     * {@code src == null} we create one to meet the invariant that all {@link RSyntaxNode}s have a
     * valid {@link SourceSection}.
     */
    public static RCallNode createCall(SourceSection src, RNode function, ArgumentsSignature signature, RSyntaxNode... arguments) {
        RCallNode call = new RCallNode(function, arguments, signature);
        if (src == null) {
            RASTDeparse.ensureSourceSection(call);
        } else {
            call.assignSourceSection(src);
        }
        return call;
    }

    /**
     * A variant of {@link #createCall} that does not require a {@link SourceSection}, because, e.g,
     * it is part of a {@code ReplacementNode} structure or some other internal rewrite (e.g.
     * {@code LApply}).
     */
    public static RCallNode createCallNotSyntax(RNode function, ArgumentsSignature signature, RSyntaxNode... arguments) {
        RCallNode call = new RCallNode(function, arguments, signature);
        return call;
    }

    /**
     * Since the {@link #arguments} field is not a Child, it it not cloned by
     * {@link NodeUtil#cloneNode(Node)} and this is necessary in the creation of "syntaxAST" for a
     * {@code ReplacementNode}.
     */
    public static void updateClonedArguments(RCallNode call, RSyntaxNode[] clonedArguments) {
        for (int i = 0; i < call.arguments.v.length; i++) {
            call.arguments.v[i] = clonedArguments[i];
        }
    }

    public static RBuiltinRootNode findBuiltinRootNode(RootCallTarget callTarget) {
        RootNode root = callTarget.getRootNode();
        if (root instanceof RBuiltinRootNode) {
            return (RBuiltinRootNode) root;
        }
        return null;
    }

    protected RCallNode getParentCallNode() {
        RNode parent = (RNode) unwrapParent();
        if (!(parent instanceof RCallNode)) {
            throw RInternalError.shouldNotReachHere();
        }
        return (RCallNode) parent;
    }

    public static boolean needsSplitting(RFunction function) {
        RootNode root = function.getRootNode();
        if (function.containsDispatch()) {
            return true;
        } else if (root instanceof RRootNode) {
            return ((RRootNode) root).needsSplitting();
        }
        return false;
    }

    public abstract static class RootCallNode extends Node {

        public abstract Object execute(VirtualFrame frame, RFunction function, S3Args s3Args);

    }

    public static final class GetTempNode extends RNode {

        @Child private FrameSlotNode slot;
        private final RSyntaxNode arg;

        public GetTempNode(Object identifier, RSyntaxNode arg) {
            slot = FrameSlotNode.createTemp(identifier, false);
            this.arg = arg;
        }

        @Override
        protected RSyntaxNode getRSyntaxNode() {
            return arg;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                return frame.getObject(slot.executeFrameSlot(frame));
            } catch (FrameSlotTypeException e) {
                throw RInternalError.shouldNotReachHere();
            }
        }
    }

    /**
     * [U]/[UV] Forms the uninitialized end of the function PIC.
     *
     * @see RCallNode
     */
    @NodeInfo(cost = NodeCost.UNINITIALIZED)
    public static final class UninitializedCallNode extends RootCallNode {

        private int depth;
        private final RCallNode call;
        private final Object dispatchTempIdentifier;

        protected UninitializedCallNode(RCallNode call, Object dispatchTempIdentifier) {
            this.call = call;
            this.dispatchTempIdentifier = dispatchTempIdentifier;
            this.depth = 0;
        }

        @Override
        public Object execute(VirtualFrame frame, RFunction function, S3Args s3Args) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            RootCallNode next = depth + 1 < FUNCTION_INLINE_CACHE_SIZE ? this : new GenericCallNode(call.createArguments(dispatchTempIdentifier, true, true));

            RBuiltinDescriptor builtin = function.getRBuiltin();
            boolean modeChange = true;
            if (builtin != null) {
                modeChange = false;
            }

            LeafCallNode current = createCacheNode(frame, call.createArguments(dispatchTempIdentifier, modeChange, true), call, function);
            RootCallNode cachedNode = new CachedCallNode(current, next, function);
            this.replace(cachedNode);
            return cachedNode.execute(frame, function, s3Args);
        }

        private static LeafCallNode createCacheNode(VirtualFrame frame, CallArgumentsNode args, RCallNode creator, RFunction function) {
            CompilerDirectives.transferToInterpreter();

            for (String name : args.getSignature()) {
                if (name != null && name.isEmpty()) {
                    /*
                     * In GnuR this is, evidently output by the parser, so very early, and never
                     * with a caller in the message.
                     */
                    throw RError.error(RError.NO_CALLER, RError.Message.ZERO_LENGTH_VARIABLE);
                }
            }

            LeafCallNode callNode = null;
            // Check implementation: If written in Java, handle differently!
            if (function.isBuiltin()) {
                RootCallTarget callTarget = function.getTarget();
                RBuiltinRootNode root = findBuiltinRootNode(callTarget);
                assert root != null;

                // We inline the given arguments here, as builtins are executed inside the same
                // frame as they are called.
                InlinedArguments inlinedArgs = ArgumentMatcher.matchArgumentsInlined(function, args, creator);
                callNode = new BuiltinCallNode(root.inline(inlinedArgs.getSignature(), inlinedArgs.getArguments(), creator.getSourceSection()));
            } else {
                // Now we need to distinguish: Do supplied arguments vary between calls?
                if (args.containsVarArgsSymbol()) {
                    // Yes, maybe.
                    VarArgsCacheCallNode nextNode = new UninitializedVarArgsCacheCallNode(args, creator);
                    ArgumentsSignature varArgsSignature = CallArgumentsNode.getVarargsAndNames(frame).getSignature();
                    callNode = DispatchedVarArgsCallNode.create(frame, args, nextNode, creator, function, varArgsSignature);
                } else {
                    // Nope! (peeewh)
                    MatchedArguments matchedArgs = ArgumentMatcher.matchArguments(function, args, creator, false);
                    callNode = new DispatchedCallNode(function, matchedArgs);
                }
            }

            return callNode;
        }
    }

    /**
     * [C] Extracts the check for function call target identity away from the individual cache
     * nodes.
     *
     * @see RCallNode
     */
    public static final class CachedCallNode extends RootCallNode {

        @Child private RootCallNode nextNode;
        @Child private LeafCallNode currentNode;

        private final CallTarget cachedCallTarget;

        public CachedCallNode(LeafCallNode current, RootCallNode next, RFunction cachedFunction) {
            this.currentNode = current;
            this.nextNode = next;
            this.cachedCallTarget = cachedFunction.getTarget();
        }

        @Override
        public Object execute(VirtualFrame frame, RFunction f, S3Args s3Args) {
            if (cachedCallTarget == f.getTarget()) {
                return currentNode.execute(frame, f, s3Args);
            }
            return nextNode.execute(frame, f, s3Args);
        }
    }

    /**
     * [GV] {@link RCallNode} in case there is no fixed {@link RFunction} AND varargs...
     *
     * @see RCallNode
     */
    private static final class GenericCallNode extends RootCallNode {

        @Child private IndirectCallNode indirectCall = Truffle.getRuntime().createIndirectCallNode();
        @Child private CallArgumentsNode arguments;

        private final RCaller caller = RDataFactory.createCaller(this);

        GenericCallNode(CallArgumentsNode arguments) {
            // here, it's safe to reuse the arguments - it's the final use
            this.arguments = arguments;
        }

        @Override
        public Object execute(VirtualFrame frame, RFunction currentFunction, S3Args s3Args) {
            CompilerDirectives.transferToInterpreter();
            // Function and arguments may change every call: Flatt'n'Match on SlowPath! :-/
            UnrolledVariadicArguments argsValuesAndNames = arguments.executeFlatten(frame);
            MatchedArguments matchedArgs = ArgumentMatcher.matchArguments(currentFunction, argsValuesAndNames, RError.ROOTNODE, true);

            Object[] argsObject = RArguments.create(currentFunction, caller, null, RArguments.getDepth(frame) + 1, matchedArgs.doExecuteArray(frame), matchedArgs.getSignature(), s3Args);
            return indirectCall.call(frame, currentFunction.getTarget(), argsObject);
        }
    }

    /**
     * This is the counterpart of {@link RCallNode}: While that is the base class for all top-level
     * nodes (C, G, U and GV), this is the base class for all nodes not on the top level of the PIC
     * (and thus don't need all information, but can rely on their parents to have them).
     *
     * @see RCallNode
     */
    public abstract static class LeafCallNode extends RBaseNode {

        public abstract Object execute(VirtualFrame frame, RFunction function, S3Args s3Args);
    }

    @NodeInfo(cost = NodeCost.NONE)
    private static final class BuiltinCallNode extends LeafCallNode {

        @Child private RBuiltinNode builtin;

        BuiltinCallNode(RBuiltinNode builtin) {
            this.builtin = builtin;
        }

        @Override
        public Object execute(VirtualFrame frame, RFunction currentFunction, S3Args s3Args) {
            return builtin.execute(frame);
        }
    }

    /**
     * [D] A {@link RCallNode} for calls to fixed {@link RFunction}s with fixed arguments (no
     * varargs).
     *
     * @see RCallNode
     */
    private static final class DispatchedCallNode extends LeafCallNode {

        @Child private DirectCallNode call;
        @Child private MatchedArgumentsNode matchedArgs;
        @Child private RArgumentsNode argsNode = RArgumentsNode.create();
        @Child private RFastPathNode fastPath;

        private final RCaller caller = RDataFactory.createCaller(this);
        private final boolean needsCallerFrame;
        private final RootCallTarget callTarget;
        @CompilationFinal private boolean needsSplitting;

        DispatchedCallNode(RFunction function, MatchedArguments matchedArgs) {
            this.matchedArgs = matchedArgs.createNode();
            this.needsCallerFrame = function.containsDispatch();
            this.needsSplitting = needsSplitting(function);
            this.callTarget = function.getTarget();

            this.fastPath = function.getFastPath() == null ? null : function.getFastPath().create();
            if (fastPath == null) {
                this.call = Truffle.getRuntime().createDirectCallNode(function.getTarget());
            }
        }

        @Override
        public Object execute(VirtualFrame frame, RFunction currentFunction, S3Args s3Args) {
            if (fastPath != null) {
                Object result = fastPath.execute(frame, matchedArgs.executeArray(frame));
                if (result != null) {
                    return result;
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                fastPath = null;
                call = insert(Truffle.getRuntime().createDirectCallNode(callTarget));
            }

            if (needsSplitting) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                needsSplitting = false;
                call.cloneCallTarget();
            }
            MaterializedFrame callerFrame = needsCallerFrame ? frame.materialize() : null;

            Object[] argsObject = argsNode.execute(currentFunction, caller, callerFrame, RArguments.getDepth(frame) + 1, matchedArgs.executeArray(frame), matchedArgs.getSignature(), s3Args);
            return call.call(frame, argsObject);
        }
    }

    /*
     * Varargs cache classes follow
     */

    /**
     * Base class for the varargs cache [UVC] and [DV].
     *
     * @see RCallNode
     */
    private abstract static class VarArgsCacheCallNode extends LeafCallNode {

        @Child private ReadVariableNode lookupVarArgs = ReadVariableNode.create(ArgumentsSignature.VARARG_NAME, RType.Any, ReadKind.Silent);

        @Override
        public final Object execute(VirtualFrame frame, RFunction function, S3Args s3Args) {
            Object varArgs = lookupVarArgs.execute(frame);
            if (varArgs == RNull.instance) {
                varArgs = RArgsValuesAndNames.EMPTY;
            }
            if (varArgs == null || !(varArgs instanceof RArgsValuesAndNames)) {
                CompilerDirectives.transferToInterpreter();
                RError.error(this, RError.Message.NO_DOT_DOT_DOT);
            }
            return execute(frame, function, ((RArgsValuesAndNames) varArgs).getSignature(), s3Args);
        }

        protected abstract Object execute(VirtualFrame frame, RFunction function, ArgumentsSignature varArgsSignature, S3Args s3Args);
    }

    /**
     * [UVC] The uninitialized end of the varargs cache.
     *
     * @see RCallNode
     */
    @NodeInfo(cost = NodeCost.UNINITIALIZED)
    public static final class UninitializedVarArgsCacheCallNode extends VarArgsCacheCallNode {
        @Child private CallArgumentsNode args;
        private int depth = 1;  // varargs cached is started with a [DV] DispatchedVarArgsCallNode

        public UninitializedVarArgsCacheCallNode(CallArgumentsNode args, @SuppressWarnings("unused") RCallNode creator) {
            this.args = args;
        }

        @Override
        public Object execute(VirtualFrame frame, RFunction function, ArgumentsSignature varArgsSignature, S3Args s3Args) {
            CompilerDirectives.transferToInterpreterAndInvalidate();

            // Extend cache
            this.depth += 1;
            CallArgumentsNode clonedArgs = NodeUtil.cloneNode(args);
            VarArgsCacheCallNode next = createNextNode(function);
            DispatchedVarArgsCallNode newCallNode = DispatchedVarArgsCallNode.create(frame, clonedArgs, next, this, function, varArgsSignature);
            return replace(newCallNode).execute(frame, function, varArgsSignature, s3Args);
        }

        private VarArgsCacheCallNode createNextNode(RFunction function) {
            if (depth < VARARGS_INLINE_CACHE_SIZE) {
                return this;
            } else {
                CallArgumentsNode clonedArgs = NodeUtil.cloneNode(args);
                return new DispatchedGenericVarArgsCallNode(function, clonedArgs);
            }
        }
    }

    /**
     * [DV] A {@link RCallNode} for calls to cached {@link RFunction}s with cached varargs.
     *
     * @see RCallNode
     */
    private static final class DispatchedVarArgsCallNode extends VarArgsCacheCallNode {
        @Child private DirectCallNode call;
        @Child private CallArgumentsNode args;
        @Child private VarArgsCacheCallNode next;
        @Child private MatchedArgumentsNode matchedArgs;
        @Child private RArgumentsNode argsNode = RArgumentsNode.create();

        private final RCaller caller = RDataFactory.createCaller(this);
        private final ArgumentsSignature cachedSignature;
        private final boolean needsCallerFrame;
        @CompilationFinal private boolean needsSplitting;

        protected DispatchedVarArgsCallNode(CallArgumentsNode args, VarArgsCacheCallNode next, RFunction function, ArgumentsSignature varArgsSignature, MatchedArguments matchedArgs) {
            this.call = Truffle.getRuntime().createDirectCallNode(function.getTarget());
            this.args = args;
            this.next = next;
            this.cachedSignature = varArgsSignature;
            this.matchedArgs = matchedArgs.createNode();
            this.needsCallerFrame = function.containsDispatch();
            /*
             * this is a simple heuristic - methods that need a caller frame should have call site -
             * specific versions
             */
            this.needsSplitting = needsSplitting(function);
        }

        protected static DispatchedVarArgsCallNode create(VirtualFrame frame, CallArgumentsNode args, VarArgsCacheCallNode next, RBaseNode creator, RFunction function,
                        ArgumentsSignature varArgsSignature) {
            UnrolledVariadicArguments unrolledArguments = args.executeFlatten(frame);
            MatchedArguments matchedArgs = ArgumentMatcher.matchArguments(function, unrolledArguments, creator, false);
            return new DispatchedVarArgsCallNode(args, next, function, varArgsSignature, matchedArgs);
        }

        @Override
        public Object execute(VirtualFrame frame, RFunction currentFunction, ArgumentsSignature varArgsSignature, S3Args s3Args) {
            // If this is the root of the varargs sub-cache: The signature needs to be created
            // once

            // If the signature does not match: delegate to next node!
            if (cachedSignature != varArgsSignature) {
                return next.execute(frame, currentFunction, varArgsSignature, s3Args);
            }

            // Our cached function and matched arguments do match, simply execute!

            if (needsSplitting) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                needsSplitting = false;
                call.cloneCallTarget();
            }
            MaterializedFrame callerFrame = needsCallerFrame ? frame.materialize() : null;
            Object[] argsObject = argsNode.execute(currentFunction, caller, callerFrame, RArguments.getDepth(frame) + 1, matchedArgs.executeArray(frame), matchedArgs.getSignature(), s3Args);
            return call.call(frame, argsObject);
        }
    }

    /**
     * [DGV] {@link RCallNode} in case there is a cached {@link RFunction} with cached varargs that
     * exceeded the cache size {@link RCallNode#VARARGS_INLINE_CACHE_SIZE} .
     *
     * @see RCallNode
     */
    private static final class DispatchedGenericVarArgsCallNode extends VarArgsCacheCallNode {

        @Child private DirectCallNode call;
        @Child private CallArgumentsNode suppliedArgs;

        private final RCaller caller = RDataFactory.createCaller(this);
        @CompilationFinal private boolean needsCallerFrame;

        DispatchedGenericVarArgsCallNode(RFunction function, CallArgumentsNode suppliedArgs) {
            this.call = Truffle.getRuntime().createDirectCallNode(function.getTarget());
            this.suppliedArgs = suppliedArgs;
        }

        @Override
        protected Object execute(VirtualFrame frame, RFunction currentFunction, ArgumentsSignature varArgsSignature, S3Args s3Args) {
            CompilerDirectives.transferToInterpreter();

            // Arguments may change every call: Flatt'n'Match on SlowPath! :-/
            UnrolledVariadicArguments argsValuesAndNames = suppliedArgs.executeFlatten(frame);
            MatchedArguments matchedArgs = ArgumentMatcher.matchArguments(currentFunction, argsValuesAndNames, this, true);

            if (!needsCallerFrame && currentFunction.containsDispatch()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                needsCallerFrame = true;
            }
            MaterializedFrame callerFrame = needsCallerFrame ? frame.materialize() : null;
            Object[] argsObject = RArguments.create(currentFunction, caller, callerFrame, RArguments.getDepth(frame) + 1, matchedArgs.doExecuteArray(frame), matchedArgs.getSignature(), s3Args);
            return call.call(frame, argsObject);
        }
    }
}
