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
package com.oracle.truffle.r.runtime.ffi.jnr;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.context.*;
import com.oracle.truffle.r.runtime.context.Engine.ParseException;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.env.*;
import com.oracle.truffle.r.runtime.env.REnvironment.PutException;
import com.oracle.truffle.r.runtime.gnur.*;
import com.oracle.truffle.r.runtime.ops.na.*;

/**
 * This class provides methods that match the functionality of the macro/function definitions in
 * thye R header files, e.g. {@code Rinternals.h} that are used by C/C++ code. For ease of
 * identification, we use method names that, as far as possible, match the names in the header
 * files. These methods should never be called from normal FastR code.
 */
public class CallRFFIHelper {
    @SuppressWarnings("unused") private static final NACheck elementNACheck = NACheck.create();

    private static RuntimeException unimplemented() {
        return unimplemented("");
    }

    private static RuntimeException unimplemented(String message) {
        System.out.println(message);
        try {
            throw RInternalError.unimplemented(message);
        } catch (Error e) {
            e.printStackTrace();
            try {
                Thread.sleep(100000);
            } catch (InterruptedException e2) {
                e2.printStackTrace();
            }
            throw e;
        }
    }

    private static void guarantee(boolean condition) {
        guarantee(condition, "");
    }

    private static void guarantee(boolean condition, String message) {
        if (!condition) {
            unimplemented(message);
        }
    }

    private static void guaranteeInstanceOf(Object x, Class<?> clazz) {
        if (x == null) {
            guarantee(false, "unexpected type: null instead of " + clazz.getSimpleName());
        } else if (!clazz.isInstance(x)) {
            guarantee(false, "unexpected type: " + x + " is " + x.getClass().getSimpleName() + " instead of " + clazz.getSimpleName());
        }
    }

    // Checkstyle: stop method name check

    static RIntVector Rf_ScalarInteger(int value) {
        return RDataFactory.createIntVectorFromScalar(value);
    }

    static RLogicalVector Rf_ScalarLogical(int value) {
        return RDataFactory.createLogicalVectorFromScalar(value != 0);
    }

    static RDoubleVector Rf_ScalarDouble(double value) {
        return RDataFactory.createDoubleVectorFromScalar(value);
    }

    static RStringVector Rf_ScalarString(String value) {
        return RDataFactory.createStringVectorFromScalar(value);
    }

    static int Rf_asInteger(Object x) {
        if (x instanceof Integer) {
            return ((Integer) x).intValue();
        } else if (x instanceof Double) {
            return RRuntime.double2int((Double) x);
        } else {
            guaranteeInstanceOf(x, RIntVector.class);
            return ((RIntVector) x).getDataAt(0);
        }
    }

    static double Rf_asReal(Object x) {
        if (x instanceof Double) {
            return ((Double) x).doubleValue();
        } else if (x instanceof Byte) {
            return RRuntime.logical2double((Byte) x);
        } else {
            guaranteeInstanceOf(x, RDoubleVector.class);
            return ((RDoubleVector) x).getDataAt(0);
        }
    }

    static int Rf_asLogical(Object x) {
        if (x instanceof Byte) {
            return ((Byte) x).intValue();
        } else {
            guaranteeInstanceOf(x, RLogicalVector.class);
            return ((RLogicalVector) x).getDataAt(0);
        }
    }

    static String Rf_asChar(Object x) {
        if (x instanceof String) {
            return (String) x;
        } else {
            guaranteeInstanceOf(x, RStringVector.class);
            return ((RStringVector) x).getDataAt(0);
        }
    }

    static Object Rf_cons(Object car, Object cdr) {
        return RDataFactory.createPairList(car, cdr);
    }

    static void Rf_defineVar(Object symbolArg, Object value, Object envArg) {
        REnvironment env = (REnvironment) envArg;
        RSymbol name = (RSymbol) symbolArg;
        try {
            env.put(name.getName(), value);
        } catch (PutException ex) {
            throw RError.error(RError.NO_NODE, ex);
        }
    }

    static Object Rf_findVar(Object symbolArg, Object envArg) {
        return findVarInFrameHelper(symbolArg, envArg, true);
    }

    static Object Rf_findVarInFrame(Object symbolArg, Object envArg) {
        return findVarInFrameHelper(symbolArg, envArg, false);
    }

    private static Object findVarInFrameHelper(Object symbolArg, Object envArg, boolean inherits) {
        if (envArg == RNull.instance) {
            throw RError.error(RError.NO_NODE, RError.Message.USE_NULL_ENV_DEFUNCT);
        }
        if (!(envArg instanceof REnvironment)) {
            throw RError.error(RError.NO_NODE, RError.Message.ARG_NOT_AN_ENVIRONMENT, inherits ? "findVar" : "findVarInFrame");
        }
        RSymbol name = (RSymbol) symbolArg;
        REnvironment env = (REnvironment) envArg;
        while (env != REnvironment.emptyEnv()) {
            Object value = env.get(name.getName());
            if (value != null) {
                return value;
            }
            if (!inherits) {
                break;
            }
            env = env.getParent();
        }
        return RUnboundValue.instance;

    }

    static Object Rf_getAttrib(Object obj, Object name) {
        Object result = RNull.instance;
        if (obj instanceof RAttributable) {
            RAttributable attrObj = (RAttributable) obj;
            RAttributes attrs = attrObj.getAttributes();
            if (attrs != null) {
                String nameAsString = ((RSymbol) name).getName().intern();
                Object attr = attrs.get(nameAsString);
                if (attr != null) {
                    result = attr;
                }
            }
        }
        return result;
    }

    static void Rf_setAttrib(Object obj, Object name, Object val) {
        if (obj instanceof RAttributable) {
            RAttributable attrObj = (RAttributable) obj;
            RAttributes attrs = attrObj.getAttributes();
            if (attrs == null) {
                attrs = attrObj.initAttributes();
            }
            String nameAsString;
            if (name instanceof RSymbol) {
                nameAsString = ((RSymbol) name).getName();
            } else {
                nameAsString = RRuntime.asString(name);
                assert nameAsString != null;
            }
            nameAsString = nameAsString.intern();
            attrs.put(nameAsString, val);
        }
    }

    private static RStringVector getClassHr(Object v) {
        if (v instanceof RAttributable) {
            return ((RAttributable) v).getClassHierarchy();
        } else if (v instanceof Byte) {
            return RLogicalVector.implicitClassHeader;
        } else if (v instanceof String) {
            return RStringVector.implicitClassHeader;
        } else if (v instanceof Integer) {
            return RIntVector.implicitClassHeader;
        } else if (v instanceof Double) {
            return RDoubleVector.implicitClassHeader;
        } else if (v instanceof RComplex) {
            return RComplexVector.implicitClassHeader;
        } else if (v instanceof RRaw) {
            return RRawVector.implicitClassHeader;
        } else {
            guaranteeInstanceOf(v, RNull.class);
            return RNull.implicitClassHeader;
        }
    }

    static int Rf_inherits(Object x, String clazz) {
        RStringVector hierarchy = getClassHr(x);
        for (int i = 0; i < hierarchy.getLength(); i++) {
            if (hierarchy.getDataAt(i).equals(clazz)) {
                return 1;
            }
        }
        return 0;
    }

    static int Rf_isString(Object x) {
        return RRuntime.asString(x) == null ? 0 : 1;
    }

    static int Rf_isNull(Object x) {
        return x == RNull.instance ? 1 : 0;
    }

    static Object Rf_PairToVectorList(Object x) {
        if (x == RNull.instance) {
            return RDataFactory.createList();
        }
        RPairList pl = (RPairList) x;
        return pl.toRList();
    }

    static void Rf_error(String msg) {
        throw RError.error(RError.NO_NODE, RError.Message.GENERIC, msg);
    }

    static void Rf_warning(String msg) {
        RError.warning(RError.NO_NODE, RError.Message.GENERIC, msg);
    }

    static void Rf_warningcall(Object call, String msg) {
        RErrorHandling.warningcallRFFI(call, msg);
    }

    static Object Rf_allocateVector(int mode, int n) {
        SEXPTYPE type = SEXPTYPE.mapInt(mode);
        if (n < 0) {
            throw RError.error(RError.NO_NODE, RError.Message.NEGATIVE_LENGTH_VECTORS_NOT_ALLOWED);
            // TODO check long vector
        }
        switch (type) {
            case INTSXP:
                return RDataFactory.createIntVector(new int[n], RDataFactory.COMPLETE_VECTOR);
            case REALSXP:
                return RDataFactory.createDoubleVector(new double[n], RDataFactory.COMPLETE_VECTOR);
            case LGLSXP:
                return RDataFactory.createLogicalVector(new byte[n], RDataFactory.COMPLETE_VECTOR);
            case STRSXP:
                return RDataFactory.createStringVector(new String[n], RDataFactory.COMPLETE_VECTOR);
            case CPLXSXP:
                return RDataFactory.createComplexVector(new double[2 * n], RDataFactory.COMPLETE_VECTOR);
            case RAWSXP:
                return RDataFactory.createRawVector(new byte[n]);
            case VECSXP:
                return RDataFactory.createList(n);
            default:
                throw unimplemented("unexpected SEXPTYPE " + type);
        }

    }

    static Object Rf_allocateArray(int mode, Object dimsObj) {
        RIntVector dims = (RIntVector) dimsObj;
        int n = 1;
        int[] newDims = new int[dims.getLength()];
        // TODO check long vector
        for (int i = 0; i < newDims.length; i++) {
            newDims[i] = dims.getDataAt(i);
            n *= newDims[i];
        }
        RAbstractVector result = (RAbstractVector) Rf_allocateVector(mode, n);
        result.setDimensions(newDims);
        return result;

    }

    static Object Rf_allocateMatrix(int mode, int ncol, int nrow) {
        SEXPTYPE type = SEXPTYPE.mapInt(mode);
        if (nrow < 0 || ncol < 0) {
            throw RError.error(RError.NO_NODE, RError.Message.NEGATIVE_EXTENTS_TO_MATRIX);
        }
        // TODO check long vector
        int[] dims = new int[]{nrow, ncol};
        switch (type) {
            case INTSXP:
                return RDataFactory.createIntVector(new int[nrow * ncol], RDataFactory.COMPLETE_VECTOR, dims);
            case REALSXP:
                return RDataFactory.createDoubleVector(new double[nrow * ncol], RDataFactory.COMPLETE_VECTOR, dims);
            case LGLSXP:
                return RDataFactory.createLogicalVector(new byte[nrow * ncol], RDataFactory.COMPLETE_VECTOR, dims);
            case CHARSXP:
                return RDataFactory.createStringVector(new String[nrow * ncol], RDataFactory.COMPLETE_VECTOR, dims);
            case CPLXSXP:
                return RDataFactory.createComplexVector(new double[2 * (nrow * ncol)], RDataFactory.COMPLETE_VECTOR, dims);
            default:
                throw unimplemented();
        }
    }

    static int LENGTH(Object x) {
        if (x instanceof RAbstractContainer) {
            return ((RAbstractContainer) x).getLength();
        } else if (x == RNull.instance) {
            return 0;
        } else if (x instanceof Integer || x instanceof Double || x instanceof Byte || x instanceof String) {
            return 1;
        } else {
            throw unimplemented("unexpected value: " + x);
        }
    }

    static void SET_STRING_ELT(Object x, int i, Object v) {
        // TODO error checks
        RStringVector xv = (RStringVector) x;
        xv.setElement(i, v);
    }

    static void SET_VECTOR_ELT(Object x, int i, Object v) {
        // TODO error checks
        RList list = (RList) x;
        list.setElement(i, v);
    }

    static byte[] RAW(Object x) {
        if (x instanceof RRawVector) {
            return ((RRawVector) x).getDataWithoutCopying();
        } else if (x instanceof RRaw) {
            return new byte[]{((RRaw) x).getValue()};
        } else {
            throw unimplemented();
        }
    }

    private static int toWideLogical(byte v) {
        return RRuntime.isNA(v) ? Integer.MIN_VALUE : v;
    }

    static int[] LOGICAL(Object x) {
        if (x instanceof RLogicalVector) {
            // TODO: this should not actually copy...
            RLogicalVector vector = (RLogicalVector) x;
            int[] array = new int[vector.getLength()];
            for (int i = 0; i < vector.getLength(); i++) {
                array[i] = toWideLogical(vector.getDataAt(i));
            }
            return array;
        } else if (x instanceof Byte) {
            return new int[]{toWideLogical((Byte) x)};
        } else {
            throw unimplemented();
        }

    }

    static int[] INTEGER(Object x) {
        if (x instanceof RIntVector) {
            return ((RIntVector) x).getDataWithoutCopying();
        } else if (x instanceof RIntSequence) {
            return ((RIntSequence) x).materialize().getDataWithoutCopying();
        } else if (x instanceof Integer) {
            return new int[]{(Integer) x};
        } else if (x instanceof RLogicalVector) {
            RLogicalVector vec = (RLogicalVector) x;
            int[] result = new int[vec.getLength()];
            for (int i = 0; i < result.length; i++) {
                result[i] = vec.getDataAt(i);
            }
            return result;
        } else {
            guaranteeInstanceOf(x, Byte.class);
            return new int[]{(Byte) x};
        }
    }

    static double[] REAL(Object x) {
        if (x instanceof RDoubleVector) {
            return ((RDoubleVector) x).getDataWithoutCopying();
        } else if (x instanceof RDoubleSequence) {
            return ((RDoubleSequence) x).materialize().getDataWithoutCopying();
        } else {
            guaranteeInstanceOf(x, Double.class);
            return new double[]{(Double) x};
        }
    }

    static String STRING_ELT(Object x, int i) {
        if (x instanceof String) {
            assert i == 0;
            return (String) x;
        } else if (x instanceof RStringVector) {
            return ((RStringVector) x).getDataAt(i);
        } else {
            throw unimplemented();
        }
    }

    static Object VECTOR_ELT(Object x, int i) {
        if (x instanceof RList) {
            return ((RList) x).getDataAt(i);
        } else {
            throw unimplemented();
        }
    }

    static int NAMED(Object x) {
        if (x instanceof RShareable) {
            return ((RShareable) x).isShared() ? 1 : 0;
        } else {
            throw unimplemented();
        }
    }

    static Object Rf_duplicate(Object x) {
        guaranteeInstanceOf(x, RAbstractVector.class);
        return ((RAbstractVector) x).copy();
    }

    static Object PRINTNAME(Object x) {
        guaranteeInstanceOf(x, RSymbol.class);
        return ((RSymbol) x).getName();
    }

    static Object TAG(Object e) {
        guaranteeInstanceOf(e, RPairList.class);
        return ((RPairList) e).getTag();
    }

    static Object CAR(Object e) {
        guaranteeInstanceOf(e, RPairList.class);
        Object car = ((RPairList) e).car();
        return car;
    }

    static Object CDR(Object e) {
        guaranteeInstanceOf(e, RPairList.class);
        Object cdr = ((RPairList) e).cdr();
        return cdr;
    }

    static Object CADR(@SuppressWarnings("unused") Object x) {
        throw unimplemented();
    }

    static Object SETCAR(Object x, Object y) {
        guaranteeInstanceOf(x, RPairList.class);
        ((RPairList) x).setCar(y);
        return x; // TODO check or y?
    }

    static Object SETCDR(Object x, Object y) {
        guaranteeInstanceOf(x, RPairList.class);
        ((RPairList) x).setCdr(y);
        return x; // TODO check or y?
    }

    static Object R_FindNamespace(Object name) {
        Object result = RContext.getInstance().stateREnvironment.getNamespaceRegistry().get(RRuntime.asString(name));
        return result;
    }

    static Object Rf_eval(Object expr, Object env) {
        guarantee(env instanceof REnvironment);
        Object result;
        if (expr instanceof RPromise) {
            result = RContext.getRRuntimeASTAccess().forcePromise(expr);
        } else if (expr instanceof RExpression) {
            result = RContext.getEngine().eval((RExpression) expr, (REnvironment) env, 0);
        } else if (expr instanceof RLanguage) {
            result = RContext.getEngine().eval((RLanguage) expr, (REnvironment) env, 0);
        } else {
            // just return value
            result = expr;
        }
        return result;
    }

    static Object Rf_findfun(Object symbolObj, Object envObj) {
        guarantee(envObj instanceof REnvironment);
        REnvironment env = (REnvironment) envObj;
        guarantee(symbolObj instanceof RSymbol);
        RSymbol symbol = (RSymbol) symbolObj;
        // Works but not remotely efficient
        Source source = Source.fromNamedText("get(\"" + symbol.getName() + "\", mode=\"function\")", "<Rf_findfun>");
        try {
            Object result = RContext.getEngine().parseAndEval(source, env.getFrame(), false);
            return result;
        } catch (ParseException ex) {
            throw RInternalError.shouldNotReachHere(ex);
        }
    }

    static Object Rf_GetOption1(Object tag) {
        guarantee(tag instanceof RSymbol);
        Object result = RContext.getInstance().stateROptions.getValue(((RSymbol) tag).getName());
        return result;
    }

    static void Rf_gsetVar(Object symbol, Object value, Object rho) {
        guarantee(symbol instanceof RSymbol);
        REnvironment baseEnv = RContext.getInstance().stateREnvironment.getBaseEnv();
        guarantee(rho == baseEnv);
        try {
            baseEnv.put(((RSymbol) symbol).getName(), value);
        } catch (PutException e) {
            e.printStackTrace();
        }
    }

    static void DUPLICATE_ATTRIB(Object to, Object from) {
        if (from instanceof RAttributable) {
            guaranteeInstanceOf(to, RAttributable.class);
            RAttributes attributes = ((RAttributable) from).getAttributes();
            ((RAttributable) to).initAttributes(attributes == null ? null : attributes.copy());
        }
        // TODO: copy OBJECT? and S4 attributes
    }

    // Checkstyle: resume method name check

    static Object validate(Object x) {
        return x;
    }

    static Object getGlobalEnv() {
        return RContext.getInstance().stateREnvironment.getGlobalEnv();
    }

    static Object getBaseEnv() {
        return RContext.getInstance().stateREnvironment.getBaseEnv();
    }

    static Object getBaseNamespace() {
        return RContext.getInstance().stateREnvironment.getBaseNamespace();
    }

    static Object getNamespaceRegistry() {
        return RContext.getInstance().stateREnvironment.getNamespaceRegistry();
    }

    static int isInteractive() {
        return RContext.getInstance().isInteractive() ? 1 : 0;
    }

    static int isS4Object(Object x) {
        return x instanceof RS4Object ? 1 : 0;
    }

    static void printf(String message) {
        RContext.getInstance().getConsoleHandler().print(message);
    }
}
