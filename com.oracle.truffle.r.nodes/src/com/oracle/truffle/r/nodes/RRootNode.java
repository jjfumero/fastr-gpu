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
package com.oracle.truffle.r.nodes;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.function.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.env.frame.*;

/**
 * The base class for R code that can be executed, namely {@link FunctionDefinitionNode} and
 * {@link RBuiltinNode}.
 */
public abstract class RRootNode extends RootNode implements HasSignature {

    @CompilationFinal protected boolean checkSingletonFrame = true;
    private final ValueProfile functionProfile = ValueProfile.createIdentityProfile();
    private final ValueProfile irregularFrameProfile = ValueProfile.createPrimitiveProfile();

    /**
     * The formal arguments this function is supposed to take.
     */
    private final FormalArguments formalArguments;

    protected RRootNode(SourceSection src, FormalArguments formalArguments, FrameDescriptor frameDescriptor) {
        super(src, frameDescriptor);
        this.formalArguments = formalArguments;
    }

    protected void verifyEnclosingAssumptions(VirtualFrame vf) {
        RArguments.setFunction(vf, functionProfile.profile(RArguments.getFunction(vf)));
        RArguments.setIsIrregular(vf, irregularFrameProfile.profile(RArguments.getIsIrregular(vf)));

        if (checkSingletonFrame) {
            checkSingletonFrame = FrameSlotChangeMonitor.checkSingletonFrame(vf);
        }
    }

    /**
     * @return The number of parameters this functions expects
     */
    public int getParameterCount() {
        return formalArguments.getSignature().getLength();
    }

    /**
     * @return {@link #formalArguments}
     */
    public FormalArguments getFormalArguments() {
        return formalArguments;
    }

    public ArgumentsSignature getSignature() {
        return formalArguments.getSignature();
    }

    public boolean needsSplitting() {
        return false;
    }

    @TruffleBoundary
    public String getSourceCode() {
        SourceSection ss = getSourceSection();
        if (ss != null) {
            return ss.getCode();
        } else {
            return null;
        }
    }

    @Override
    public boolean isCloningAllowed() {
        return true;
    }
}
