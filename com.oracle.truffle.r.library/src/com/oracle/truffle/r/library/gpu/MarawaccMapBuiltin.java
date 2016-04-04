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
import uk.ac.ed.datastructures.tuples.Tuple2;
import uk.ac.ed.datastructures.tuples.Tuple3;
import uk.ac.ed.datastructures.tuples.Tuple4;
import uk.ac.ed.jpai.ArrayFunction;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.r.library.gpu.cache.RGPUCache;
import com.oracle.truffle.r.library.gpu.options.ASTxOptions;
import com.oracle.truffle.r.library.gpu.types.TypeInfo;
import com.oracle.truffle.r.library.gpu.types.TypeInfoList;
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
 */
public final class MarawaccMapBuiltin extends RExternalBuiltinNode {

    private static <T, R> ArrayFunction<T, R> createMarawaccLambda(int nArgs, RootCallTarget callTarget, RFunction rFunction, String[] nameArgs, int nThreads) {
        @SuppressWarnings("unchecked")
        ArrayFunction<T, R> function = (ArrayFunction<T, R>) uk.ac.ed.jpai.Marawacc.mapJavaThreads(nThreads, x -> {
            Object[] argsPackage = ASTxUtils.getArgsPackage(nArgs, rFunction, x, nameArgs);
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
    private static PArray<?> marshalSimple(TypeInfoList infoList, RAbstractVector input) {
        PArray parray = null;
        TypeInfo type = infoList.get(0);
        switch (type) {
            case INT:
                parray = new PArray<>(input.getLength(), TypeFactory.Integer());
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, input.getDataAtAsObject(k));
                }
                return parray;
            case DOUBLE:
                parray = new PArray<>(input.getLength(), TypeFactory.Double());
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, input.getDataAtAsObject(k));
                }
                return parray;
            case BOOLEAN:
                parray = new PArray<>(input.getLength(), TypeFactory.Boolean());
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, input.getDataAtAsObject(k));
                }
                return parray;
            default:
                throw new RuntimeException("Data type not supported");
        }
    }

    public static String composeReturnType(TypeInfoList infoList) {
        StringBuffer returns = new StringBuffer("Tuple" + infoList.size() + "<");
        returns.append(infoList.get(0).getJavaType());
        for (int i = 1; i < infoList.size(); i++) {
            returns.append("," + infoList.get(i).getJavaType());
        }
        returns.append(">");
        return returns.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static PArray<?> marshalWithTuples(RAbstractVector input, RAbstractVector[] additionalArgs, TypeInfoList infoList) {
        String returns = composeReturnType(infoList);
        PArray parray = new PArray<>(input.getLength(), TypeFactory.Tuple(returns));
        switch (infoList.size()) {
            case 2:
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, new Tuple2<>(input.getDataAtAsObject(k), additionalArgs[0].getDataAtAsObject(k)));
                }
                return parray;
            case 3:
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, new Tuple3<>(input.getDataAtAsObject(k), additionalArgs[0].getDataAtAsObject(k), additionalArgs[1].getDataAtAsObject(k)));
                }
                return parray;
            case 4:
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, new Tuple4<>(input.getDataAtAsObject(k), additionalArgs[0].getDataAtAsObject(k), additionalArgs[1].getDataAtAsObject(k), additionalArgs[2].getDataAtAsObject(k)));
                }
                return parray;
            default:
                throw new RuntimeException("Tuple number not supported yet");
        }
    }

    @SuppressWarnings("rawtypes")
    private static PArray<?> marshall(RAbstractVector input, RAbstractVector[] additionalArgs, TypeInfoList infoList) {
        PArray parray = null;
        if (additionalArgs == null) {
            // Simple PArray
            parray = marshalSimple(infoList, input);
        } else {
            // Tuples
            parray = marshalWithTuples(input, additionalArgs, infoList);
        }
        return parray;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static PArray<?> runMarawaccThreads(RAbstractVector input, RootCallTarget callTarget, RFunction rFunction, String[] nameArgs, int nThreads,
                    RAbstractVector[] additionalArgs, TypeInfoList infoList) {
        ArrayFunction composeLambda = createMarawaccLambda(infoList.size(), callTarget, rFunction, nameArgs, nThreads);
        PArray pArrayInput = marshall(input, additionalArgs, infoList);
        PArray<?> result = composeLambda.apply(pArrayInput);

        if (ASTxOptions.printResult) {
            System.out.println("result -- ");
            printPArray(result);
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RAbstractVector unMarshallResultFromPArrays(TypeInfo type, PArray result) {
        if (type == TypeInfo.INT) {
            return FactoryDataUtils.getIntVector(result);
        } else {
            return FactoryDataUtils.getDoubleVector(result);
        }
    }

    private static RAbstractVector unMarshallResultFromList(TypeInfo type, ArrayList<Object> result) {
        if (type == TypeInfo.INT) {
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

    public static TypeInfo typeInference(RAbstractVector input) {
        TypeInfo type = null;
        if (input instanceof RIntSequence) {
            type = TypeInfo.INT;
        } else if (input instanceof RDoubleSequence) {
            type = TypeInfo.DOUBLE;
        } else if (input instanceof RLogicalVector) {
            type = TypeInfo.BOOLEAN;
        }
        return type;
    }

    public static TypeInfoList typeInference(RAbstractVector input, RAbstractVector[] additionalArgs) {
        TypeInfoList list = new TypeInfoList();
        list.add(typeInference(input));
        if (additionalArgs != null) {
            for (int i = 0; i < additionalArgs.length; i++) {
                list.add(typeInference(additionalArgs[i]));
            }
        }
        return list;
    }

    public static TypeInfo typeInference(Object value) {
        TypeInfo type = null;
        if (value instanceof Integer) {
            type = TypeInfo.INT;
        } else if (value instanceof Double) {
            type = TypeInfo.DOUBLE;
        } else if (value instanceof Boolean) {
            type = TypeInfo.BOOLEAN;
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

        TypeInfoList inputTypeList = typeInference(input, additionalArgs);
        TypeInfo outputType = typeInference(value);

        if (ASTxOptions.runMarawaccThreads) {
            // Marawacc multithread
            PArray<?> result = runMarawaccThreads(input, target, function, argsName, nThreads, additionalArgs, inputTypeList);
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
