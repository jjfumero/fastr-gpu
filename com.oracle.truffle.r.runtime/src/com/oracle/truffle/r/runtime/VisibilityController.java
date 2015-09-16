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
package com.oracle.truffle.r.runtime;

import com.oracle.truffle.r.runtime.context.*;

/**
 * This interface must be implemented by all nodes in the FastR implementation that control the
 * visibility of results in the shell. All specializations or, if no specializations exist,
 * {@code execute()} methods, of a node implementing this interface must call the
 * {@link #controlVisibility()} method as defined in this interface.
 *
 * N.B. printing visibility is independent from 'interactive'.
 */
public interface VisibilityController {

    /**
     * Return the visibility value the global visibility flag in {@link RContext} should be set to.
     * The default is {@code true}, so only nodes that force invisibility need to override this
     * method.
     */
    default boolean getVisibility() {
        return true;
    }

    /**
     * Set the global visibility flag according to the visibility property of the node implementing
     * this interface. There is no need to ever override this method, but all node classes
     * implementing the {@link VisibilityController} interface must call this method in each of
     * their specializations or {@code execute()} methods.
     *
     * This call is not necessary in specializations or {@code execute()} methods that are
     * guaranteed to always replace and execute, as is common in "unresolved" node implementations.
     */
    default void controlVisibility() {
        if (!FastROptions.IgnoreVisibility) {
            RContext.getInstance().setVisible(getVisibility());
        }
    }

    /**
     * Force the visibility to {@code state}. Useful for builtins where the visibility depends on
     * the result, e.g. {@code switch}.
     */
    default void forceVisibility(boolean state) {
        if (!FastROptions.IgnoreVisibility) {
            RContext.getInstance().setVisible(state);
        }
    }
}
