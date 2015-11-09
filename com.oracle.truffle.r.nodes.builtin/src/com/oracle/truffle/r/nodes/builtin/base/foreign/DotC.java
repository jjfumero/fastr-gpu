/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995-2012, The R Core Team
 * Copyright (c) 2003, The R Foundation
 * Copyright (c) 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.nodes.builtin.base.foreign;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.access.AccessFieldNode;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ffi.*;
import com.oracle.truffle.r.runtime.ffi.DLL.*;

/**
 * {@code .C} functions.
 *
 * TODO Completeness (more types, more error checks), Performance (copying). Especially all the
 * subtleties around copying.
 *
 * See <a href="https://stat.ethz.ch/R-manual/R-devel/library/base/html/Foreign.html">here</a>.
 */
@RBuiltin(name = ".C", kind = RBuiltinKind.PRIMITIVE, parameterNames = {".NAME", "...", "NAOK", "DUP", "PACKAGE", "ENCODING"})
public abstract class DotC extends RBuiltinNode {

    private static final int SCALAR_DOUBLE = 0;
    private static final int SCALAR_INT = 1;
    private static final int SCALAR_LOGICAL = 2;
    @SuppressWarnings("unused") private static final int SCALAR_STRING = 3;
    private static final int VECTOR_DOUBLE = 10;
    private static final int VECTOR_INT = 11;
    private static final int VECTOR_LOGICAL = 12;
    @SuppressWarnings("unused") private static final int VECTOR_STRING = 12;

    @Override
    public Object[] getDefaultParameterValues() {
        return new Object[]{RMissing.instance, RArgsValuesAndNames.EMPTY, RRuntime.LOGICAL_FALSE, RRuntime.LOGICAL_FALSE, RMissing.instance, RMissing.instance};
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RList c(RList symbol, RArgsValuesAndNames args, byte naok, byte dup, RMissing rPackage, RMissing encoding) {
        controlVisibility();
        long address = ((RExternalPtr) symbol.getDataAt(AccessFieldNode.getElementIndexByName(symbol.getNames(), "address"))).getAddr();
        String name = RRuntime.asString(symbol.getDataAt(AccessFieldNode.getElementIndexByName(symbol.getNames(), "name")));
        return dispatch(this, address, name, naok, dup, args.getArguments());
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RList c(String f, RArgsValuesAndNames args, byte naok, byte dup, RMissing rPackage, RMissing encoding, //
                    @Cached("create()") BranchProfile errorProfile) {
        controlVisibility();
        SymbolInfo symbolInfo = DLL.findSymbolInfo(f, null);
        if (symbolInfo == null) {
            errorProfile.enter();
            throw RError.error(this, RError.Message.C_SYMBOL_NOT_IN_TABLE, f);
        }
        return dispatch(this, symbolInfo.address, symbolInfo.symbol, naok, dup, args.getArguments());
    }

    private static int[] checkNAs(RBuiltinNode node, int argIndex, int[] data) {
        CompilerAsserts.neverPartOfCompilation();
        for (int i = 0; i < data.length; i++) {
            if (RRuntime.isNA(data[i])) {
                throw RError.error(node, RError.Message.NA_IN_FOREIGN_FUNCTION_CALL, argIndex);
            }
        }
        return data;
    }

    private static double[] checkNAs(RBuiltinNode node, int argIndex, double[] data) {
        CompilerAsserts.neverPartOfCompilation();
        for (int i = 0; i < data.length; i++) {
            if (!RRuntime.isFinite(data[i])) {
                throw RError.error(node, RError.Message.NA_NAN_INF_IN_FOREIGN_FUNCTION_CALL, argIndex);
            }
        }
        return data;
    }

    private static RStringVector validateArgNames(int argsLength, ArgumentsSignature signature) {
        String[] listArgNames = new String[argsLength];
        for (int i = 0; i < argsLength; i++) {
            String name = signature.getName(i + 1);
            if (name == null) {
                name = RRuntime.NAMES_ATTR_EMPTY_VALUE;
            }
            listArgNames[i] = name;
        }
        return RDataFactory.createStringVector(listArgNames, RDataFactory.COMPLETE_VECTOR);
    }

    @TruffleBoundary
    public static RList dispatch(RBuiltinNode node, long address, String name, byte naok, byte dup, Object[] argValues) {
        @SuppressWarnings("unused")
        boolean dupArgs = RRuntime.fromLogical(dup);
        @SuppressWarnings("unused")
        boolean checkNA = RRuntime.fromLogical(naok);
        // Analyze the args, making copies (ignoring dup for now)
        int[] argTypes = new int[argValues.length];
        Object[] nativeArgs = new Object[argValues.length];
        for (int i = 0; i < argValues.length; i++) {
            Object arg = argValues[i];
            if (arg instanceof RAbstractDoubleVector) {
                argTypes[i] = VECTOR_DOUBLE;
                nativeArgs[i] = checkNAs(node, i + 1, ((RAbstractDoubleVector) arg).materialize().getDataCopy());
            } else if (arg instanceof RAbstractIntVector) {
                argTypes[i] = VECTOR_INT;
                nativeArgs[i] = checkNAs(node, i + 1, ((RAbstractIntVector) arg).materialize().getDataCopy());
            } else if (arg instanceof RAbstractLogicalVector) {
                argTypes[i] = VECTOR_LOGICAL;
                // passed as int[]
                byte[] data = ((RAbstractLogicalVector) arg).materialize().getDataWithoutCopying();
                int[] dataAsInt = new int[data.length];
                for (int j = 0; j < data.length; j++) {
                    // An NA is an error but the error handling happens in checkNAs
                    dataAsInt[j] = RRuntime.isNA(data[j]) ? RRuntime.INT_NA : data[j];
                }
                nativeArgs[i] = checkNAs(node, i + 1, dataAsInt);
            } else if (arg instanceof Double) {
                argTypes[i] = SCALAR_DOUBLE;
                nativeArgs[i] = checkNAs(node, i + 1, new double[]{(double) arg});
            } else if (arg instanceof Integer) {
                argTypes[i] = SCALAR_INT;
                nativeArgs[i] = checkNAs(node, i + 1, new int[]{(int) arg});
            } else if (arg instanceof Byte) {
                argTypes[i] = SCALAR_LOGICAL;
                nativeArgs[i] = checkNAs(node, i + 1, new int[]{RRuntime.isNA((byte) arg) ? RRuntime.INT_NA : (byte) arg});
            } else {
                throw RError.error(node, RError.Message.UNIMPLEMENTED_ARG_TYPE, i + 1);
            }
        }
        if (FastROptions.TraceNativeCalls.getBooleanValue()) {
            trace(name, nativeArgs);
        }
        RFFIFactory.getRFFI().getCRFFI().invoke(address, nativeArgs);
        // we have to assume that the native method updated everything
        RStringVector listNames = validateArgNames(argValues.length, node.getSuppliedSignature());
        Object[] results = new Object[argValues.length];
        for (int i = 0; i < argValues.length; i++) {
            switch (argTypes[i]) {
                case SCALAR_DOUBLE:
                    results[i] = RDataFactory.createDoubleVector((double[]) nativeArgs[i], RDataFactory.COMPLETE_VECTOR);
                    break;
                case SCALAR_INT:
                    results[i] = RDataFactory.createIntVector((int[]) nativeArgs[i], RDataFactory.COMPLETE_VECTOR);
                    break;
                case SCALAR_LOGICAL:
                    results[i] = RDataFactory.createLogicalVector((byte[]) nativeArgs[i], RDataFactory.COMPLETE_VECTOR);
                    break;
                case VECTOR_DOUBLE:
                    results[i] = ((RAbstractDoubleVector) argValues[i]).materialize().copyResetData((double[]) nativeArgs[i]);
                    break;
                case VECTOR_INT:
                    results[i] = ((RAbstractIntVector) argValues[i]).materialize().copyResetData((int[]) nativeArgs[i]);
                    break;
                case VECTOR_LOGICAL: {
                    int[] intData = (int[]) nativeArgs[i];
                    byte[] byteData = new byte[intData.length];
                    for (int j = 0; j < intData.length; j++) {
                        byteData[j] = RRuntime.isNA(intData[j]) ? RRuntime.LOGICAL_NA : RRuntime.asLogical(intData[j] != 0);
                    }
                    results[i] = ((RAbstractLogicalVector) argValues[i]).materialize().copyResetData(byteData);
                    break;
                }
            }
        }
        return RDataFactory.createList(results, listNames);
    }

    @TruffleBoundary
    private static void trace(String name, Object[] nativeArgs) {
        System.out.println("calling " + name + ": " + Arrays.toString(nativeArgs));
    }
}
