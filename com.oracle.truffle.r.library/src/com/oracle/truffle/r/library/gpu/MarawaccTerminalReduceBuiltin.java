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

import uk.ac.ed.accelerator.truffle.ASTxOptions;
import uk.ac.ed.datastructures.common.PArray;
import uk.ac.ed.jpai.ArrayFunction;
import uk.ac.ed.jpai.Marawacc;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.r.library.gpu.cache.RGPUCache;
import com.oracle.truffle.r.library.gpu.exceptions.MarawaccRuntimeDeoptException;
import com.oracle.truffle.r.library.gpu.exceptions.MarawaccTypeException;
import com.oracle.truffle.r.library.gpu.types.TypeInfo;
import com.oracle.truffle.r.library.gpu.types.TypeInfoList;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public final class MarawaccTerminalReduceBuiltin extends RExternalBuiltinNode {

    private static <T, R> ArrayFunction<T, R> createMarawaccLambda(int nArgs, RootCallTarget callTarget, RFunction rFunction, String[] nameArgs, int neutral) {
        @SuppressWarnings("unchecked")
        ArrayFunction<T, R> function = (ArrayFunction<T, R>) Marawacc.reduce((x, y) -> {
            Object[] argsPackage = ASTxUtils.createRArguments(nArgs, rFunction, x, y, nameArgs);
            Object result = callTarget.call(argsPackage);
            return (Integer) result;
        }, neutral);
        return function;
    }

    private static void printPArray(PArray<?> result) {
        for (int k = 0; k < result.size(); k++) {
            System.out.println(result.get(k));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static PArray<?> runMarawaccThreads(RAbstractVector input, RootCallTarget callTarget, RFunction rFunction, String[] nameArgs, int nThreads,
                    RAbstractVector[] additionalArgs, TypeInfoList infoList) {
        ArrayFunction composeLambda = createMarawaccLambda(infoList.size() + 1, callTarget, rFunction, nameArgs, nThreads);
        PArray pArrayInput = ASTxUtils.marshal(input, additionalArgs, infoList);
        PArray<?> result = composeLambda.apply(pArrayInput);

        if (ASTxOptions.printResult) {
            System.out.println("result -- ");
            printPArray(result);
        }
        return result;
    }

    private static ArrayList<Object> runJavaSequential(RAbstractVector input, RootCallTarget target, RFunction function, int nArgs, RAbstractVector[] additionalArgs, String[] argsName,
                    Object firstValue) {
        Object accumulator = firstValue;
        for (int i = 1; i < input.getLength(); i++) {
            Object[] argsPackage = ASTxUtils.createRArgumentsForReduction(nArgs, accumulator, function, input, additionalArgs, argsName, i);
            accumulator = target.call(argsPackage);
        }
        ArrayList<Object> output = new ArrayList<>();
        output.add(accumulator);
        return output;
    }

    public static RAbstractVector computeReduction(RAbstractVector input, RFunction function, RootCallTarget target, RAbstractVector[] additionalArgs, int neutral) {

        int nArgs = ASTxUtils.getNumberOfArguments(function);
        String[] argsName = ASTxUtils.getArgumentsNames(function);

        Object[] argsPackage = ASTxUtils.createRArgumentsForReduction(nArgs, neutral, function, input, additionalArgs, argsName, 0);
        Object value = function.getTarget().call(argsPackage);
        TypeInfoList inputTypeList = null;
        TypeInfo outputType = null;
        try {
            inputTypeList = ASTxUtils.typeInference(input, additionalArgs);
            outputType = ASTxUtils.typeInference(value);
        } catch (MarawaccTypeException e) {
            throw new MarawaccRuntimeDeoptException("Input types not supported");
        }

        if (!ASTxOptions.runMarawaccThreads) {
            // Run sequential
            ArrayList<Object> result = runJavaSequential(input, target, function, nArgs, additionalArgs, argsName, value);
            return ASTxUtils.unMarshallResultFromArrayList(outputType, result);
        } else {
            // Marawacc multithread
            PArray<?> result = runMarawaccThreads(input, target, function, argsName, neutral, additionalArgs, inputTypeList);
            return ASTxUtils.unMarshallResultFromPArrays(outputType, result);
        }
    }

    /**
     * Call method with variable number of arguments.
     *
     * Built-in from R:
     *
     * <code>
     * marawacc.reduce(x, function, ...)
     * </code>
     *
     * It invokes to the Marawacc-API for multiple-threads/GPU backend.
     *
     */
    @Override
    public Object call(RArgsValuesAndNames args) {
        RAbstractVector input = (RAbstractVector) args.getArgument(0);
        RFunction function = (RFunction) args.getArgument(1);

        // Get the callTarget from the cache
        RootCallTarget target = RGPUCache.INSTANCE.lookup(function);
        int neutral = ((Double) args.getArgument(2)).intValue();

        // Prepare all inputs in an array of Objects
        RAbstractVector[] additionalInputs = null;
        if (args.getLength() > 3) {
            additionalInputs = new RAbstractVector[args.getLength() - 3];
            for (int i = 0; i < additionalInputs.length; i++) {
                additionalInputs[i] = (RAbstractVector) args.getArgument(i + 3);
            }
        }
        return computeReduction(input, function, target, additionalInputs, neutral);
    }
}
