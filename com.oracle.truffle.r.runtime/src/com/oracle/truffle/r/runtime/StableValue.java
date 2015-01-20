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

import com.oracle.truffle.api.*;

public final class StableValue<T> {

    public static final StableValue<?> INVALIDATED = new StableValue<>();

    /**
     * @return A singleton stable value instance that is invalidated.
     */
    @SuppressWarnings("unchecked")
    public static <T> StableValue<T> invalidated() {
        return (StableValue<T>) INVALIDATED;
    }

    private final T value;
    private final Assumption assumption;

    public StableValue(T value, String name) {
        this.value = value;
        this.assumption = Truffle.getRuntime().createAssumption(name);
    }

    private StableValue() {
        this.value = null;
        this.assumption = Truffle.getRuntime().createAssumption("invalidated singleton");
        this.assumption.invalidate();
    }

    public T getValue() {
        return value;
    }

    public Assumption getAssumption() {
        return assumption;
    }

    @Override
    public String toString() {
        return "[" + value + ", " + assumption + "]";
    }
}
