/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.library.gpu.options;

/**
 * ASTx runtime options.
 *
 */
public class ASTxOptions {

    /**
     * Print internal results. Debugging purposes
     */
    public static boolean printResult = false;

    /**
     * Execute the parallel operations with Marawacc.
     */
    public static boolean runMarawaccThreads = getBoolean("-Dastx.marawacc.threads", true);

    /**
     * Use Java futures for async computation when pattern composition is presented.
     */
    public static boolean useAsyncComputation = true;

    /**
     * Dont clean the cache for async functions. It uses the result stored in the array of Futures.
     */
    public static boolean useAsyncMemoisation = true;

    /**
     * Print internal messages such as deoptimizations, data types and so on for debugging.
     */
    public static boolean debug = true;

    /**
     * Print information related to the cache system.
     */
    public static boolean debugCache = false;

    private static boolean getBoolean(String property, boolean defaultValue) {
        if (System.getProperty(property) == null) {
            return defaultValue;
        } else if (System.getProperty(property).toLowerCase().equals("true")) {
            return true;
        } else if (System.getProperty(property).toLowerCase().equals("false")) {
            return false;
        }
        return defaultValue;
    }

}
