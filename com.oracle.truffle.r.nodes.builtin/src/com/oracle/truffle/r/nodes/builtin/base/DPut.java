/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.r.runtime.RBuiltinKind.INTERNAL;

import java.io.IOException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RInvisibleBuiltinNode;
import com.oracle.truffle.r.runtime.RBuiltin;
import com.oracle.truffle.r.runtime.RDeparse;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.conn.RConnection;

/**
 * The {@code cat .Internal}.
 */
@RBuiltin(name = "dput", kind = INTERNAL, parameterNames = {"x", "file", "opts"})
public abstract class DPut extends RInvisibleBuiltinNode {

    @Override
    protected void createCasts(CastBuilder casts) {
        casts.firstIntegerWithError(2, RError.Message.WRONG_LENGTH_ARG, "opts");
    }

    @Specialization
    @TruffleBoundary
    public Object dput(Object x, RConnection file, int opts) {
        controlVisibility();

        String string = RDeparse.deparse1Line(x, false, RDeparse.DEFAULT_Cutoff, opts);
        try (RConnection openConn = file.forceOpen("wt")) {
            openConn.writeString(string, true);
        } catch (IOException ex) {
            throw RError.error(this, RError.Message.GENERIC, ex.getMessage());
        }
        return x;
    }
}
