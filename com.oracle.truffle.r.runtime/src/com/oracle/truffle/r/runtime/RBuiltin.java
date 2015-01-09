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
package com.oracle.truffle.r.runtime;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
public @interface RBuiltin {

    /**
     * The "kind" of the builtin.
     */
    RBuiltinKind kind();

    /**
     * The name of the builtin function in the R language.
     */
    String name();

    /**
     * The parameter names. No default, as, at a minimum, the number is required information.
     */
    String[] parameterNames();

    /**
     * A list of aliases for {@code name()}.
     */
    String[] aliases() default {};

    /**
     * Some primitives do not evaluate one or more of their arguments. This is either a list of
     * indices for the non-evaluated arguments (zero based) or {@code -1} to mean none are
     * evaluated. An empty array means all arguments are evaluated. N.B. The indices identify the
     * arguments in the order they appear in the specification, i.e., after the re-ordering of named
     * arguments. N.B. "..." is treated a single argument for this purpose and identified by its
     * index like any other arg.
     */
    int[] nonEvalArgs() default {};
}
