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
import com.oracle.truffle.r.library.gpu.utils.AcceleratorRUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public final class XMapBuiltin extends RExternalBuiltinNode {

    private enum Type {
        INT,
        DOUBLE,
        NULL
    }

    // Unmarshall to RIntVector
    private static RIntVector getIntVector(ArrayList<Object> list) {
        int[] array = list.stream().mapToInt(i -> (Integer) i).toArray();
        return RDataFactory.createIntVector(array, false);
    }

    // Unmarshall to RDoubleVector
    private static RDoubleVector getDoubleVector(ArrayList<Object> list) {
        double[] array = list.stream().mapToDouble(i -> (Double) i).toArray();
        return RDataFactory.createDoubleVector(array, false);
    }

    @SuppressWarnings("unchecked")
    private static <T, R> ArrayFunction<T, R> createLambda(RootCallTarget callTarget, RFunction rFunction, String[] nameArgs) {

        ArrayFunction<T, R> function = (ArrayFunction<T, R>) uk.ac.ed.jpai.Marawacc.mapJavaThreads(8, x -> {
            Object[] argsPackage = AcceleratorRUtils.getArgsPackage(1, rFunction, x, nameArgs);

            long start = System.nanoTime();
            // Invoke the R code
                        Object result = callTarget.call(argsPackage);
                        long end = System.nanoTime();
                        if ((end - start) > 100000) {
                            System.out.println(Thread.currentThread().getName() + ": " + (end - start));
                        }

                        return result;
                    });

        return function;
    }

    private static void checkMarawaccAPILambdas(int nArgs, RAbstractVector input, RootCallTarget callTarget, RFunction rFunction, String[] nameArgs) {
        if (nArgs == 1) {

            // If nArgs is equal 1, means we need to build the PArray (no tuples).
            // For the input.

            // NOTE: special case for int for testing
            ArrayFunction<Integer, ?> composeLambda = createLambda(callTarget, rFunction, nameArgs);

            // Use the lambda as an example
            PArray<Integer> i = new PArray<>(input.getLength(), TypeFactory.Integer());
            // Marshal data
            for (int k = 0; k < i.size(); k++) {
                i.put(k, (Integer) input.getDataAtAsObject(k));
            }
            PArray<?> result = composeLambda.apply(i);

// System.out.println("result -- ");
// for (int k = 0; k < result.size(); k++) {
// System.out.println(result.get(k));
// }
        }
    }

    @SuppressWarnings({"unused"})
    public static RAbstractVector computeMap(RAbstractVector input, RFunction function, RootCallTarget target, RAbstractVector inputB) {

        int nArgs = AcceleratorRUtils.getNumberOfArguments(function);
        String[] argsName = AcceleratorRUtils.getArgumentsNames(function);

        String source = AcceleratorRUtils.getSourceCode(function);
        StringBuffer rcodeSource = new StringBuffer(source);

        RAbstractVector[] additionalArgs = null;
        if (inputB != null) {
            additionalArgs = new RAbstractVector[]{inputB};
        }

        Object[] argsPackage = AcceleratorRUtils.getArgsPackage(nArgs, function, input, additionalArgs, argsName, 0);
        Object value = function.getTarget().call(argsPackage);

        Type outputType = null;

        if (value instanceof Integer) {
            outputType = Type.INT;
        } else if (value instanceof Double) {
            outputType = Type.DOUBLE;
        } else {
            System.out.println("Data type not supported: " + value.getClass());
            return null;
        }

        ArrayList<Object> output = new ArrayList<>(input.getLength());
        output.add(value);

        RootCallTarget callTarget = function.getTarget();

        // Just to check the lambda expressions
        checkMarawaccAPILambdas(nArgs, input, callTarget, function, argsName);

        for (int i = 1; i < input.getLength(); i++) {
            argsPackage = AcceleratorRUtils.getArgsPackage(nArgs, function, input, additionalArgs, argsName, i);
            Object val = callTarget.call(argsPackage);
            output.add(val);
        }

        if (outputType == Type.INT) {
            return getIntVector(output);
        } else if (outputType == Type.DOUBLE) {
            return getDoubleVector(output);
        } else {
            // This case never happens
            System.out.println("Data type not supported: ");
            return null;
        }

        // NOTE: force the compilation with no profiling (the lambda should be different)
        // try {
        // boolean compileFunction = AccTruffleCompiler.compileFunction(function);
        // } catch (InvocationTargetException | IllegalAccessException e) {
        // e.printStackTrace();
        // }
    }

    public static RAbstractVector computeMap(RAbstractVector input, RFunction function, RAbstractVector inputB) {
        return computeMap(input, function, function.getTarget(), inputB);
    }

    @Override
    public Object call(RArgsValuesAndNames args) {
        // flink.map(x, function, ...)
        RAbstractVector input = (RAbstractVector) args.getArgument(0);
        RFunction function = (RFunction) args.getArgument(1);

        RAbstractVector input2 = null;
        if (args.getLength() > 2) {
            input2 = (RAbstractVector) args.getArgument(2);
        }
        return computeMap(input, function, input2);
    }

}
