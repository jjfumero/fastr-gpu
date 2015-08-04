/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.r.runtime.*;

/**
 * This node reifies a runtime object into the AST by creating nodes for frequently encountered
 * values. This can be used to bridge the gap between code as runtime data and executed code.
 */
public abstract class CallInlineCacheNode extends BaseRNode {

    protected static final int CACHE_LIMIT = 2;

    public abstract Object execute(VirtualFrame frame, CallTarget target, Object[] arguments);

    protected static DirectCallNode createDirectCallNode(CallTarget target) {
        return Truffle.getRuntime().createDirectCallNode(target);
    }

    @Specialization(guards = "target == callNode.getCallTarget()", limit = "CACHE_LIMIT")
    protected Object call(VirtualFrame frame, @SuppressWarnings("unused") CallTarget target, Object[] arguments, @Cached("createDirectCallNode(target)") DirectCallNode callNode) {
        return callNode.call(frame, arguments);
    }

    protected static IndirectCallNode createIndirectCallNode() {
        return Truffle.getRuntime().createIndirectCallNode();
    }

    @Specialization
    protected Object call(VirtualFrame frame, CallTarget target, Object[] arguments, @Cached("createIndirectCallNode()") IndirectCallNode callNode) {
        return callNode.call(frame, target, arguments);
    }

}
