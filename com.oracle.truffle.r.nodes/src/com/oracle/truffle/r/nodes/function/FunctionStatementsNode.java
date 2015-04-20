/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.control.SequenceNode;
import com.oracle.truffle.r.runtime.env.REnvironment;

/**
 * Encapsulates the sequence of statements (expressions) of a function. Has no specific execute
 * behavior but is an important placeholder for debugging instrumentation.
 *
 * The {@link SourceSection} for a non-empty sequence is that of the sequence itself.
 */
public class FunctionStatementsNode extends SequenceNode {

    public FunctionStatementsNode(RNode[] sequence) {
        // TODO revisit what this variant is really for
        super(sequence);
    }

    public FunctionStatementsNode(RNode sequence) {
        super(sequence);
        assignSourceSection(sequence.getSourceSection());
    }

    @Override
    public RNode substitute(REnvironment env) {
        return new FunctionStatementsNode(super.substitute(env));
    }

}
