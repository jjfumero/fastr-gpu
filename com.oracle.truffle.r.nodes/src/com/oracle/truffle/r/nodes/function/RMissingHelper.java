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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.access.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.RPromise.*;

/**
 * This class implements the behavior for {@link RMissing} which is needed inside this module, as it
 * would induce unnecessary dependencies otherwise.
 */
public class RMissingHelper {

    /**
     * <code>true</code> if value == {@link RMissing#instance} OR value is an
     * {@link RArgsValuesAndNames#isEmpty()} {@link RArgsValuesAndNames} (in case of "...").
     *
     * @param value
     * @return Whether the given value represents an argument that has not been provided.
     */
    public static boolean isMissing(Object value) {
        return value == RMissing.instance || (value instanceof RArgsValuesAndNames) && ((RArgsValuesAndNames) value).isMissing();
    }

    /**
     * This method determines whether a given name is missing in the given frame. This is used to
     * determine whether an argument has to be replaced by its default value or not. In case the
     * given name is associated with a promise, {@link #isMissingName(RPromise)} is called.
     *
     * @param frame The frame in which to decide whether value is missing or not
     * @param name The name to check
     * @return See {@link #isMissingName(RPromise)}
     */
    @TruffleBoundary
    public static boolean isMissingArgument(Frame frame, String name) {
        // Check symbols value
        Object value = getMissingValue(frame, name);
        if (isMissing(value)) {
            return true;
        }

        // Check for Promise
        if (value instanceof RPromise) {
            RPromise promise = (RPromise) value;
            return isMissingName(promise);    // promise.isDefaulted() ||
        }

        return false;
    }

    /**
     * @param arg The {@link RNode}, expected to be a {@link ReadVariableNode} (possibly wrapped
     *            into an {@link WrapArgumentNode})
     * @return The name, if any ({@code null} else)
     */
    public static String unwrapName(RNode arg) {
        RNode rvnArg = arg;
        if (rvnArg instanceof WrapArgumentNode) {
            rvnArg = ((WrapArgumentNode) rvnArg).getOperand();
        }

        // ReadVariableNode denotes a name
        if (rvnArg instanceof ReadVariableNode) {
            return ((ReadVariableNode) rvnArg).getName();
        }
        return null;
    }

    /**
     * @param frame The frame to read name in
     * @param name The name to read
     * @return The value for the given name in the given frame. {@code null} if name is not bound or
     *         type is not object.
     */
    public static Object getMissingValue(Frame frame, String name) {
        // Check binding
        FrameSlot frameSlot = frame.getFrameDescriptor().findFrameSlot(name);
        if (frameSlot == null) {
            return null;
        }

        // Read name's value
        try {
            return frame.getObject(frameSlot);
        } catch (FrameSlotTypeException e) {
            return null;
        }
    }

    /**
     * @param promise The {@link RPromise} which is checked whether it contains a
     *            {@link #isMissingArgument(Frame, String)}.
     * @return Whether the given {@link RPromise} represents a name that is 'missing' in its frame
     */
    @TruffleBoundary
    public static boolean isMissingName(RPromise promise) {
        boolean result = false;
        // Missing RPromises throw an error on evaluation, so this might only be checked if it has
        // not been evaluated yet.
        if (!promise.isEvaluated()) {
            Object exprObj = promise.getRep();

            // Unfold WrapArgumentNode
            if (exprObj instanceof WrapArgumentNode) {
                exprObj = ((WrapArgumentNode) exprObj).getOperand();
            }

            // Check for ReadVariableNode
            if (exprObj instanceof ReadVariableNode) {
                ReadVariableNode rvn = (ReadVariableNode) exprObj;

                // Check: If there is a cycle, return true. (This is done like in GNU R)
                if (promise.isUnderEvaluation()) {
                    return true;
                }

                try {
                    // TODO Profile necessary here???
                    if (promise instanceof EagerPromise) {
                        ((EagerPromise) promise).materialize();
                    }
                    // promise.materialize(globalMissingPromiseProfile);
                    promise.setUnderEvaluation(true);
                    result = isMissingArgument(promise.getFrame(), rvn.getName());
                } finally {
                    promise.setUnderEvaluation(false);
                }
            }
        }
        return result;
    }
}
