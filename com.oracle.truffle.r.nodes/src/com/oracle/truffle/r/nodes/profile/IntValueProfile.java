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
package com.oracle.truffle.r.nodes.profile;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public final class IntValueProfile {

    private static final byte UNINITIALIZED = 0;
    private static final byte SPECIALIZED = 1;
    private static final byte GENERIC = 2;

    @CompilationFinal private int cachedValue;
    @CompilationFinal private byte state = 0;

    private IntValueProfile() {
    }

    public int profile(int value) {
        if (state == GENERIC) {
            return value;
        } else if (state == SPECIALIZED && cachedValue == value) {
            return cachedValue;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (state == UNINITIALIZED) {
                this.cachedValue = value;
                this.state = SPECIALIZED;
            } else {
                this.state = GENERIC;
            }
            return value;
        }
    }

    public static IntValueProfile create() {
        return new IntValueProfile();
    }

}
