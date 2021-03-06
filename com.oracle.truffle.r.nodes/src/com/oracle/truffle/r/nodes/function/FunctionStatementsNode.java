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
package com.oracle.truffle.r.nodes.function;

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.nodes.control.BlockNode;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

/**
 * Encapsulates the sequence of statements (expressions) of a function, i.e. the function body. Has
 * no specific execute behavior but is an important placeholder for debugging instrumentation as it
 * is the place in the AST where a debugger wants to stop on entry.
 *
 */
public class FunctionStatementsNode extends BlockNode {

    public FunctionStatementsNode() {
        super(null, BlockNode.EMPTY_BLOCK);
    }

    public FunctionStatementsNode(SourceSection src, RSyntaxNode sequence) {
        super(src, sequence);
    }

    @Override
    public RSyntaxNode substituteImpl(REnvironment env) {
        return new FunctionStatementsNode(null, super.substitute(env));
    }

}
