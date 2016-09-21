/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995, 1996, 1997  Robert Gentleman and Ross Ihaka
 * Copyright (c) 1995-2014, The R Core Team
 * Copyright (c) 2002-2008, The R Foundation
 * Copyright (c) 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.runtime.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.utilities.BranchProfile;
import com.oracle.truffle.api.utilities.ConditionProfile;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode.ReadKind;
import com.oracle.truffle.r.nodes.attributes.AttributeAccess;
import com.oracle.truffle.r.nodes.attributes.AttributeAccessNodeGen;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.function.signature.RArgumentsNode;
import com.oracle.truffle.r.nodes.objects.CollectGenericArgumentsNode;
import com.oracle.truffle.r.nodes.objects.CollectGenericArgumentsNodeGen;
import com.oracle.truffle.r.nodes.objects.DispatchGeneric;
import com.oracle.truffle.r.nodes.objects.DispatchGenericNodeGen;
import com.oracle.truffle.r.nodes.unary.CastIntegerScalarNode;
import com.oracle.truffle.r.nodes.unary.CastStringScalarNode;
import com.oracle.truffle.r.nodes.unary.CastStringScalarNodeGen;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RArguments;
import com.oracle.truffle.r.runtime.RBuiltin;
import com.oracle.truffle.r.runtime.RCaller;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RAttributable;
import com.oracle.truffle.r.runtime.data.RAttributeProfiles;
import com.oracle.truffle.r.runtime.data.RAttributes;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractStringVector;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.nodes.RNode;

// transcribed from src/main/objects.c
@RBuiltin(name = "standardGeneric", kind = PRIMITIVE, parameterNames = {"f", "fdef"})
public abstract class StandardGeneric extends RBuiltinNode {

    // TODO: for now, we always go through generic dispatch

    @Child private AttributeAccess genericAttrAccess;
    @Child private FrameFunctions.SysFunction sysFunction;
    @Child private CastStringScalarNode castStringScalar = CastStringScalarNodeGen.create();
    @Child private ReadVariableNode readMTableFirst = ReadVariableNode.create(RRuntime.DOT_ALL_MTABLE, RType.Any, ReadKind.SilentLocal);
    @Child private ReadVariableNode readMTableSecond = ReadVariableNode.create(RRuntime.DOT_ALL_MTABLE, RType.Any, ReadKind.SilentLocal);
    @Child private ReadVariableNode readSigLength = ReadVariableNode.create(RRuntime.DOT_SIG_LENGTH, RType.Any, ReadKind.SilentLocal);
    @Child private ReadVariableNode readSigARgs = ReadVariableNode.create(RRuntime.DOT_SIG_ARGS, RType.Any, ReadKind.SilentLocal);
    @Child private ReadVariableNode getMethodsTableFind = ReadVariableNode.create(".getMethodsTable", RType.Function, ReadKind.Normal);
    @Child private DirectCallNode getMethodsTableCall;
    @Child private RArgumentsNode argsNode = RArgumentsNode.create();
    @Child private CastIntegerScalarNode castIntScalar = CastIntegerScalarNode.create();
    @Child private CollectGenericArgumentsNode collectArgumentsNode;
    @Child private DispatchGeneric dispatchGeneric = DispatchGenericNodeGen.create();
    @CompilationFinal private RFunction getMethodsTableFunction;
    private final ConditionProfile cached = ConditionProfile.createBinaryProfile();
    private final RCaller caller = RDataFactory.createCaller(this);
    private final BranchProfile initialize = BranchProfile.create();

    private final RAttributeProfiles attrProfiles = RAttributeProfiles.create();

    @Specialization(guards = "fVec.getLength() > 0")
    protected Object stdGeneric(VirtualFrame frame, RAbstractStringVector fVec, RFunction fdef) {
        controlVisibility();
        String fname = fVec.getDataAt(0);
        REnvironment methodsEnv = REnvironment.getRegisteredNamespace("methods");
        MaterializedFrame fnFrame = fdef.getEnclosingFrame();
        REnvironment mtable = (REnvironment) readMTableFirst.execute(null, fnFrame);
        if (mtable == null) {
            // sometimes this branch seems to be never taken (in particular when initializing a
            // freshly created object) which happens via a generic
            initialize.enter();
            if (getMethodsTableFunction == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getMethodsTableFunction = (RFunction) getMethodsTableFind.execute(null, methodsEnv.getFrame());
                getMethodsTableCall = insert(Truffle.getRuntime().createDirectCallNode(getMethodsTableFunction.getTarget()));
            }
            RFunction currentFunction = (RFunction) getMethodsTableFind.execute(null, methodsEnv.getFrame());
            if (cached.profile(currentFunction == getMethodsTableFunction)) {
                Object[] args = argsNode.execute(getMethodsTableFunction, caller, null, RArguments.getDepth(frame) + 1, new Object[]{fdef}, ArgumentsSignature.get("fdef"), null);
                getMethodsTableCall.call(frame, args);
            } else {
                // slow path
                RContext.getEngine().evalFunction(currentFunction, frame.materialize(), fdef);
            }
            // TODO: can we use a single ReadVariableNode for getting mtable?
            mtable = (REnvironment) readMTableSecond.execute(null, fnFrame);
        }
        RList sigArgs = (RList) readSigARgs.execute(null, fnFrame);
        int sigLength = castIntScalar.executeInt(readSigLength.execute(null, fnFrame));
        if (sigLength > sigArgs.getLength()) {
            throw RError.error(this, RError.Message.GENERIC, "'.SigArgs' is shorter than '.SigLength' says it should be");
        }
        if (collectArgumentsNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            collectArgumentsNode = insert(CollectGenericArgumentsNodeGen.create(sigArgs.getDataWithoutCopying(), sigLength));
        }

        String[] classes = collectArgumentsNode.execute(frame, sigArgs, sigLength);
        Object ret = dispatchGeneric.executeObject(frame, methodsEnv, mtable, RDataFactory.createStringVector(classes, RDataFactory.COMPLETE_VECTOR), fdef, fname);
        return ret;
    }

    @Specialization(guards = "fVec.getLength() > 0")
    protected Object stdGeneric(VirtualFrame frame, RAbstractStringVector fVec, @SuppressWarnings("unused") RMissing fdef) {
        String fname = fVec.getDataAt(0);
        int n = RArguments.getDepth(frame);
        if (genericAttrAccess == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            assert sysFunction == null;
            genericAttrAccess = insert(AttributeAccessNodeGen.create(RRuntime.GENERIC_ATTR_KEY));
            sysFunction = insert(FrameFunctionsFactory.SysFunctionNodeGen.create(new RNode[1], null, null));
        }
        // TODO: GNU R counts to (i < n) - does their equivalent of getDepth return a different
        // value
        // TODO; shouldn't we count from n to 0?
        for (int i = 0; i <= n; i++) {
            Object fnObj = sysFunction.executeObject(frame, i);
            if (fnObj != RNull.instance) {
                RFunction fn = (RFunction) fnObj;
                Object genObj = null;
                RAttributes attributes = fn.getAttributes();
                if (attributes == null) {
                    continue;
                }
                genObj = genericAttrAccess.execute(attributes);
                if (genObj == null) {
                    continue;
                }
                String gen = castStringScalar.executeString(genObj);
                if (gen.equals(fname)) {
                    return stdGeneric(frame, fVec, fn);
                }
            }
        }
        controlVisibility();
        throw RError.error(this, RError.Message.STD_GENERIC_WRONG_CALL, fname);
    }

    @Specialization
    protected Object stdGeneric(Object fVec, RAttributable fdef) {
        controlVisibility();
        if (!(fVec instanceof String || (fVec instanceof RAbstractStringVector && ((RAbstractStringVector) fVec).getLength() > 0))) {
            throw RError.error(this, RError.Message.GENERIC, "argument to 'standardGeneric' must be a non-empty character string");
        } else {
            RStringVector cl = fdef.getClassAttr(attrProfiles);
            // not a GNU R error message
            throw RError.error(this, RError.Message.EXPECTED_GENERIC, cl.getLength() == 0 ? RRuntime.STRING_NA : cl.getDataAt(0));
        }
    }

}
