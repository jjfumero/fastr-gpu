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
package com.oracle.truffle.r.runtime.ops.na;

import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;

/**
 * This helper class provides automatically profiled NA checks. The checks themselves are delegated
 * to {@link RRuntime}.
 */
public final class NAProfile {

    private final ConditionProfile profile = ConditionProfile.createBinaryProfile();

    public static NAProfile create() {
        return new NAProfile();
    }

    public boolean isNA(double value) {
        return profile.profile(RRuntime.isNA(value));
    }

    public boolean isNA(RComplex value) {
        return profile.profile(RRuntime.isNA(value));
    }

    public boolean isNA(int value) {
        return profile.profile(RRuntime.isNA(value));
    }

    public boolean isNA(String value) {
        return profile.profile(RRuntime.isNA(value));
    }

    public boolean isNA(byte value) {
        return profile.profile(RRuntime.isNA(value));
    }

    public void ifNa(double value, Runnable runnable) {
        if (isNA(value)) {
            runnable.run();
        }
    }

    public void ifNa(RComplex value, Runnable runnable) {
        if (isNA(value)) {
            runnable.run();
        }
    }

    public void ifNa(int value, Runnable runnable) {
        if (isNA(value)) {
            runnable.run();
        }
    }

    public void ifNa(String value, Runnable runnable) {
        if (isNA(value)) {
            runnable.run();
        }
    }

    public void ifNa(byte value, Runnable runnable) {
        if (isNA(value)) {
            runnable.run();
        }
    }
}
