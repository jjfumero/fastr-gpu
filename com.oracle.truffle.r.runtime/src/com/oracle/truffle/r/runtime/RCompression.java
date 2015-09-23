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

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;

import com.oracle.truffle.r.runtime.conn.GZIPConnections.GZIPRConnection;
import com.oracle.truffle.r.runtime.ffi.RFFIFactory;

/**
 * Abstracts the implementation of the various forms of compression used in R. Since the C API for
 * LZMA is very complex (as used by GnuR), we use an 'xz' subprocess to do the work.
 */
public class RCompression {
    public enum Type {
        NONE('0'),
        GZIP('1'),
        BZIP2('2'),
        LZMA('Z');

        public final byte typeByte;

        private Type(char typeChar) {
            this.typeByte = (byte) typeChar;
        }

        public static Type fromTypeChar(byte typeByte) {
            for (Type t : Type.values()) {
                if (t.typeByte == typeByte) {
                    return t;
                }
            }
            return null;
        }

        /**
         * Decode the compression type from the bytes in buf (which must be at least length 5).
         */
        public static Type decodeBuf(byte[] buf) {
            if (buf[0] == 'B' && buf[1] == 'Z' && buf[2] == 'h') {
                return RCompression.Type.BZIP2;
            } else if (buf[0] == (byte) 0xFD && buf[1] == '7' && buf[2] == 'z' && buf[3] == 'X' && buf[4] == 'Z') {
                return RCompression.Type.LZMA;
            } else {
                return RCompression.Type.NONE;
            }
        }
    }

    public static boolean uncompress(Type type, byte[] udata, byte[] cdata) {
        switch (type) {
            case NONE:
                System.arraycopy(cdata, 0, udata, 0, cdata.length);
                return true;
            case GZIP:
                return gzipUncompress(udata, cdata);
            case BZIP2:
                throw RInternalError.unimplemented("BZIP2 compression");
            case LZMA:
                return lzmaUncompress(udata, cdata);
            default:
                assert false;
                return false;
        }
    }

    public static boolean compress(Type type, byte[] udata, byte[] cdata) {
        switch (type) {
            case NONE:
                System.arraycopy(udata, 0, cdata, 0, udata.length);
                return true;
            case GZIP:
                return gzipCompress(udata, cdata);
            case BZIP2:
                throw RInternalError.unimplemented("BZIP2 compression");
            case LZMA:
                return lzmaCompress(udata, cdata);
            default:
                assert false;
                return false;
        }

    }

    private static boolean gzipCompress(byte[] udata, byte[] cdata) {
        long[] cdatalen = new long[1];
        cdatalen[0] = cdata.length;
        int rc = RFFIFactory.getRFFI().getBaseRFFI().compress(cdata, cdatalen, udata);
        return rc == 0;
    }

    private static boolean gzipUncompress(byte[] udata, byte[] data) {
        long[] destlen = new long[1];
        destlen[0] = udata.length;
        int rc = RFFIFactory.getRFFI().getBaseRFFI().uncompress(udata, destlen, data);
        return rc == 0;
    }

    private static boolean lzmaCompress(byte[] udata, byte[] cdata) {
        int rc;
        ProcessBuilder pb = new ProcessBuilder("xz", "--compress", "--format=raw", "--lzma2", "--stdout");
        pb.redirectError(Redirect.INHERIT);
        try {
            Process p = pb.start();
            OutputStream os = p.getOutputStream();
            InputStream is = p.getInputStream();
            ProcessOutputThread readThread = new ProcessOutputThreadFixed(is, cdata);
            readThread.start();
            os.write(udata);
            os.close();
            rc = p.waitFor();
            if (rc == 0) {
                readThread.join();
                return true;
            }
        } catch (InterruptedException | IOException ex) {
            return false;
        }
        return rc == 0;

    }

    private static boolean lzmaUncompress(byte[] udata, byte[] data) {
        int rc;
        ProcessBuilder pb = new ProcessBuilder("xz", "--decompress", "--format=raw", "--lzma2", "--stdout");
        pb.redirectError(Redirect.INHERIT);
        try {
            Process p = pb.start();
            OutputStream os = p.getOutputStream();
            InputStream is = p.getInputStream();
            ProcessOutputThread readThread = new ProcessOutputThreadFixed(is, udata);
            readThread.start();
            os.write(data);
            os.close();
            rc = p.waitFor();
            if (rc == 0) {
                readThread.join();
                if (readThread.totalRead != udata.length) {
                    return false;
                }
            }
        } catch (InterruptedException | IOException ex) {
            return false;
        }
        return rc == 0;
    }

    /**
     * This is used by {@link GZIPRConnection}.
     */
    public static byte[] lzmaUncompressFromFile(String path) {
        return genericUncompressFromFile(new String[]{"xz", "--decompress", "--lzma2", "--stdout", path});
    }

    public static byte[] bzipUncompressFromFile(String path) {
        return genericUncompressFromFile(new String[]{"bzip2", "-dc", path});
    }

    private static byte[] genericUncompressFromFile(String[] command) {
        int rc;
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectError(Redirect.INHERIT);
        try {
            Process p = pb.start();
            InputStream is = p.getInputStream();
            ProcessOutputThreadVariable readThread = new ProcessOutputThreadVariable(is);
            readThread.start();
            rc = p.waitFor();
            if (rc == 0) {
                readThread.join();
                return readThread.getData();
            }
        } catch (InterruptedException | IOException ex) {
            // fall through
        }
        return null;

    }

    private abstract static class ProcessOutputThread extends Thread {
        protected final InputStream is;
        protected int totalRead;

        ProcessOutputThread(InputStream is) {
            super("XZProcessOutputThread");
            this.is = is;
        }

    }

    /**
     * Reads until the expected length or EOF (which is an error).
     */
    private static final class ProcessOutputThreadFixed extends ProcessOutputThread {
        protected byte[] data;

        private ProcessOutputThreadFixed(InputStream is, byte[] data) {
            super(is);
            this.data = data;
        }

        @Override
        public void run() {
            int n;
            try {
                while (totalRead < data.length && (n = is.read(data, totalRead, data.length - totalRead)) != -1) {
                    totalRead += n;
                }
            } catch (IOException ex) {
                return;
            }
        }
    }

    /**
     * Reads a variable sized amount of data into a growing array.
     *
     */
    private static final class ProcessOutputThreadVariable extends ProcessOutputThread {
        private byte[] data;

        private ProcessOutputThreadVariable(InputStream is) {
            super(is);
            this.data = new byte[8192];
        }

        @Override
        public void run() {
            int n;
            try {
                while ((n = is.read(data, totalRead, data.length - totalRead)) != -1) {
                    totalRead += n;
                    if (totalRead == data.length) {
                        byte[] udataNew = new byte[data.length * 2];
                        System.arraycopy(data, 0, udataNew, 0, data.length);
                        data = udataNew;
                    }
                }
            } catch (IOException ex) {
                return;
            }
        }

        private byte[] getData() {
            return data;
        }
    }

}
