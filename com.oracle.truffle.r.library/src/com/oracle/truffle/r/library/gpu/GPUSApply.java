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

import uk.ac.ed.accelerator.common.GraalAcceleratorOptions;
import uk.ac.ed.datastructures.common.AcceleratorPArray;
import uk.ac.ed.datastructures.common.PArray;
import uk.ac.ed.jpai.graal.GraalGPUCompilationUnit;
import uk.ac.ed.jpai.graal.GraalGPUCompiler;
import uk.ac.ed.jpai.graal.GraalGPUExecutor;
import uk.ac.ed.marawacc.compilation.MarawaccGraalIR;
import uk.ac.ed.marawacc.graal.CompilationUtils;

import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.truffle.OptimizedCallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.r.library.gpu.cache.InternalGraphCache;
import com.oracle.truffle.r.library.gpu.cache.RGPUCache;
import com.oracle.truffle.r.library.gpu.exceptions.MarawaccTypeException;
import com.oracle.truffle.r.library.gpu.options.ASTxOptions;
import com.oracle.truffle.r.library.gpu.phases.GPUBoxingEliminationPhase;
import com.oracle.truffle.r.library.gpu.phases.GPUFrameStateEliminationPhase;
import com.oracle.truffle.r.library.gpu.types.TypeInfo;
import com.oracle.truffle.r.library.gpu.types.TypeInfoList;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.nodes.function.FunctionDefinitionNode;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

/**
 * AST Node to check the connection with Marawacc. This is just a proof of concept.
 *
 */
public final class GPUSApply extends RExternalBuiltinNode {

    private boolean gpuExecution = false;

    private static void applyCompilationPhasesForGPU(StructuredGraph graph) {
        // new GPUCleanPhase().apply(graphToCompile);
        new GPUFrameStateEliminationPhase().apply(graph);
        // new GPURemoveInterpreterPhase().apply(graphToCompile);

        CompilationUtils.dumpGraph(graph, "afterGPUFrameState");

        // new GPUFixedGuardNodeRemovePhase().apply(graphToCompile);
        // GraalIRConversion.dumpGraph(graphToCompile, "GPUFixedGuardNodeRemovePhase");

        new GPUBoxingEliminationPhase().apply(graph);
        CompilationUtils.dumpGraph(graph, "GPUBoxingEliminationPhase");

    }

    /**
     * Given the {@link StructuredGraph}, this method invokes the OpenCL code generation. We also
     * need some meta-data to generate the code such as the input array (in {@link PArray} form, and
     * the first value (runtime object) to generate the output for the kernel template.
     *
     * @param inputPArray
     * @param callTarget
     * @param graphToCompile
     * @param firstValue
     * @return {@link GraalGPUCompilationUnit}
     */
    private GraalGPUCompilationUnit compileForMarawaccBackend(PArray<?> inputPArray, OptimizedCallTarget callTarget, StructuredGraph graphToCompile, Object firstValue) {

        applyCompilationPhasesForGPU(graphToCompile);

        if (ASTxOptions.debug) {
            // Force OpenCL kernel visualisation
            GraalAcceleratorOptions.printOffloadKernel = true;
        }

        // Compilation to the GPU
        boolean isTruffle = true;
        GraalGPUCompilationUnit gpuCompilationUnit = GraalGPUCompiler.compileGraphToGPU(inputPArray, graphToCompile, callTarget, firstValue, isTruffle);

        // Insert graph into cache
        InternalGraphCache.INSTANCE.installGPUBinary(graphToCompile, gpuCompilationUnit);

        gpuExecution = true;
        return gpuCompilationUnit;
    }

    /**
     * Given the {@link GraalGPUCompilationUnit}, this method executes the OpenCL code. It copies
     * the data to the device, runs the kernel and copies back the result.
     *
     * It returns an array list with one element, the result in Object format (PArray).
     *
     * @param inputPArray
     * @param graph
     * @param gpuCompilationUnit
     * @return {@link ArrayList}
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArrayList<Object> runWithMarawaccAccelerator(PArray<?> inputPArray, StructuredGraph graph, GraalGPUCompilationUnit gpuCompilationUnit) {
        AcceleratorPArray copyToDevice = GraalGPUExecutor.copyToDevice(inputPArray, gpuCompilationUnit.getInputType());
        AcceleratorPArray executeOnTheDevice = GraalGPUExecutor.executeOnTheDevice(graph, copyToDevice, gpuCompilationUnit.getOuputType());
        PArray result = GraalGPUExecutor.copyToHost(executeOnTheDevice, gpuCompilationUnit.getOuputType());
        ArrayList<Object> arrayList = new ArrayList<>();
        arrayList.add(result);
        return arrayList;
    }

    private ArrayList<Object> runJavaSequential(RAbstractVector input, RootCallTarget callTarget, RFunction function, int nArgs, RAbstractVector[] additionalArgs, String[] argsName,
                    Object firstValue, PArray<?> inputPArray) {

        ArrayList<Object> output = new ArrayList<>(input.getLength());
        output.add(firstValue);

        // Create a new root node
        // RootNode rootNode = function.getRootNode();
        // RootCallTarget newCallTarget = Truffle.getRuntime().createCallTarget(rootNode);

        callTarget.generateIDForGPU();
        // Set the GPU execution to true;
        ((FunctionDefinitionNode) function.getRootNode()).setGPUFlag(true);

        StructuredGraph graphToCompile = MarawaccGraalIR.getInstance().getCompiledGraph(callTarget.getIDForGPU());
        GraalGPUCompilationUnit gpuCompilationUnit = InternalGraphCache.INSTANCE.getGPUCompilationUnit(graphToCompile);

        if (graphToCompile != null && gpuCompilationUnit != null) {
            // Get the compiled code from the cache
            if (ASTxOptions.debugCache) {
                System.out.println("[MARAWACC-ASTX] Getting the GPU binary from the cache");
            }
            return runWithMarawaccAccelerator(inputPArray, graphToCompile, gpuCompilationUnit);
        }

        for (int i = 1; i < input.getLength(); i++) {
            Object[] argsPackage = ASTxUtils.createRArguments(nArgs, function, input, additionalArgs, argsName, i);
            // Object val = newCallTarget.call(argsPackage);
            Object val = callTarget.call(argsPackage);
            output.add(val);

            // int counter = MarawaccGraalIR.getInstance().getCounter();

            /*
             * Check if the graph is prepared for GPU compilation and invoke the compilation.
             */
            if (graphToCompile != null && gpuCompilationUnit == null) {
                // Get the Structured Graph and compile it for GPU
                gpuCompilationUnit = compileForMarawaccBackend(inputPArray, (OptimizedCallTarget) callTarget, graphToCompile, firstValue);
                return runWithMarawaccAccelerator(inputPArray, graphToCompile, gpuCompilationUnit);
            }
        }

        return output;
    }

    @SuppressWarnings("rawtypes")
    private RAbstractVector computeMap(RAbstractVector input, RFunction function, RootCallTarget target, RAbstractVector[] additionalArgs) {

        int nArgs = ASTxUtils.getNumberOfArguments(function);
        String[] argsName = ASTxUtils.getArgumentsNames(function);
        Object[] argsPackage = ASTxUtils.createRArguments(nArgs, function, input, additionalArgs, argsName, 0);
        Object value = function.getTarget().call(argsPackage);
        TypeInfo outputType = null;
        try {
            outputType = ASTxUtils.typeInference(value);
        } catch (MarawaccTypeException e) {
            // TODO: Deoptimize
            e.printStackTrace();
        }

        TypeInfoList inputTypeList = null;
        try {
            inputTypeList = ASTxUtils.typeInference(input, additionalArgs);
        } catch (MarawaccTypeException e) {
            // TODO: Deoptimize
            e.printStackTrace();
        }

        // Create PArrays
        PArray<?> inputPArrayFormat = ASTxUtils.marshal(input, additionalArgs, inputTypeList);

        ArrayList<Object> result = runJavaSequential(input, target, function, nArgs, additionalArgs, argsName, value, inputPArrayFormat);
        if (!gpuExecution) {
            return ASTxUtils.unMarshallResultFromArrayList(outputType, result);
        } else {
            return ASTxUtils.unMarshallResultFromPArrays(outputType, (PArray) result.get(0));
        }
    }

    @Override
    public Object call(RArgsValuesAndNames args) {

        RAbstractVector input = (RAbstractVector) args.getArgument(0);
        RFunction rFunction = (RFunction) args.getArgument(1);

        // Get the callTarget from the cache
        RootCallTarget target = RGPUCache.INSTANCE.lookup(rFunction);

        // Prepare all inputs in an array of Objects
        RAbstractVector[] additionalInputs = null;
        if (args.getLength() > 2) {
            additionalInputs = new RAbstractVector[args.getLength() - 2];
            for (int i = 0; i < additionalInputs.length; i++) {
                additionalInputs[i] = (RAbstractVector) args.getArgument(i + 2);
            }
        }

        return computeMap(input, rFunction, target, additionalInputs);
    }
}
