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
import com.oracle.truffle.r.library.gpu.types.RGPUType;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.library.gpu.utils.FactoryDataUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RDoubleSequence;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RIntSequence;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

/**
 * Parallel Map implementation corresponding to sapply R Builin. This implementation connects to
 * Marawacc-API for the Java threads implementation and GPU.
 *
 * The GPU supports relies on the Partial Evaluation step after Truffle decides to compile the AST
 * to binary code.
 *
 *
 */
public final class MarawaccMapBuiltin extends RExternalBuiltinNode {

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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static PArray<?> marshalSimple(RGPUType type, RAbstractVector input) {
        PArray parray = null;
        switch (type) {
            case INT:
                parray = new PArray<>(input.getLength(), TypeFactory.Integer());
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, input.getDataAtAsObject(k));
                }
                break;
            case DOUBLE:
                parray = new PArray<>(input.getLength(), TypeFactory.Double());
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, input.getDataAtAsObject(k));
                }
            case BOOLEAN:
                parray = new PArray<>(input.getLength(), TypeFactory.Boolean());
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, input.getDataAtAsObject(k));
                }
            default:
                throw new RuntimeException("Data type not supported");
        }
        return parray;
    }

    @SuppressWarnings({"unchecked", "cast", "rawtypes"})
    private static PArray<?> marshalWithTuples(RGPUType type, RAbstractVector input, RAbstractVector[] additionalArgs) {
        PArray parray = null;
        switch (type) {
            case INT:
                parray = new PArray<>(input.getLength(), TypeFactory.Integer());
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, (Integer) input.getDataAtAsObject(k));
                }
                break;
            case DOUBLE:
                parray = new PArray<>(input.getLength(), TypeFactory.Double());
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, (Integer) input.getDataAtAsObject(k));
                }
            case BOOLEAN:
                parray = new PArray<>(input.getLength(), TypeFactory.Boolean());
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, (Boolean) input.getDataAtAsObject(k));
                }
            default:
                throw new RuntimeException("Data type not supported");
        }
        return parray;
    }

    @SuppressWarnings("rawtypes")
    private static PArray<?> marshall(RGPUType typeFirstInput, RAbstractVector input, RAbstractVector[] additionalArgs) {
        PArray parray = null;
        if (additionalArgs == null) {
            // Simple PArray
            parray = marshalSimple(typeFirstInput, input);
        } else {
            // Tuples
            parray = marshalWithTuples(typeFirstInput, input, additionalArgs);
        }
        return parray;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static PArray<?> runMarawaccThreads(RAbstractVector input, RootCallTarget callTarget, RFunction rFunction, String[] nameArgs, RGPUType inputType, int nThreads,
                    RAbstractVector[] additionalArgs) {
        ArrayFunction composeLambda = createMarawaccLambda(callTarget, rFunction, nameArgs, nThreads);
        PArray pArrayInput = marshall(inputType, input, additionalArgs);
        PArray<?> result = composeLambda.apply(pArrayInput);

        if (ASTxOptions.printResult) {
            System.out.println("result -- ");
            printPArray(result);
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RAbstractVector unMarshallResultFromPArrays(RGPUType type, PArray result) {
        if (type == RGPUType.INT) {
            return FactoryDataUtils.getIntVector(result);
        } else {
            return FactoryDataUtils.getDoubleVector(result);
        }
    }

    private static RAbstractVector unMarshallResultFromList(RGPUType type, ArrayList<Object> result) {
        if (type == RGPUType.INT) {
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

    public static RGPUType typeInference(RAbstractVector input) {
        RGPUType type = null;
        if (input instanceof RIntSequence) {
            type = RGPUType.INT;
        } else if (input instanceof RDoubleSequence) {
            type = RGPUType.DOUBLE;
        } else if (input instanceof RLogicalVector) {
            type = RGPUType.BOOLEAN;
        }
        return type;
    }

    public static RGPUType typeInference(Object value) {
        RGPUType type = null;
        if (value instanceof Integer) {
            type = RGPUType.INT;
        } else if (value instanceof Double) {
            type = RGPUType.DOUBLE;
        } else if (value instanceof Boolean) {
            type = RGPUType.BOOLEAN;
        } else {
            System.out.println("Data type not supported: " + value.getClass());
        }
        return type;
    }

    @SuppressWarnings({"unused"})
    public static RAbstractVector computeMap(RAbstractVector input, RFunction function, RootCallTarget target, RAbstractVector[] additionalArgs, int nThreads) {

        int nArgs = ASTxUtils.getNumberOfArguments(function);
        String[] argsName = ASTxUtils.getArgumentsNames(function);

        String source = ASTxUtils.getSourceCode(function);
        StringBuffer rcodeSource = new StringBuffer(source);

        Object[] argsPackage = ASTxUtils.getArgsPackage(nArgs, function, input, additionalArgs, argsName, 0);
        Object value = function.getTarget().call(argsPackage);

        RGPUType inputType = typeInference(input);
        RGPUType outputType = typeInference(value);

        if (ASTxOptions.runMarawaccThreads) {
            // Marawacc multithread
            PArray<?> result = runMarawaccThreads(input, target, function, argsName, inputType, nThreads, additionalArgs);
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

        // Prepare all inputs in an array of Objects
        RAbstractVector[] additionalInputs = null;
        if (args.getLength() > 3) {
            additionalInputs = new RAbstractVector[args.getLength() - 3];

            for (int i = 0; i < additionalInputs.length; i++) {
                additionalInputs[i] = (RAbstractVector) args.getArgument(i + 3);
            }
        }
        return computeMap(input, function, target, additionalInputs, nThreads);
    }
}
