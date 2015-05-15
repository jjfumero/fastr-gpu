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

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.instrument.*;
import com.oracle.truffle.r.nodes.instrument.wrappers.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.RDeparse.*;

/**
 * The "syntax" variant corresponding to {@code x <<- y} in the source.
 */
@NodeInfo(cost = NodeCost.NONE)
@NeedsWrapper
public class WriteSuperVariableNode extends WriteVariableNodeSyntaxHelper implements RSyntaxNode, VisibilityController {

    @Child WriteVariableNode writeSuperFrameVariableNode;

    public static WriteSuperVariableNode create(SourceSection src, String name, RNode rhs) {
        WriteSuperVariableNode result = new WriteSuperVariableNode();
        result.assignSourceSection(src);
        result.writeSuperFrameVariableNode = result.insert(WriteSuperFrameVariableNode.create(name, rhs, Mode.REGULAR));
        return result;
    }

    @Override
    @NeedsWrapper
    public String getName() {
        return writeSuperFrameVariableNode.getName();
    }

    @Override
    @NeedsWrapper
    public RNode getRhs() {
        return writeSuperFrameVariableNode.getRhs();
    }

    @Override
    @NeedsWrapper
    public Object execute(VirtualFrame frame) {
        Object result = writeSuperFrameVariableNode.execute(frame);
        forceVisibility(false);
        return result;
    }

    @Override
    @NeedsWrapper
    public void execute(VirtualFrame frame, Object value) {
        writeSuperFrameVariableNode.execute(frame, value);
    }

    @Override
    public void deparse(State state) {
        deparseHelper(state, " <<- ");
    }

    @Override
    public void serialize(RSerialize.State state) {
        serializeHelper(state, "<<-");
    }

    @Override
    public ProbeNode.WrapperNode createWrapperNode() {
        return new WriteSuperVariableNodeWrapper(this);
    }

}
