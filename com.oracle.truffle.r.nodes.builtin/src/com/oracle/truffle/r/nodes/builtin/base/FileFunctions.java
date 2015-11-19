/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995-2012, The R Core Team
 * Copyright (c) 2003, The R Foundation
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.runtime.RBuiltinKind.*;

import java.io.*;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.function.*;
import java.util.regex.*;
import java.util.stream.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ffi.*;

// Much of this code was influences/transcribed from GnuR src/main/platform.c

public class FileFunctions {

    @RBuiltin(name = "file.access", kind = INTERNAL, parameterNames = {"names", "mode"})
    public abstract static class FileAccess extends RBuiltinNode {
        private static final int EXECUTE = 1;
        private static final int WRITE = 2;
        private static final int READ = 4;

        @Override
        protected void createCasts(CastBuilder casts) {
            casts.toInteger(1);
        }

        @Specialization
        @TruffleBoundary
        public Object fileAccess(RAbstractStringVector names, int mode) {
            if (mode == RRuntime.INT_NA || mode < 0 || mode > 7) {
                throw RError.error(this, RError.Message.INVALID_ARGUMENT, "mode");
            }
            int[] data = new int[names.getLength()];
            for (int i = 0; i < data.length; i++) {
                File file = new File(Utils.tildeExpand(names.getDataAt(i)));
                if (file.exists()) {
                    if ((mode & EXECUTE) != 0 && !file.canExecute()) {
                        data[i] = -1;
                    }
                    if ((mode & READ) != 0 && !file.canRead()) {
                        data[i] = -1;
                    }
                    if ((mode & WRITE) != 0 && !file.canWrite()) {
                        data[i] = -1;
                    }
                } else {
                    data[i] = -1;
                }
            }
            return RDataFactory.createIntVector(data, RDataFactory.COMPLETE_VECTOR);
        }
    }

    @RBuiltin(name = "file.append", kind = INTERNAL, parameterNames = {"file1", "file2"})
    public abstract static class FileAppend extends RBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected RLogicalVector doFileAppend(RAbstractStringVector file1Vec, RAbstractStringVector file2Vec) {
            /*
             * There are two simple (non-trivial) cases and one tricky 1. 1. Append one or more
             * files to a single file (len1 == 1, len2 >= 1) 2. Append one file to one file for
             * several files (len1 == len2)
             * 
             * The tricky case is when len1 > 1 && len2 > len1. E.g. f1,f2 <- g1,g2,g3 In this case,
             * this is really f1,f2,f1 <- g1,g2,g3
             */

            int len1 = file1Vec.getLength();
            int len2 = file2Vec.getLength();
            if (len1 < 1) {
                throw RError.error(this, RError.Message.FILE_APPEND_TO);
            }
            if (len2 < 1) {
                return RDataFactory.createEmptyLogicalVector();
            }
            int len = len1 > len2 ? len1 : len2;
            byte[] status = new byte[len];
            if (len1 == 1) {
                String file1 = file1Vec.getDataAt(0);
                if (file1 != RRuntime.STRING_NA) {
                    file1 = Utils.tildeExpand(file1);
                    try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file1, true))) {
                        for (int f2 = 0; f2 < len2; f2++) {
                            String file2 = file2Vec.getDataAt(f2);
                            status[f2] = RRuntime.asLogical(appendFile(out, file2));
                        }
                    } catch (IOException ex) {
                        // failure to open output file not reported as error by GnuR, just status
                    }
                }
            } else {
                // align vectors, redundant if len1 == len2, but avoids duplication
                String[] file1A = new String[len];
                String[] file2A = new String[len];
                for (int f = 0; f < len; f++) {
                    file1A[f] = file1Vec.getDataAt(f % len1);
                    file2A[f] = file2Vec.getDataAt(f % len2);
                }
                for (int f = 0; f < len; f++) {
                    String file1 = file1A[f];
                    if (file1 != RRuntime.STRING_NA) {
                        file1 = Utils.tildeExpand(file1);
                        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file1, true))) {
                            String file2 = file2A[f];
                            status[f] = RRuntime.asLogical(appendFile(out, file2));
                        } catch (IOException ex) {
                            // status is set
                        }
                    }
                }
            }
            return RDataFactory.createLogicalVector(status, RDataFactory.COMPLETE_VECTOR);
        }

        private boolean appendFile(BufferedOutputStream out, String pathArg) {
            if (pathArg == RRuntime.STRING_NA) {
                return false;
            }
            String path = Utils.tildeExpand(pathArg);
            File file = new File(path);
            if (!file.exists()) {
                // not an error (cf GnuR), just status
                return false;
            } else {
                byte[] buf = new byte[(int) file.length()];
                try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(path))) {
                    in.read(buf);
                } catch (IOException ex) {
                    // not an error (cf GnuR), just status
                    return false;
                }
                try {
                    out.write(buf);
                    return true;
                } catch (IOException ex) {
                    RError.warning(this, RError.Message.FILE_APPEND_WRITE);
                    return false;
                }
            }

        }
    }

    @RBuiltin(name = "file.create", kind = INTERNAL, parameterNames = {"vec", "showWarnings"})
    public abstract static class FileCreate extends RBuiltinNode {

        @Specialization
        @TruffleBoundary
        protected Object doFileCreate(RAbstractStringVector vec, byte showWarnings) {
            controlVisibility();
            byte[] status = new byte[vec.getLength()];
            for (int i = 0; i < status.length; i++) {
                String path = vec.getDataAt(i);
                if (RRuntime.isNA(path)) {
                    status[i] = RRuntime.LOGICAL_FALSE;
                } else {
                    boolean ok = true;
                    try {
                        new FileOutputStream(Utils.tildeExpand(path)).close();
                    } catch (IOException ex) {
                        ok = false;
                        if (showWarnings == RRuntime.LOGICAL_TRUE) {
                            RError.warning(this, RError.Message.FILE_CANNOT_CREATE, path);
                        }
                    }
                    status[i] = RRuntime.asLogical(ok);
                }
            }
            return RDataFactory.createLogicalVector(status, RDataFactory.COMPLETE_VECTOR);
        }

        @Fallback
        @TruffleBoundary
        protected Object doFileCreate(@SuppressWarnings("unused") Object x, @SuppressWarnings("unused") Object y) {
            controlVisibility();
            throw RError.error(this, RError.Message.INVALID_ARGUMENT, "file");
        }
    }

    @RBuiltin(name = "file.info", kind = INTERNAL, parameterNames = {"fn"})
    public abstract static class FileInfo extends RBuiltinNode {
        // @formatter:off
        private static enum Column {
            size, isdir, mode, mtime, ctime, atime, uid, gid, uname, grname;
            private static final Column[] VALUES = values();
        }
        // @formatter:on
        private static final String[] NAMES = new String[]{Column.size.name(), Column.isdir.name(), Column.mode.name(), Column.mtime.name(), Column.ctime.name(), Column.atime.name(),
                        Column.uid.name(), Column.gid.name(), Column.uname.name(), Column.grname.name()};
        private static final RStringVector NAMES_VECTOR = RDataFactory.createStringVector(NAMES, RDataFactory.COMPLETE_VECTOR);
        private static final RStringVector OCTMODE = RDataFactory.createStringVectorFromScalar("octmode");

        @Specialization
        @TruffleBoundary
        protected RList doFileInfo(RAbstractStringVector vec) {
            /*
             * Create a list, the elements of which are vectors of length vec.getLength() containing
             * the information. The R closure that called the .Internal turns the result into a
             * dataframe and sets the row.names attributes to the paths in vec. It also updates the
             * mtime, ctime, atime fields using .POSIXct.
             * 
             * We try to use the JDK classes, even though they provide a more abstract interface
             * than R. In particular there seems to be no way to get the uid/gid values. We might be
             * better off justing using a native call.
             */
            controlVisibility();
            int vecLength = vec.getLength();
            Object[] data = new Object[NAMES.length];
            boolean[] complete = new boolean[NAMES.length];
            for (int n = 0; n < Column.VALUES.length; n++) {
                data[n] = createColumnData(Column.VALUES[n], vecLength);
                complete[n] = RDataFactory.COMPLETE_VECTOR; // optimistic
            }
            FileSystem fileSystem = FileSystems.getDefault();
            for (int i = 0; i < vecLength; i++) {
                String vecPath = vec.getDataAt(i);
                Path path = fileSystem.getPath(Utils.tildeExpand(vecPath));
                // missing defaults to NA
                if (Files.exists(path)) {
                    double size = RRuntime.DOUBLE_NA;
                    byte isdir = RRuntime.LOGICAL_NA;
                    int mode = RRuntime.INT_NA;
                    int mtime = RRuntime.INT_NA;
                    int atime = RRuntime.INT_NA;
                    int ctime = RRuntime.INT_NA;
                    int uid = RRuntime.INT_NA;
                    int gid = RRuntime.INT_NA;
                    String uname = RRuntime.STRING_NA;
                    String grname = RRuntime.STRING_NA;
                    try {
                        PosixFileAttributes pfa = Files.readAttributes(path, PosixFileAttributes.class);
                        size = pfa.size();
                        isdir = RRuntime.asLogical(pfa.isDirectory());
                        mtime = getTimeInSecs(pfa.lastModifiedTime());
                        ctime = getTimeInSecs(pfa.creationTime());
                        atime = getTimeInSecs(pfa.lastAccessTime());
                        uname = pfa.owner().getName();
                        grname = pfa.group().getName();
                        mode = Utils.intFilePermissions(pfa.permissions());
                    } catch (IOException ex) {
                        // ok, NA value is used
                    }
                    setColumnValue(Column.size, data, complete, i, size);
                    setColumnValue(Column.isdir, data, complete, i, isdir);
                    setColumnValue(Column.mode, data, complete, i, mode);
                    setColumnValue(Column.mtime, data, complete, i, mtime);
                    setColumnValue(Column.ctime, data, complete, i, ctime);
                    setColumnValue(Column.atime, data, complete, i, atime);
                    setColumnValue(Column.uid, data, complete, i, uid);
                    setColumnValue(Column.gid, data, complete, i, gid);
                    setColumnValue(Column.uname, data, complete, i, uname);
                    setColumnValue(Column.grname, data, complete, i, grname);
                } else {
                    for (int n = 0; n < Column.VALUES.length; n++) {
                        setNA(Column.VALUES[n], data, i);
                        complete[n] = false;
                    }
                }
            }
            for (int n = 0; n < Column.VALUES.length; n++) {
                data[n] = createColumnResult(Column.VALUES[n], data[n], complete[n]);
            }
            return RDataFactory.createList(data, NAMES_VECTOR);
        }

        private static int getTimeInSecs(Object fileTime) {
            if (fileTime == null) {
                return RRuntime.INT_NA;
            } else {
                return (int) ((FileTime) fileTime).toMillis() / 1000;
            }
        }

        private static Object createColumnData(Column column, int vecLength) {
            // @formatter:off
            switch(column) {
                case size: return new double[vecLength];
                case isdir: return new byte[vecLength];
                case mode: case mtime: case ctime: case atime:
                case uid: case gid: return new int[vecLength];
                case uname: case grname: return new String[vecLength];
                default: throw RInternalError.shouldNotReachHere();
            }
            // @formatter:on
        }

        private static void updateComplete(int slot, boolean[] complete, boolean update) {
            if (complete[slot]) {
                complete[slot] = update;
            }
        }

        private static void setColumnValue(Column column, Object[] data, boolean[] complete, int index, Object value) {
            int slot = column.ordinal();
            // @formatter:off
            switch(column) {
                case size: ((double[]) data[slot])[index] = (double) value; updateComplete(slot, complete, (double) value != RRuntime.DOUBLE_NA); return;
                case isdir: ((byte[]) data[slot])[index] = (byte) value; updateComplete(slot, complete, (byte) value != RRuntime.LOGICAL_NA); return;
                case mode: case mtime: case ctime: case atime:
                case uid: case gid: ((int[]) data[slot])[index] = (int) value; updateComplete(slot, complete, (int) value != RRuntime.INT_NA); return;
                case uname: case grname: ((String[]) data[slot])[index] = (String) value; updateComplete(slot, complete, (String) value != RRuntime.STRING_NA); return;
                default: throw RInternalError.shouldNotReachHere();
            }
            // @formatter:on
        }

        private static void setNA(Column column, Object[] data, int index) {
            int slot = column.ordinal();
            // @formatter:off
            switch(column) {
                case size: ((double[]) data[slot])[index] = RRuntime.DOUBLE_NA; return;
                case isdir: ((byte[]) data[slot])[index] = RRuntime.LOGICAL_NA; return;
                case mode: case mtime: case ctime: case atime:
                case uid: case gid: ((int[]) data[slot])[index] = RRuntime.INT_NA; return;
                case uname: case grname: ((String[]) data[slot])[index] = RRuntime.STRING_NA; return;
                default: throw RInternalError.shouldNotReachHere();
            }
            // @formatter:on
        }

        private static Object createColumnResult(Column column, Object data, boolean complete) {
            // @formatter:off
            switch(column) {
                case size: return RDataFactory.createDoubleVector((double[]) data, complete);
                case isdir: return RDataFactory.createLogicalVector((byte[]) data, complete);
                case mode: RIntVector res = RDataFactory.createIntVector((int[]) data, complete); res.setClassAttr(OCTMODE, false); return res;
                case mtime: case ctime: case atime:
                case uid: case gid: return RDataFactory.createIntVector((int[]) data, complete);
                case uname: case grname: return RDataFactory.createStringVector((String[]) data, complete);
                default: throw RInternalError.shouldNotReachHere();
            }
            // @formatter:on
        }
    }

    abstract static class FileLinkAdaptor extends RBuiltinNode {
        protected Object doFileLink(RAbstractStringVector vecFrom, RAbstractStringVector vecTo, boolean symbolic) {
            int lenFrom = vecFrom.getLength();
            int lenTo = vecTo.getLength();
            if (lenFrom < 1) {
                throw RError.error(this, RError.Message.NOTHING_TO_LINK);
            }
            if (lenTo < 1) {
                return RDataFactory.createLogicalVector(0);
            }
            int len = lenFrom > lenTo ? lenFrom : lenTo;
            FileSystem fileSystem = FileSystems.getDefault();
            byte[] status = new byte[len];
            for (int i = 0; i < len; i++) {
                String from = vecFrom.getDataAt(i % lenFrom);
                String to = vecTo.getDataAt(i % lenTo);
                if (RRuntime.isNA(from) || RRuntime.isNA(to)) {
                    status[i] = RRuntime.LOGICAL_FALSE;
                } else {
                    Path fromPath = fileSystem.getPath(Utils.tildeExpand(from));
                    Path toPath = fileSystem.getPath(Utils.tildeExpand(to));
                    status[i] = RRuntime.LOGICAL_TRUE;
                    try {
                        if (symbolic) {
                            Files.createSymbolicLink(toPath, fromPath);
                        } else {
                            Files.createLink(toPath, fromPath);
                        }
                    } catch (UnsupportedOperationException | IOException ex) {
                        status[i] = RRuntime.LOGICAL_FALSE;
                        RError.warning(this, RError.Message.FILE_CANNOT_LINK, from, to, ex.getMessage());
                    }
                }
            }
            return RDataFactory.createLogicalVector(status, RDataFactory.COMPLETE_VECTOR);
        }
    }

    @RBuiltin(name = "file.link", kind = INTERNAL, parameterNames = {"from", "to"})
    public abstract static class FileLink extends FileLinkAdaptor {
        @Specialization
        @TruffleBoundary
        protected Object doFileLink(RAbstractStringVector vecFrom, RAbstractStringVector vecTo) {
            controlVisibility();
            return doFileLink(vecFrom, vecTo, false);
        }

        @Fallback
        @TruffleBoundary
        protected Object doFileLink(@SuppressWarnings("unused") Object from, @SuppressWarnings("unused") Object to) {
            controlVisibility();
            throw RError.error(this, RError.Message.INVALID_ARGUMENT, "file");
        }
    }

    @RBuiltin(name = "file.symlink", kind = INTERNAL, parameterNames = {"from", "to"})
    public abstract static class FileSymLink extends FileLinkAdaptor {
        @Specialization
        @TruffleBoundary
        protected Object doFileSymLink(RAbstractStringVector vecFrom, RAbstractStringVector vecTo) {
            controlVisibility();
            return doFileLink(vecFrom, vecTo, true);
        }

        @Fallback
        @TruffleBoundary
        protected Object doFileSymLink(@SuppressWarnings("unused") Object from, @SuppressWarnings("unused") Object to) {
            controlVisibility();
            throw RError.error(this, RError.Message.INVALID_ARGUMENT, "file");
        }
    }

    @RBuiltin(name = "file.remove", kind = INTERNAL, parameterNames = {"vec"})
    public abstract static class FileRemove extends RBuiltinNode {

        @Specialization
        @TruffleBoundary
        protected Object doFileRemove(RAbstractStringVector vec) {
            controlVisibility();
            byte[] status = new byte[vec.getLength()];
            for (int i = 0; i < status.length; i++) {
                String path = vec.getDataAt(i);
                if (RRuntime.isNA(path)) {
                    status[i] = RRuntime.LOGICAL_FALSE;
                } else {
                    File f = new File(Utils.tildeExpand(path));
                    boolean ok = f.delete();
                    status[i] = RRuntime.asLogical(ok);
                    if (!ok) {
                        RError.warning(this, RError.Message.FILE_CANNOT_REMOVE, path);
                    }
                }
            }
            return RDataFactory.createLogicalVector(status, RDataFactory.COMPLETE_VECTOR);
        }

        @Fallback
        @TruffleBoundary
        protected Object doFileRemove(@SuppressWarnings("unused") Object x) {
            controlVisibility();
            throw RError.error(this, RError.Message.INVALID_ARGUMENT, "file");
        }
    }

    @RBuiltin(name = "file.rename", kind = INTERNAL, parameterNames = {"from", "to"})
    public abstract static class FileRename extends RBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected Object doFileRename(RAbstractStringVector vecFrom, RAbstractStringVector vecTo) {
            controlVisibility();
            int len = vecFrom.getLength();
            if (len != vecTo.getLength()) {
                throw RError.error(this, RError.Message.FROM_TO_DIFFERENT);
            }
            byte[] status = new byte[len];
            for (int i = 0; i < len; i++) {
                String from = vecFrom.getDataAt(i);
                String to = vecTo.getDataAt(i);
                // GnuR's behavior regarding NA is quite inconsistent (error, warning, ignored)
                // we choose ignore
                if (RRuntime.isNA(from) || RRuntime.isNA(to)) {
                    status[i] = RRuntime.LOGICAL_FALSE;
                } else {
                    boolean ok = new File(Utils.tildeExpand(from)).renameTo(new File(Utils.tildeExpand(to)));
                    status[i] = RRuntime.asLogical(ok);
                    if (!ok) {
                        RError.warning(this, RError.Message.FILE_CANNOT_RENAME, from, to);
                    }
                }
            }
            return RDataFactory.createLogicalVector(status, RDataFactory.COMPLETE_VECTOR);
        }

        @Fallback
        @TruffleBoundary
        protected Object doFileRename(@SuppressWarnings("unused") Object from, @SuppressWarnings("unused") Object to) {
            controlVisibility();
            throw RError.error(this, RError.Message.INVALID_ARGUMENT, "file");
        }
    }

    @RBuiltin(name = "file.exists", kind = INTERNAL, parameterNames = {"vec"})
    public abstract static class FileExists extends RBuiltinNode {

        @Specialization
        @TruffleBoundary
        protected Object doFileExists(RAbstractStringVector vec) {
            controlVisibility();
            byte[] status = new byte[vec.getLength()];
            for (int i = 0; i < status.length; i++) {
                String path = vec.getDataAt(i);
                if (RRuntime.isNA(path)) {
                    status[i] = RRuntime.LOGICAL_FALSE;
                } else {
                    File f = new File(Utils.tildeExpand(path));
                    // TODO R's notion of exists may not match Java - check
                    status[i] = f.exists() ? RRuntime.LOGICAL_TRUE : RRuntime.LOGICAL_FALSE;
                }
            }
            return RDataFactory.createLogicalVector(status, RDataFactory.COMPLETE_VECTOR);
        }

        @Fallback
        protected Object doFileExists(@SuppressWarnings("unused") Object vec) {
            controlVisibility();
            throw RError.error(this, RError.Message.INVALID_ARGUMENT, "file");
        }

    }

    // TODO Implement all the options
    @RBuiltin(name = "list.files", kind = INTERNAL, parameterNames = {"path", "pattern", "all.files", "full.names", "recursive", "ignore.case", "include.dirs", "no.."})
    public abstract static class ListFiles extends RBuiltinNode {
        private static final String DOT = ".";
        private static final String DOTDOT = "..";

        @SuppressWarnings("unused")
        @Specialization
        @TruffleBoundary
        protected RStringVector doListFiles(RAbstractStringVector vec, RNull patternVec, byte allFiles, byte fullNames, byte recursive, byte ignoreCase, byte includeDirs, byte noDotDot) {
            return doListFilesBody(vec, null, allFiles, fullNames, recursive, ignoreCase, includeDirs, noDotDot);
        }

        @Specialization
        @TruffleBoundary
        protected RStringVector doListFiles(RAbstractStringVector vec, RAbstractStringVector patternVec, byte allFiles, byte fullNames, byte recursive, byte ignoreCase, byte includeDirs, byte noDotDot) {
            /*
             * Pattern in first element of vector, remaining elements are ignored (as per GnuR).
             * N.B. The pattern matches file names not paths, which means we cannot just use the
             * Java File path matcher.
             */

            String pattern = null;
            if (!(patternVec.getLength() == 0 || patternVec.getDataAt(0).length() == 0)) {
                pattern = patternVec.getDataAt(0);
            }
            return doListFilesBody(vec, pattern, allFiles, fullNames, recursive, ignoreCase, includeDirs, noDotDot);
        }

        protected RStringVector doListFilesBody(RAbstractStringVector vec, String patternString, byte allFilesL, byte fullNamesL, byte recursiveL, byte ignoreCaseL, byte includeDirsL, byte noDotDotL) {
            controlVisibility();
            boolean allFiles = RRuntime.fromLogical(allFilesL);
            boolean fullNames = RRuntime.fromLogical(fullNamesL);
            boolean recursive = RRuntime.fromLogical(recursiveL);
            @SuppressWarnings("unused")
            boolean ignoreCase = check(ignoreCaseL, "ignoreCase");
            boolean includeDirs = !recursive || RRuntime.fromLogical(includeDirsL);
            boolean noDotDot = RRuntime.fromLogical(noDotDotL);
            Pattern pattern = patternString == null ? null : Pattern.compile(patternString);
            // Curiously the result is not a vector of same length as the input,
            // as typical for R, but a single vector, which means duplicates may occur
            ArrayList<String> files = new ArrayList<>();
            for (int i = 0; i < vec.getLength(); i++) {
                String vecPathString = vec.getDataAt(i);
                String pathString = Utils.tildeExpand(vecPathString, true);
                File root = new File(pathString);
                if (!root.exists()) {
                    continue;
                }
                Path rootPath = root.toPath();
                try (Stream<Path> stream = Files.find(rootPath, recursive ? Integer.MAX_VALUE : 1, new FileMatcher(pattern, allFiles, includeDirs))) {
                    Iterator<Path> iter = stream.iterator();
                    Path vecPath = null;
                    if (!fullNames) {
                        vecPath = FileSystems.getDefault().getPath(vecPathString);
                    }
                    while (iter.hasNext()) {
                        Path file = iter.next();
                        if (Files.isSameFile(file, rootPath)) {
                            continue;
                        }
                        if (!fullNames) {
                            file = vecPath.relativize(file);
                        }
                        files.add(file.toString());
                    }
                    /*
                     * Annoyingly "." and ".." are never visited by Files.find, so we have to
                     * process them manually.
                     */
                    if (!noDotDot) {
                        if (pattern == null || pattern.matcher(DOT).find()) {
                            files.add(fullNames ? FileSystems.getDefault().getPath(vecPathString, DOT).toString() : DOT);
                        }
                        if (pattern == null || pattern.matcher(DOTDOT).find()) {
                            files.add(fullNames ? FileSystems.getDefault().getPath(vecPathString, DOTDOT).toString() : DOTDOT);
                        }
                    }
                } catch (IOException ex) {
                    // ignored
                }
            }
            if (files.size() == 0) {
                // The manual says "" but GnuR returns an empty vector
                return RDataFactory.createEmptyStringVector();
            } else {
                String[] data = new String[files.size()];
                files.toArray(data);
                Arrays.sort(data);
                return RDataFactory.createStringVector(data, RDataFactory.COMPLETE_VECTOR);
            }
        }

        private boolean check(byte valueLogical, String argName) {
            boolean value = RRuntime.fromLogical(valueLogical);
            if (value) {
                RError.warning(this, RError.Message.GENERIC, "'" + argName + "'" + " is not implemented");
            }
            return value;
        }

        private static class FileMatcher implements BiPredicate<Path, BasicFileAttributes> {
            final Pattern pattern;
            final boolean includeDirs;
            final boolean allFiles;

            FileMatcher(Pattern pattern, boolean allFiles, boolean includeDirs) {
                this.allFiles = allFiles;
                this.includeDirs = includeDirs;
                this.pattern = pattern;
            }

            public boolean test(Path path, BasicFileAttributes u) {
                if (u.isDirectory() && !includeDirs) {
                    return false;
                }
                if (!allFiles && path.getFileName().toString().charAt(0) == '.') {
                    return false;
                }
                if (pattern == null) {
                    return true;
                }
                Matcher m = pattern.matcher(path.getFileName().toString());
                boolean result = m.find();
                return result;
            }
        }
    }

    @RBuiltin(name = "list.dirs", kind = INTERNAL, parameterNames = {"path", "full.names", "recursive"})
    public abstract static class ListDirs extends RBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected RStringVector listDirs(RAbstractStringVector paths, byte fullNamesL, byte recursiveL) {
            boolean fullNames = RRuntime.fromLogical(fullNamesL);
            boolean recursive = RRuntime.fromLogical(recursiveL);
            ArrayList<String> dirList = new ArrayList<>();
            for (int i = 0; i < paths.getLength(); i++) {
                String vecPathString = paths.getDataAt(i);
                String pathString = Utils.tildeExpand(vecPathString, true);
                File root = new File(pathString);
                if (!root.exists()) {
                    continue;
                }
                Path path = root.toPath();
                try (Stream<Path> stream = Files.find(path, recursive ? Integer.MAX_VALUE : 1, new FileMatcher())) {
                    Iterator<Path> iter = stream.iterator();
                    Path vecPath = null;
                    if (!fullNames) {
                        FileSystem fileSystem = FileSystems.getDefault();
                        vecPath = fileSystem.getPath(vecPathString);
                    }
                    while (iter.hasNext()) {
                        Path dir = iter.next();
                        if (!fullNames) {
                            dir = vecPath.relativize(dir);
                        }
                        dirList.add(dir.toString());
                    }
                } catch (IOException ex) {
                    // ignored
                }
            }
            String[] data = new String[dirList.size()];
            dirList.toArray(data);
            Arrays.sort(data);
            return RDataFactory.createStringVector(data, RDataFactory.COMPLETE_VECTOR);
        }

        private static class FileMatcher implements BiPredicate<Path, BasicFileAttributes> {
            public boolean test(Path path, BasicFileAttributes u) {
                boolean result = u.isDirectory();
                return result;
            }
        }
    }

    // TODO handle the general case, which is similar to paste
    @RBuiltin(name = "file.path", kind = INTERNAL, parameterNames = {"paths", "fsep"})
    public abstract static class FilePath extends RBuiltinNode {

        @Child private CastStringNode castStringNode;

        private CastStringNode initCastStringNode() {
            if (castStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castStringNode = insert(CastStringNodeGen.create(false, false, false, false));
            }
            return castStringNode;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "lengthZero(vec)")
        @TruffleBoundary
        protected RStringVector doFilePathZero(RList vec, RAbstractStringVector fsep) {
            return RDataFactory.createEmptyStringVector();
        }

        @Specialization(guards = "!lengthZero(args)")
        protected RStringVector doFilePath(RList args, RAbstractStringVector fsepVec) {
            Object[] argValues = args.getDataWithoutCopying();
            int resultLength = 0;
            for (int i = 0; i < argValues.length; i++) {
                Object elem = argValues[i];
                int argLength;
                if (elem instanceof RAbstractContainer) {
                    argLength = ((RAbstractContainer) elem).getLength();
                } else {
                    argLength = 1;
                }
                if (argLength > resultLength) {
                    resultLength = argLength;
                }
            }
            if (resultLength == 0) {
                return RDataFactory.createEmptyStringVector();
            }
            String[] result = new String[resultLength];
            String[][] inputs = new String[argValues.length][];
            for (int i = 0; i < argValues.length; i++) {
                Object elem = argValues[i];
                if (!(elem instanceof String || elem instanceof RStringVector)) {
                    elem = initCastStringNode().executeString(elem);
                }
                if (elem instanceof String) {
                    inputs[i] = new String[]{(String) elem};
                } else if (elem instanceof RStringVector) {
                    inputs[i] = ((RStringVector) elem).getDataWithoutCopying();
                } else {
                    RInternalError.shouldNotReachHere();
                }
            }
            String fsep = fsepVec.getDataAt(0);
            for (int i = 0; i < resultLength; i++) {
                String path = "";
                for (int j = 0; j < inputs.length; j++) {
                    path += inputs[j][i % inputs[j].length];
                    if (j != inputs.length - 1) {
                        path += fsep;
                    }

                }
                result[i] = path;
            }
            return RDataFactory.createStringVector(result, RDataFactory.COMPLETE_VECTOR);
        }

        public static boolean lengthZero(RList list) {
            if (list.getLength() == 0) {
                return true;
            }
            for (int i = 0; i < list.getLength(); i++) {
                Object elem = list.getDataAt(i);
                if (elem instanceof RAbstractContainer) {
                    if (((RAbstractContainer) elem).getLength() == 0) {
                        return true;
                    }
                }
            }
            return false;
        }

    }

    /**
     * {@code file.copy} builtin. This is only called when the target is a directory.
     */
    @RBuiltin(name = "file.copy", kind = INTERNAL, parameterNames = {"from", "to", "overwrite", "recursive", "copy.mode", "copy.date"})
    public abstract static class FileCopy extends RBuiltinNode {

        @Override
        protected void createCasts(CastBuilder casts) {
            casts.toLogical(2).toLogical(3).toLogical(4).toLogical(5);
        }

        protected boolean checkLogical(byte value, String name) throws RError {
            if (RRuntime.isNA(value)) {
                throw RError.error(this, RError.Message.INVALID_ARGUMENT, name);
            } else {
                return RRuntime.fromLogical(value);
            }
        }

        @Specialization
        @TruffleBoundary
        protected RLogicalVector fileCopy(RAbstractStringVector vecFrom, RAbstractStringVector vecTo, byte overwriteArg, byte recursiveArg, byte copyModeArg, byte copyDateArg) {
            int lenFrom = vecFrom.getLength();
            byte[] status = new byte[lenFrom];
            if (lenFrom > 0) {
                int lenTo = vecTo.getLength();
                if (lenTo != 1) {
                    throw RError.error(this, RError.Message.INVALID_ARGUMENT, "to");
                }
                boolean overWrite = checkLogical(overwriteArg, "overwrite");
                boolean recursive = checkLogical(recursiveArg, "recursive");
                boolean copyMode = checkLogical(copyModeArg, "copy.mode");
                boolean copyDate = checkLogical(copyDateArg, "copy.dates");

                // Java cannot distinguish copy.mode and copy.dates
                CopyOption[] copyOptions;
                if (copyMode || copyDate) {
                    copyOptions = new CopyOption[overWrite ? 2 : 1];
                    copyOptions[overWrite ? 1 : 0] = StandardCopyOption.COPY_ATTRIBUTES;
                } else if (overWrite) {
                    copyOptions = new CopyOption[1];
                } else {
                    copyOptions = new CopyOption[0];
                }
                if (overWrite) {
                    copyOptions[0] = StandardCopyOption.REPLACE_EXISTING;
                }
                FileSystem fileSystem = FileSystems.getDefault();
                Path toDir = null;
                if (vecTo.getLength() == 1) {
                    Path vecTo0Path = fileSystem.getPath(Utils.tildeExpand(vecTo.getDataAt(0)));
                    if (Files.isDirectory(vecTo0Path)) {
                        toDir = vecTo0Path;
                    }
                }
                if (recursive) {
                    if (toDir == null) {
                        RError.warning(this, RError.Message.FILE_COPY_RECURSIVE_IGNORED);
                        recursive = false;
                    }
                }

                for (int i = 0; i < lenFrom; i++) {
                    String from = vecFrom.getDataAt(i % lenFrom);
                    String to = vecTo.getDataAt(i % lenTo);
                    Path fromPathKeepRel = fileSystem.getPath(Utils.tildeExpand(from, true));
                    if (toDir != null && !fromPathKeepRel.isAbsolute()) {
                        to = toDir.resolve(fromPathKeepRel.getFileName()).toString();
                    }
                    Path fromPath = fileSystem.getPath(Utils.tildeExpand(from));
                    Path toPath = fileSystem.getPath(Utils.tildeExpand(to));
                    status[i] = RRuntime.LOGICAL_TRUE;
                    try {
                        if (recursive && Files.isDirectory(fromPath)) {
                            // to is just one dir (checked above)
                            boolean copyError = copyDir(fromPath, toPath, copyOptions);
                            if (copyError) {
                                status[i] = RRuntime.LOGICAL_FALSE;
                            }
                        } else {
                            // copy to existing files is skipped unless overWrite
                            if (!Files.exists(toPath) || overWrite) {
                                /*
                                 * Be careful if toPath is a directory, if empty Java will replace
                                 * it with a plain file, otherwise the copy will fail
                                 */
                                if (Files.isDirectory(toPath)) {
                                    Path fromFileNamePath = fromPath.getFileName();
                                    toPath = toPath.resolve(fromFileNamePath);
                                }
                                Files.copy(fromPath, toPath, copyOptions);
                            }
                        }
                    } catch (UnsupportedOperationException | IOException ex) {
                        status[i] = RRuntime.LOGICAL_FALSE;
                        RError.warning(this, RError.Message.FILE_CANNOT_COPY, from, to, ex.getMessage());
                    }
                }
            }
            return RDataFactory.createLogicalVector(status, RDataFactory.COMPLETE_VECTOR);
        }

        private static final class DirCopy extends SimpleFileVisitor<Path> {
            private final Path fromDir;
            private final Path toDir;
            private final CopyOption[] copyOptions;
            private boolean error;

            private DirCopy(Path fromDir, Path toDir, CopyOption[] copyOptions) {
                this.fromDir = fromDir;
                this.toDir = toDir;
                this.copyOptions = copyOptions;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                Path newDir = toDir.resolve(fromDir.relativize(fromDir));
                try {
                    Files.copy(dir, newDir, copyOptions);
                } catch (FileAlreadyExistsException x) {
                    // ok
                } catch (IOException ex) {
                    error = true;
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path newFile = toDir.resolve(fromDir.relativize(file));
                Files.copy(file, newFile, copyOptions);
                return FileVisitResult.CONTINUE;
            }
        }

        @SuppressWarnings("static-method")
        private boolean copyDir(Path fromDir, Path toDir, CopyOption[] copyOptions) throws IOException {
            DirCopy dirCopy = new DirCopy(fromDir, toDir, copyOptions);
            Files.walkFileTree(fromDir, dirCopy);
            return dirCopy.error;
        }
    }

    abstract static class XyzNameAdapter extends RBuiltinNode {

        protected RStringVector doXyzName(RAbstractStringVector vec, BiFunction<FileSystem, String, String> fun) {
            FileSystem fileSystem = FileSystems.getDefault();
            boolean complete = RDataFactory.COMPLETE_VECTOR;
            String[] data = new String[vec.getLength()];
            for (int i = 0; i < data.length; i++) {
                String name = vec.getDataAt(i);
                if (RRuntime.isNA(name)) {
                    data[i] = name;
                    complete = RDataFactory.INCOMPLETE_VECTOR;
                } else if (name.length() == 0) {
                    data[i] = name;
                } else {
                    data[i] = fun.apply(fileSystem, name);
                }
            }
            return RDataFactory.createStringVector(data, complete);
        }
    }

    @RBuiltin(name = "dirname", kind = INTERNAL, parameterNames = {"path"})
    public abstract static class DirName extends XyzNameAdapter {
        @Specialization
        @TruffleBoundary
        protected RStringVector doDirName(RAbstractStringVector vec) {
            return doXyzName(vec, (fileSystem, name) -> {
                Path path = fileSystem.getPath(Utils.tildeExpand(name));
                Path parent = path.getParent();
                return parent != null ? parent.toString() : ".";
            });
        }
    }

    @RBuiltin(name = "basename", kind = INTERNAL, parameterNames = {"path"})
    public abstract static class BaseName extends XyzNameAdapter {
        @Specialization
        @TruffleBoundary
        protected RStringVector doDirName(RAbstractStringVector vec) {
            return doXyzName(vec, (fileSystem, name) -> {
                Path path = fileSystem.getPath(name);
                Path parent = path.getFileName();
                return parent != null ? parent.toString() : name;
            });
        }
    }

    @RBuiltin(name = "unlink", kind = INTERNAL, parameterNames = {"x", "recursive", "force"})
    public abstract static class Unlink extends RInvisibleBuiltinNode {

        @Override
        protected void createCasts(CastBuilder casts) {
            casts.toLogical(1).toLogical(2);
        }

        protected boolean checkLogical(byte value, String name) throws RError {
            if (RRuntime.isNA(value)) {
                throw RError.error(this, RError.Message.INVALID_ARGUMENT, name);
            } else {
                return RRuntime.fromLogical(value);
            }
        }

        @Specialization
        @TruffleBoundary
        protected int doUnlink(RAbstractStringVector vec, byte recursiveArg, byte forceArg) {
            @SuppressWarnings("unused")
            boolean force = checkLogical(forceArg, "force");
            boolean recursive = checkLogical(recursiveArg, "recursive");
            int result = 1;
            FileSystem fileSystem = FileSystems.getDefault();
            for (int i = -0; i < vec.getLength(); i++) {
                String pathPattern = Utils.tildeExpand(vec.getDataAt(i));
                if (pathPattern.length() == 0 || RRuntime.isNA(pathPattern)) {
                    continue;
                }
                if (containsGlobChar(pathPattern) >= 0) {
                    throw RError.nyi(this, "wildcards");
                }
                Path path = fileSystem.getPath(pathPattern);
                if (Files.isDirectory(path)) {
                    if (!recursive) {
                        continue;
                    } else {
                        result = recursiveDelete(path);
                    }
                }
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    result = 0;
                }
            }
            return result;
        }

        private static final char[] GLOBCHARS = new char[]{'*', '?', '['};

        private static int containsGlobChar(String pathPattern) {
            for (int i = 0; i < pathPattern.length(); i++) {
                char ch = pathPattern.charAt(i);
                for (int j = 0; j < GLOBCHARS.length; j++) {
                    if (ch == GLOBCHARS[j]) {
                        return i;
                    }
                }
            }
            return -1;
        }

        private int recursiveDelete(Path path) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        recursiveDelete(entry);
                    }
                    Files.deleteIfExists(entry);
                }
                return 1;
            } catch (IOException ex) {
                return 0;
            }

        }

        @SuppressWarnings("unused")
        @Fallback
        protected int doUnlink(Object vec, Object recursive, Object force) {
            throw RError.nyi(this, "unlink");
        }

        public static boolean simpleArgs(@SuppressWarnings("unused") RAbstractStringVector vec, byte recursive, byte force) {
            return recursive == RRuntime.LOGICAL_FALSE && force == RRuntime.LOGICAL_FALSE;
        }
    }

    @RBuiltin(name = "dir.create", kind = INTERNAL, parameterNames = {"path", "showWarnings", "recursive", "mode"})
    public abstract static class DirCreate extends RInvisibleBuiltinNode {
        @TruffleBoundary
        @Specialization
        protected byte dirCreate(RAbstractStringVector pathVec, byte showWarnings, byte recursive, RIntVector octMode) {
            controlVisibility();
            boolean ok = true;
            if (pathVec.getLength() != 1) {
                throw RError.error(this, RError.Message.INVALID_ARGUMENT, "path");
            }
            String path = Utils.tildeExpand(pathVec.getDataAt(0));
            if (RRuntime.fromLogical(recursive)) {
                ok = mkparentdirs(new File(path).getAbsoluteFile().getParentFile(), showWarnings, octMode.getDataAt(0));
            }
            if (ok) {
                ok = mkdir(path, showWarnings, octMode.getDataAt(0));
            }
            return RRuntime.asLogical(ok);
        }

        protected boolean mkparentdirs(File file, byte showWarnings, int mode) {
            if (file.isDirectory()) {
                return true;
            }
            if (file.exists()) {
                return false;
            }
            if (mkparentdirs(file.getParentFile(), showWarnings, mode)) {
                return mkdir(file.getAbsolutePath(), showWarnings, mode);
            } else {
                return false;
            }
        }

        protected boolean mkdir(String path, byte showWarnings, int mode) {
            try {
                RFFIFactory.getRFFI().getBaseRFFI().mkdir(path, mode);
                return true;
            } catch (IOException ex) {
                if (RRuntime.fromLogical(showWarnings)) {
                    RError.warning(this, RError.Message.DIR_CANNOT_CREATE, path);
                }
                return false;
            }
        }
    }
}
