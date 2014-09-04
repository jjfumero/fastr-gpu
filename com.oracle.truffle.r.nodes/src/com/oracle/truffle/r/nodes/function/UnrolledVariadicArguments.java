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

import java.util.*;

import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.runtime.data.RPromise.Closure;

/**
 * @author TODO Gero, add comment!
 *
 */
public class UnrolledVariadicArguments extends Arguments<RNode> implements UnmatchedArguments {

    private final Map<RNode, Closure> closureCache;

    private UnrolledVariadicArguments(RNode[] arguments, String[] names, ClosureCache closureCache) {
        super(arguments, names);
        this.closureCache = closureCache.getContent();

    }

    public static UnrolledVariadicArguments create(RNode[] arguments, String[] names, ClosureCache closureCache) {
        return new UnrolledVariadicArguments(arguments, names, closureCache);
    }

    @Override
    public RNode[] getArguments() {
        return arguments;
    }

    @Override
    public String[] getNames() {
        return names;
    }

    @Override
    public Map<RNode, Closure> getContent() {
        return closureCache;
    }
}
