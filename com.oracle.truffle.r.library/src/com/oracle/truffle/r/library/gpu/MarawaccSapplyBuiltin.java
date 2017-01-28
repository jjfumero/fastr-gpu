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

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.r.library.gpu.cache.RGPUCache;
import com.oracle.truffle.r.library.gpu.deoptimization.MarawaccDeopt;
import com.oracle.truffle.r.library.gpu.exceptions.MarawaccTypeException;
import com.oracle.truffle.r.library.gpu.types.TypeInfo;
import com.oracle.truffle.r.library.gpu.types.TypeInfoList;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

/**
 * Parallel Sapply implementation corresponding to sapply R Builin. This implementation connects to
 * Marawacc-API for the Java threads implementation and GPU. This is a blocking operation.
 *
 * The GPU supports relies on the Partial Evaluation step after Truffle decides to compile the AST
 * to binary code.
 *
 */
public final class MarawaccSapplyBuiltin extends RExternalBuiltinNode {

    /**
     * Create the lambda for Marawacc threads API.
     *
     * @param nArgs
     * @param callTarget
     * @param rFunction
     * @param nameArgs
     * @param nThreads
     * @return {@link ArrayFunction}
     */
    @SuppressWarnings("unchecked")
    private static <T, R> ArrayFunction<T, R> createMarawaccLambda(int nArgs, RootCallTarget callTarget, RFunction rFunction, String[] nameArgs, int nThreads) {
        ArrayFunction<T, R> function = (ArrayFunction<T, R>) uk.ac.ed.jpai.Marawacc.mapJavaThreads(nThreads, dataItem -> {
            Object[] argsPackage = ASTxUtils.createRArguments(nArgs, rFunction, dataItem, nameArgs);
            Object result = callTarget.call(argsPackage);
            return result;
        });
        return function;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static PArray<?> runMarawaccThreads(RAbstractVector input, RootCallTarget callTarget, RFunction rFunction, String[] nameArgs, int nThreads,
                    RAbstractVector[] additionalArgs, TypeInfoList infoList) {
        ArrayFunction composeLambda = createMarawaccLambda(infoList.size(), callTarget, rFunction, nameArgs, nThreads);
        PArray pArrayInput = ASTxUtils.marshal(input, additionalArgs, infoList);
        PArray<?> result = composeLambda.apply(pArrayInput);

        if (ASTxOptions.printResult) {
            System.out.println("result -- ");
            ASTxUtils.printPArray(result);
        }
        return result;
    }

    private static ArrayList<Object> runJavaSequential(RAbstractVector input, RootCallTarget target, RFunction function, int nArgs, RAbstractVector[] additionalArgs, String[] argsName,
                    Object firstValue) {
        ArrayList<Object> output = new ArrayList<>(input.getLength());
        output.add(firstValue);
        for (int i = 1; i < input.getLength(); i++) {
            Object[] argsPackage = ASTxUtils.createRArguments(nArgs, function, input, additionalArgs, argsName, i);
            Object val = target.call(argsPackage);
            output.add(val);
        }
        return output;
    }

    public static RAbstractVector computeMap(RAbstractVector input, RFunction function, RootCallTarget target, RAbstractVector[] additionalArgs, int nThreads) {

        int nArgs = ASTxUtils.getNumberOfArguments(function);
        String[] argsName = ASTxUtils.getArgumentsNames(function);
        Object[] argsPackage = ASTxUtils.createRArguments(nArgs, function, input, additionalArgs, argsName, 0);
        Object value = function.getTarget().call(argsPackage);

        TypeInfoList inputTypeList = null;
        try {
            inputTypeList = ASTxUtils.typeInference(input, additionalArgs);
        } catch (MarawaccTypeException e) {
            // XXX: DEOPTIMIZE: we can deoptimize to LApply
            System.out.println("[ASTx] Deoptimization generic LApply");
            e.printStackTrace();
        }

        TypeInfo outputType = null;
        try {
            outputType = ASTxUtils.typeInference(value);
        } catch (MarawaccTypeException e) {
            // deopt to LApply
            return MarawaccDeopt.deoptToLApply(input, function);
        }

        if (ASTxOptions.runMarawaccThreads) {
            // Marawacc multiple-thread
            PArray<?> result = runMarawaccThreads(input, target, function, argsName, nThreads, additionalArgs, inputTypeList);
            return ASTxUtils.unMarshallResultFromPArrays(outputType, result);
        } else {
            // Run in sequential
            ArrayList<Object> result = runJavaSequential(input, target, function, nArgs, additionalArgs, argsName, value);
            return ASTxUtils.unMarshallResultFromArrayList(outputType, result);
        }
    }

    /**
     * Call method with variable number of arguments.
     *
     * Built-in from R:
     *
     * <code>
     * marawacc.sapply(x, function, threads=1, ...)
     * </code>
     *
     * This is a blocking operation. Therefore the return Object will contain the result. It invokes
     * to the Marawacc-API for multiple-threads/GPU backend.
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
