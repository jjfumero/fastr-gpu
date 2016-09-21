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

import static com.oracle.truffle.r.runtime.RBuiltinKind.INTERNAL;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.NoSuchFileException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.utilities.ConditionProfile;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RBuiltin;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractStringVector;

@RBuiltin(name = "normalizePath", kind = INTERNAL, parameterNames = {"path", "winslash", "mustwork"})
public abstract class NormalizePath extends RBuiltinNode {

    private final ConditionProfile doesNotNeedToWork = ConditionProfile.createBinaryProfile();

    @TruffleBoundary
    @Specialization
    protected RStringVector doNormalizePath(RAbstractStringVector pathVec, @SuppressWarnings("unused") String winslash, byte mustWork) {
        controlVisibility();
        String[] results = new String[pathVec.getLength()];
        FileSystem fileSystem = FileSystems.getDefault();
        for (int i = 0; i < results.length; i++) {
            String path = pathVec.getDataAt(i);
            String expandPath = Utils.tildeExpand(path);
            String normPath = expandPath;
            try {
                normPath = fileSystem.getPath(normPath).toRealPath().toString();
            } catch (IOException e) {
                if (doesNotNeedToWork.profile(mustWork == RRuntime.LOGICAL_FALSE)) {
                    // no error or warning
                } else {
                    Object[] errorArgs;
                    Message msg;
                    if (e instanceof NoSuchFileException) {
                        errorArgs = new Object[]{i + 1, expandPath};
                        msg = Message.NORMALIZE_PATH_NOSUCH;
                    } else {
                        errorArgs = new Object[]{e.toString()};
                        msg = Message.GENERIC;
                    }
                    if (mustWork == RRuntime.LOGICAL_TRUE) {
                        throw RError.error(this, msg, errorArgs);
                    } else {
                        // NA means warning
                        RError.warning(this, msg, errorArgs);
                    }
                }
            }
            results[i] = normPath;
        }
        return RDataFactory.createStringVector(results, RDataFactory.COMPLETE_VECTOR);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected Object doNormalizePath(Object path, Object winslash, Object mustWork) {
        controlVisibility();
        throw RError.error(this, RError.Message.WRONG_TYPE);
    }
}
