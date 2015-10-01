/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.data;

import com.oracle.truffle.r.runtime.nodes.*;

/**
 * This interface can be used to provide a fast path, implemented in Java, for an R function. This
 * may be useful for cases in which there is a significantly simpler implementation for a known
 * configuration of arguments. Returning {@code null} from the fast path node will revert the call
 * site so that it calls the normal R code again.
 */
@FunctionalInterface
public interface FastPathFactory {

    FastPathFactory EVALUATE_ARGS = () -> null;

    FastPathFactory FORCED_EAGER_ARGS = new FastPathFactory() {

        public RFastPathNode create() {
            return null;
        }

        public boolean evaluatesArgument(int index) {
            return false;
        }

        public boolean forcedEagerPromise(int index) {
            return true;
        }
    };

    RFastPathNode create();

    default boolean evaluatesArgument(@SuppressWarnings("unused") int index) {
        return true;
    }

    default boolean forcedEagerPromise(@SuppressWarnings("unused") int index) {
        return false;
    }
}
