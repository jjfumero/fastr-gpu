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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.utilities.ConditionProfile;
import com.oracle.truffle.r.nodes.access.ConstantNode;
import com.oracle.truffle.r.nodes.access.UpdateSlotNode;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode.ReadKind;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.function.ClassHierarchyNode;
import com.oracle.truffle.r.nodes.function.ClassHierarchyNodeGen;
import com.oracle.truffle.r.nodes.function.RCallNode;
import com.oracle.truffle.r.nodes.function.WrapArgumentNode;
import com.oracle.truffle.r.nodes.function.signature.RArgumentsNode;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RAttributable;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.RSymbol;

@RBuiltin(name = "@<-", kind = RBuiltinKind.PRIMITIVE, parameterNames = {"", "", ""}, nonEvalArgs = 1)
public abstract class UpdateSlot extends RBuiltinNode {

    @CompilationFinal RFunction checkSlotAssignFunction;
    @Child private ClassHierarchyNode objClassHierarchy;
    @Child private ClassHierarchyNode valClassHierarchy;
    @Child UpdateSlotNode updateSlotNode = com.oracle.truffle.r.nodes.access.UpdateSlotNodeGen.create(null, null, null);
    @Child ReadVariableNode checkAtAssignmentFind = ReadVariableNode.create("checkAtAssignment", RType.Function, ReadKind.Normal);
    @Child DirectCallNode checkAtAssignmentCall;
    @Child private RArgumentsNode argsNode = RArgumentsNode.create();
    private final ConditionProfile cached = ConditionProfile.createBinaryProfile();
    private final RCaller caller = RDataFactory.createCaller(this);

    @Override
    protected void createCasts(CastBuilder casts) {
        casts.toAttributable(0, true, true, true);
    }

    protected String getName(Object nameObj) {
        if (nameObj instanceof RPromise) {
            Object rep = ((RPromise) nameObj).getRep();
            if (rep instanceof WrapArgumentNode) {
                rep = ((WrapArgumentNode) rep).getOperand();
            }
            if (rep instanceof ConstantNode) {
                Object val = ((ConstantNode) rep).getValue();
                if (val instanceof String) {
                    return (String) val;
                }
                if (val instanceof RSymbol) {
                    return ((RSymbol) val).getName();
                }
            } else if (rep instanceof ReadVariableNode) {
                return ((ReadVariableNode) rep).getIdentifier();
            } else if (rep instanceof RCallNode) {
                throw RError.error(this, RError.Message.SLOT_INVALID_TYPE, "language");
            }
        }
        // TODO: this is not quite correct, but I wonder if we even reach here (can also be
        // augmented on demand)
        throw RError.error(this, RError.Message.SLOT_INVALID_TYPE, nameObj.getClass().toString());
    }

    private void checkSlotAssign(VirtualFrame frame, RAttributable object, String name, Object value) {
        // TODO: optimize using a mechanism similar to overrides?
        if (checkSlotAssignFunction == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            checkSlotAssignFunction = (RFunction) checkAtAssignmentFind.execute(frame);
            checkAtAssignmentCall = Truffle.getRuntime().createDirectCallNode(checkSlotAssignFunction.getTarget());
            assert objClassHierarchy == null && valClassHierarchy == null;
            objClassHierarchy = insert(ClassHierarchyNodeGen.create(true));
            valClassHierarchy = insert(ClassHierarchyNodeGen.create(true));

        }
        RStringVector objClass = objClassHierarchy.execute(object);
        RStringVector valClass = objClassHierarchy.execute(value);
        RFunction currentFunction = (RFunction) checkAtAssignmentFind.execute(frame);
        if (cached.profile(currentFunction == checkSlotAssignFunction)) {
            Object[] args = argsNode.execute(checkSlotAssignFunction, caller, null, RArguments.getDepth(frame) + 1, new Object[]{objClass, name, valClass},
                            ArgumentsSignature.get("cl", "name", "valueClass"), null);
            checkAtAssignmentCall.call(frame, args);
        } else {
            // slow path
            RContext.getEngine().evalFunction(checkSlotAssignFunction, frame.materialize(), objClass, name, valClass);
        }
    }

    @Specialization
    protected Object updateSlot(VirtualFrame frame, RAttributable object, Object nameObj, Object value) {
        String name = getName(nameObj);
        checkSlotAssign(frame, object, name, value);
        return updateSlotNode.executeUpdate(object, name, value);
    }

}
