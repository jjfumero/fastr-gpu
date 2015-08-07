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
package com.oracle.truffle.r.runtime;

import java.io.*;
import java.util.*;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.r.runtime.RContext.ContextState;
import com.oracle.truffle.r.runtime.conn.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.RAttributes.RAttribute;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.env.*;
import com.oracle.truffle.r.runtime.gnur.*;
import com.oracle.truffle.r.runtime.instrument.*;

// Code loosely transcribed from GnuR serialize.c.

/**
 * Serialize/unserialize.
 *
 * It is sometimes convenient when debugging to trace the serialization process, particularly when
 * unserializing an object created by GnurR. The following options are available. N.B. These should
 * normally be set in the console using the {@code fastr.debug} function as a great deal of
 * unserialization happens on startup.
 * <p>
 * Debugging options:
 * <ul>
 * <li>unserialize: trace the input as it is read</li>
 * <li>printUclosure: print the pairlist resulting from unserializing an object of type CLOSXP.</li>
 * <li>printWclosure: print the pairlist that will be written when serializing a CLOSXP.</li>
 * </ul>
 * N.B. All output goes to the Java standard output. Once {@code printUclosure} is set all lazily
 * loaded functions will print, e.g. calling {@code quit()} will print the pairlist for the
 * {@code quit} function.
 *
 */
// Checkstyle: stop final class check
public class RSerialize implements RContext.StateFactory {

    private static class Flags {
        static final int IS_OBJECT_BIT_MASK = 1 << 8;
        static final int HAS_ATTR_BIT_MASK = 1 << 9;
        static final int HAS_TAG_BIT_MASK = 1 << 10;
        static final int TYPE_MASK = 255;
        static final int LEVELS_SHIFT = 12;

        private Flags() {
            // prevent construction
        }

        public static int ptype(int flagsValue) {
            return flagsValue & Flags.TYPE_MASK;
        }

        @SuppressWarnings("unused")
        public static int plevs(int flagsValue) {
            return flagsValue >> Flags.LEVELS_SHIFT;
        }

        @SuppressWarnings("unused")
        public static boolean isObj(int flagsValue) {
            return (flagsValue & IS_OBJECT_BIT_MASK) != 0;
        }

        public static boolean hasAttr(int flagsValue) {
            return (flagsValue & HAS_ATTR_BIT_MASK) != 0;
        }

        public static boolean hasTag(int flagsValue) {
            return (flagsValue & HAS_TAG_BIT_MASK) != 0;
        }

        public static int packFlags(SEXPTYPE type, @SuppressWarnings("unused") int levs, boolean isObj, boolean hasAttr, boolean hasTag) {
            // TODO levs
            int val = type.code;
            if (isObj) {
                val |= IS_OBJECT_BIT_MASK;
            }
            if (hasAttr) {
                val |= HAS_ATTR_BIT_MASK;
            }
            if (hasTag) {
                val |= HAS_TAG_BIT_MASK;
            }
            return val;
        }
    }

    /**
     * Provides access to the underlying byte array.
     */
    private static class PByteArrayInputStream extends ByteArrayInputStream {

        public PByteArrayInputStream(byte[] buf) {
            super(buf);
        }

        byte[] getData() {
            return buf;
        }

        int pos() {
            return pos;
        }
    }

    public interface CallHook {
        Object eval(Object arg);
    }

    private static class ContextStateImpl implements RContext.ContextState {
        /**
         * {@code true} iff we are saving the source from the deparse of an unserialized function
         * (for debugging later).
         */
        private boolean saveDeparse;

        /**
         * {@code ...getNamespace} in "namespace.R", used to callback to handle a
         * {@link SEXPTYPE#NAMESPACESXP} item.
         */
        private RFunction dotDotFindNamespace;

        /**
         * Initialize and return the value of {@link #dotDotFindNamespace}. This is lazy because
         * when this instance is created, the {@link REnvironment} context state has not been set
         * up, so we can't look up anything in the base env.
         */
        RFunction getDotDotFindNamespace() {
            if (dotDotFindNamespace == null) {
                Object f = REnvironment.baseEnv().findFunction("..getNamespace");
                dotDotFindNamespace = (RFunction) RContext.getRRuntimeASTAccess().forcePromise(f);
            }
            return dotDotFindNamespace;
        }
    }

    public ContextState newContext(RContext context, Object... objects) {
        return new ContextStateImpl();
    }

    private static final int MAX_PACKED_INDEX = Integer.MAX_VALUE >> 8;

    private static int packRefIndex(int i) {
        return (i << 8) | SEXPTYPE.REFSXP.code;
    }

    private static int unpackRefIndex(int i) {
        return i >> 8;
    }

    private abstract static class Common {

        private static final Object ENV_PLACEHOLDER = new Object();
        protected Object[] refTable = new Object[128];
        protected int refTableIndex;
        protected final CallHook hook;
        protected final int frameDepth;
        protected final ContextStateImpl contextState;

        protected Common(CallHook hook, int frameDepth) {
            this.hook = hook;
            this.frameDepth = frameDepth;
            this.contextState = getContextState();
        }

        protected static IOException formatError(byte format, boolean ok) throws IOException {
            throw new IOException("serialized stream format " + (ok ? "not implemented" : "not recognized") + ": " + format);
        }

        protected Object addReadRef(Object item) {
            assert item != null;
            if (refTableIndex >= refTable.length) {
                Object[] newRefTable = new Object[2 * refTable.length];
                System.arraycopy(refTable, 0, newRefTable, 0, refTable.length);
                refTable = newRefTable;
            }
            refTable[refTableIndex++] = item;
            return item;
        }

        /**
         * Create a slot for an ENVSXP value and return the index for filling in later.
         */
        protected int addEnvReadRef() {
            int currentRefTableIndex = refTableIndex;
            addReadRef(ENV_PLACEHOLDER);
            return currentRefTableIndex;
        }

        protected void updateEnvReadRef(int index, Object item) {
            assert refTable[index] == ENV_PLACEHOLDER;
            refTable[index] = item;
        }

        protected Object getReadRef(int index) {
            assert index > 0 && index <= refTableIndex;
            return refTable[index - 1];
        }

        protected int getRefIndex(Object obj) {
            for (int i = 0; i < refTableIndex; i++) {
                if (refTable[i] == obj) {
                    return i + 1;
                }
            }
            return -1;
        }
    }

    public static final int DEFAULT_VERSION = 2;

    /**
     * Lazily read in case set during execution for debugging purposes. This is necessary because
     * setting the option on startup will trace all the standard library functions as they are
     * lazily loaded.
     */
    private static boolean trace() {
        return FastROptions.debugMatches("unserialize");
    }

    private static ContextStateImpl getContextState() {
        return (ContextStateImpl) RContext.getContextState(RContext.ClassStateKind.RSerialize);
    }

    /**
     * Supports the saving of deparsed lazily loaded package functions for instrumentation access.
     */
    public static void setSaveDeparse(boolean status) {
        ContextStateImpl serializeContextState = getContextState();
        serializeContextState.saveDeparse = status;
    }

    @TruffleBoundary
    public static Object unserialize(RConnection conn, int frameDepth) throws IOException {
        Input instance = trace() ? new TracingInput(conn, frameDepth) : new Input(conn, frameDepth);
        Object result = instance.unserialize();
        return result;
    }

    /**
     * This variant exists for the {@code lazyLoadDBFetch} function. In certain cases, when
     * {@link Input#persistentRestore} is called, an R function needs to be evaluated with an
     * argument read from the serialized stream. This is handled with a callback object.
     *
     * @param packageName the name of the package that the lozyLoad is from
     */
    @TruffleBoundary
    public static Object unserialize(byte[] data, CallHook hook, int frameDepth, String packageName) throws IOException {
        InputStream is = new PByteArrayInputStream(data);
        Input instance = trace() ? new TracingInput(is, hook, frameDepth, packageName) : new Input(is, hook, frameDepth, packageName);
        Object result = instance.unserialize();
        return result;
    }

    private static class Input extends Common {
        private static final String UNKNOWN_PACKAGE_SOURCE_PREFIX = "<package:";

        protected final PInputStream stream;
        /**
         * Only set when called from lazyLoadDBFetch. Helps to identify the package of the deparsed
         * closure.
         */
        protected final String packageName;

        /**
         * We need to know whether we are unserializing a {@link SEXPTYPE#CLOSXP} as we do not want
         * convert embedded instances of {@link SEXPTYPE#LANGSXP} into ASTs.
         */
        private int closureDepth;
        /**
         * For formula, the same logic applies as we only want to convert to an RFormula when
         * langDepth is zero.
         */
        private int langDepth;

        private Input(RConnection conn, int depth) throws IOException {
            this(conn.getInputStream(), null, depth, null);
        }

        private Input(InputStream is, CallHook hook, int depth, String packageName) throws IOException {
            super(hook, depth);
            this.packageName = packageName;
            this.closureDepth = 0;
            byte[] buf = new byte[2];
            is.read(buf);
            switch (buf[0]) {
                case 'A':
                case 'B':
                    throw formatError(buf[0], true);
                case 'X':
                    stream = new XdrInputFormat(is);
                    break;
                case '\n':
                    // special case in 'A'
                    throw formatError((byte) 'A', true);
                default:
                    throw formatError(buf[0], false);
            }
        }

        private int inRefIndex(int flags) throws IOException {
            int i = unpackRefIndex(flags);
            if (i == 0) {
                return stream.readInt();
            } else {
                return i;
            }
        }

        private Object unserialize() throws IOException {
            int version = stream.readInt();
            @SuppressWarnings("unused")
            int writerVersion = stream.readInt();
            @SuppressWarnings("unused")
            int releaseVersion = stream.readInt();
            assert version == DEFAULT_VERSION; // TODO proper error message
            Object result = readItem();
            return result;
        }

        protected Object readItem() throws IOException {
            int flags = stream.readInt();
            Object result = readItem(flags);
            assert result != null;
            return result;
        }

        private void incDepth(SEXPTYPE type) {
            switch (type) {
                case CLOSXP:
                    closureDepth++;
                    break;
                case LANGSXP:
                    langDepth++;
                    break;
                default:
                    break;
            }
        }

        protected Object readItem(int flags) throws IOException {
            Object result = null;
            SEXPTYPE type = SEXPTYPE.mapInt(Flags.ptype(flags));
            switch (type) {
                case NILVALUE_SXP:
                    return RNull.instance;

                case EMPTYENV_SXP:
                    return REnvironment.emptyEnv();

                case BASEENV_SXP:
                    return REnvironment.baseEnv();

                case GLOBALENV_SXP:
                    return REnvironment.globalEnv();

                case MISSINGARG_SXP:
                    return RMissing.instance;

                case BASENAMESPACE_SXP:
                    return REnvironment.baseNamespaceEnv();

                case REFSXP: {
                    int index = inRefIndex(flags);
                    Object r = getReadRef(index);
                    return checkResult(r);
                }

                case NAMESPACESXP: {
                    RStringVector s = inStringVec(false);
                    /*
                     * TODO we do not record "lastname", which is passed as second argument, but
                     * only used in a warning message in the unlikely event that the namespace
                     * cannot be found.
                     */
                    Object r = RContext.getEngine().evalFunction(contextState.getDotDotFindNamespace(), s, "");
                    return checkResult(addReadRef(r));
                }

                case PERSISTSXP: {
                    RStringVector sv = inStringVec(false);
                    result = persistentRestore(sv);
                    return checkResult(addReadRef(result));
                }

                case ENVSXP: {
                    /*
                     * Behavior varies depending on whether hashtab is present, since this is
                     * optional in GnuR. Unfortunately, we MUST do an addReadRef before we know what
                     * kind of environment to create, otherwise the ref indexes can get out of sync.
                     */
                    int locked = stream.readInt();
                    /* MUST register before filling in (see serialize.c) */
                    int envRefTableIndex = addEnvReadRef();

                    Object enclos = readItem();
                    REnvironment enclosing = enclos == RNull.instance ? REnvironment.baseEnv() : (REnvironment) enclos;
                    Object frame = readItem();
                    boolean hashed = frame == RNull.instance;
                    REnvironment env;
                    Object hashtab = readItem();
                    if (hashed) {
                        if (hashtab == RNull.instance) {
                            env = RDataFactory.createNewEnv(enclosing, null);
                        } else {
                            env = RDataFactory.createNewEnv(enclosing, null, true, ((RList) hashtab).getLength());
                            RList hashList = (RList) hashtab;
                            // GnuR sizes its hash tables, empty slots indicated by RNull
                            for (int i = 0; i < hashList.getLength(); i++) {
                                Object val = hashList.getDataAt(i);
                                if (val == RNull.instance) {
                                    continue;
                                }
                                RPairList pl = (RPairList) val;
                                env.safePut(((RSymbol) pl.getTag()).getName(), pl.car());
                            }
                        }
                    } else {
                        env = RDataFactory.createNewEnv(enclosing, null);
                        while (frame != RNull.instance) {
                            RPairList pl = (RPairList) frame;
                            env.safePut(((RSymbol) pl.getTag()).getName(), pl.car());
                            frame = pl.cdr();
                        }
                    }
                    updateEnvReadRef(envRefTableIndex, env);
                    if (locked != 0) {
                        env.lock(false);
                    }
                    Object attr = readItem();
                    if (attr != RNull.instance) {
                        setAttributes(env, attr);
                    }
                    return checkResult(env);
                }

                case PACKAGESXP: {
                    RStringVector s = inStringVec(false);
                    /*
                     * TODO GnuR eval's findPackageEnv, but we don't want to eval here. That will
                     * call require, so we can only find packages that are already loaded.
                     */
                    REnvironment pkgEnv = REnvironment.lookupOnSearchPath(s.getDataAt(0));
                    if (pkgEnv == null) {
                        pkgEnv = REnvironment.globalEnv();
                    }

                    return checkResult(addReadRef(pkgEnv));
                }

                case CLOSXP:
                case LANGSXP:
                case LISTSXP:
                case PROMSXP:
                case DOTSXP: {
                    incDepth(type);
                    Object attrItem = RNull.instance;
                    Object tagItem = RNull.instance;
                    if (Flags.hasAttr(flags)) {
                        attrItem = readItem();

                    }
                    if (Flags.hasTag(flags)) {
                        tagItem = readItem();
                    }
                    Object carItem = readItem();
                    Object cdrItem = readItem();
                    RPairList pairList = RDataFactory.createPairList(carItem, cdrItem, tagItem, type);
                    result = pairList;
                    if (attrItem != RNull.instance) {
                        /*
                         * TODO Currently we are losing attributes on CLOSXP (and LANGSXP) objects
                         * because this code places the attributes on the pairList and not on the
                         * RFunction object we eventually convert the pairlist into.
                         */
                        setAttributes(pairList, attrItem);
                    }
                    if (type == SEXPTYPE.CLOSXP) {
                        closureDepth--;
                        /*
                         * Must convert the RPairList to a FastR AST We could convert to an AST
                         * directly, but it is easier and more robust to deparse and reparse. N.B.
                         * We always convert closures regardless of whether they are at top level or
                         * not (and they are not always at the top in the default packages)
                         */
                        RPairList rpl = (RPairList) result;
                        if (FastROptions.debugMatches("printUclosure")) {
                            Debug.printClosure(rpl);
                        }
                        String deparse = RDeparse.deparse(rpl);
                        try {
                            RExpression expr = parse(deparse, true);
                            if (expr == null) {
                                /*
                                 * The source did not deparse, either due to an error in the deparse
                                 * logic or an error in the FastR parser. Rather than fail, we
                                 * return a function that, if invoked, reports this as an R error.
                                 */
                                expr = createFailedDeparseExpression();
                            }
                            /*
                             * The tag of result is the enclosing environment (from NAMESPACESEXP)
                             * for the function. However the namespace is locked, so can't just eval
                             * there (and overwrite the promise), so we fix the enclosing frame up
                             * on return.
                             */
                            RFunction func = (RFunction) RContext.getEngine().eval(expr, RDataFactory.createNewEnv(REnvironment.emptyEnv(), null), frameDepth + 1);
                            // copy the function with a different enclosing frame
                            func = RDataFactory.createFunction(func.getName(), func.getTarget(), func.getRBuiltin(), ((REnvironment) rpl.getTag()).getFrame(), func.containsDispatch());
                            Source source = func.getRootNode().getSourceSection().getSource();
                            if (!source.getName().startsWith(UNKNOWN_PACKAGE_SOURCE_PREFIX)) {
                                /*
                                 * Located a function source file from which we can retrieve the
                                 * function name
                                 */
                                String funcName = RPackageSource.decodeName(source.getName());
                                func.setName(funcName);
                            }
                            copyAttributes(func, rpl.getAttributes());
                            result = func;
                        } catch (Throwable ex) {
                            throw new RInternalError(ex, "unserialize - failed to eval deparsed closure");
                        }
                    } else if (type == SEXPTYPE.LANGSXP) {
                        langDepth--;
                        /*
                         * N.B. LANGSXP values occur within CLOSXP structures, so we only want to
                         * convert them to an AST when they occur outside of a CLOSXP, as in the
                         * CLOSXP case, the entire structure is deparsed at the end. Ditto for
                         * LANGSXP when specifying a formula
                         */
                        if (closureDepth == 0 && langDepth == 0) {
                            RPairList pl = (RPairList) result;
                            String deparse = RDeparse.deparse(pl);
                            RExpression expr = parse(deparse, false);
                            assert expr.getLength() == 1;
                            result = expr.getDataAt(0);
                            RAttributes attrs = pl.getAttributes();
                            copyAttributes((RAttributable) result, attrs);
                        }
                    } else if (type == SEXPTYPE.PROMSXP) {
                        // @formatter:off
                        // TODO figure out why this causes an error on startup
                        // result = RDataFactory.createPromise(RContext.getRASTHelper().createNodeForValue(cdrItem), (REnvironment) carItem);
                        // @formatter:on
                    }
                    return checkResult(result);
                }

                /*
                 * These break out of the switch to have their ATTR, LEVELS, and OBJECT fields
                 * filled in.
                 */

                case EXPRSXP:
                case VECSXP: {
                    int len = stream.readInt();
                    // TODO long vector support?
                    assert len >= 0;
                    Object[] data = new Object[len];
                    for (int i = 0; i < len; i++) {
                        Object elem = readItem();
                        data[i] = elem;
                    }
                    if (type == SEXPTYPE.EXPRSXP) {
                        result = RDataFactory.createExpression(RDataFactory.createList(data));
                    } else {
                        // this could (ultimately) be a list, factor or dataframe
                        result = RDataFactory.createList(data);
                    }
                    break;
                }

                case STRSXP: {
                    result = inStringVec(true);
                    break;
                }

                case INTSXP: {
                    int len = stream.readInt();
                    int[] data = new int[len];
                    boolean complete = RDataFactory.COMPLETE_VECTOR;
                    for (int i = 0; i < len; i++) {
                        int intVal = stream.readInt();
                        if (intVal == RRuntime.INT_NA) {
                            complete = false;
                        }
                        data[i] = intVal;
                    }
                    result = RDataFactory.createIntVector(data, complete);
                    break;
                }

                case LGLSXP: {
                    int len = stream.readInt();
                    byte[] data = new byte[len];
                    boolean complete = RDataFactory.COMPLETE_VECTOR;
                    for (int i = 0; i < len; i++) {
                        int intVal = stream.readInt();
                        if (intVal == RRuntime.INT_NA) {
                            complete = false;
                            data[i] = RRuntime.LOGICAL_NA;
                        } else {
                            data[i] = (byte) intVal;
                        }
                    }
                    result = RDataFactory.createLogicalVector(data, complete);
                    break;
                }

                case REALSXP: {
                    int len = stream.readInt();
                    double[] data = new double[len];
                    boolean complete = RDataFactory.COMPLETE_VECTOR;
                    for (int i = 0; i < len; i++) {
                        double doubleVal = stream.readDouble();
                        if (RRuntime.isNA(doubleVal)) {
                            complete = false;
                        }
                        data[i] = doubleVal;
                    }
                    result = RDataFactory.createDoubleVector(data, complete);
                    break;
                }

                case CPLXSXP: {
                    int len = stream.readInt();
                    double[] data = new double[2 * len];
                    boolean complete = RDataFactory.COMPLETE_VECTOR;
                    for (int i = 0; i < len; i++) {
                        int ix = 2 * i;
                        double reVal = stream.readDouble();
                        if (RRuntime.isNA(reVal)) {
                            complete = false;
                        }
                        double imVal = stream.readDouble();
                        if (RRuntime.isNA(imVal)) {
                            complete = false;
                        }
                        data[ix] = reVal;
                        data[ix + 1] = imVal;
                    }
                    result = RDataFactory.createComplexVector(data, complete);
                    break;
                }

                case SPECIALSXP:
                case BUILTINSXP: {
                    int len = stream.readInt();
                    String s = stream.readString(len);
                    result = RContext.lookupBuiltin(s);
                    if (result == null) {
                        throw RInternalError.shouldNotReachHere("lookup failed in unserialize for builtin: " + s);
                    }
                    break;
                }

                case CHARSXP: {
                    int len = stream.readInt();
                    if (len == -1) {
                        return RRuntime.STRING_NA;
                    } else {
                        result = stream.readString(len);
                    }
                    break;
                }

                case SYMSXP: {
                    String name = (String) readItem();
                    result = RDataFactory.createSymbol(name);
                    addReadRef(result);
                    break;
                }

                case BCODESXP: {
                    result = readBC();
                    break;
                }

                case S4SXP: {
                    result = RDataFactory.createS4Object();
                    break;
                }

                case EXTPTRSXP: {
                    Object prot = readItem();
                    long addr = 0;
                    Object tag = readItem();
                    result = RDataFactory.createExternalPtr(addr, tag, prot);
                    addReadRef(result);
                    break;
                }

                case RAWSXP: {
                    int len = stream.readInt();
                    byte[] data = new byte[len];
                    stream.readRaw(data);
                    result = RDataFactory.createRawVector(data);
                    break;
                }

                case UNBOUNDVALUE_SXP: {
                    result = RUnboundValue.instance;
                    break;
                }

                default:
                    throw RInternalError.unimplemented();
            }
            // TODO SETLEVELS
            if (type == SEXPTYPE.CHARSXP) {
                /*
                 * With the CHARSXP cache maintained through the ATTRIB field that field has already
                 * been filled in by the mkChar/mkCharCE call above, so we need to leave it alone.
                 * If there is an attribute (as there might be if the serialized data was created by
                 * an older version) we read and ignore the value.
                 */
                if (Flags.hasAttr(flags)) {
                    readItem();
                }
            } else {
                if (Flags.hasAttr(flags)) {
                    Object attr = readItem();
                    result = setAttributes(result, attr);
                }
            }

            return checkResult(result);
        }

        private static Object checkResult(Object result) {
            assert result != null;
            return result;
        }

        private static void copyAttributes(RAttributable obj, RAttributes attrs) {
            if (attrs == null) {
                return;
            }
            Iterator<RAttribute> iter = attrs.iterator();
            while (iter.hasNext()) {
                RAttribute attr = iter.next();
                obj.setAttr(attr.getName(), attr.getValue());
            }
        }

        private RExpression parse(String deparseRaw, boolean isClosure) throws IOException {
            try {
                String sourcePath = null;
                String deparse = deparseRaw;
                if (isClosure) {
                    /*
                     * To disambiguate identical saved deparsed files in different packages add a
                     * header line
                     */
                    deparse = "# deparsed from package: " + packageName + "\n" + deparse;
                    if (contextState.saveDeparse) {
                        saveDeparseResult(deparse, false);
                    } else {
                        sourcePath = RPackageSource.lookup(deparse);
                    }
                }
                Source source;
                if (sourcePath == null) {
                    source = Source.fromText(deparse, UNKNOWN_PACKAGE_SOURCE_PREFIX + packageName + " deparse>");
                } else {
                    source = Source.fromNamedText(deparse, sourcePath);
                }
                return RContext.getEngine().parse(source);
            } catch (Throwable ex) {
                /*
                 * Denotes a deparse/eval error, which is an unrecoverable bug, except in the
                 * special case where we are just saving package sources.
                 */
                saveDeparseResult(deparseRaw, true);
                if (!contextState.saveDeparse) {
                    throw Utils.fail("internal deparse error - see file DEPARSE_ERROR");
                } else {
                    return null;
                }
            }
        }

        private void saveDeparseResult(String deparse, boolean isError) throws IOException {
            if (contextState.saveDeparse) {
                RPackageSource.deparsed(deparse, isError);
            } else if (isError) {
                try (FileWriter wr = new FileWriter(new File(new File(REnvVars.rHome()), "DEPARSE" + (isError ? "_ERROR" : "")))) {
                    wr.write(deparse);
                }
            }
        }

        private static final String FAILED_DEPARSE_FUNCTION = "function(...) stop(\"FastR error: proxy for lazily loaded function that did not deparse/parse\")";
        private static final Source FAILED_DEPARSE_FUNCTION_SOURCE = Source.fromText(FAILED_DEPARSE_FUNCTION, UNKNOWN_PACKAGE_SOURCE_PREFIX + "deparse_error>");
        private static RExpression failedDeparseExpression;

        private static RExpression createFailedDeparseExpression() {
            if (failedDeparseExpression == null) {
                try {
                    failedDeparseExpression = RContext.getEngine().parse(FAILED_DEPARSE_FUNCTION_SOURCE);
                } catch (Throwable ex) {
                    throw RInternalError.shouldNotReachHere();
                }
            }
            return failedDeparseExpression;
        }

        /**
         * GnuR uses a pairlist to represent attributes, whereas FastR uses the abstract RAttributes
         * class. FastR also uses different types to represent data/frame and factor which is
         * handled in the setClassAttr. N.B. In theory connections can be unserialized but they are
         * unusable, so we don't go to the trouble of converting the {@link RIntVector}
         * representation into an {@link RConnection}.
         */
        private static Object setAttributes(final Object object, Object attr) {
            RAttributable rAttributable = (RAttributable) object;
            RPairList pl = (RPairList) attr;
            Object result = object;
            while (true) {
                RSymbol tagSym = (RSymbol) pl.getTag();
                String tag = tagSym.getName().intern();
                // this may convert a plain vector to a data.frame or factor
                if (result instanceof RVector && tag.equals(RRuntime.CLASS_ATTR_KEY)) {
                    RStringVector classes = (RStringVector) pl.car();
                    result = ((RVector) result).setClassAttr(classes, false);
                } else {
                    rAttributable.setAttr(tag, pl.car());
                }
                Object cdr = pl.cdr();
                if (cdr instanceof RNull) {
                    break;
                } else {
                    pl = (RPairList) cdr;
                }
            }
            return result;
        }

        private RStringVector inStringVec(boolean strsxp) throws IOException {
            if (!strsxp) {
                if (stream.readInt() != 0) {
                    throw RError.nyi(null, "names in persistent strings");
                }
            }
            int len = stream.readInt();
            String[] data = new String[len];
            boolean complete = RDataFactory.COMPLETE_VECTOR; // optimistic
            for (int i = 0; i < len; i++) {
                String item = (String) readItem();
                if (RRuntime.isNA(item)) {
                    complete = RDataFactory.INCOMPLETE_VECTOR;
                }
                data[i] = item;
            }
            return RDataFactory.createStringVector(data, complete);
        }

        private Object persistentRestore(RStringVector sv) throws IOException {
            if (hook == null) {
                throw new IOException("no restore method available");
            }
            // have to evaluate the hook function with sv as argument.
            Object result = hook.eval(sv);
            return result;
        }

        /**
         * Read GnuR bytecode. Not because we care, but it may be in there.
         */
        private Object readBC() throws IOException {
            int repsLength = stream.readInt();
            Object[] reps = new Object[repsLength];
            return readBC1(reps);
        }

        private Object readBC1(Object[] reps) throws IOException {
            Object car = readItem();
            // TODO R_bcEncode(car) (if we care)
            Object cdr = readBCConsts(reps);
            return RDataFactory.createPairList(car, cdr, RNull.instance, SEXPTYPE.BCODESXP);
        }

        private Object readBCConsts(Object[] reps) throws IOException {
            int n = stream.readInt();
            Object[] ans = new Object[n];
            for (int i = 0; i < n; i++) {
                int intType = stream.readInt();
                SEXPTYPE type = SEXPTYPE.mapInt(intType);
                switch (type) {
                    case BCODESXP: {
                        Object c = readBC1(reps);
                        ans[i] = c;
                        break;
                    }
                    case LANGSXP:
                    case LISTSXP:
                    case BCREPDEF:
                    case BCREPREF:
                    case ATTRLANGSXP:
                    case ATTRLISTSXP: {
                        Object c = readBCLang(type, reps);
                        ans[i] = c;
                        break;
                    }

                    default:
                        ans[i] = readItem();
                }
            }
            return RDataFactory.createList(ans);
        }

        private Object readBCLang(final SEXPTYPE typeArg, Object[] reps) throws IOException {
            SEXPTYPE type = typeArg;
            switch (type) {
                case BCREPREF:
                    return reps[stream.readInt()];
                case BCREPDEF:
                case LANGSXP:
                case LISTSXP:
                case ATTRLANGSXP:
                case ATTRLISTSXP: {
                    int pos = -1;
                    boolean hasattr = false;
                    if (type == SEXPTYPE.BCREPDEF) {
                        pos = stream.readInt();
                        type = SEXPTYPE.mapInt(stream.readInt());
                    }
                    switch (type) {
                        case ATTRLANGSXP:
                            type = SEXPTYPE.LANGSXP;
                            hasattr = true;
                            break;
                        case ATTRLISTSXP:
                            type = SEXPTYPE.LISTSXP;
                            hasattr = true;
                            break;
                    }
                    if (hasattr) {
                        readItem();
                        assert false;
                    }
                    Object tag = readItem();
                    Object car = readBCLang(SEXPTYPE.mapInt(stream.readInt()), reps);
                    Object cdr = readBCLang(SEXPTYPE.mapInt(stream.readInt()), reps);
                    Object ans = RDataFactory.createPairList(car, cdr, tag, type);
                    if (pos >= 0)
                        reps[pos] = ans;
                    return ans;
                }
                default: {
                    Object ans = readItem();
                    return ans;
                }
            }
        }

    }

    private abstract static class PInputStream {
        protected InputStream is;

        PInputStream(InputStream is) {
            this.is = is;
        }

        abstract int readInt() throws IOException;

        abstract String readString(int len) throws IOException;

        abstract double readDouble() throws IOException;

        abstract void readRaw(byte[] data) throws IOException;

    }

    @SuppressWarnings("unused")
    private abstract static class AsciiInputFormat extends PInputStream {
        AsciiInputFormat(InputStream is) {
            super(is);
        }
    }

    @SuppressWarnings("unused")
    private abstract static class BinaryInputFormat extends PInputStream {
        BinaryInputFormat(InputStream is) {
            super(is);
        }
    }

    private static final class XdrInputFormat extends PInputStream {

        private static final int READ_BUFFER_SIZE = 32 * 1024;

        private final byte[] buf;
        private int size;
        private int offset;

        XdrInputFormat(InputStream is) {
            super(is);
            if (is instanceof PByteArrayInputStream) {
                // we already have the data and we have read the beginning
                PByteArrayInputStream pbis = (PByteArrayInputStream) is;
                buf = pbis.getData();
                size = pbis.getData().length;
                offset = pbis.pos();
            } else {
                buf = new byte[READ_BUFFER_SIZE];
                size = 0;
                offset = 0;
            }
        }

        @Override
        int readInt() throws IOException {
            ensureData(4);
            return ((buf[offset++] & 0xff) << 24 | (buf[offset++] & 0xff) << 16 | (buf[offset++] & 0xff) << 8 | (buf[offset++] & 0xff));
        }

        @Override
        double readDouble() throws IOException {
            ensureData(8);
            long val = ((long) (buf[offset++] & 0xff) << 56 | (long) (buf[offset++] & 0xff) << 48 | (long) (buf[offset++] & 0xff) << 40 | (long) (buf[offset++] & 0xff) << 32 |
                            (long) (buf[offset++] & 0xff) << 24 | (long) (buf[offset++] & 0xff) << 16 | (long) (buf[offset++] & 0xff) << 8 | buf[offset++] & 0xff);
            return Double.longBitsToDouble(val);
        }

        @Override
        String readString(int len) throws IOException {
            ensureData(len);
            String s = new String(buf, offset, len);
            offset += len;
            return s;
        }

        private void ensureData(int n) throws IOException {
            if (offset + n > size) {
                int readOffset = 0;
                if (offset != size) {
                    // copy end piece to beginning
                    readOffset = size - offset;
                    System.arraycopy(buf, offset, buf, 0, readOffset);
                }
                offset = 0;
                // read some more data
                int nread = is.read(buf, readOffset, buf.length - readOffset);
                assert nread > 0;
                size = nread + readOffset;
            }
        }

        @Override
        void readRaw(byte[] data) throws IOException {
            ensureData(data.length);
            System.arraycopy(buf, offset, data, 0, data.length);
            offset += data.length;
        }

    }

    /**
     * Traces the items read for debugging.
     */
    private static final class TracingInput extends Input {
        private int nesting;

        private TracingInput(RConnection conn, int depth) throws IOException {
            this(conn.getInputStream(), null, depth, null);
        }

        private TracingInput(InputStream is, CallHook hook, int depth, String packageName) throws IOException {
            super(is, hook, depth, packageName);
        }

        @Override
        protected Object readItem() throws IOException {
            // CheckStyle: stop system..print check
            int flags = stream.readInt();
            SEXPTYPE type = SEXPTYPE.mapInt(Flags.ptype(flags));
            for (int i = 0; i < nesting; i++) {
                System.out.print("  ");
            }
            System.out.printf("%d %s", nesting, type);
            if (type != SEXPTYPE.CHARSXP) {
                System.out.println();
            }
            nesting++;
            Object result = super.readItem(flags);
            if (type == SEXPTYPE.CHARSXP) {
                System.out.printf(" \"%s\"%n", result);
            }
            nesting--;

            return result;
        }

    }

    // Serialize support is currently very limited, essentially to saving the CRAN package format
// info,

    private abstract static class POutputStream {
        protected OutputStream os;

        POutputStream(OutputStream os) {
            this.os = os;
        }

        abstract void writeInt(int value) throws IOException;

        abstract void writeString(String value) throws IOException;

        abstract void writeDouble(double value) throws IOException;

        abstract void writeRaw(byte[] value) throws IOException;

        abstract void flush() throws IOException;

    }

    private static class XdrOutputFormat extends POutputStream {
        private byte[] buf;
        private int offset;

        XdrOutputFormat(OutputStream os) {
            super(os);
            buf = new byte[8192];
            buf[offset++] = 'X';
            buf[offset++] = '\n';
        }

        @Override
        void writeInt(int value) throws IOException {
            ensureSpace(4);
            buf[offset++] = (byte) (value >>> 24);
            buf[offset++] = (byte) (value >> 16);
            buf[offset++] = (byte) (value >> 8);
            buf[offset++] = (byte) value;
        }

        @Override
        void writeString(String value) throws IOException {
            byte[] bytes = value.getBytes();
            int bytesLen = bytes.length;
            assert bytesLen < buf.length - 4;
            ensureSpace(bytesLen + 4);
            writeInt(bytesLen);
            System.arraycopy(bytes, 0, buf, offset, bytesLen);
            offset += bytesLen;

        }

        @Override
        void writeDouble(double value) throws IOException {
            ensureSpace(8);
            long valueBits = Double.doubleToLongBits(value);
            buf[offset++] = (byte) (valueBits >>> 56);
            buf[offset++] = (byte) ((valueBits >> 48) & 0xff);
            buf[offset++] = (byte) ((valueBits >> 40) & 0xff);
            buf[offset++] = (byte) ((valueBits >> 32) & 0xff);
            buf[offset++] = (byte) ((valueBits >> 24) & 0xff);
            buf[offset++] = (byte) ((valueBits >> 16) & 0xff);
            buf[offset++] = (byte) ((valueBits >> 8) & 0xff);
            buf[offset++] = (byte) (valueBits & 0xff);
        }

        private void ensureSpace(int n) throws IOException {
            if (offset + n > buf.length) {
                flushBuffer();
            }
        }

        void flushBuffer() throws IOException {
            if (offset > 0) {
                os.write(buf, 0, offset);
                offset = 0;
            }
        }

        @Override
        void flush() throws IOException {
            flushBuffer();
            os.flush();
        }

        @Override
        void writeRaw(byte[] value) throws IOException {
            ensureSpace(value.length);
            System.arraycopy(value, 0, buf, offset, value.length);
            offset += value.length;
        }
    }

    private static class Output extends Common {
        private State state;
        protected final POutputStream stream;
        private int version;

        private Output(RConnection conn, char format, int version, CallHook hook, int depth) throws IOException {
            this(conn.getOutputStream(), format, version, hook, depth);
        }

        private Output(OutputStream os, char format, int version, CallHook hook, int depth) throws IOException {
            super(hook, depth);
            this.version = version;
            switch (format) {
                case 'A':
                case 'B':
                    throw formatError((byte) format, true);
                case 'X':
                    stream = new XdrOutputFormat(os);
                    break;
                default:
                    throw formatError((byte) format, false);
            }
        }

        private void serialize(State s, Object obj) throws IOException {
            this.state = s;
            switch (version) {
                case DEFAULT_VERSION:
                    stream.writeInt(version);
                    stream.writeInt(196865);
                    stream.writeInt(RVersionInfo.SERIALIZE_VERSION);
                    break;

                default:
                    throw RInternalError.unimplemented();
            }
            writeItem(obj);
            stream.flush();
        }

        private static SEXPTYPE saveSpecialHook(Object item) {
            if (item == RNull.instance)
                return SEXPTYPE.NILVALUE_SXP;
            if (item == REnvironment.emptyEnv())
                return SEXPTYPE.EMPTYENV_SXP;
            if (item == REnvironment.baseEnv())
                return SEXPTYPE.BASEENV_SXP;
            if (item == REnvironment.globalEnv())
                return SEXPTYPE.GLOBALENV_SXP;
            if (item == RUnboundValue.instance)
                return SEXPTYPE.UNBOUNDVALUE_SXP;
            if (item == RMissing.instance)
                return SEXPTYPE.MISSINGARG_SXP;
            if (item == REmpty.instance)
                return SEXPTYPE.MISSINGARG_SXP;
            if (item == REnvironment.baseNamespaceEnv())
                return SEXPTYPE.BASENAMESPACE_SXP;
            return null;
        }

        private void writeItem(Object obj) throws IOException {
            SEXPTYPE specialType;
            Object psn;
            if ((psn = getPersistentName(obj)) != RNull.instance) {
                addReadRef(obj);
                stream.writeInt(SEXPTYPE.PERSISTSXP.code);
                outStringVec((RStringVector) psn, false);
                return;
            }
            if ((specialType = saveSpecialHook(obj)) != null) {
                stream.writeInt(specialType.code);
                return;
            }
            SEXPTYPE type = SEXPTYPE.typeForClass(obj.getClass());
            SEXPTYPE gnuRType = SEXPTYPE.gnuRType(type, obj);
            int refIndex;
            if ((refIndex = getRefIndex(obj)) != -1) {
                outRefIndex(refIndex);
            } else if (type == SEXPTYPE.SYMSXP) {
                writeSymbol((RSymbol) obj);
            } else if (type == SEXPTYPE.ENVSXP) {
                REnvironment env = (REnvironment) obj;
                addReadRef(obj);
                String name = null;
                if ((name = env.isPackageEnv()) != null) {
                    RError.warning(RError.NO_NODE, RError.Message.PACKAGE_AVAILABLE, name);
                    stream.writeInt(SEXPTYPE.PACKAGESXP.code);
                    stream.writeString(name);
                } else if (env.isNamespaceEnv()) {
                    stream.writeInt(SEXPTYPE.NAMESPACESXP.code);
                    RStringVector nameSpaceEnvSpec = env.getNamespaceSpec();
                    outStringVec(nameSpaceEnvSpec, false);
                } else {
                    stream.writeInt(SEXPTYPE.ENVSXP.code);
                    stream.writeInt(env.isLocked() ? 1 : 0);
                    writeItem(env.getParent());
                    /*
                     * TODO To be truly compatible with GnuR we should remember whether an
                     * environment was created with new.env(hash=T) and output it in that form with
                     * the associated size. For internal FastR use it does not matter, so we use the
                     * "frame" form, which is a pairlist. tag is binding name, car is binding value
                     */
                    String[] bindings = env.ls(true, null, false).getDataWithoutCopying();
                    for (String binding : bindings) {
                        Object value = env.get(binding);
                        writePairListEntry(binding, value);
                    }
                    terminatePairList();
                    writeItem(RNull.instance); // hashtab
                    RAttributes attributes = env.getAttributes();
                    if (attributes != null) {
                        writeAttributes(attributes);
                    } else {
                        writeItem(RNull.instance);
                    }
                }
            } else if (type == SEXPTYPE.FASTR_DATAFRAME) {
                RDataFrame dataFrame = (RDataFrame) obj;
                writeItem(dataFrame.getVector());
                return;
            } else if (type == SEXPTYPE.FASTR_FACTOR) {
                RFactor factor = (RFactor) obj;
                writeItem(factor.getVector());
                return;
            } else {
                // flags
                RAttributes attributes = null;
                if (obj instanceof RAttributable) {
                    RAttributable rattr = (RAttributable) obj;
                    attributes = rattr.getAttributes();
                    if (attributes != null && attributes.isEmpty()) {
                        attributes = null;
                    }
                }
                boolean hasTag = gnuRType == SEXPTYPE.CLOSXP || (type == SEXPTYPE.LISTSXP && !((RPairList) obj).isNullTag());
                int flags = Flags.packFlags(gnuRType, 0, false, attributes != null, hasTag);
                stream.writeInt(flags);
                switch (type) {
                    case STRSXP: {
                        if (obj instanceof String) {
                            // length 1 vector
                            stream.writeInt(1);
                            writeCHARSXP((String) obj);
                        } else {
                            outStringVec((RStringVector) obj, true);
                        }
                        break;
                    }

                    case INTSXP: {
                        RAbstractIntVector vec = (RAbstractIntVector) obj;
                        stream.writeInt(vec.getLength());
                        for (int i = 0; i < vec.getLength(); i++) {
                            stream.writeInt(vec.getDataAt(i));
                        }
                        break;
                    }

                    case REALSXP: {
                        RAbstractDoubleVector vec = (RAbstractDoubleVector) obj;
                        stream.writeInt(vec.getLength());
                        for (int i = 0; i < vec.getLength(); i++) {
                            stream.writeDouble(vec.getDataAt(i));
                        }
                        break;
                    }

                    case LGLSXP: {
                        // Output as ints
                        RLogicalVector vec = (RLogicalVector) obj;
                        stream.writeInt(vec.getLength());
                        for (int i = 0; i < vec.getLength(); i++) {
                            byte val = vec.getDataAt(i);
                            if (RRuntime.isNA(val)) {
                                stream.writeInt(RRuntime.INT_NA);
                            } else {
                                stream.writeInt(vec.getDataAt(i));
                            }
                        }
                        break;
                    }

                    case CPLXSXP: {
                        RComplexVector vec = (RComplexVector) obj;
                        stream.writeInt(vec.getLength());
                        for (int i = 0; i < vec.getLength(); i++) {
                            RComplex val = vec.getDataAt(i);
                            stream.writeDouble(val.getRealPart());
                            stream.writeDouble(val.getImaginaryPart());
                        }
                        break;
                    }

                    case VECSXP: {
                        RList list = (RList) obj;
                        stream.writeInt(list.getLength());
                        for (int i = 0; i < list.getLength(); i++) {
                            Object listObj = list.getDataAt(i);
                            writeItem(listObj);
                        }
                        break;
                    }

                    case LISTSXP: {
                        if (attributes != null) {
                            writeAttributes(attributes);
                            attributes = null;
                        }
                        RPairList pl = (RPairList) obj;
                        if (!pl.isNullTag()) {
                            writeItem(pl.getTag());
                        }
                        writeItem(pl.car());
                        writeItem(pl.cdr());
                        break;
                    }

                    case RAWSXP: {
                        RRawVector raw = (RRawVector) obj;
                        byte[] data = raw.getDataWithoutCopying();
                        stream.writeInt(data.length);
                        stream.writeRaw(data);
                        break;
                    }

                    case FUNSXP: {
                        RFunction fun = (RFunction) obj;
                        if (fun.isBuiltin()) {
                            String name = fun.getRBuiltin().getName();
                            stream.writeString(name);
                        } else {
                            // write attributes first (cf GnuR)
                            if (attributes != null) {
                                writeAttributes(attributes);
                                attributes = null;
                            }
                            RPairList pl = (RPairList) RContext.getRRuntimeASTAccess().serialize(state, fun);
                            if (pl != null) {
                                state.convertUnboundValues(pl);
                                if (FastROptions.debugMatches("printWclosure")) {
                                    Debug.printClosure(pl);
                                }
                                writeItem(pl.getTag());
                                writeItem(pl.car());
                                writeItem(pl.cdr());
                            }
                        }
                        break;
                    }

                    case EXTPTRSXP: {
                        addReadRef(obj);
                        RExternalPtr xptr = (RExternalPtr) obj;
                        writeItem(xptr.getProt());
                        writeItem(xptr.getTag());
                        break;
                    }

                    /*
                     * FastR scalar, (length 1) "vectors"
                     */

                    case FASTR_INT: {
                        Integer value = (Integer) obj;
                        stream.writeInt(1);
                        stream.writeInt(value);
                        break;
                    }

                    case FASTR_DOUBLE: {
                        Double value = (Double) obj;
                        stream.writeInt(1);
                        stream.writeDouble(value);
                        break;
                    }

                    case FASTR_BYTE: {
                        Byte value = (Byte) obj;
                        stream.writeInt(1);
                        if (RRuntime.isNA(value)) {
                            stream.writeInt(RRuntime.INT_NA);
                        } else {
                            stream.writeInt(value);
                        }
                        break;
                    }

                    case FASTR_COMPLEX: {
                        RComplex value = (RComplex) obj;
                        stream.writeInt(1);
                        stream.writeDouble(value.getRealPart());
                        stream.writeDouble(value.getImaginaryPart());
                        break;
                    }

                    case FASTR_CONNECTION: {
                        RConnection con = (RConnection) obj;
                        stream.writeInt(1);
                        stream.writeInt(con.getDescriptor());
                        break;
                    }

                    case LANGSXP: {
                        // write attributes first (cf GnuR)
                        if (attributes != null) {
                            writeAttributes(attributes);
                            attributes = null;
                        }
                        RPairList pl = (RPairList) RContext.getRRuntimeASTAccess().serialize(state, obj);
                        state.convertUnboundValues(pl);
                        writeItem(pl.car());
                        writeItem(pl.cdr());
                        break;
                    }

                    default:
                        throw RInternalError.unimplemented();
                }

                if (attributes != null) {
                    writeAttributes(attributes);
                }
            }
        }

        private Object getPersistentName(Object obj) {
            if (hook == null) {
                return RNull.instance;
            }
            switch (SEXPTYPE.typeForClass(obj.getClass())) {
                case WEAKREFSXP:
                case EXTPTRSXP:
                    break;
                case ENVSXP:
                    REnvironment env = (REnvironment) obj;
                    if (env == REnvironment.globalEnv() || env == REnvironment.emptyEnv() || env == REnvironment.baseEnv() || env.isNamespaceEnv() || env.isPackageEnv() != null) {
                        return RNull.instance;
                    } else {
                        break;
                    }
                default:
                    return RNull.instance;
            }
            Object result = hook.eval(obj);
            if (result instanceof String) {
                result = RDataFactory.createStringVectorFromScalar((String) result);
            }
            return result;
        }

        private void outStringVec(RStringVector vec, boolean strsxp) throws IOException {
            if (!strsxp) {
                stream.writeInt(0);
            }
            stream.writeInt(vec.getLength());
            for (int i = 0; i < vec.getLength(); i++) {
                writeCHARSXP(vec.getDataAt(i));
            }
        }

        /**
         * Write the element of a STRSXP. We can't call {@link #writeItem} because that always
         * treats a {@code String} as an STRSXP.
         */
        private void writeCHARSXP(String s) throws IOException {
            int flags = Flags.packFlags(SEXPTYPE.CHARSXP, 0, false, false, false);
            stream.writeInt(flags);
            if (s == RRuntime.STRING_NA) {
                stream.writeInt(-1);
            } else {
                stream.writeString(s);
            }
        }

        private void writeAttributes(RAttributes attributes) throws IOException {
            // have to convert to GnuR pairlist
            Iterator<RAttribute> iter = attributes.iterator();
            while (iter.hasNext()) {
                RAttribute attr = iter.next();
                // name is the tag of the virtual pairlist
                // value is the car
                // next is the cdr
                writePairListEntry(attr.getName(), attr.getValue());
            }
            terminatePairList();
        }

        private void writePairListEntry(String name, Object value) throws IOException {
            stream.writeInt(Flags.packFlags(SEXPTYPE.LISTSXP, 0, false, false, true));
            RSymbol sym = state.findSymbol(name);
            int refIndex;
            if ((refIndex = getRefIndex(sym)) != -1) {
                outRefIndex(refIndex);
            } else {
                writeSymbol(sym);
            }
            writeItem(value);
        }

        private void writeSymbol(RSymbol name) throws IOException {
            addReadRef(name);
            stream.writeInt(SEXPTYPE.SYMSXP.code);
            writeCHARSXP(name.getName());
        }

        private void terminatePairList() throws IOException {
            stream.writeInt(Flags.packFlags(SEXPTYPE.NILVALUE_SXP, 0, false, false, false));
        }

        private void outRefIndex(int index) throws IOException {
            if (index > MAX_PACKED_INDEX) {
                stream.writeInt(SEXPTYPE.REFSXP.code);
                stream.writeInt(index);
            } else {
                stream.writeInt(packRefIndex(index));
            }
        }
    }

    /**
     * Value that is passed to the Truffle AST walker {@code RSyntaxNode.serialize} to convert AST
     * nodes into a pairlist as required by the GnuR serialization format. The intent is to abstract
     * the client from the details of physical pairlists, possibly not actually creating them at
     * all. Since pairlists are inherently recursive structures through the {@code car} and
     * {@code cdr} fields, the class maintains a virtual stack, however for the most part only the
     * top entry is of interest. The general invariant is that on entry to
     * {@code RSyntaxNode.serialize} the top entry is the one that the method should update and
     * leave on the stack. Any child nodes visited by the method may need a new virtual pairlist
     * that should be pushed with one of the {@code openXXX} methods prior to calling the child's
     * {@code serialize} method. On return the caller is responsible for removing the virtual
     * pairlist with {@link State#closePairList()} and assigning it into the appropriate field (
     * {@code car} or {@code cdr}) of it's virtual pairlist.
     *
     */
    public abstract static class State {

        protected final Output output;
        private Map<String, RSymbol> symbolMap = new HashMap<>();

        private State(Output output) {
            this.output = output;
        }

        /**
         * Pushes a new virtual pairlist (no type) onto the stack. An untyped pairlist is subject to
         * the down-shifting to a simple value on {@link #closePairList()}.
         *
         * @return {@code this}, to allow immediate use of {@code setTag}.
         */
        public abstract State openPairList();

        /**
         * Pushes a new virtual pairlist of specific type onto the stack. Such a virtual pairlist
         * will never down-shift" to its {@code car}.
         *
         * @return {@code this}, to allow immediate use of {@code setTag}.
         */
        public abstract State openPairList(SEXPTYPE type);

        /**
         * Change the type of the active element to LANGSXP.
         */
        public abstract void setAsLangType();

        /**
         * Sets the {@code tag} of the current pairlist.
         */
        public abstract void setTag(Object tag);

        /**
         * A special form of {@link #setTag} that <b<must</b> be used for symbols, i.e. identifiers.
         */
        public abstract void setTagAsSymbol(String name);

        /**
         * A special form of {@link #setCar} that <b<must</b> be used for symbols, i.e. identifiers.
         */
        public abstract void setCarAsSymbol(String name);

        /**
         * Sets the {@code car} of the current pairlist.
         */
        public abstract void setCar(Object car);

        /**
         * Sets the {@code cdr} of the current pairlist.
         */
        public abstract void setCdr(Object cdr);

        /**
         * Use this for the case where the current pairlist should be replaced by an {@link RNull}
         * value, e.g., empty statement sequence.
         */
        public abstract void setNull();

        /**
         * Checks for the special case where the active pairlist has a {@link RNull} {@code car} and
         * {@code cdr} and an unset {@code tag}.
         */
        public abstract boolean isNull();

        /**
         * Closes the current pairlist, handling the case where a "simple" value is down-shifted
         * from a pairlist to just the value.
         *
         * @return If the {@code tag}, {@code type} and the {@cdr} are unset ({@code null}), return
         *         the {@code car} else return the pairlist.
         */
        public abstract Object closePairList();

        /**
         * Use this for sequences, e.g. formals, call arguments, statements. If {@code n <= 1} has
         * no effect, otherwise it connects the stack of pairlists through their {@code cdr} fields,
         * terminates the pairlist with a {@link RNull#instance} and and pops {@code n - 1} entries
         * off the stack. On exit, therefore, the top element of the stack is the head of a chained
         * list.
         */
        public abstract void linkPairList(int n);

        /**
         * Handles the special case of '[', where the indices and "drop/exact" values are in
         * different parts of the AST but need to be in the same list.
         */
        public abstract void setPositionsLength(int n);

        /**
         * Returns value from previous call to {@link #setPositionsLength(int)}.
         */
        public abstract int getPositionsLength();

        /**
         * Special case where the value is in the {@code cdr} and it needs to be in the {@code car}.
         */
        public abstract void switchCdrToCar();

        /**
         * Clean up any {@link RUnboundValue}s from shrink optimization.
         */
        public abstract void convertUnboundValues(RPairList pl);

        // Implementation independent convenience methods

        /**
         * Similar to {@link #setNull} but denotes a missing value, e.g., missing default for
         * function argument.
         */
        public void setCarMissing() {
            setCar(RMissing.instance);
        }

        public void openBrace() {
            openPairList(SEXPTYPE.LANGSXP);
            setCarAsSymbol("{");
        }

        public void closeBrace() {
            setCar(closePairList());
        }

        public void setAsBuiltin(String name) {
            setAsLangType();
            setCarAsSymbol(name);
        }

        public void serializeNodeSetCar(Object node) {
            openPairList();
            RContext.getRRuntimeASTAccess().serializeNode(this, node);
            setCar(closePairList());
        }

        public void serializeNodeSetCdr(Object node, SEXPTYPE type) {
            openPairList(type);
            RContext.getRRuntimeASTAccess().serializeNode(this, node);
            setCdr(closePairList());
        }

        RSymbol findSymbol(String name) {
            RSymbol symbol = symbolMap.get(name);
            if (symbol == null) {
                symbol = RDataFactory.createSymbol(name);
                symbolMap.put(name, symbol);
            }
            return symbol;
        }

    }

    /**
     * Implementation that creates a physical {@link RPairList}.
     */
    private static class PLState extends State {
        private static final RPairList NULL = RDataFactory.createPairList();
        private Deque<RPairList> active = new LinkedList<>();
        private int[] positionsLength = new int[10];
        private int px = 0;

        private PLState(Output output) {
            super(output);
        }

        @Override
        public State openPairList() {
            /*
             * In order to implement the "shrink" optimization in closePairList we set the tag and
             * the cdr to RUnboundValue. N.B. It is a bug if this ever escapes to the outside world.
             */
            RPairList result = RDataFactory.createPairList(RNull.instance, RUnboundValue.instance, RUnboundValue.instance);
            active.addFirst(result);
            return this;
        }

        @Override
        public State openPairList(SEXPTYPE type) {
            RPairList result = RDataFactory.createPairList();
            result.setType(type);
            active.addFirst(result);
            return this;
        }

        @Override
        public void setAsLangType() {
            assert active.peekFirst() != NULL;
            active.peekFirst().setType(SEXPTYPE.LANGSXP);
        }

        @Override
        public void setTag(Object tag) {
            active.peekFirst().setTag(tag);
        }

        @Override
        public void setTagAsSymbol(String name) {
            active.peekFirst().setTag(findSymbol(name));
        }

        @Override
        public void setCarAsSymbol(String name) {
            active.peekFirst().setCar(findSymbol(name));
        }

        @Override
        public void setCar(Object car) {
            active.peekFirst().setCar(car);
        }

        @Override
        public void setCdr(Object cdr) {
            active.peekFirst().setCdr(cdr);
        }

        @Override
        public void setNull() {
            active.removeFirst();
            active.addFirst(NULL);
        }

        @Override
        public Object closePairList() {
            RPairList top = active.removeFirst();
            if (top == NULL) {
                return RNull.instance;
            } else {
                if (top.cdr() == RUnboundValue.instance) {
                    if (top.getTag() == RUnboundValue.instance && top.getType() == null) {
                        // shrink back to non-pairlist (cf GnuR)
                        return top.car();
                    } else {
                        top.setCdr(RNull.instance);
                        return top;
                    }
                } else {
                    return top;
                }
            }
        }

        @Override
        public void linkPairList(int n) {
            if (n > 1) {
                setCdr(RNull.instance); // terminate pairlist
                for (int i = 0; i < n - 1; i++) {
                    RPairList top = active.removeFirst();
                    setCdr(top); // chain
                }
            }
        }

        @Override
        public boolean isNull() {
            RPairList pl = active.peekFirst();
            return pl.getTag() == RUnboundValue.instance && pl.car() == RNull.instance && pl.cdr() == RNull.instance;
        }

        @Override
        public void switchCdrToCar() {
            RPairList pl = active.removeFirst();
            // setting the type prevents the usual value down-shift on close
            RPairList spl;
            if (pl.cdr() instanceof RPairList && ((RPairList) pl.cdr()).getType() == null) {
                // preserve the "shrink" optimization
                spl = RDataFactory.createPairList(pl.cdr(), RUnboundValue.instance, RUnboundValue.instance);
            } else {
                spl = RDataFactory.createPairList(pl.cdr(), RNull.instance, RNull.instance, SEXPTYPE.LISTSXP);
            }
            active.addFirst(spl);
        }

        @Override
        public String toString() {
            // IDE debugging
            Iterator<RPairList> iter = active.iterator();
            if (iter.hasNext()) {
                StringBuffer sb = new StringBuffer();
                while (iter.hasNext()) {
                    RPairList pl = iter.next();
                    sb.append('[');
                    if (pl == NULL) {
                        sb.append("NULL");
                    } else {
                        sb.append(pl.toString());
                    }
                    sb.append("] ");
                }
                return sb.toString();
            } else {
                return "EMPTY";
            }
        }

        @Override
        public void setPositionsLength(int n) {
            positionsLength[px++] = n;
        }

        @Override
        public int getPositionsLength() {
            px--;
            return positionsLength[px];
        }

        @Override
        public void convertUnboundValues(RPairList pl) {
            Object obj = pl;
            while (obj instanceof RPairList) {
                RPairList plt = (RPairList) obj;
                if (plt.getTag() == RUnboundValue.instance) {
                    plt.setTag(RNull.instance);
                }
                if (plt.car() instanceof RPairList) {
                    convertUnboundValues((RPairList) plt.car());
                }
                obj = plt.cdr();
                assert !(obj instanceof RUnboundValue);
            }
        }

    }

    /**
     * For {@code lazyLoadDBinsertValue}.
     */
    @TruffleBoundary
    public static byte[] serialize(Object obj, boolean ascii, @SuppressWarnings("unused") boolean xdr, int version, Object refhook, int depth) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Output output = new Output(out, ascii ? 'A' : 'X', version, (CallHook) refhook, depth);
            State state = new PLState(output);
            output.serialize(state, obj);
            return out.toByteArray();
        } catch (IOException ex) {
            throw RInternalError.shouldNotReachHere();
        }
    }

    @TruffleBoundary
    public static void serialize(RConnection conn, Object obj, boolean ascii, @SuppressWarnings("unused") boolean xdr, int version, Object refhook, int depth) throws IOException {
        Output output = new Output(conn, ascii ? 'A' : 'X', version, (CallHook) refhook, depth);
        State state = new PLState(output);
        output.serialize(state, obj);
    }

    private static class Debug {
        private static int indent;
        private static PrintStream out;

        private static void printClosure(RPairList pl) {
            indent = 0;
            out = System.out;
            printObject(pl);
        }

        private static SEXPTYPE type(Object obj) {
            if (obj instanceof RPairList) {
                SEXPTYPE s = ((RPairList) obj).getType();
                return s == null ? SEXPTYPE.LISTSXP : s;
            } else {
                return SEXPTYPE.typeForClass(obj.getClass());
            }
        }

        private static void printObject(Object obj) {
            printObject(obj, true);
        }

        private static void printObject(Object obj, boolean printType) {
            SEXPTYPE type = type(obj);
            if (printType) {
                print("%s", type.name());
            }
            switch (type) {
                case SYMSXP: {
                    print("\"%s\"", ((RSymbol) obj).getName());
                    break;
                }

                case CLOSXP:
                case LISTSXP:
                case LANGSXP: {
                    RPairList pl = (RPairList) obj;
                    indent++;
                    print("TAG: %s", pl.getTag());
                    SEXPTYPE carType = type(pl.car());
                    print("CAR: %s %s", type(pl.car()).name(), (carType == SEXPTYPE.SYMSXP ? ((RSymbol) pl.car()).getName() : ""));
                    if (carType != SEXPTYPE.SYMSXP) {
                        printObject(pl.car(), false);
                    }
                    SEXPTYPE cdrType = type(pl.cdr());
                    print("CDR: %s %s", type(pl.cdr()).name(), (cdrType == SEXPTYPE.SYMSXP ? ((RSymbol) pl.cdr()).getName() : ""));
                    if (cdrType != SEXPTYPE.SYMSXP) {
                        printObject(pl.cdr(), false);
                    }
                    indent--;
                    break;
                }

                default:
            }
        }

        private static void print(String format, Object... objects) {
            for (int i = 0; i < indent * 2; i++) {
                out.write(' ');
            }
            out.printf(format, objects);
            out.write('\n');
        }
    }

}
