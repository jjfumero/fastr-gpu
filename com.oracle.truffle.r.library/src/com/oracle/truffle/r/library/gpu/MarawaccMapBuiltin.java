/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.library.gpu;

import java.util.ArrayList;

import uk.ac.ed.datastructures.common.PArray;
import uk.ac.ed.datastructures.common.TypeFactory;
import uk.ac.ed.jpai.ArrayFunction;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.r.library.gpu.cache.RGPUCache;
import com.oracle.truffle.r.library.gpu.options.ASTxOptions;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.library.gpu.utils.FactoryDataUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RDoubleSequence;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RIntSequence;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public final class MarawaccMapBuiltin extends RExternalBuiltinNode {

    private enum Type {
        INT,
        DOUBLE,
        NULL
    }

    private static <T, R> ArrayFunction<T, R> createMarawaccLambda(RootCallTarget callTarget, RFunction rFunction, String[] nameArgs, int nThreads) {
        @SuppressWarnings("unchecked")
        ArrayFunction<T, R> function = (ArrayFunction<T, R>) uk.ac.ed.jpai.Marawacc.mapJavaThreads(nThreads, x -> {
            Object[] argsPackage = ASTxUtils.getArgsPackage(1, rFunction, x, nameArgs);
            Object result = callTarget.call(argsPackage);
            return result;
        });
        return function;
    }

    private static void printPArray(PArray<?> result) {
        for (int k = 0; k < result.size(); k++) {
            System.out.println(result.get(k));
        }
    }

    @SuppressWarnings({"unchecked", "cast", "rawtypes"})
    private static PArray<?> marshall(Type type, RAbstractVector input) {
        PArray parray = null;
        if (type == Type.INT) {
            parray = new PArray<>(input.getLength(), TypeFactory.Integer());
            for (int k = 0; k < parray.size(); k++) {
                parray.put(k, (Integer) input.getDataAtAsObject(k));
            }
        }
        if (type == Type.DOUBLE) {
            parray = new PArray<>(input.getLength(), TypeFactory.Double());
            for (int k = 0; k < parray.size(); k++) {
                parray.put(k, (Double) input.getDataAtAsObject(k));
            }
        }
        return parray;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static PArray<?> runMarawaccThreads(int nArgs, RAbstractVector input, RootCallTarget callTarget, RFunction rFunction, String[] nameArgs, Type inputType, int nThreads) {
        if (nArgs == 1) {
            // If nArgs is equal 1, means we need to build the PArray (no tuples).
            // For the input.
            ArrayFunction composeLambda = createMarawaccLambda(callTarget, rFunction, nameArgs, nThreads);
            PArray pArrayInput = marshall(inputType, input);
            PArray<?> result = composeLambda.apply(pArrayInput);

            if (ASTxOptions.printResult) {
                System.out.println("result -- ");
                printPArray(result);
            }
            return result;
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RAbstractVector unMarshallResultFromPArrays(Type type, PArray result) {
        if (type == Type.INT) {
            return FactoryDataUtils.getIntVector(result);
        } else {
            return FactoryDataUtils.getDoubleVector(result);
        }
    }

    private static RAbstractVector unMarshallResultFromList(Type type, ArrayList<Object> result) {
        if (type == Type.INT) {
            return FactoryDataUtils.getIntVector(result);
        } else {
            return FactoryDataUtils.getDoubleVector(result);
        }
    }

    private static ArrayList<Object> runJavaSequential(RAbstractVector input, RootCallTarget target, RFunction function, int nArgs, RAbstractVector[] additionalArgs, String[] argsName,
                    Object firstValue) {
        // Java sequential
        ArrayList<Object> output = new ArrayList<>(input.getLength());
        output.add(firstValue);
        for (int i = 1; i < input.getLength(); i++) {
            Object[] argsPackage = ASTxUtils.getArgsPackage(nArgs, function, input, additionalArgs, argsName, i);
            Object val = target.call(argsPackage);
            output.add(val);
        }
        // NOTE: force the compilation with no profiling (the lambda should be different)
        // try {
        // boolean compileFunction = AccTruffleCompiler.compileFunction(function);
        // } catch (InvocationTargetException | IllegalAccessException e) {
        // e.printStackTrace();
        // }
        return output;
    }

    @SuppressWarnings({"unused"})
    public static RAbstractVector computeMap(RAbstractVector input, RFunction function, RootCallTarget target, RAbstractVector inputB, int nThreads) {

        int nArgs = ASTxUtils.getNumberOfArguments(function);
        String[] argsName = ASTxUtils.getArgumentsNames(function);

        String source = ASTxUtils.getSourceCode(function);
        StringBuffer rcodeSource = new StringBuffer(source);

        RAbstractVector[] additionalArgs = null;
        if (inputB != null) {
            additionalArgs = new RAbstractVector[]{inputB};
        }

        Object[] argsPackage = ASTxUtils.getArgsPackage(nArgs, function, input, additionalArgs, argsName, 0);
        Object value = function.getTarget().call(argsPackage);

        Type outputType = null;
        Type inputType = null;

        if (input instanceof RIntSequence) {
            inputType = Type.INT;
        } else if (input instanceof RDoubleSequence) {
            inputType = Type.DOUBLE;
        }

        if (value instanceof Integer) {
            outputType = Type.INT;
        } else if (value instanceof Double) {
            outputType = Type.DOUBLE;
        } else {
            System.out.println("Data type not supported: " + value.getClass());
            return null;
        }

        if (ASTxOptions.runMarawaccThreads) {
            // Marawacc multithread
            PArray<?> result = runMarawaccThreads(nArgs, input, target, function, argsName, inputType, nThreads);
            return unMarshallResultFromPArrays(outputType, result);
        } else {
            // Run sequential
            ArrayList<Object> result = runJavaSequential(input, target, function, nArgs, additionalArgs, argsName, value);
            return unMarshallResultFromList(outputType, result);
        }
    }

    /**
     * Call method with nargs arguments.
     *
     * Built-in from R:
     *
     * <code>
     * gpu.parallelMap(x, function, ...)
     * </code>
     *
     * It invokes to Marawacc API for multhread/GPU backend.
     *
     */
    @Override
    public Object call(RArgsValuesAndNames args) {
        RAbstractVector input = (RAbstractVector) args.getArgument(0);
        RFunction function = (RFunction) args.getArgument(1);

        // Get the callTarget from the cache
        RootCallTarget target = RGPUCache.INSTANCE.lookup(function);
        int nThreads = ((Double) args.getArgument(2)).intValue();
        RAbstractVector input2 = null;
        if (args.getLength() > 3) {
            input2 = (RAbstractVector) args.getArgument(3);
        }
        return computeMap(input, function, target, input2, nThreads);
    }
}
