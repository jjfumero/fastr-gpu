/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.ffi.jnr;

import jnr.ffi.LibraryLoader;
import jnr.ffi.annotations.In;
import jnr.ffi.annotations.Out;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.ffi.PCRERFFI;

/**
 * PCRE support using JNR.
 */
public class JNR_PCRE implements PCRERFFI {
    public interface PCRE {
        long pcre_maketables();

        long pcre_compile(String pattern, int options, @Out byte[] errorMessage, @Out int[] errOffset, long tables);

        int pcre_exec(long code, long extra, @In byte[] subject, int subjectLength, int startOffset, int options, @Out int[] ovector, int ovecSize);
    }

    private static class PCREProvider {
        private static PCRE pcre;

        @TruffleBoundary
        private static PCRE createAndLoadLib() {
            return LibraryLoader.create(PCRE.class).load("pcre");
        }

        static PCRE pcre() {
            if (pcre == null) {
                pcre = createAndLoadLib();
            }
            return pcre;
        }
    }

    private static PCRE pcre() {
        return PCREProvider.pcre();
    }

    public long maketables() {
        return pcre().pcre_maketables();
    }

    public Result compile(String pattern, int options, long tables) {
        int[] errOffset = new int[1];
        byte[] errorMessage = new byte[512];
        long result = pcre().pcre_compile(pattern, options, errorMessage, errOffset, tables);
        if (result == 0) {
            return new Result(result, new String(errorMessage), errOffset[0]);
        } else {
            return new Result(result, null, 0);
        }
    }

    public int exec(long code, long extra, String subject, int offset, int options, int[] ovector) {
        return pcre().pcre_exec(code, extra, subject.getBytes(), subject.length(), offset, options, ovector, ovector.length);
    }

    public Result study(long code, int options) {
        throw RInternalError.unimplemented("pcre_study");
    }

}
