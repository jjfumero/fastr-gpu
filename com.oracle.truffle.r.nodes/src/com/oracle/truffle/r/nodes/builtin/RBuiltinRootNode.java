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
package com.oracle.truffle.r.nodes.builtin;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.function.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.RDeparse.State;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.nodes.*;

public final class RBuiltinRootNode extends RRootNode implements RSyntaxNode {

    @Child private RBuiltinNode builtin;

    public RBuiltinRootNode(RBuiltinNode builtin, FormalArguments formalArguments, FrameDescriptor frameDescriptor) {
        super(createSourceSection(builtin, formalArguments), formalArguments, frameDescriptor);
        this.builtin = builtin;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        verifyEnclosingAssumptions(frame);
        try {
            return builtin.execute(frame);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | AssertionError e) {
            CompilerDirectives.transferToInterpreter();
            throw new RInternalError(e, "internal error");
        }
    }

    public RBuiltinNode getBuiltin() {
        return builtin;
    }

    @Override
    public boolean needsSplitting() {
        return builtin.getBuiltin().isAlwaysSplit();
    }

    public RBuiltinNode inline(ArgumentsSignature signature, RNode[] args, SourceSection callSrc) {
        assert builtin.getSuppliedSignature() != null : this;
        return builtin.inline(signature, args, callSrc);
    }

    public Object getDefaultParameterValue(int index) {
        Object[] values = builtin.getDefaultParameterValues();
        return index < values.length ? values[index] : null;
    }

    @Override
    public String getSourceCode() {
        throw RInternalError.shouldNotReachHere();
    }

    @Override
    public String toString() {
        return "RBuiltin(" + builtin + ")";
    }

    public void deparseImpl(State state) {
        state.startNodeDeparse(this);
        state.append(deparseImplHelper(this.getBuiltin(), this.getFormalArguments()));
        state.endNodeDeparse(this);
    }

    private static String deparseImplHelper(RBuiltinNode builtin, @SuppressWarnings("unused") FormalArguments formalArguments) {
        // TODO we ignore formals for now, as they are typically null
        return "function() .Primitive(\"" + builtin.getBuiltin().getName() + "\")";
    }

    private static SourceSection createSourceSection(RBuiltinNode builtin, FormalArguments formalArguments) {
        String s = deparseImplHelper(builtin, formalArguments);
        Source callSource = Source.fromText(s, builtin.getBuiltin().getName());
        return callSource.createSection("", 0, s.length());
    }

    public RSyntaxNode substituteImpl(REnvironment env) {
        throw RInternalError.shouldNotReachHere();
    }

    public void serializeImpl(com.oracle.truffle.r.runtime.RSerialize.State state) {
        throw RInternalError.shouldNotReachHere();
    }

    public int getRlengthImpl() {
        throw RInternalError.unimplemented();
    }

    @Override
    public Object getRelementImpl(int index) {
        throw RInternalError.unimplemented();
    }

    @Override
    public boolean getRequalsImpl(RSyntaxNode other) {
        throw RInternalError.unimplemented();
    }
}
