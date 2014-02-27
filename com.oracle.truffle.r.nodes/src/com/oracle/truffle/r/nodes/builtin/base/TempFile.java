/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.ffi.*;

@RBuiltin(".Internal.tempfile")
public abstract class TempFile extends RBuiltinNode {

    private static Random rand = new Random();

    private static final String RANDOM_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final int RANDOM_CHARACTERS_LENGTH = RANDOM_CHARACTERS.length();
    private static final int RANDOM_LENGTH = 12; // as per GnuR
    private static final String INVALID_PATTERN = mkErrorMsg("filename");
    private static final String INVALID_TEMPDIR = mkErrorMsg("tempdir");
    private static final String INVALID_FILEEXT = mkErrorMsg("file extension");

    private static String mkErrorMsg(String msg) {
        return "invalid '" + msg + "'";
    }

    @Specialization(order = 0, guards = "tempDirL1")
    public RStringVector tempfile(String pattern, RStringVector tempDir, String fileExt) {
        return RDataFactory.createStringVector(createFile(pattern, tempDir.getDataAt(0), fileExt));
    }

    @SuppressWarnings("unused")
    public static boolean tempDirL1(String pattern, RStringVector tempDir, String fileExt) {
        return tempDir.getLength() == 1;
    }

    @Generic
    public RStringVector tempfileGeneric(@SuppressWarnings("unused") Object pattern, Object tempDir, Object fileExt) throws RError {
        RStringVector patternVec = checkVector(pattern, INVALID_PATTERN);
        RStringVector tempDirVec = checkVector(tempDir, INVALID_TEMPDIR);
        RStringVector fileExtVec = checkVector(fileExt, INVALID_FILEEXT);
        // Now we have RStringVectors of at least length 1
        return null;
    }

    private RStringVector checkVector(Object obj, String msg) throws RError {
        if (obj instanceof RStringVector) {
            RStringVector result = (RStringVector) obj;
            if (result.getLength() > 0) {
                return result;
            }
        }
        throw RError.getGenericError(getSourceSection(), msg);
    }

    private static RStringVector create(RStringVector pattern, RStringVector tempDir, RStringVector fileExt) {
        return null;
    }

    private static String createFile(String pattern, String tempDir, String fileExt) {
        while (true) {
            StringBuilder sb = new StringBuilder(tempDir);
            sb.append(File.separatorChar);
            sb.append(pattern);
            appendRandomString(sb);
            if (fileExt.length() > 0) {
                sb.append(fileExt);
            }
            String path = sb.toString();
            if (!BaseRFFIFactory.getRFFI().exists(path)) {
                return path;
            }
        }
    }

    private static void appendRandomString(StringBuilder sb) {
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            sb.append(RANDOM_CHARACTERS.charAt(rand.nextInt(RANDOM_CHARACTERS_LENGTH)));
        }
    }

}
