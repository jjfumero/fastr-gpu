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

import uk.ac.ed.accelerator.truffle.ASTxOptions;
import uk.ac.ed.datastructures.common.PArray;
import uk.ac.ed.jpai.ArrayFunction;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.r.library.gpu.cache.MarawaccPackage;
import com.oracle.truffle.r.library.gpu.cache.RGPUCache;
import com.oracle.truffle.r.library.gpu.cache.RMarawaccFutures;
import com.oracle.truffle.r.library.gpu.cache.RMarawaccPromises;
import com.oracle.truffle.r.library.gpu.exceptions.MarawaccRuntimeDeoptException;
import com.oracle.truffle.r.library.gpu.exceptions.MarawaccTypeException;
import com.oracle.truffle.r.library.gpu.types.TypeInfo;
import com.oracle.truffle.r.library.gpu.types.TypeInfoList;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

/**
 * Parallel <code>map</code> implementation corresponding to <code>sapply</code> R Builin. This
 * implementation connects to Marawacc-API for the Java threads implementation and GPU. This is a
 * non blocking operation.
 *
 * The GPU supports relies on the Partial Evaluation step after Truffle decides to compile the AST
 * to binary code. If Graal is available, it will go through the Partial Evaluator when the code
 * becomes hot, otherwise, the code will be executed in the normal C2 compiler.
 *
 */
public final class MarawaccMapBuiltin extends RExternalBuiltinNode {

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
    private static <T, R> ArrayFunction<T, R> createMarawaccLambda(int nArgs, RootCallTarget callTarget, RFunction rFunction, String[] nameArgs, int nThreads,
                    ArrayFunction prev) {
        ArrayFunction<T, R> function = prev.mapJavaThreads(nThreads, dataItem -> {
            Object[] argsPackage = ASTxUtils.createRArguments(nArgs, rFunction, dataItem, nameArgs);
            Object result = callTarget.call(argsPackage);
            return result;
        });
        return function;
    }

    @SuppressWarnings("rawtypes")
    public static ArrayFunction composeExpression(RAbstractVector input, RFunction rFunction, RootCallTarget callTarget, RAbstractVector[] additionalArgs, int nThreads) {
        String[] argsName = ASTxUtils.getArgumentsNames(rFunction);
        TypeInfoList inputTypeList = null;
        try {
            inputTypeList = ASTxUtils.typeInference(input, additionalArgs);
        } catch (MarawaccTypeException e) {
            throw new MarawaccRuntimeDeoptException("Input types not supported");
        }
        ArrayFunction composeLambda = createMarawaccLambda(inputTypeList.size(), callTarget, rFunction, argsName, nThreads);
        PArray<?> pArrayInput = ASTxUtils.marshal(input, additionalArgs, inputTypeList);

        int nArgs = ASTxUtils.getNumberOfArguments(rFunction);
        Object[] argsPackage = ASTxUtils.createRArguments(nArgs, rFunction, input, additionalArgs, argsName, 0);
        Object value = callTarget.call(argsPackage);
        TypeInfo outputType = null;
        try {
            outputType = ASTxUtils.typeInference(value);
        } catch (MarawaccTypeException e) {
            throw new MarawaccRuntimeDeoptException("Input types not supported");
        }

        // Create package and annotate in the promises
        MarawaccPackage marawaccPackage = new MarawaccPackage(composeLambda);
        marawaccPackage.setpArray(pArrayInput);
        marawaccPackage.setTypeInfo(outputType);
        marawaccPackage.setOutput(value);

        if (ASTxOptions.useAsyncComputation) {
            RMarawaccFutures.INSTANCE.addFuture(marawaccPackage);
        } else {
            RMarawaccPromises.INSTANCE.addPromise(marawaccPackage);
        }

        return composeLambda;
    }

    @SuppressWarnings("rawtypes")
    public static ArrayFunction composeExpression(ArrayFunction marawaccFunction, RFunction rFunction, RootCallTarget callTarget, RAbstractVector[] additionalArgs, int nThreads) {

        int nArgs = ASTxUtils.getNumberOfArguments(rFunction);
        String[] argsName = ASTxUtils.getArgumentsNames(rFunction);
        ArrayFunction composeLambda = null;
        MarawaccPackage packageForArrayFunction = null;

        if (!ASTxOptions.useAsyncComputation) {
            composeLambda = createMarawaccLambda(nArgs, callTarget, rFunction, argsName, nThreads, marawaccFunction);
            packageForArrayFunction = RMarawaccPromises.INSTANCE.getPackageForArrayFunction(marawaccFunction);
        } else {
            composeLambda = createMarawaccLambda(nArgs, callTarget, rFunction, argsName, nThreads);
            packageForArrayFunction = RMarawaccFutures.INSTANCE.getPackageForArrayFunction(marawaccFunction);
        }

        Object output = packageForArrayFunction.getExecutionValue();
        Object[] argsPackage = ASTxUtils.createRArguments(nArgs, rFunction, output, additionalArgs, argsName, 0);
        Object value = callTarget.call(argsPackage);
        TypeInfo outputType = null;
        try {
            outputType = ASTxUtils.typeInference(value);
        } catch (MarawaccTypeException e) {
            throw new MarawaccRuntimeDeoptException("Input types not supported");
        }

        // Create package and annotate in the promises
        MarawaccPackage marawaccPackage = new MarawaccPackage(composeLambda);
        marawaccPackage.setTypeInfo(outputType);
        marawaccPackage.setOutput(value);

        if (ASTxOptions.useAsyncComputation) {
            RMarawaccFutures.INSTANCE.addFuture(marawaccPackage);
        } else {
            RMarawaccPromises.INSTANCE.addPromise(marawaccPackage);
        }
        return composeLambda;
    }

    /**
     * Call method with variable number of arguments.
     *
     * Built-in from R:
     *
     * <code>
     * marawacc.map(x, function, ...)
     * </code>
     *
     * It invokes to the Marawacc-API for multiple-threads/GPU backend.
     *
     * It returns an ArrayFunction.
     *
     */
    @Override
    public Object call(RArgsValuesAndNames args) {

        // The first argument could be either an ArrayFunction
        // or input data (RAbstractVector)
        Object firstArgument = args.getArgument(0);
        RAbstractVector input = null;
        ArrayFunction<?, ?> marawaccFunction = null;
        if (firstArgument instanceof RAbstractVector) {
            // It is the initial operation
            input = (RAbstractVector) firstArgument;
        } else if (firstArgument instanceof ArrayFunction) {
            marawaccFunction = (ArrayFunction<?, ?>) firstArgument;
        }
        RFunction rFunction = (RFunction) args.getArgument(1);

        // Get the callTarget from the cache
        RootCallTarget target = RGPUCache.INSTANCE.lookup(rFunction);
        int nThreads = ((Double) args.getArgument(2)).intValue();

        // Prepare all inputs in an array of Objects
        RAbstractVector[] additionalInputs = null;
        if (args.getLength() > 3) {
            additionalInputs = new RAbstractVector[args.getLength() - 3];
            for (int i = 0; i < additionalInputs.length; i++) {
                additionalInputs[i] = (RAbstractVector) args.getArgument(i + 3);
            }
        }

        if (input != null) {
            return composeExpression(input, rFunction, target, additionalInputs, nThreads);
        } else if (marawaccFunction != null) {
            return composeExpression(marawaccFunction, rFunction, target, additionalInputs, nThreads);
        } else {
            throw new RuntimeException("Data type not supported yet");
        }
    }
}
