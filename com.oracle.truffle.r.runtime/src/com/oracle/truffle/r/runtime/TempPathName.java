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

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;

import com.oracle.truffle.api.CompilerDirectives.*;
import com.oracle.truffle.r.runtime.ffi.*;

/**
 *
 * As per the GnuR spec, the tempdir() directory is identified on startup.
 *
 */
public class TempPathName {
    private static final String RANDOM_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final int RANDOM_CHARACTERS_LENGTH = RANDOM_CHARACTERS.length();
    private static final int RANDOM_LENGTH = 12; // as per GnuR

    private static String tempDirPath;
    private static final Random rand = new Random();

    public static void initialize() {
        if (tempDirPath == null) {
            //
            final String[] envVars = new String[]{"TMPDIR", "TMP", "TEMP"};
            String startingTempDir = null;
            for (String envVar : envVars) {
                String value = System.getenv(envVar);
                if (value != null && isWriteableDirectory(value)) {
                    startingTempDir = value;
                }
            }
            if (startingTempDir == null) {
                startingTempDir = "/tmp";
            }
            Path startingTempDirPath = FileSystems.getDefault().getPath(startingTempDir, "Rtmp");
            // ensure absolute, to avoid problems with R code does a setwd
            if (!startingTempDirPath.isAbsolute()) {
                startingTempDirPath = startingTempDirPath.toAbsolutePath();
            }
            String t = RFFIFactory.getRFFI().getBaseRFFI().mkdtemp(startingTempDirPath.toString() + "XXXXXX");
            if (t != null) {
                tempDirPath = t;
            } else {
                Utils.fail("cannot create 'R_TempDir'");
            }
        }
    }

    private static boolean isWriteableDirectory(String path) {
        File f = new File(path);
        return f.exists() && f.isDirectory() && f.canWrite();
    }

    public static String tempDirPath() {
        return tempDirPath;
    }

    @TruffleBoundary
    public static String createNonExistingFilePath(String pattern, String tempDir, String fileExt) {
        while (true) {
            StringBuilder sb = new StringBuilder(tempDir);
            sb.append(File.separatorChar);
            sb.append(pattern);
            appendRandomString(sb);
            if (fileExt.length() > 0) {
                sb.append(fileExt);
            }
            String path = sb.toString();
            if (!new File(path).exists()) {
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
