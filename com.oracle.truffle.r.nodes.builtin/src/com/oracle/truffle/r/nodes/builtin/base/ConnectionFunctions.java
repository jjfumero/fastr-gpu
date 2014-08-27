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

import static com.oracle.truffle.r.runtime.RBuiltinKind.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.RContext.ConsoleHandler;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

public abstract class ConnectionFunctions {

    // TODO remove invisibility when print for RConnection works

    public static class StdinConnection extends RConnection {

        @Override
        public String[] readLines(int n) throws IOException {
            ConsoleHandler console = RContext.getInstance().getConsoleHandler();
            ArrayList<String> lines = new ArrayList<>();
            String line;
            while ((line = console.readLine()) != null) {
                lines.add(line);
                if (n > 0 && lines.size() == n) {
                    break;
                }
            }
            String[] result = new String[lines.size()];
            lines.toArray(result);
            return result;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            throw new IOException();
        }

        @Override
        public void close() throws IOException {
            throw new IOException();
        }
    }

    private static StdinConnection stdin;

    @RBuiltin(name = "stdin", kind = INTERNAL, parameterNames = {})
    public abstract static class Stdin extends RInvisibleBuiltinNode {
        @Specialization
        protected RConnection stdin() {
            controlVisibility();
            if (stdin == null) {
                stdin = new StdinConnection();
            }
            return stdin;
        }
    }

    public static class FileReadRConnection extends RConnection {
        private final BufferedReader bufferedReader;

        FileReadRConnection(String path) throws IOException {
            bufferedReader = new BufferedReader(new FileReader(path));
        }

        @SlowPath
        @Override
        public String[] readLines(int n) throws IOException {
            ArrayList<String> lines = new ArrayList<>();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                lines.add(line);
                if (n > 0 && lines.size() == n) {
                    break;
                }
            }
            String[] result = new String[lines.size()];
            lines.toArray(result);
            return result;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            throw new IOException();
        }

        @Override
        public void close() throws IOException {
            bufferedReader.close();
        }
    }

    public static class GZIPInputRConnection extends RConnection {
        private final GZIPInputStream stream;

        GZIPInputRConnection(String path) throws IOException {
            stream = new GZIPInputStream(new FileInputStream(path));
        }

        @Override
        public String[] readLines(int n) throws IOException {
            throw new IOException("TODO");
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return stream;
        }

        @Override
        public void close() throws IOException {
            stream.close();
        }
    }

    @RBuiltin(name = "file", kind = INTERNAL, parameterNames = {"description", "open", "blocking", "encoding", "raw"})
    public abstract static class File extends RInvisibleBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        protected Object file(RAbstractStringVector description, String open, byte blocking, RAbstractStringVector encoding, byte raw) {
            controlVisibility();
            if (!open.equals("r")) {
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.UNIMPLEMENTED_OPEN_MODE, open);
            }
            String ePath = Utils.tildeExpand(description.getDataAt(0));
            try {
                // temporarily return invisible to avoid missing print support
                return new FileReadRConnection(ePath);
            } catch (IOException ex) {
                RError.warning(RError.Message.CANNOT_OPEN_FILE, description.getDataAt(0), ex.getMessage());
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.CANNOT_OPEN_CONNECTION);
            }
        }

        @SuppressWarnings("unused")
        @Specialization
        protected Object file(Object description, Object open, Object blocking, Object encoding, Object raw) {
            controlVisibility();
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.INVALID_UNNAMED_ARGUMENTS);
        }
    }

    @RBuiltin(name = "gzfile", kind = INTERNAL, parameterNames = {"description", "open", "encoding", "compression"})
    public abstract static class GZFile extends RInvisibleBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        protected Object gzFile(RAbstractStringVector description, String open, RAbstractStringVector encoding, double compression) {
            controlVisibility();
            String ePath = Utils.tildeExpand(description.getDataAt(0));
            try {
                // temporarily return invisible to avoid missing print support
                return new GZIPInputRConnection(ePath);
            } catch (IOException ex) {
                RError.warning(RError.Message.CANNOT_OPEN_FILE, description.getDataAt(0), ex.getMessage());
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.CANNOT_OPEN_CONNECTION);
            }
        }
    }

    @RBuiltin(name = "close", kind = INTERNAL, parameterNames = {"con", "..."})
    // TODO Internal
    public abstract static class Close extends RInvisibleBuiltinNode {
        @Specialization
        protected Object close(Object con) {
            controlVisibility();
            if (con instanceof RConnection) {
                try {
                    ((RConnection) con).close();
                } catch (IOException e) {
                    throw RInternalError.unimplemented();
                }
                return RNull.instance;
            } else {
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.NOT_CONNECTION, "con");
            }
        }
    }

    @RBuiltin(name = "readLines", kind = INTERNAL, parameterNames = {"con", "n", "ok", "warn", "encoding"})
    public abstract static class ReadLines extends RBuiltinNode {
        @Specialization
        protected Object readLines(RConnection con, int n, byte ok, @SuppressWarnings("unused") byte warn, @SuppressWarnings("unused") String encoding) {
            controlVisibility();
            try {
                String[] lines = con.readLines(n);
                if (n > 0 && lines.length < n && ok == RRuntime.LOGICAL_FALSE) {
                    throw RError.error(getEncapsulatingSourceSection(), RError.Message.TOO_FEW_LINES_READ_LINES);
                }
                return RDataFactory.createStringVector(lines, RDataFactory.COMPLETE_VECTOR);
            } catch (IOException x) {
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.ERROR_READING_CONNECTION, x.getMessage());
            }
        }

        @SuppressWarnings("unused")
        @Specialization
        protected Object readLines(Object con, Object n, Object ok, Object warn, Object encoding) {
            controlVisibility();
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.INVALID_UNNAMED_ARGUMENTS);
        }
    }

}
