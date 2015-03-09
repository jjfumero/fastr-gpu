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
package com.oracle.truffle.r.runtime.conn;

import java.io.*;
import java.nio.*;
import java.util.*;

import com.oracle.truffle.api.CompilerDirectives.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.RContext.*;
import com.oracle.truffle.r.runtime.conn.ConnectionSupport.*;
import com.oracle.truffle.r.runtime.data.model.*;

public class StdConnections {
    private static StdinConnection stdin;
    private static StdoutConnection stdout;
    private static StderrConnection stderr;

    public static void initialize() {
        // This ensures the connections are initialized on engine startup.
        try {
            stdin = new StdinConnection();
            stdout = new StdoutConnection();
            stderr = new StderrConnection();
        } catch (IOException ex) {
            Utils.fail("failed to open stdconnections:");
        }
    }

    public static RConnection getStdin() {
        return stdin;
    }

    public static RConnection getStdout() {
        return stdout;
    }

    public static RConnection getStderr() {
        return stderr;
    }

    public static boolean pushDivertOut(RConnection conn, boolean closeOnExit) {
        return stdout.pushDivert(conn, closeOnExit);
    }

    public static void popDivertOut() throws IOException {
        stdout.popDivert();
    }

    public static void divertErr(RConnection conn) {
        stderr.divertErr(conn);
    }

    public static int stdoutDiversions() {
        return stdout.numDiversions();
    }

    public static int stderrDiversion() {
        return stderr.diversionIndex();
    }

    /**
     * Subclasses are special in that they do not use delegation as the connection is always open.
     */
    private abstract static class StdConnection extends BaseRConnection {
        StdConnection(AbstractOpenMode openMode, int index) throws IOException {
            super(openMode, index);
            this.opened = true;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            throw RInternalError.shouldNotReachHere();
        }

        @Override
        public OutputStream getOutputStream() {
            throw RInternalError.shouldNotReachHere();
        }

        @Override
        public void closeAndDestroy() throws IOException {
            throw new IOException(RError.Message.CANNOT_CLOSE_STANDARD_CONNECTIONS.message);
        }

        @Override
        protected void createDelegateConnection() throws IOException {
            // nothing to do, as we override the RConnection methods directly.
        }

        @Override
        public void writeBin(ByteBuffer buffer) throws IOException {
            throw new IOException("'write' not enabled for this connection");
        }

        @Override
        public void writeChar(String s, int pad, String eos, boolean useBytes) throws IOException {
            throw new IOException("'write' not enabled for this connection");
        }

        @Override
        public int readBin(ByteBuffer buffer) throws IOException {
            throw new IOException("readBin not implemented for this connection");
        }

        @Override
        public byte[] readBinChars() throws IOException {
            throw new IOException("readBinChars not implemented for this connection");
        }

        @Override
        public RConnection forceOpen(String modeString) {
            return this;
        }
    }

    private static class StdinConnection extends StdConnection {

        StdinConnection() throws IOException {
            super(AbstractOpenMode.Read, 0);
        }

        @Override
        public String getSummaryDescription() {
            return "stdin";
        }

        @Override
        public boolean canRead() {
            return true;
        }

        @Override
        public boolean canWrite() {
            return false;
        }

        @Override
        @TruffleBoundary
        public String[] readLinesInternal(int n) throws IOException {
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

    }

    private abstract static class StdoutputAdapter extends StdConnection {
        protected ConsoleHandler consoleHandler = RContext.getInstance().getConsoleHandler();

        StdoutputAdapter(int index) throws IOException {
            super(AbstractOpenMode.Write, index);
            this.opened = true;
        }

        @Override
        public boolean canRead() {
            return false;
        }

        @Override
        public boolean canWrite() {
            return true;
        }

    }

    private static class StdoutConnection extends StdoutputAdapter {

        private static class Diversion {
            final RConnection conn;
            final boolean closeOnExit;

            Diversion(RConnection conn, boolean closeOnExit) {
                this.conn = conn;
                this.closeOnExit = closeOnExit;
            }

        }

        private static final Diversion[] diversions = new Diversion[20];

        private static int top = -1;

        StdoutConnection() throws IOException {
            super(1);
        }

        int numDiversions() {
            return top + 1;
        }

        @Override
        public String getSummaryDescription() {
            return "stdout";
        }

        @Override
        public void writeLines(RAbstractStringVector lines, String sep) throws IOException {
            for (int i = 0; i < lines.getLength(); i++) {
                String line = lines.getDataAt(i);
                writeString(line, false);
                writeString(sep, false);
            }
        }

        @Override
        public void writeString(String s, boolean nl) throws IOException {
            if (top < 0) {
                if (nl) {
                    consoleHandler.println(s);
                } else {
                    consoleHandler.print(s);
                }
            } else {
                diversions[top].conn.writeString(s, nl);
            }
        }

        boolean pushDivert(RConnection conn, boolean closeOnExit) {
            if (top < diversions.length - 1) {
                top++;
                diversions[top] = new Diversion(conn, closeOnExit);
            } else {
                return false;
            }
            return true;
        }

        void popDivert() throws IOException {
            if (top >= 0) {
                int ctop = top;
                top--;
                if (diversions[ctop].closeOnExit) {
                    diversions[ctop].conn.closeAndDestroy();
                }
            }
        }
    }

    private static class StderrConnection extends StdoutputAdapter {
        RConnection diversion;

        StderrConnection() throws IOException {
            super(2);
        }

        void divertErr(RConnection conn) {
            if (conn == stderr) {
                diversion = null;
            } else {
                diversion = conn;
            }
        }

        int diversionIndex() {
            return diversion == null ? 2 : diversion.getDescriptor();
        }

        @Override
        public String getSummaryDescription() {
            return "stderr";
        }

        @Override
        public void writeLines(RAbstractStringVector lines, String sep) throws IOException {
            for (int i = 0; i < lines.getLength(); i++) {
                String line = lines.getDataAt(i);
                writeString(line, false);
                writeString(sep, false);
            }
        }

        @Override
        public void writeString(String s, boolean nl) throws IOException {
            if (diversion != null) {
                diversion.writeString(s, nl);
            } else {
                if (nl) {
                    consoleHandler.printErrorln(s);
                } else {
                    consoleHandler.printError(s);
                }
            }
        }

    }

}