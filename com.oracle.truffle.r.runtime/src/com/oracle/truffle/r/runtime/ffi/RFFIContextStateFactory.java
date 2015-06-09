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
package com.oracle.truffle.r.runtime.ffi;

import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.RContext.*;

/**
 * This is the factory-independent class referenced by {@link RContext} that manages the
 * context-specific state for any given {@link RFFIFactory}. It simply forwards the calls to the
 * actual factory.
 */
public class RFFIContextStateFactory implements StateFactory {
    private static RFFIFactory theFactory;

    public static void registerFactory(RFFIFactory factory) {
        theFactory = factory;
    }

    public ContextState newContext(RContext context, Object... objects) {
        return theFactory.newContext(context, objects);
    }

    public void systemInitialized(RContext context, ContextState state) {
        theFactory.systemInitialized(context, state);
    }

    public void beforeDestroy(RContext context, ContextState state) {
        theFactory.beforeDestroy(context, state);
    }

}
