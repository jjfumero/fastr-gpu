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
package com.oracle.truffle.r.runtime;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.r.runtime.RContext.ConsoleHandler;
import com.oracle.truffle.r.runtime.conn.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

public final class Utils {

    public static boolean isIsoLatinDigit(char c) {
        return c >= '\u0030' && c <= '\u0039';
    }

    public static boolean isRomanLetter(char c) {
        return (/* lower case */c >= '\u00DF' && c <= '\u00FF') || (/* upper case */c >= '\u00C0' && c <= '\u00DE');
    }

    public static int incMod(int value, int mod) {
        int result = (value + 1);
        if (result == mod) {
            return 0;
        }
        return result;
    }

    public static void dumpFunction(String groupName, RFunction function) {
        GraphPrintVisitor graphPrinter = new GraphPrintVisitor();
        RootCallTarget callTarget = function.getTarget();
        if (callTarget != null) {
            graphPrinter.beginGroup(groupName);
            graphPrinter.beginGraph(RRuntime.toString(function)).visit(callTarget.getRootNode());
        }
        graphPrinter.printToNetwork(true);
    }

    public static Source getResourceAsSource(Class<?> clazz, String resourceName) {
        URL url = ResourceHandlerFactory.getHandler().getResource(clazz, resourceName);
        try {
            return Source.fromFileName(url.getPath());
        } catch (IOException ex) {
            throw Utils.fail("resource " + resourceName + " not found");
        }
    }

    public static String getResourceAsString(Class<?> clazz, String resourceName, boolean mustExist) {
        InputStream is = ResourceHandlerFactory.getHandler().getResourceAsStream(clazz, resourceName);
        if (is == null) {
            if (!mustExist) {
                return null;
            }
        } else {
            try {
                return Utils.getResourceAsString(is);
            } catch (IOException ex) {
            }
        }
        throw Utils.fail("resource " + resourceName + " not found");
    }

    private static String getResourceAsString(InputStream is) throws IOException {
        try (BufferedReader bs = new BufferedReader(new InputStreamReader(is))) {
            char[] buf = new char[1024];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = bs.read(buf, 0, buf.length)) > 0) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        }
    }

    public static void warn(String msg) {
        // CheckStyle: stop system..print check
        System.err.println("FastR warning: " + msg);
        // CheckStyle: resume system..print check
    }

    /**
     * When running in "debug" mode, this exception is thrown rather than a call to System.exit, so
     * that control can return to an in-process debugger.
     */
    public static class DebugExitException extends RuntimeException {

        private static final long serialVersionUID = 1L;

    }

    /**
     * All terminations should go through this method.
     */
    public static RuntimeException exit(int status) {
        RPerfStats.report();
        if (RCmdOptions.DEBUGGER.getValue() != null) {
            throw new DebugExitException();
        } else {
            System.exit(status);
            return null;
        }
    }

    public static RuntimeException fail(String msg) {
        // CheckStyle: stop system..print check
        System.err.println("FastR internal error: " + msg);
        // CheckStyle: resume system..print check
        throw Utils.exit(2);
    }

    public static RuntimeException fatalError(String msg) {
        System.err.println("Fatal error: " + msg);
        throw Utils.exit(2);
    }

    private static String userHome;

    private static String userHome() {
        CompilerAsserts.neverPartOfCompilation("property access cannot be expanded by PE");
        if (userHome == null) {
            userHome = System.getProperty("user.home");
        }
        return userHome;
    }

    private static final class WorkingDirectoryState {
        /**
         * The initial working directory on startup. This and {@link #current} are always absolute
         * paths.
         */
        private FileSystem fileSystem;
        private final String initial;
        private String current;
        private Path currentPath;

        private WorkingDirectoryState() {
            if (fileSystem == null) {
                fileSystem = FileSystems.getDefault();
            }
            initial = System.getProperty("user.dir");
            current = initial;
            currentPath = fileSystem.getPath(initial);
        }

        private Path getCurrentPath() {
            return currentPath;
        }

        private void setCurrent(String path) {
            current = path;
            currentPath = fileSystem.getPath(path);
        }

        private boolean isInitial() {
            return current.equals(initial);
        }

        private FileSystem getFileSystem() {
            return fileSystem;
        }
    }

    /**
     * Keeps a record of the current working directory as Java provides no way to change this AND
     * many of the file methods that operate on relative paths work from the initial value.
     */
    private static WorkingDirectoryState wdState;

    private static WorkingDirectoryState wdState() {
        if (wdState == null) {
            wdState = new WorkingDirectoryState();
        }
        return wdState;
    }

    public static void updateCurwd(String path) {
        wdState().setCurrent(path);
    }

    /**
     * Performs "~" expansion and also checks whether we need to take special case over relative
     * paths due to the curwd having moved from the initial setting. In the latter case, if the path
     * was relative it is adjusted for the new curwd setting. If {@code keepRelative == true} the
     * value is returned as a relative path, otherwise absolute. Almost all use cases should call
     * {@link #tildeExpand(String)} because providing a relative path to Java file methods with a
     * shifted curwd will not produce the right result. This {@code keepRelative == true} case is
     * required for file/directory listings.
     */
    public static String tildeExpand(String path, boolean keepRelative) {
        if (path.length() > 0 && path.charAt(0) == '~') {
            return userHome() + path.substring(1);
        } else {
            if (wdState().isInitial()) {
                return path;
            } else {
                /*
                 * This is moderately painful, as can't rely on most of the normal relative path
                 * support in Java as much of it works relative to the initial setting.
                 */
                if (path.length() == 0) {
                    return keepRelative ? path : wdState().getCurrentPath().toString();
                } else {
                    Path p = wdState().getFileSystem().getPath(path);
                    if (p.isAbsolute()) {
                        return path;
                    } else {
                        Path currentPath = wdState().getCurrentPath();
                        Path truePath = currentPath.resolve(p);
                        if (keepRelative) {
                            // relativize it (it was relative to start with)
                            return currentPath.relativize(truePath).toString();
                        } else {
                            return truePath.toString();
                        }
                    }
                }
            }
        }
    }

    /**
     * Return an absolute path, with "~" expansion, for {@code path}, taking into account any change
     * in curwd.
     */
    public static String tildeExpand(String path) {
        return tildeExpand(path, false);
    }

    /**
     * Retrieve a frame from the call stack. N.B. This method cannot not be used to get the current
     * frame, use {@link #getActualCurrentFrame()} for that.
     *
     * @param fa kind of access required to the frame
     * @param depth identifies which frame is required (> 0)
     * @return {@link Frame} instance or {@code null} if {@code depth} is out of range
     */
    @TruffleBoundary
    public static Frame getStackFrame(FrameAccess fa, int depth) {
        return Truffle.getRuntime().iterateFrames(frameInstance -> {
            Frame f = frameInstance.getFrame(fa, false);
            return RArguments.getDepth(f) == depth ? f : null;
        });
    }

    /**
     * TODO provide a better way of determining promise evaluation nature of frames than using
     * {@code toString()} on the call target.
     */
    @TruffleBoundary
    private static boolean isPromiseEvaluationFrame(FrameInstance frameInstance) {
        String desc = frameInstance.getCallTarget().toString();
        return desc == RPromise.CLOSURE_WRAPPER_NAME;
    }

    /**
     * An arguments array length of 1 is indicative of a substituted frame. See
     * {@code FunctionDefinitionNode.substituteFrame}.
     */
    private static boolean isSubstitutedFrame(Frame frame) {
        return frame.getArguments().length == 1;
    }

    /**
     * Retrieve the caller frame of the current frame.
     */
    public static Frame getCallerFrame(VirtualFrame frame, FrameAccess fa) {
        return getStackFrame(fa, RArguments.getDepth(frame) - 1);
    }

    /**
     * Retrieve the actual current frame. This may be different from the frame returned by
     * {@link TruffleRuntime#getCurrentFrame()} due to operations applied in
     * {@code FunctionDefinitionNode.execute(VirtualFrame)}. Also see
     * {@code FunctionDefinitionNode.substituteFrame}.
     */
    @TruffleBoundary
    public static Frame getActualCurrentFrame() {
        FrameInstance frameInstance = Truffle.getRuntime().getCurrentFrame();
        if (frameInstance == null) {
            // Might be the case during initialization, when envs are prepared before the actual
            // Truffle/R system has started
            return null;
        }
        Frame frame = frameInstance.getFrame(FrameAccess.MATERIALIZE, true);
        if (isSubstitutedFrame(frame)) {
            frame = (Frame) frame.getArguments()[0];
        }
        return frame;
    }

    @TruffleBoundary
    public static String createStackTrace() {
        return createStackTrace(true);
    }

    /**
     * Generate a stack trace as a string.
     */
    @TruffleBoundary
    public static String createStackTrace(boolean printFrameSlots) {
        FrameInstance current = Truffle.getRuntime().getCurrentFrame();
        if (current == null) {
            return "no R stack trace available\n";
        } else {
            StringBuilder str = new StringBuilder();
            dumpFrame(str, current.getCallTarget(), current.getFrame(FrameAccess.READ_ONLY, true), false, current.isVirtualFrame());
            Truffle.getRuntime().iterateFrames(frameInstance -> {
                dumpFrame(str, frameInstance.getCallTarget(), frameInstance.getFrame(FrameAccess.READ_ONLY, true), false, frameInstance.isVirtualFrame());
                return null;
            });
            if (printFrameSlots) {
                str.append("\n\nwith frame slot contents:\n");
                dumpFrame(str, current.getCallTarget(), current.getFrame(FrameAccess.READ_ONLY, true), true, current.isVirtualFrame());
                Truffle.getRuntime().iterateFrames(frameInstance -> {
                    dumpFrame(str, frameInstance.getCallTarget(), frameInstance.getFrame(FrameAccess.READ_ONLY, true), true, frameInstance.isVirtualFrame());
                    return null;
                });
            }
            str.append("\n");
            return str.toString();
        }
    }

    private static void dumpFrame(StringBuilder str, CallTarget callTarget, Frame frame, boolean printFrameSlots, boolean isVirtual) {
        if (str.length() > 0) {
            str.append("\n");
        }
        SourceSection callSrc = RArguments.getCallSourceSection(frame);
        str.append("Frame: ").append(callTarget).append(isVirtual ? " (virtual)" : "");
        if (callSrc != null) {
            str.append(" (called as: ").append(callSrc.getCode()).append(')');
        }
        if (printFrameSlots) {
            FrameDescriptor frameDescriptor = frame.getFrameDescriptor();
            for (FrameSlot s : frameDescriptor.getSlots()) {
                str.append("\n      ").append(s.getIdentifier()).append(" = ");
                Object value = frame.getValue(s);
                try {
                    if (value instanceof RAbstractContainer && ((RAbstractContainer) value).getLength() > 32) {
                        str.append('<').append(value.getClass().getSimpleName()).append(" with ").append(((RAbstractContainer) value).getLength()).append(" elements>");
                    } else {
                        String text = String.valueOf(value);
                        str.append(text.length() < 256 ? text : text.substring(0, 256) + "...");
                    }
                } catch (Throwable t) {
                    // RLanguage values may not react kindly to getLength() calls
                    str.append("<exception ").append(t.getClass().getSimpleName()).append(" while printing value of type ").append(value == null ? "null" : value.getClass().getSimpleName()).append(
                                    '>');
                }
            }
        }
    }

    public static <T> T[] resizeArray(T[] oldValues, int newSize) {
        T[] newValues = oldValues;
        if (oldValues != null) {
            newValues = Arrays.copyOf(oldValues, newSize);
        }
        return newValues;
    }

    // simple implementations of non-recursive hash-maps to enable compilation
    // TODO: consider replacing with a more efficient library implementation

    public static class NonRecursiveHashMap<KEY> {

        private Object[] keys;
        private int[] values;

        public NonRecursiveHashMap(int approxCapacity) {
            keys = new Object[Math.max(approxCapacity, 1)];
            values = new int[Math.max(approxCapacity, 1)];
        }

        public boolean put(KEY key, int value) {
            int ind = Math.abs(key.hashCode()) % keys.length;
            int firstInd = ind;
            while (true) {
                if (keys[ind] == null) {
                    keys[ind] = key;
                    values[ind] = value;
                    return false;
                } else if (key.equals(keys[ind])) {
                    values[ind] = value;
                    return true;
                } else {
                    ind = (ind + 1) % keys.length;
                    if (ind == firstInd) {
                        // resize
                        Object[] newKeys = new Object[keys.length + keys.length / 2];
                        int[] newValues = new int[values.length + values.length / 2];
                        for (int i = 0; i < keys.length; i++) {
                            if (keys[i] != null) {
                                int tmpInd = Math.abs(keys[i].hashCode()) % newKeys.length;
                                while (true) {
                                    if (newKeys[tmpInd] == null) {
                                        newKeys[tmpInd] = keys[i];
                                        newValues[tmpInd] = values[i];
                                        break;
                                    } else {
                                        assert !keys[i].equals(newKeys[tmpInd]);
                                        tmpInd = (tmpInd + 1) % newKeys.length;
                                    }
                                }
                            }
                        }

                        keys = newKeys;
                        values = newValues;

                        // start hashing from the beginning
                        ind = Math.abs(key.hashCode()) % keys.length;
                    }
                }
            }
        }

        public int get(KEY key) {
            int ind = Math.abs(key.hashCode()) % keys.length;
            int firstInd = ind;
            while (true) {
                if (key.equals(keys[ind])) {
                    return values[ind];
                } else {
                    ind = (ind + 1) % keys.length;
                    if (ind == firstInd || keys[ind] == null) {
                        return -1;
                    }
                }
            }
        }
    }

    public static class NonRecursiveHashMapDouble {

        private double[] keys;
        private int[] values;

        public NonRecursiveHashMapDouble(int approxCapacity) {
            keys = new double[Math.max(approxCapacity, 1)];
            Arrays.fill(keys, RRuntime.DOUBLE_NA);
            values = new int[Math.max(approxCapacity, 1)];
        }

        public boolean put(double key, int value) {
            int ind = Math.abs(Double.hashCode(key)) % keys.length;
            int firstInd = ind;
            while (true) {
                if (RRuntime.isNAorNaN(keys[ind])) {
                    keys[ind] = key;
                    values[ind] = value;
                    return false;
                } else if (key == keys[ind]) {
                    values[ind] = value;
                    return true;
                } else {
                    ind = (ind + 1) % keys.length;
                    if (ind == firstInd) {
                        // resize
                        double[] newKeys = new double[keys.length + keys.length / 2];
                        Arrays.fill(newKeys, RRuntime.DOUBLE_NA);
                        int[] newValues = new int[values.length + values.length / 2];
                        for (int i = 0; i < keys.length; i++) {
                            if (!RRuntime.isNAorNaN(keys[i])) {
                                int tmpInd = Math.abs(Double.hashCode(keys[i])) % newKeys.length;
                                while (true) {
                                    if (RRuntime.isNAorNaN(newKeys[tmpInd])) {
                                        newKeys[tmpInd] = keys[i];
                                        newValues[tmpInd] = values[i];
                                        break;
                                    } else {
                                        assert keys[i] != newKeys[tmpInd];
                                        tmpInd = (tmpInd + 1) % newKeys.length;
                                    }
                                }
                            }
                        }

                        keys = newKeys;
                        values = newValues;

                        // start hashing from the beginning
                        ind = Math.abs(Double.hashCode(key)) % keys.length;
                    }
                }
            }
        }

        public int get(double key) {
            int ind = Math.abs(Double.hashCode(key)) % keys.length;
            int firstInd = ind;
            while (true) {
                if (key == keys[ind]) {
                    return values[ind];
                } else {
                    ind = (ind + 1) % keys.length;
                    if (ind == firstInd || RRuntime.isNAorNaN(keys[ind])) {
                        return -1;
                    }
                }
            }
        }
    }

    public static class NonRecursiveHashMapInt {

        private int[] keys;
        private int[] values;

        public NonRecursiveHashMapInt(int approxCapacity) {
            keys = new int[Math.max(approxCapacity, 1)];
            Arrays.fill(keys, RRuntime.INT_NA);
            values = new int[Math.max(approxCapacity, 1)];
        }

        public boolean put(int key, int value) {
            int ind = Math.abs(Integer.hashCode(key)) % keys.length;
            int firstInd = ind;
            while (true) {
                if (RRuntime.isNA(keys[ind])) {
                    keys[ind] = key;
                    values[ind] = value;
                    return false;
                } else if (key == keys[ind]) {
                    values[ind] = value;
                    return true;
                } else {
                    ind = (ind + 1) % keys.length;
                    if (ind == firstInd) {
                        // resize
                        int[] newKeys = new int[keys.length + keys.length / 2];
                        Arrays.fill(newKeys, RRuntime.INT_NA);
                        int[] newValues = new int[values.length + values.length / 2];
                        for (int i = 0; i < keys.length; i++) {
                            if (!RRuntime.isNA(keys[i])) {
                                int tmpInd = Math.abs(Integer.hashCode(keys[i])) % newKeys.length;
                                while (true) {
                                    if (RRuntime.isNA(newKeys[tmpInd])) {
                                        newKeys[tmpInd] = keys[i];
                                        newValues[tmpInd] = values[i];
                                        break;
                                    } else {
                                        assert keys[i] != newKeys[tmpInd];
                                        tmpInd = (tmpInd + 1) % newKeys.length;
                                    }
                                }
                            }
                        }

                        keys = newKeys;
                        values = newValues;

                        // start hashing from the beginning
                        ind = Math.abs(Integer.hashCode(key)) % keys.length;
                    }
                }
            }
        }

        public int get(int key) {
            int ind = Math.abs(Integer.hashCode(key)) % keys.length;
            int firstInd = ind;
            while (true) {
                if (key == keys[ind]) {
                    return values[ind];
                } else {
                    ind = (ind + 1) % keys.length;
                    if (ind == firstInd || RRuntime.isNA(keys[ind])) {
                        return -1;
                    }
                }
            }
        }
    }

    public static class NonRecursiveHashSet<VAL> {
        private NonRecursiveHashMap<VAL> map;

        public NonRecursiveHashSet(int approxCapacity) {
            map = new NonRecursiveHashMap<>(approxCapacity);
        }

        public boolean add(VAL value) {
            return map.put(value, 1);
        }

        public boolean contains(VAL value) {
            return map.get(value) == 1 ? true : false;
        }
    }

    public static class NonRecursiveHashSetInt {
        private NonRecursiveHashMapInt map;

        public NonRecursiveHashSetInt(int approxCapacity) {
            map = new NonRecursiveHashMapInt(approxCapacity);
        }

        public boolean add(int value) {
            return map.put(value, 1);
        }

        public boolean contains(int value) {
            return map.get(value) == 1 ? true : false;
        }
    }

    public static class NonRecursiveHashSetDouble {
        private NonRecursiveHashMapDouble map;

        public NonRecursiveHashSetDouble(int approxCapacity) {
            map = new NonRecursiveHashMapDouble(approxCapacity);
        }

        public boolean add(double value) {
            return map.put(value, 1);
        }

        public boolean contains(double value) {
            return map.get(value) == 1 ? true : false;
        }
    }

    public static void writeStderr(String s, boolean nl) {
        try {
            StdConnections.getStderr().writeString(s, nl);
        } catch (IOException ex) {
            // Very unlikely
            ConsoleHandler consoleHandler = RContext.getInstance().getConsoleHandler();
            consoleHandler.printErrorln("Error writing to stderr: " + ex.getMessage());
            consoleHandler.printErrorln(s);

        }
    }

    public static String toHexString(byte[] data) {
        StringBuffer sb = new StringBuffer();
        for (byte b : data) {
            int ub = Byte.toUnsignedInt(b);
            if (ub > 15) {
                sb.append(Integer.toHexString(ub >> 4));
            } else {
                sb.append('0');
            }
            sb.append(Integer.toHexString(ub & 0xF));
        }
        return sb.toString();
    }

    public static int intFilePermissions(Set<PosixFilePermission> permissions) {
        int r = 0;
        for (PosixFilePermission pfp : permissions) {
            // @formatter:off
            switch (pfp) {
                case OTHERS_EXECUTE: r |= 1; break;
                case OTHERS_WRITE: r |= 2; break;
                case OTHERS_READ: r |= 4; break;
                case GROUP_EXECUTE: r |= 8; break;
                case GROUP_WRITE: r |= 16; break;
                case GROUP_READ: r |= 32; break;
                case OWNER_EXECUTE: r |= 64; break;
                case OWNER_WRITE: r |= 128; break;
                case OWNER_READ: r |= 256; break;
            }
            // @formatter:on
        }
        return r;
    }

}
