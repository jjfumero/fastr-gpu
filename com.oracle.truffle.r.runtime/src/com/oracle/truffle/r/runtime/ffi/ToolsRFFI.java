/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.ffi;

import com.oracle.truffle.r.runtime.conn.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.env.*;

/**
 * Interface to native (C) methods provided by the {@code tools} package.
 */
public interface ToolsRFFI {
    /**
     * This invokes the Rd parser, written in C, and part of GnuR, that does its work using the R
     * FFI interface. The R code initially invokes this via {@code .External2(C_parseRd, ...)},
     * which has a custom specialization in the implementation of the {@code .External2} buitin.
     * That does some work in Java, and then calls this method to invoke the actual C code. We can't
     * go straight to the GnuR C entry point as that makes GnuR-specific assumptions about, for
     * example, how connections are implemented.
     */
    Object parseRd(RConnection con, REnvironment srcfile, RLogicalVector verbose, RLogicalVector fragment, RStringVector basename, RLogicalVector warningCalls);
}
