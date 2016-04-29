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

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.util.ArrayList;

import uk.ac.ed.accelerator.common.GraalAcceleratorOptions;
import uk.ac.ed.datastructures.common.AcceleratorPArray;
import uk.ac.ed.datastructures.common.PArray;
import uk.ac.ed.datastructures.tuples.Tuple2;
import uk.ac.ed.jpai.graal.GraalGPUCompilationUnit;
import uk.ac.ed.jpai.graal.GraalGPUCompiler;
import uk.ac.ed.jpai.graal.GraalGPUExecutor;
import uk.ac.ed.marawacc.compilation.MarawaccGraalIR;

import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.truffle.OptimizedCallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.r.library.gpu.cache.RGPUCache;
import com.oracle.truffle.r.library.gpu.exceptions.MarawaccTypeException;
import com.oracle.truffle.r.library.gpu.types.TypeInfo;
import com.oracle.truffle.r.library.gpu.types.TypeInfoList;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public final class GPUTestNode extends RExternalBuiltinNode {

    private static boolean gpuExecution = false;

    @SuppressWarnings({"unchecked", "unused", "rawtypes"})
    private static ArrayList<Object> runJavaSequential(RAbstractVector input, RootCallTarget callTarget, RFunction function, int nArgs, RAbstractVector[] additionalArgs, String[] argsName,
                    Object firstValue, PArray<?> inputPArray) throws ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException,
                    SecurityException, MalformedURLException {
        ArrayList<Object> output = new ArrayList<>(input.getLength());
        output.add(firstValue);

        // Create a new root node
        // RootNode rootNode = function.getRootNode();
        // RootCallTarget newCallTarget = Truffle.getRuntime().createCallTarget(rootNode);

        callTarget.generateIDForGPU();

        for (int i = 1; i < input.getLength(); i++) {
            Object[] argsPackage = ASTxUtils.getArgsPackage(nArgs, function, input, additionalArgs, argsName, i);
            // Object val = newCallTarget.call(argsPackage);
            Object val = callTarget.call(argsPackage);
            output.add(val);

            StructuredGraph graphToCompile = MarawaccGraalIR.getInstance().getCompiledGraph(callTarget.getIDForGPU());

            if (graphToCompile != null) {
                System.out.println("[MARAWACC] >>>>>>>>>>>>!!!COMPILE TO GPU: " + graphToCompile);

                for (Node node : graphToCompile.getNodes()) {
                    System.out.println(node);
                }

                // Force OpenCL kernel visualisation
                GraalAcceleratorOptions.printOffloadKernel = true;

                // Compilation
                GraalGPUCompilationUnit compileGraphToGPU = GraalGPUCompiler.compileGraphToGPU(inputPArray, graphToCompile, false, (OptimizedCallTarget) callTarget);

                // Execution
                AcceleratorPArray copyToDevice = GraalGPUExecutor.copyToDevice(inputPArray, compileGraphToGPU.getInputType());
                AcceleratorPArray<Double> executeOnTheDevice = GraalGPUExecutor.<Tuple2<Double, Double>, Double> executeOnTheDevice(graphToCompile, copyToDevice, compileGraphToGPU.getOuputType());
                PArray result = GraalGPUExecutor.copyToHost(executeOnTheDevice, compileGraphToGPU.getOuputType());
                ArrayList<Object> arrayList = new ArrayList<Object>();
                arrayList.add(result);
                gpuExecution = true;
                return arrayList;
            }
        }

        return output;
    }

    private static RAbstractVector computeMap(RAbstractVector input, RFunction function, RootCallTarget target, RAbstractVector[] additionalArgs) throws ClassNotFoundException,
                    IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, MalformedURLException {

        int nArgs = ASTxUtils.getNumberOfArguments(function);
        String[] argsName = ASTxUtils.getArgumentsNames(function);
        Object[] argsPackage = ASTxUtils.getArgsPackage(nArgs, function, input, additionalArgs, argsName, 0);
        Object value = function.getTarget().call(argsPackage);
        TypeInfo outputType = null;
        try {
            outputType = ASTxUtils.typeInference(value);
        } catch (MarawaccTypeException e) {
            // DEPTIOMIZE
            e.printStackTrace();
        }

        TypeInfoList inputTypeList = null;
        try {
            inputTypeList = ASTxUtils.typeInference(input, additionalArgs);
        } catch (MarawaccTypeException e1) {
            e1.printStackTrace();
        }

        // Create PArrays
        PArray<?> inputPArrayFormat = ASTxUtils.marshall(input, additionalArgs, inputTypeList);

        ArrayList<Object> result = runJavaSequential(input, target, function, nArgs, additionalArgs, argsName, value, inputPArrayFormat);
        if (!gpuExecution) {
            return ASTxUtils.unMarshallResultFromList(outputType, result);
        } else {
            return ASTxUtils.unMarshallResultFromPArrays(outputType, (PArray) result.get(0));
        }
    }

    @Override
    public Object call(RArgsValuesAndNames args) {

        RAbstractVector input = (RAbstractVector) args.getArgument(0);
        RFunction function = (RFunction) args.getArgument(1);

        // Get the callTarget from the cache
        RootCallTarget target = RGPUCache.INSTANCE.lookup(function);

        // Prepare all inputs in an array of Objects
        RAbstractVector[] additionalInputs = null;
        if (args.getLength() > 3) {
            additionalInputs = new RAbstractVector[args.getLength() - 3];
            for (int i = 0; i < additionalInputs.length; i++) {
                additionalInputs[i] = (RAbstractVector) args.getArgument(i + 3);
            }
        }
        try {
            return computeMap(input, function, target, additionalInputs);
        } catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException | MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }
}
