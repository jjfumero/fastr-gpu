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
package com.oracle.truffle.r.nodes.control;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.runtime.RDeparse;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RSerialize;
import com.oracle.truffle.r.runtime.VisibilityController;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

public final class BreakNode extends RNode implements RSyntaxNode, VisibilityController {

    public BreakNode(SourceSection src) {
        assignSourceSection(src);
    }

    @Override
    public void deparseImpl(RDeparse.State state) {
        state.startNodeDeparse(this);
        state.append("break");
        state.endNodeDeparse(this);
    }

    @Override
    public void serializeImpl(RSerialize.State state) {
        state.setAsBuiltin("break");
    }

    @Override
    public Object execute(VirtualFrame frame) {
        forceVisibility(false);
        throw BreakException.instance;
    }

    public RSyntaxNode substituteImpl(REnvironment env) {
        return this;
    }

    public int getRlengthImpl() {
        return 1;
    }

    @Override
    public Object getRelementImpl(int index) {
        return RDataFactory.createSymbol("break");
    }

    @Override
    public boolean getRequalsImpl(RSyntaxNode other) {
        throw RInternalError.unimplemented();
    }
}
