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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.runtime.RBuiltinKind.*;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.model.*;

@RBuiltin(name = "vector", kind = INTERNAL, parameterNames = {"mode", "length"})
public abstract class Vector extends RBuiltinNode {

    private static final String CACHED_MODES_LIMIT = "3";

    @Override
    protected void createCasts(CastBuilder casts) {
        casts.convertToInteger(1);
    }

    protected RType modeToType(String mode) {
        RType type = RType.fromMode(mode);
        if (!type.isVector()) {
            throw RError.error(this, RError.Message.CANNOT_MAKE_VECTOR_OF_MODE, mode);
        }
        return type;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"mode == cachedMode"}, limit = CACHED_MODES_LIMIT)
    RAbstractVector vectorCached(String mode, int length, @Cached("mode") String cachedMode, @Cached("modeToType(mode)") RType type) {
        controlVisibility();
        return type.create(length, false);
    }

    @Specialization(contains = "vectorCached")
    @TruffleBoundary
    protected RAbstractVector vector(String mode, int length) {
        controlVisibility();
        return modeToType(mode).create(length, false);
    }
}
