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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.access.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;

/**
 * A {@link ArgumentStatePush} is used to bump up state transition for function arguments.
 */
@NodeChild(value = "op")
public abstract class ArgumentStatePush extends RNode {

    public abstract Object executeObject(VirtualFrame frame, Object shareable);

    private final BranchProfile everSeenShared = BranchProfile.create();
    private final BranchProfile everSeenTemporary = BranchProfile.create();
    private final BranchProfile everSeenNonTemporary = BranchProfile.create();

    private final int index;
    @CompilationFinal private int mask = 0;
    @Child WriteLocalFrameVariableNode writeArgNode;

    public static final int MAX_COUNTED_ARGS = 8;
    public static final int[] INVALID_TRANS_ARGS = new int[0];
    public static final int INVALID_INDEX = -1;

    public ArgumentStatePush(int index) {
        this.index = index;
    }

    public boolean refCounted() {
        return mask > 0;
    }

    private void transitionStateExp(VirtualFrame frame, RShareable shareable) {
        shareable.incRefCount();
        if (!FastROptions.RefCountIncrementOnly) {
            if (mask == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                RFunction fun = RArguments.getFunction(frame);
                if (fun == null) {
                    mask = -1;
                    return;
                }
                Object root = fun.getRootNode();
                if (!(root instanceof FunctionDefinitionNode)) {
                    // root is RBuiltinRootNode
                    mask = -1;
                    return;
                }
                FunctionDefinitionNode fdn = (FunctionDefinitionNode) root;
                if (fdn.getArgPostProcess() == null) {
                    // arguments to this function are not to be reference counted
                    mask = -1;
                    return;
                }
                if (index >= Math.min(fdn.getArgPostProcess().getLength(), MAX_COUNTED_ARGS)) {
                    mask = -1;
                    return;
                }
                mask = 1 << index;
                int transArgsBitSet = fdn.getArgPostProcess().transArgsBitSet;
                fdn.getArgPostProcess().transArgsBitSet = transArgsBitSet | mask;
                writeArgNode = insert(WriteLocalFrameVariableNode.createForRefCount(Integer.valueOf(mask)));
            }
            if (mask != -1) {
                writeArgNode.execute(frame, shareable);
            }
        }
    }

    @Specialization
    public RNull transitionState(VirtualFrame frame, RShareable shareable) {
        if (FastROptions.NewStateTransition) {
            transitionStateExp(frame, shareable);
        } else {
            if (shareable.isShared()) {
                everSeenShared.enter();
            } else if (shareable.isTemporary()) {
                everSeenTemporary.enter();
                shareable.markNonTemporary();
            } else {
                everSeenNonTemporary.enter();
                shareable.makeShared();
            }
        }
        return RNull.instance;
    }

    @Specialization(guards = "!isShareable(o)")
    public RNull transitionStateNonShareable(VirtualFrame frame, @SuppressWarnings("unused") Object o) {
        if (mask > 0) {
            // this argument used to be reference counted but is no longer
            CompilerDirectives.transferToInterpreterAndInvalidate();
            RFunction fun = RArguments.getFunction(frame);
            assert fun != null;
            FunctionDefinitionNode fdn = (FunctionDefinitionNode) fun.getRootNode();
            assert fdn != null;
            int transArgsBitSet = fdn.getArgPostProcess().transArgsBitSet;
            fdn.getArgPostProcess().transArgsBitSet = transArgsBitSet & (~mask);
            mask = -1;
        }
        return RNull.instance;
    }

    protected boolean isShareable(Object o) {
        return o instanceof RShareable;
    }

    public static void transitionStateSlowPath(Object o) {
        // this is expected to be used in rare cases where no RNode is easily available so we don't
        // bother with the reference count
        if (o instanceof RShareable) {
            RShareable shareable = (RShareable) o;
            if (shareable.isTemporary()) {
                shareable.markNonTemporary();
            } else if (!shareable.isShared()) {
                shareable.makeShared();
            }
        }
    }

}
