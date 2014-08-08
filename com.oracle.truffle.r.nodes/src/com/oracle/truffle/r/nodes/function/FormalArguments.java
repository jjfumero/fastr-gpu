/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.access.ConstantNode.ConstantMissingNode;

/**
 * This class denotes a list of formal arguments which consist of the tuple
 * <ul>
 * <li>argument name (String, {@link #getNames()})</li>
 * <li>expression ({@link RNode}, {@link #getDefaultArgs()})</li>
 * </ul>
 * The order is always the one defined by the function definition.
 */
public final class FormalArguments extends Arguments<RNode> {

    public static final FormalArguments NO_ARGS = new FormalArguments(new String[0], new RNode[0]);

    /**
     * Serves as cache for {@link #hasVarArgs()}/{@link #getVarArgIndex()}.
     */
    private final int varArgsIndex;

    /**
     * @param argumentsNames {@link #getNames()}
     * @param defaultArguments {@link #getDefaultArgs()}
     */
    private FormalArguments(String[] argumentsNames, RNode[] defaultArguments) {
        super(defaultArguments, argumentsNames);
        this.varArgsIndex = super.getVarArgIndex();
    }

    /**
     * @param argumentsNames {@link #getNames()}
     * @param defaultArguments {@link #getDefaultArgs()}, but handles <code>null</code>
     * @return A fresh {@link FormalArguments}
     */
    public static FormalArguments create(String[] argumentsNames, RNode[] defaultArguments) {
        RNode[] newDefaults = new RNode[defaultArguments.length];
        for (int i = 0; i < newDefaults.length; i++) {
            RNode defArg = defaultArguments[i];
            newDefaults[i] = defArg instanceof ConstantMissingNode ? null : defArg;
        }
        return new FormalArguments(argumentsNames, newDefaults);
    }

    @Override
    public int getVarArgIndex() {
        return varArgsIndex;
    }

    /**
     * @return The list of argument names a function definition specifies
     */
    public String[] getNames() {
        return names;
    }

    /**
     * @return The list of default arguments a function body specifies. 'No default value' is
     *         denoted by <code>null</code>
     */
    public RNode[] getDefaultArgs() {
        return arguments;
    }

    /**
     * This works as a direct accessor to on of the {@link #getDefaultArgs()}.
     *
     * @param index
     * @return The default arguments for the given index, or <code>null</code> if there is none.
     */
    public RNode getDefaultArg(int index) {
        assert index >= 0 && index < arguments.length;
        return arguments[index];
    }

    /**
     * @return The length of the argument array
     */
    public int getArgsCount() {
        return arguments.length;
    }
}
