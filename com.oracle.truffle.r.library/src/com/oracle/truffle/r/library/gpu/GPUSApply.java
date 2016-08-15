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
import uk.ac.ed.accelerator.profiler.Profiler;
import uk.ac.ed.accelerator.profiler.ProfilerType;
import uk.ac.ed.datastructures.common.AcceleratorPArray;
import uk.ac.ed.datastructures.common.PArray;
import uk.ac.ed.datastructures.interop.InteropTable;
import uk.ac.ed.datastructures.interop.Interoperable;
import uk.ac.ed.jpai.graal.GraalGPUCompilationUnit;
import uk.ac.ed.jpai.graal.GraalGPUCompiler;
import uk.ac.ed.jpai.graal.GraalGPUExecutor;
import uk.ac.ed.marawacc.compilation.MarawaccGraalIR;
import uk.ac.ed.marawacc.graal.CompilerUtils;

import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.truffle.OptimizedCallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.r.library.gpu.cache.CacheGPUExecutor;
import com.oracle.truffle.r.library.gpu.cache.InternalGraphCache;
import com.oracle.truffle.r.library.gpu.cache.RCacheObjects;
import com.oracle.truffle.r.library.gpu.cache.RFunctionMetadata;
import com.oracle.truffle.r.library.gpu.cache.RGPUCache;
import com.oracle.truffle.r.library.gpu.exceptions.MarawaccTypeException;
import com.oracle.truffle.r.library.gpu.nodes.utils.ASTLexicalScoping;
import com.oracle.truffle.r.library.gpu.nodes.utils.ASTxPrinter;
import com.oracle.truffle.r.library.gpu.options.ASTxOptions;
import com.oracle.truffle.r.library.gpu.phases.GPUBoxingEliminationPhase;
import com.oracle.truffle.r.library.gpu.phases.GPUCheckCastRemovalPhase;
import com.oracle.truffle.r.library.gpu.phases.GPUFixedGuardRemovalPhase;
import com.oracle.truffle.r.library.gpu.phases.GPUFrameStateEliminationPhase;
import com.oracle.truffle.r.library.gpu.phases.GPUInstanceOfRemovePhase;
import com.oracle.truffle.r.library.gpu.phases.ScopeArraysDetectionPhase;
import com.oracle.truffle.r.library.gpu.phases.ScopeCleanPhase;
import com.oracle.truffle.r.library.gpu.phases.ScopeDetectionPhase;
import com.oracle.truffle.r.library.gpu.types.TypeInfo;
import com.oracle.truffle.r.library.gpu.types.TypeInfoList;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.nodes.function.FunctionDefinitionNode;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

/**
 * AST Node to check the connection with Marawacc. This is just a proof of concept.
 */
public final class GPUSApply extends RExternalBuiltinNode {

    private boolean gpuExecution = false;
    private static int iteration = 0;

    private static class ScopeData {
        private Object[] scopeArray;

        public ScopeData(Object[] data) {
            this.scopeArray = data;
        }

        public Object[] getData() {
            return scopeArray;
        }

        public void setData(Object[] data) {
            this.scopeArray = data;
        }
    }

    ArrayList<com.oracle.graal.graph.Node> scopedNodes;

    private static ScopeData scopeArrayDetection(StructuredGraph graph) {
        ScopeDetectionPhase scopeDetection = new ScopeDetectionPhase();
        scopeDetection.apply(graph);
        ScopeData scopeData = new ScopeData(scopeDetection.getDataArray());
        return scopeData;
    }

    private void applyCompilationPhasesForGPU(StructuredGraph graph) {

        CompilerUtils.dumpGraph(graph, "beforeOptomisations");

        new GPUFrameStateEliminationPhase().apply(graph);
        CompilerUtils.dumpGraph(graph, "GPUFrameStateEliminationPhase");

        new GPUInstanceOfRemovePhase().apply(graph);
        CompilerUtils.dumpGraph(graph, "GPUInstanceOfRemovePhase");

        new GPUCheckCastRemovalPhase().apply(graph);
        CompilerUtils.dumpGraph(graph, "GPUCheckCastRemovalPhase");

        new GPUFixedGuardRemovalPhase().apply(graph);
        CompilerUtils.dumpGraph(graph, "GPUFixedGuardRemovalPhase");

        new GPUBoxingEliminationPhase().apply(graph);
        CompilerUtils.dumpGraph(graph, "GPUBoxingEliminationPhase");

        // we can try to analyze for R<T>Vector#data
        ScopeArraysDetectionPhase arraysDetectionPhase = new ScopeArraysDetectionPhase();
        arraysDetectionPhase.apply(graph);

        if (arraysDetectionPhase.isScopeDetected()) {
            scopedNodes = arraysDetectionPhase.getScopedNodes();
        }

        if (isCleanPhaseEnabled()) {
            // Just for testing when needed
            ScopeCleanPhase cleanPhase = new ScopeCleanPhase(scopedNodes);
            cleanPhase.apply(graph);
            CompilerUtils.dumpGraph(graph, "ScopeCleanPhase");
        }
    }

    private static boolean isCleanPhaseEnabled() {
        return false;
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
    private GraalGPUCompilationUnit compileForMarawaccBackend(PArray<?> inputPArray, OptimizedCallTarget callTarget, StructuredGraph graphToCompile, Object firstValue, Interoperable interoperable,
                    Object[] lexicalScope) {

        ScopeData scopeData = scopeArrayDetection(graphToCompile);

        if (lexicalScope != null) {
            scopeData.setData(lexicalScope);
        }

        applyCompilationPhasesForGPU(graphToCompile);

        if (ASTxOptions.debug) {
            // Force OpenCL kernel visualisation once generated
            GraalAcceleratorOptions.printOffloadKernel = true;
        }

        // Compilation to the GPU
        boolean ISTRUFFLE = true;
        GraalGPUCompilationUnit gpuCompilationUnit = GraalGPUCompiler.compileGraphToGPU(inputPArray, graphToCompile, callTarget, firstValue, ISTRUFFLE, interoperable, scopeData.getData(),
                        scopedNodes);
        gpuCompilationUnit.setScopeArrays(scopeData.getData());
        gpuCompilationUnit.setScopeNodes(scopedNodes);

        // Insert graph into cache
        InternalGraphCache.INSTANCE.installGPUBinaryIntoCache(graphToCompile, gpuCompilationUnit);

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
    private ArrayList<Object> runWithMarawaccAccelerator(PArray<?> inputPArray, StructuredGraph graph, GraalGPUCompilationUnit gpuCompilationUnit) {

        GraalGPUExecutor executor = CacheGPUExecutor.INSTANCE.getExecutor(gpuCompilationUnit);
        if (executor == null) {
            executor = new GraalGPUExecutor();
            CacheGPUExecutor.INSTANCE.insert(gpuCompilationUnit, executor);
        }

        long tcopyIN = System.nanoTime();
        AcceleratorPArray copyToDevice = executor.copyToDevice(inputPArray, gpuCompilationUnit.getInputType());
        long texecute = System.nanoTime();
        AcceleratorPArray executeOnTheDevice = executor.executeOnTheDevice(graph, copyToDevice, gpuCompilationUnit.getOuputType(), gpuCompilationUnit.getScopeArrays());
        long tcopyOUT = System.nanoTime();
        PArray result = executor.copyToHost(executeOnTheDevice, gpuCompilationUnit.getOuputType());
        long tend = System.nanoTime();
        gpuExecution = true;

        if (ASTxOptions.profiler) {
            Profiler.getInstance().writeInBuffer("copyIN total", (texecute - tcopyIN));
            Profiler.getInstance().writeInBuffer("execute total", (tcopyOUT - texecute));
            Profiler.getInstance().writeInBuffer("tOUT total", (tend - tcopyOUT));
            Profiler.getInstance().writeInBuffer("runWithMarawaccAccelerator total", (tend - tcopyIN));
        }

        ArrayList<Object> arrayList = new ArrayList<>();
        arrayList.add(result);
        return arrayList;
    }

    private static void printAST(RFunction function) {
        Node root = function.getTarget().getRootNode();
        ASTxPrinter printAST = new ASTxPrinter();
        RSyntaxNode.accept(root, 0, printAST);
    }

    private static String[] lexicalScopingAST(RFunction function) {
        ASTLexicalScoping lexicalScoping = new ASTLexicalScoping();
        lexicalScoping.apply(function);
        String[] scopeVars = lexicalScoping.scopeVars();
        return scopeVars;
    }

    // Run in the interpreter
    private ArrayList<Object> runJavaOpenCLJIT(RAbstractVector input, RootCallTarget callTarget, RFunction function, int nArgs, RAbstractVector[] additionalArgs, String[] argsName,
                    Object firstValue, PArray<?> inputPArray, Interoperable interoperable, Object[] lexicalScopes) {

        ArrayList<Object> output = new ArrayList<>(input.getLength());
        output.add(firstValue);

        if (RGPUCache.INSTANCE.getCachedObjects(function).getIDExecution() == 0) {
            callTarget.generateIDForGPU();
            // Set the GPU execution to true;
            ((FunctionDefinitionNode) function.getRootNode()).setGPUFlag(true);
            RGPUCache.INSTANCE.getCachedObjects(function).incID();
        }

        StructuredGraph graphToCompile = MarawaccGraalIR.getInstance().getCompiledGraph(callTarget.getIDForGPU());
        GraalGPUCompilationUnit gpuCompilationUnit = InternalGraphCache.INSTANCE.getGPUCompilationUnit(graphToCompile);

        if (graphToCompile != null && gpuCompilationUnit != null) {
            return runWithMarawaccAccelerator(inputPArray, graphToCompile, gpuCompilationUnit);
        }

        // Run in the AST interpreter
        for (int i = 1; i < input.getLength(); i++) {
            Object[] argsPackage = ASTxUtils.createRArguments(nArgs, function, input, additionalArgs, argsName, i);
            Object value = callTarget.call(argsPackage);
            output.add(value);

            /*
             * Check if the graph is prepared for GPU compilation and invoke the compilation + GPU
             * Execution. On Stack Replacement (OSR): switch to compiled GPU code
             */
            graphToCompile = MarawaccGraalIR.getInstance().getCompiledGraph(callTarget.getIDForGPU());
            if (graphToCompile != null && gpuCompilationUnit == null) {
                if (ASTxOptions.debug) {
                    System.out.println("[MARAWACC-ASTX] Compiling the Graph to GPU - Iteration: " + i);
                }
                gpuCompilationUnit = compileForMarawaccBackend(inputPArray, (OptimizedCallTarget) callTarget, graphToCompile, firstValue, interoperable, lexicalScopes);
                return runWithMarawaccAccelerator(inputPArray, graphToCompile, gpuCompilationUnit);
            }
        }

        return output;
    }

    private ArrayList<Object> runJavaOpenCLJIT(PArray<?> input, RootCallTarget callTarget, RFunction function, int nArgs, PArray<?>[] additionalArgs, String[] argsName,
                    Object firstValue, PArray<?> inputPArray, Interoperable interoperable, Object[] lexicalScopes, int totalSize) {

        long start = System.nanoTime();

        ArrayList<Object> output = new ArrayList<>();
        output.add(firstValue);

        if (RGPUCache.INSTANCE.getCachedObjects(function).getIDExecution() == 0) {
            callTarget.generateIDForGPU();
            // Set the GPU execution to true;
            ((FunctionDefinitionNode) function.getRootNode()).setGPUFlag(true);
            RGPUCache.INSTANCE.getCachedObjects(function).incID();
        }

        StructuredGraph graphToCompile = MarawaccGraalIR.getInstance().getCompiledGraph(callTarget.getIDForGPU());
        GraalGPUCompilationUnit gpuCompilationUnit = InternalGraphCache.INSTANCE.getGPUCompilationUnit(graphToCompile);

        if (graphToCompile != null && gpuCompilationUnit != null) {
            ArrayList<Object> runWithMarawaccAccelerator = runWithMarawaccAccelerator(inputPArray, graphToCompile, gpuCompilationUnit);
            long end = System.nanoTime();
            Profiler.getInstance().writeInBuffer("runJavaOpenCLJIT total", (end - start));
            return runWithMarawaccAccelerator;
        }

        // Interpreter mode
        for (int i = 1; i < totalSize; i++) {
            Object[] argsPackage = ASTxUtils.createRArguments(nArgs, function, input, additionalArgs, argsName, i);
            Object value = callTarget.call(argsPackage);
            output.add(value);

            /*
             * Check if the graph is prepared for GPU compilation and invoke the compilation and
             * execution. On Stack Replacement (OSR): switch to compiled GPU code
             */
            if (graphToCompile != null && gpuCompilationUnit == null) {
                if (ASTxOptions.debug) {
                    System.out.println("[MARAWACC-ASTX] Compiling the Graph to GPU - Iteration: " + i);
                }
                gpuCompilationUnit = compileForMarawaccBackend(inputPArray, (OptimizedCallTarget) callTarget, graphToCompile, firstValue, interoperable, lexicalScopes);
                return runWithMarawaccAccelerator(inputPArray, graphToCompile, gpuCompilationUnit);
            }
        }
        return output;
    }

    private static TypeInfo obtainTypeInfo(Object value) {
        TypeInfo outputType = null;
        try {
            outputType = ASTxUtils.typeInference(value);
        } catch (MarawaccTypeException e) {
            // TODO: DEOPTIMIZATION
            throw new RuntimeException("Interop data type not supported yet: " + value.getClass());
        }
        return outputType;
    }

    private static InteropTable obtainInterop(TypeInfo outputType) {
        InteropTable interop = null;
        if (outputType != null && outputType.getGenericType().equals(TypeInfo.TUPLE_GENERIC_TYPE.getGenericType())) {
            if (outputType == TypeInfo.TUPLE2) {
                interop = InteropTable.T2;
            } else if (outputType == TypeInfo.TUPLE3) {
                interop = InteropTable.T3;
            } else if (outputType == TypeInfo.TUPLE4) {
                interop = InteropTable.T4;
            } else if (outputType == TypeInfo.TUPLE5) {
                interop = InteropTable.T5;
            } else if (outputType == TypeInfo.TUPLE6) {
                interop = InteropTable.T6;
            } else {
                throw new RuntimeException("Interop data type not supported yet");
            }
        } else if (outputType == null) {
            // TODO: DEOPTIMIZATION
            throw new RuntimeException("Interop data type not supported yet");
        }
        return interop;
    }

    private static Class<?>[] createListSubTypes(InteropTable interop, Object value) {
        Class<?>[] typeObject = null;
        if (interop != null) {
            // Create sub-type list
            RList list = (RList) value;
            int ntuple = list.getLength();
            typeObject = new Class<?>[ntuple];
            for (int i = 0; i < ntuple; i++) {
                Class<?> k = list.getDataAt(i).getClass();
                typeObject[i] = k;
            }
        }
        return typeObject;
    }

    /**
     * If tuple contains the name="tuple".
     *
     * @param interop
     * @param value
     * @return {@link Class}
     */
    @SuppressWarnings("unused")
    private static Class<?>[] createListSubTypesWithName(InteropTable interop, Object value) {
        Class<?>[] typeObject = null;
        if (interop != null) {
            // Create sub-type list
            RList list = (RList) value;
            int ntuple = list.getLength();
            typeObject = new Class<?>[ntuple - 1];
            for (int i = 0; i < ntuple; i++) {
                Class<?> k = list.getDataAt(i).getClass();
                typeObject[i - 1] = k;
            }
        }
        return typeObject;
    }

    private static TypeInfoList createTypeInfoListForInput(RAbstractVector input, RAbstractVector[] additionalArgs) {
        TypeInfoList inputTypeList = null;
        try {
            inputTypeList = ASTxUtils.typeInference(input, additionalArgs);
        } catch (MarawaccTypeException e) {
            // TODO: DEOPTIMIZE
            e.printStackTrace();
        }
        return inputTypeList;
    }

    private static TypeInfoList createTypeInfoListForInputWithPArrays(RAbstractVector input, RAbstractVector[] additionalArgs) {
        TypeInfoList inputTypeList = null;
        try {
            inputTypeList = ASTxUtils.typeInferenceWithPArray(input, additionalArgs);
        } catch (MarawaccTypeException e) {
            // TODO: DEOPTIMIZE
            e.printStackTrace();
        }
        return inputTypeList;
    }

    private static TypeInfoList createTypeInfoListForInputWithPArrays(PArray<?> input, PArray<?>[] additionalArgs) {
        TypeInfoList inputTypeList = null;
        try {
            inputTypeList = ASTxUtils.typeInferenceWithPArray(input, additionalArgs);
        } catch (MarawaccTypeException e) {
            // TODO: DEOPTIMIZE
            e.printStackTrace();
        }
        return inputTypeList;
    }

    private static PArray<?> createPArrays(RAbstractVector input, RAbstractVector[] additionalArgs, TypeInfoList inputTypeList) {
        PArray<?> inputPArrayFormat = null;
        if (ASTxOptions.usePArrays) {
            if (ASTxOptions.optimizeRSequence) {
                // Optimise with RSequences data types (openCL logic to compute the data) and no
                // input copy.
                inputPArrayFormat = ASTxUtils.marshalWithReferencesAndSequenceOptimize(input, additionalArgs, inputTypeList);
            } else {
                // RTypes with PArray information
                inputPArrayFormat = ASTxUtils.marshalWithReferences(input, additionalArgs, inputTypeList);
            }
        } else {
            // real marshal
            inputPArrayFormat = ASTxUtils.marshal(input, additionalArgs, inputTypeList);
        }
        return inputPArrayFormat;
    }

    @SuppressWarnings("rawtypes")
    private static int getSize(PArray input, PArray[] additionalArgs) {
        int totalSize = input.size();
        if (input.isSequence()) {
            totalSize = input.getTotalSize();
        } else if (additionalArgs != null) {
            for (PArray<?> p : additionalArgs) {
                if (p.isSequence()) {
                    totalSize = p.getTotalSize();
                    break;
                }
            }
        }
        return totalSize;
    }

    @SuppressWarnings("rawtypes")
    private static PArray<?> createPArrays(PArray input, PArray[] additionalArgs, TypeInfoList inputTypeList) {
        int totalSize = getSize(input, additionalArgs);
        PArray<?> inputPArrayFormat = ASTxUtils.marshalWithReferences(input, additionalArgs, inputTypeList, totalSize);
        return inputPArrayFormat;
    }

    @SuppressWarnings({"rawtypes"})
    private RAbstractVector getResult(TypeInfo outputType, ArrayList<Object> result) {
        if (!gpuExecution) {
            // get the output in R-Type format
            return ASTxUtils.unMarshallResultFromArrayList(outputType, result);
        } else if (ASTxOptions.usePArrays) {
            // get the references
            return ASTxUtils.unMarshallFromFullPArrays(outputType, (PArray) result.get(0));
        } else {
            // Real un-marshal
            return ASTxUtils.unMarshallResultFromPArrays(outputType, (PArray) result.get(0));
        }
    }

    @SuppressWarnings({"rawtypes"})
    private RAbstractVector getResultFromPArray(TypeInfo outputType, ArrayList<Object> result) {
        if (!gpuExecution) {
            // get the output in R-Type format
            return ASTxUtils.unMarshallResultFromArrayList(outputType, result);
        } else {
            // get the references
            return ASTxUtils.unMarshallFromFullPArrays(outputType, (PArray) result.get(0));
        }
    }

    private static RFunctionMetadata getCachedFunctionMetadata(PArray<?> input, RFunction function, PArray<?>[] additionalArgs) {
        if (RGPUCache.INSTANCE.getCachedObjects(function).getRFunctionMetadata() == null) {
            // Type inference -> execution of the first element
            int nArgs = ASTxUtils.getNumberOfArguments(function);
            String[] argsName = ASTxUtils.getArgumentsNames(function);
            Object[] argsPackage = ASTxUtils.createRArguments(nArgs, function, input, additionalArgs, argsName, 0);
            Object value = function.getTarget().call(argsPackage);

            // Inter-operable objects
            TypeInfo outputType = obtainTypeInfo(value);
            InteropTable interop = obtainInterop(outputType);

            Class<?>[] typeObject = createListSubTypes(interop, value);
            Interoperable interoperable = (interop != null) ? new Interoperable(interop, typeObject) : null;

            RFunctionMetadata metadata = new RFunctionMetadata(nArgs, argsName, argsPackage, value, outputType, interop, typeObject, interoperable);
            RGPUCache.INSTANCE.getCachedObjects(function).insertRFuctionMetadata(metadata);
            return metadata;
        } else {
            return RGPUCache.INSTANCE.getCachedObjects(function).getRFunctionMetadata();
        }
    }

    private RAbstractVector computeOpenCLSApply(PArray<?> input, RFunction function, RootCallTarget target, PArray<?>[] additionalArgs, Object[] lexicalScopes) {

        RFunctionMetadata cachedFunctionMetadata = getCachedFunctionMetadata(input, function, additionalArgs);
        int nArgs = cachedFunctionMetadata.getnArgs();
        String[] argsName = cachedFunctionMetadata.getArgsName();
        Object value = cachedFunctionMetadata.getFirstValue();
        TypeInfo outputType = cachedFunctionMetadata.getOutputType();
        Interoperable interoperable = cachedFunctionMetadata.getInteroperable();

        int totalSize = getSize(input, additionalArgs);
        TypeInfoList inputTypeList = createTypeInfoListForInputWithPArrays(input, additionalArgs);

        // Marshal
        long startMarshal = System.nanoTime();
        PArray<?> inputPArrayFormat = createPArrays(input, additionalArgs, inputTypeList);
        long endMarshal = System.nanoTime();

        // Execution
        long startExecution = System.nanoTime();
        ArrayList<Object> result = runJavaOpenCLJIT(input, target, function, nArgs, additionalArgs, argsName, value, inputPArrayFormat, interoperable, lexicalScopes, totalSize);
        long endExecution = System.nanoTime();

        // Get the result (un-marshal)
        long startUnmarshal = System.nanoTime();
        RAbstractVector resultFastR = getResultFromPArray(outputType, result);
        long endUnmarshal = System.nanoTime();

        // Print profiler
        if (ASTxOptions.profiler) {
            // Marshal
            Profiler.getInstance().writeInBuffer(ProfilerType.AST_R_MARSHAL, "start", startMarshal);
            Profiler.getInstance().writeInBuffer(ProfilerType.AST_R_MARSHAL, "end", endMarshal);
            Profiler.getInstance().writeInBuffer(ProfilerType.AST_R_MARSHAL, "end-start", (endMarshal - startMarshal));

            // Execution
            Profiler.getInstance().writeInBuffer(ProfilerType.AST_R_EXECUTE, "start", startExecution);
            Profiler.getInstance().writeInBuffer(ProfilerType.AST_R_EXECUTE, "end", endExecution);
            Profiler.getInstance().writeInBuffer(ProfilerType.AST_R_EXECUTE, "end-start", (endExecution - startExecution));

            // Unmarshal
            Profiler.getInstance().writeInBuffer(ProfilerType.AST_R_UNMARSHAL, "start", startUnmarshal);
            Profiler.getInstance().writeInBuffer(ProfilerType.AST_R_UNMARSHAL, "end", endUnmarshal);
            Profiler.getInstance().writeInBuffer(ProfilerType.AST_R_UNMARSHAL, "end-start", (endUnmarshal - startUnmarshal));
        }
        return resultFastR;
    }

    private RAbstractVector computeOpenCLSApply(RAbstractVector input, RFunction function, RootCallTarget target, RAbstractVector[] additionalArgs, Object[] lexicalScopes) {

        // Type inference -> execution of the first element
        int nArgs = ASTxUtils.getNumberOfArguments(function);
        String[] argsName = ASTxUtils.getArgumentsNames(function);
        Object[] argsPackage = ASTxUtils.createRArguments(nArgs, function, input, additionalArgs, argsName, 0);
        Object value = function.getTarget().call(argsPackage);

        // Inter-operable objects
        TypeInfo outputType = obtainTypeInfo(value);
        InteropTable interop = obtainInterop(outputType);

        Class<?>[] typeObject = createListSubTypes(interop, value);
        Interoperable interoperable = (interop != null) ? new Interoperable(interop, typeObject) : null;

        TypeInfoList inputTypeList = null;
        if (ASTxOptions.usePArrays) {
            inputTypeList = createTypeInfoListForInputWithPArrays(input, additionalArgs);
        } else {
            inputTypeList = createTypeInfoListForInput(input, additionalArgs);
        }

        // Marshal
        long startMarshal = System.nanoTime();
        PArray<?> inputPArrayFormat = createPArrays(input, additionalArgs, inputTypeList);
        long endMarshal = System.nanoTime();

        // Execution
        long startExecution = System.nanoTime();
        ArrayList<Object> result = runJavaOpenCLJIT(input, target, function, nArgs, additionalArgs, argsName, value, inputPArrayFormat, interoperable, lexicalScopes);
        long endExecution = System.nanoTime();

        // Get the result (un-marshal)
        long startUnmarshal = System.nanoTime();
        RAbstractVector resultFastR = getResult(outputType, result);
        long endUnmarshal = System.nanoTime();

        // Print profiler
        if (ASTxOptions.profiler) {

            // Marshal
            Profiler.getInstance().writeInBuffer(ProfilerType.AST_R_MARSHAL, "start", startMarshal);
            Profiler.getInstance().writeInBuffer(ProfilerType.AST_R_MARSHAL, "end", endMarshal);
            Profiler.getInstance().writeInBuffer(ProfilerType.AST_R_MARSHAL, "end-start", (endMarshal - startMarshal));

            // Execution
            Profiler.getInstance().writeInBuffer(ProfilerType.AST_R_EXECUTE, "start", startExecution);
            Profiler.getInstance().writeInBuffer(ProfilerType.AST_R_EXECUTE, "end", endExecution);
            Profiler.getInstance().writeInBuffer(ProfilerType.AST_R_EXECUTE, "end-start", (endExecution - startExecution));

            // Unmarshal
            Profiler.getInstance().writeInBuffer(ProfilerType.AST_R_UNMARSHAL, "start", startUnmarshal);
            Profiler.getInstance().writeInBuffer(ProfilerType.AST_R_UNMARSHAL, "end", endUnmarshal);
            Profiler.getInstance().writeInBuffer(ProfilerType.AST_R_UNMARSHAL, "end-start", (endUnmarshal - startUnmarshal));
        }
        return resultFastR;
    }

    private static RAbstractVector[] getRArrayWithAdditionalArguments(RArgsValuesAndNames args) {
        RAbstractVector[] additionalInputs = null;
        if (args.getLength() > 2) {
            additionalInputs = new RAbstractVector[args.getLength() - 2];
            for (int i = 0; i < additionalInputs.length; i++) {
                additionalInputs[i] = (RAbstractVector) args.getArgument(i + 2);
            }
        }
        return additionalInputs;
    }

    private static PArray<?>[] getPArrayWithAdditionalArguments(RArgsValuesAndNames args) {
        PArray<?>[] additionalInputs = null;
        if (args.getLength() > 2) {
            additionalInputs = new PArray<?>[args.getLength() - 2];
            for (int i = 0; i < additionalInputs.length; i++) {
                additionalInputs[i] = (PArray<?>) args.getArgument(i + 2);
            }
        }
        return additionalInputs;
    }

    @Override
    public Object call(RArgsValuesAndNames args) {

        Profiler.getInstance().print("\nIteration: " + iteration++);

        if (ASTxOptions.usePArrays) {
            RVector.WITH_PARRAYS = true;
        }

        long start = System.nanoTime();
        Object firstParam = args.getArgument(0);
        RFunction function = (RFunction) args.getArgument(1);

        RAbstractVector input = null;
        PArray<?> parrayInput = null;
        boolean parrayFormat = false;

        if (firstParam instanceof RAbstractVector) {
            input = (RAbstractVector) firstParam;
        } else if (firstParam instanceof PArray) {
            parrayFormat = true;
            parrayInput = (PArray<?>) firstParam;
        } else {
            throw new RuntimeException("Vector expected");
        }

        if (ASTxOptions.printAST) {
            printAST(function);
        }

        RootCallTarget target = null;
        Object[] lexicalScopes = null;

        // Get the callTarget from the cache
        if (!RGPUCache.INSTANCE.contains(function)) {
            // Lexical scoping from the AST level
            String[] scopeVars = lexicalScopingAST(function);
            lexicalScopes = ASTxUtils.getValueOfScopeArrays(scopeVars, function);
            RCacheObjects cachedObjects = new RCacheObjects(function.getTarget(), scopeVars, lexicalScopes);
            target = RGPUCache.INSTANCE.updateCacheObjects(function, cachedObjects);
        } else {
            target = RGPUCache.INSTANCE.getCallTarget(function);
            lexicalScopes = RGPUCache.INSTANCE.getCachedObjects(function).getLexicalScopeVars();
        }

        long gpuCalling = System.nanoTime();

        RAbstractVector mapResult = null;
        // Prepare all inputs in an array of Objects
        if (!parrayFormat) {
            RAbstractVector[] additionalInputs = getRArrayWithAdditionalArguments(args);
            mapResult = computeOpenCLSApply(input, function, target, additionalInputs, lexicalScopes);
        } else {
            PArray<?>[] additionalInputs = getPArrayWithAdditionalArguments(args);
            mapResult = computeOpenCLSApply(parrayInput, function, target, additionalInputs, lexicalScopes);
        }

        long end = System.nanoTime();

        if (ASTxOptions.profiler) {
            Profiler.getInstance().writeInBuffer("gpu calling", (gpuCalling - start));
            Profiler.getInstance().writeInBuffer("gpu start-end", (end - start));
            Profiler.getInstance().writeInBuffer("gpu start", start);
            Profiler.getInstance().writeInBuffer("gpu end", end);
        }

        return mapResult;
    }
}
