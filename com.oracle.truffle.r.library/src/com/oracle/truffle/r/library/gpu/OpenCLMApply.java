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

import uk.ac.ed.accelerator.profiler.Profiler;
import uk.ac.ed.accelerator.profiler.ProfilerType;
import uk.ac.ed.datastructures.common.AcceleratorPArray;
import uk.ac.ed.datastructures.common.PArray;
import uk.ac.ed.datastructures.interop.InteropTable;
import uk.ac.ed.datastructures.interop.Interoperable;
import uk.ac.ed.jpai.graal.GraalOpenCLCompilationUnit;
import uk.ac.ed.jpai.graal.GraalOpenCLExecutor;
import uk.ac.ed.jpai.graal.GraalOpenCLJITCompiler;
import uk.ac.ed.marawacc.compilation.MarawaccGraalIRCache;
import uk.ac.ed.marawacc.graal.CompilerUtils;

import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.truffle.OptimizedCallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.r.library.gpu.cache.CacheGPUExecutor;
import com.oracle.truffle.r.library.gpu.cache.CacheInputBuffers;
import com.oracle.truffle.r.library.gpu.cache.InternalGraphCache;
import com.oracle.truffle.r.library.gpu.cache.LookupFunctionToData;
import com.oracle.truffle.r.library.gpu.cache.RCacheObjects;
import com.oracle.truffle.r.library.gpu.cache.RFunctionMetadata;
import com.oracle.truffle.r.library.gpu.cache.RGPUCache;
import com.oracle.truffle.r.library.gpu.exceptions.AcceleratorExecutionException;
import com.oracle.truffle.r.library.gpu.options.ASTxOptions;
import com.oracle.truffle.r.library.gpu.phases.FilterInterpreterNodes;
import com.oracle.truffle.r.library.gpu.phases.scope.ScopeData;
import com.oracle.truffle.r.library.gpu.types.TypeInfo;
import com.oracle.truffle.r.library.gpu.types.TypeInfoList;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils.ScopeVarInfo;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.nodes.function.FunctionDefinitionNode;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.context.Engine.ParseException;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

/**
 * MApply parallel skeleton for OpenCL. It executes the normal R mapply function but it has the
 * logic for JIT compilation for OpenCL and execution.
 */
public final class OpenCLMApply extends RExternalBuiltinNode {

    private static final String R_EVAL_DESCRIPTION = "<eval>";
    private static final boolean TRUFFLE_ENABLED = true;

    private int compileIndex = 1;

    // For Batch processing
    private ArrayList<Integer> typeSizes = new ArrayList<>();
    private int scopeTotalBytes;
    private boolean wasBatch = false;
    private int totalSizeWhenBatch = 0;

    private static LookupFunctionToData lookupFunction = new LookupFunctionToData();

    // For debug
    private static int iteration = 0;

    /**
     * Given the {@link StructuredGraph}, this method invokes the OpenCL code generation. We also
     * need some meta-data to generate the code such as the input array (in {@link PArray} form, and
     * the first value (runtime object) to generate the output for the kernel template.
     *
     * @param inputPArray
     * @param callTarget
     * @param graphToCompile
     * @param firstValue
     * @return {@link GraalOpenCLCompilationUnit}
     */
    private GraalOpenCLCompilationUnit compileForMarawaccBackend(PArray<?> inputPArray, OptimizedCallTarget callTarget, StructuredGraph graphToCompile, Object firstValue,
                    Interoperable interoperable,
                    Object[] lexicalScope, int nArgs) {

        ScopeData scopeData = ASTxUtils.scopeArrayConstantDetection(graphToCompile);

        if (lexicalScope != null) {
            scopeData.setData(lexicalScope);
        }

        ArrayList<com.oracle.graal.graph.Node> scopedNodes;
        if (ASTxOptions.debug) {
            scopedNodes = ASTxUtils.applyCompilationPhasesForOpenCLAndDump(graphToCompile);
        } else {
            scopedNodes = ASTxUtils.applyCompilationPhasesForOpenCL(graphToCompile);
        }

        int numScopeBytes = 0;
        if (scopedNodes != null) {
            for (int i = 0; i < lexicalScope.length; i++) {
                if (lexicalScope[i] instanceof double[]) {
                    numScopeBytes += 8 * ((double[]) lexicalScope[i]).length;
                } else {
                    System.err.println("Data type not suppported yet.");
                }
            }
        }
        scopeTotalBytes = numScopeBytes;

        new FilterInterpreterNodes(6).apply(graphToCompile);

        if (ASTxOptions.debug) {
            CompilerUtils.dumpGraph(graphToCompile, "GraphToTheOpenCLBackend");
        }

        GraalOpenCLCompilationUnit gpuCompilationUnit = GraalOpenCLJITCompiler.compileGraphToOpenCL(inputPArray, graphToCompile, callTarget, firstValue, TRUFFLE_ENABLED, interoperable, scopeData.getData(),
                        scopedNodes, nArgs);
        InternalGraphCache.INSTANCE.installGPUBinaryIntoCache(graphToCompile, gpuCompilationUnit);
        return gpuCompilationUnit;
    }

    private static void profiling(long startCopy, long endCopy, long startExecution, long endExecution, long startDeviceToHost, long endDeviceToHost) {
        // Marshal
        Profiler.getInstance().writeInBuffer(ProfilerType.COPY_TO_DEVICE, "end-start", (endCopy - startCopy));

        // Execution
        Profiler.getInstance().writeInBuffer(ProfilerType.COMPUTE_MAP, "end-start", (endExecution - startExecution));

        // Unmarshal
        Profiler.getInstance().writeInBuffer(ProfilerType.COPY_TO_HOST, "end-start", (endDeviceToHost - startDeviceToHost));
    }

    private long computeTotalBytes(int elements) {
        // Input - output
        int totalBytes = typeSizes.stream()
                        .map(i -> i * elements)
                        .reduce(0, (x, y) -> x + y);
        totalBytes += scopeTotalBytes;
        return totalBytes;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArrayList<Object> run(PArray<?> inputPArray, StructuredGraph graph, GraalOpenCLCompilationUnit gpuCompilationUnit, RFunction function, boolean newAllocation)
                    throws AcceleratorExecutionException {
        GraalOpenCLExecutor executor = CacheGPUExecutor.INSTANCE.getExecutor(gpuCompilationUnit);
        if (executor == null) {
            executor = new GraalOpenCLExecutor();
            CacheGPUExecutor.INSTANCE.insert(gpuCompilationUnit, executor);
        }

        executor.setNewAllocation(newAllocation);
        long s1 = System.nanoTime();
        AcceleratorPArray copyToDevice = executor.copyToDevice(inputPArray, gpuCompilationUnit.getInputType());
        long s2 = System.nanoTime();
        AcceleratorPArray executeOnTheDevice = executor.executeOnTheDevice(graph, copyToDevice, gpuCompilationUnit.getOuputType(), gpuCompilationUnit.getScopeArrays());
        long s3 = System.nanoTime();
        PArray result = executor.copyToHost(executeOnTheDevice, gpuCompilationUnit.getOuputType());
        long s4 = System.nanoTime();
        profiling(s1, s2, s2, s3, s3, s4);
        PArray<Integer> deopt = executor.getDeoptBuffer();
        if (deopt != null) {
            if (deopt.get(0) != 0) {
                Profiler.getInstance().writeInBuffer(ProfilerType.GENERAL_LOG_MESSAGE, "Deoptimization in thread:", deopt.get(0));
                throw new AcceleratorExecutionException("Deoptimization in thread: ", deopt.get(0));
            }
        }
        RGPUCache.INSTANCE.getCachedObjects(function).enableGPUExecution();
        ArrayList<Object> arrayList = new ArrayList<>();
        arrayList.add(result);
        return arrayList;
    }

    /**
     * @param newAllocation
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArrayList<Object> runBatch(PArray<?> inputPArray, StructuredGraph graph, GraalOpenCLCompilationUnit gpuCompilationUnit, RFunction function, boolean newAllocation, int iterations,
                    int size)
                    throws AcceleratorExecutionException {
        GraalOpenCLExecutor executor = CacheGPUExecutor.INSTANCE.getExecutor(gpuCompilationUnit);
        if (executor == null) {
            executor = new GraalOpenCLExecutor();
            CacheGPUExecutor.INSTANCE.insert(gpuCompilationUnit, executor);
        }

        // / XXX: IDEA, an executor per iterator (number of chunks)
        ArrayList<Object> arrayList = new ArrayList<>();
        PArray result = null;
        int base = (inputPArray.size() / iterations);
        for (int i = 0; i < iterations; i++) {
            int offset = base * i;
            executor.setNewAllocation(true);
            long s1 = System.nanoTime();
            AcceleratorPArray copyToDevice = executor.copyToDevice(inputPArray, gpuCompilationUnit.getInputType(), size, offset);
            long s2 = System.nanoTime();
            AcceleratorPArray executeOnTheDevice = executor.executeOnTheDevice(graph, copyToDevice, gpuCompilationUnit.getOuputType(), gpuCompilationUnit.getScopeArrays());
            long s3 = System.nanoTime();
            result = executor.copyToHost(executeOnTheDevice, gpuCompilationUnit.getOuputType());
            long s4 = System.nanoTime();
            profiling(s1, s2, s2, s3, s3, s4);
            PArray<Integer> deopt = executor.getDeoptBuffer();
            if (deopt != null) {
                if (deopt.get(0) != 0) {
                    Profiler.getInstance().writeInBuffer(ProfilerType.GENERAL_LOG_MESSAGE, "Deoptimization in thread:", deopt.get(0));
                    throw new AcceleratorExecutionException("Deoptimization in thread: ", deopt.get(0));
                }
            }
            arrayList.add(result);
        }
        RGPUCache.INSTANCE.getCachedObjects(function).enableGPUExecution();
        wasBatch = true;
        totalSizeWhenBatch = inputPArray.size();
        return arrayList;
    }

    /**
     * Given the {@link GraalOpenCLCompilationUnit}, this method executes the OpenCL code. It copies
     * the data to the device, runs the kernel and copies back the result.
     *
     * It returns an array list with one element, the result in Object format (PArray).
     *
     * @param inputPArray
     * @param graph
     * @param gpuCompilationUnit
     * @return {@link ArrayList}
     * @throws AcceleratorExecutionException
     */
    private ArrayList<Object> runWithMarawaccAccelerator(PArray<?> inputPArray, StructuredGraph graph, GraalOpenCLCompilationUnit gpuCompilationUnit, RFunction function, boolean newAllocation)
                    throws AcceleratorExecutionException {
        GraalOpenCLExecutor executor = CacheGPUExecutor.INSTANCE.getExecutor(gpuCompilationUnit);
        if (executor == null) {
            executor = new GraalOpenCLExecutor();
            CacheGPUExecutor.INSTANCE.insert(gpuCompilationUnit, executor);
        }

        long globalMaxGPUMemory = executor.getGlobalMaxGPUMemory();
        int iterations = 1;

        int elements = inputPArray.size();
        long totalBytes = computeTotalBytes(elements);

        // globalMaxGPUMemory = 30000;
        if (totalBytes > globalMaxGPUMemory) {
            // Set Appropriate size
            System.out.println("Data does not fit in Memory");
            while (true) {
                elements /= 2;
                totalBytes = computeTotalBytes(elements);
                iterations++;
                if (totalBytes < globalMaxGPUMemory) {
                    break;
                }
            }
        }

        if (iterations == 1) {
            return run(inputPArray, graph, gpuCompilationUnit, function, newAllocation);
        } else {
            return runBatch(inputPArray, graph, gpuCompilationUnit, function, newAllocation, iterations, elements);
        }
    }

    private static ArrayList<Object> addOutputElement(Object firstValue) {
        ArrayList<Object> output = new ArrayList<>();
        output.add(firstValue);
        return output;
    }

    private static void checkIfRFunctionIsInCache(RFunction function, RootCallTarget callTarget) {
        if (RGPUCache.INSTANCE.getCachedObjects(function).getIDExecution() == 0) {
            callTarget.generateIDForOpenCL();
            // Set the GPU execution to true;
            ((FunctionDefinitionNode) function.getRootNode()).setOpenCLFlag(true);
            RGPUCache.INSTANCE.getCachedObjects(function).incID();
        }
    }

    private static class JITMetaInput {
        private Object firstValue;
        private Interoperable interoperable;
        private Object[] lexicalScopes;
        private PArray<?> inputPArray;

        public JITMetaInput(Object firstValue, Interoperable interoperable, Object[] lexicalScopes, PArray<?> inputPArray) {
            this.firstValue = firstValue;
            this.interoperable = interoperable;
            this.lexicalScopes = lexicalScopes;
            this.inputPArray = inputPArray;
        }
    }

    /*
     * Check if the graph is prepared for GPU compilation and invoke the compilation and execution.
     * On Stack Replacement (OSR): switch to compiled GPU code
     */
    private ArrayList<Object> checkAndRunWithOpenCL(GraalOpenCLCompilationUnit gpuCompilationUnit, RootCallTarget callTarget, int index, JITMetaInput meta, RFunction function, int inputArgs)
                    throws AcceleratorExecutionException {
        StructuredGraph graphToCompile = MarawaccGraalIRCache.getInstance().getCompiledGraph(callTarget.getIDForOpenCL());
        if ((graphToCompile != null) && (gpuCompilationUnit == null)) {
            if (ASTxOptions.debug) {
                System.out.println("[MARAWACC-ASTX] Compiling the Graph to GPU - Iteration: " + index);

            }
            Profiler.getInstance().writeInBuffer(ProfilerType.GENERAL_LOG_MESSAGE, "Compiling the Graph to GPU - Iteration:", index);
            compileIndex = index;
            // For debugging
            if (ASTxOptions.printASTforRFunction) {
                ASTxUtils.printAST(function);
            }

            Profiler.getInstance().writeInBuffer(ProfilerType.DEOPTTRACE, "OpenCL_Compilation_start", System.nanoTime());
            GraalOpenCLCompilationUnit openCLCompileUnit = compileForMarawaccBackend(meta.inputPArray, (OptimizedCallTarget) callTarget, graphToCompile, meta.firstValue, meta.interoperable,
                            meta.lexicalScopes, inputArgs);
            Profiler.getInstance().writeInBuffer(ProfilerType.DEOPTTRACE, "OpenCL_Exec_start", System.nanoTime());
            ArrayList<Object> runWithMarawaccAccelerator = runWithMarawaccAccelerator(meta.inputPArray, graphToCompile, openCLCompileUnit, function, false);
            Profiler.getInstance().writeInBuffer(ProfilerType.DEOPTTRACE, "OpenCL_Exec_end", System.nanoTime());
            return runWithMarawaccAccelerator;
        }
        return null;
    }

    /**
     * It tells if the buffers in the GPUExecution unit has to be re-allocated or not. This is
     * mainly because the input function keeps stable and the input data is changed.
     *
     * @param input
     * @param additionalArgs
     * @param function
     * @return boolean
     */
    private static boolean newAllocationBuffer(RAbstractVector input, RAbstractVector[] additionalArgs, RFunction function) {
        int len = (additionalArgs == null) ? 1 : additionalArgs.length + 1;
        RAbstractVector[] v = new RAbstractVector[len];
        v[0] = input;
        // compose the input
        if (additionalArgs != null) {
            for (int i = 0; i < additionalArgs.length; i++) {
                v[i + 1] = additionalArgs[0];
            }
        }
        if (CacheInputBuffers.getInstance().constainsRVector(function)) {
            if (!CacheInputBuffers.getInstance().check(function, v)) {
                CacheInputBuffers.getInstance().add(function, v);
                return true;
            }
        } else {
            CacheInputBuffers.getInstance().add(function, v);
        }
        return false;
    }

    /**
     * It tells if the buffers in the GPUExecution unit has to be re-allocated or not. This is
     * mainly because the input function keeps stable and the input data is changed.
     *
     * @param input
     * @param additionalArgs
     * @param function
     * @return boolean
     */
    private static boolean newAllocationBuffer(PArray<?> input, PArray<?>[] additionalArgs, RFunction function) {
        int len = (additionalArgs == null) ? 1 : additionalArgs.length + 1;
        PArray<?>[] v = new PArray<?>[len];
        v[0] = input;
        // compose the input
        if (additionalArgs != null) {
            for (int i = 0; i < additionalArgs.length; i++) {
                v[i + 1] = additionalArgs[0];
            }
        }
        if (CacheInputBuffers.getInstance().constainsRVector(function)) {
            if (!CacheInputBuffers.getInstance().check(function, v)) {
                CacheInputBuffers.getInstance().add(function, v);
                return true;
            }
        } else {
            CacheInputBuffers.getInstance().add(function, v);
        }
        return false;
    }

    /**
     * Run in the interpreter and then JIT when the CFG is prepared for compilation.
     */
    private ArrayList<Object> runJavaOpenCLJIT(RAbstractVector input, RootCallTarget callTarget, RFunction function, int nArgs, RAbstractVector[] additionalArgs, String[] argsName,
                    Object firstValue, PArray<?> inputPArray, Interoperable interoperable, Object[] lexicalScopes, int argsOriginal) throws AcceleratorExecutionException {

        Profiler.getInstance().writeInBuffer(ProfilerType.DEOPTTRACE, "AST_INTERPRETER", System.nanoTime());

        checkIfRFunctionIsInCache(function, callTarget);
        StructuredGraph graphToCompile = MarawaccGraalIRCache.getInstance().getCompiledGraph(callTarget.getIDForOpenCL());
        GraalOpenCLCompilationUnit gpuCompilationUnit = InternalGraphCache.INSTANCE.getGPUCompilationUnit(graphToCompile);

        boolean newAllocation = newAllocationBuffer(input, additionalArgs, function);
        if (graphToCompile != null && gpuCompilationUnit != null) {
            return runWithMarawaccAccelerator(inputPArray, graphToCompile, gpuCompilationUnit, function, newAllocation);
        }

        JITMetaInput meta = new JITMetaInput(firstValue, interoperable, lexicalScopes, inputPArray);
        ArrayList<Object> listResult = addOutputElement(firstValue);
        Profiler.getInstance().writeInBuffer(ProfilerType.GENERAL_LOG_MESSAGE, "START ID: ", compileIndex);
        for (int i = compileIndex; i < input.getLength(); i++) {
            Object[] argsPackage = ASTxUtils.createRArguments(nArgs, function, input, additionalArgs, argsName, i);
            Object value = callTarget.call(argsPackage);
            listResult.add(value);
            ArrayList<Object> result = checkAndRunWithOpenCL(gpuCompilationUnit, callTarget, i, meta, function, argsOriginal);
            if (result != null) {
                return result;
            }
        }
        return listResult;
    }

    private static ArrayList<Object> runInASTInterpreter(RAbstractVector input, RootCallTarget callTarget, RFunction function, int nArgs, RAbstractVector[] additionalArgs, String[] argsName,
                    Object firstValue) {
        ArrayList<Object> output = addOutputElement(firstValue);
        for (int i = 1; i < input.getLength(); i++) {
            Object[] argsPackage = ASTxUtils.createRArguments(nArgs, function, input, additionalArgs, argsName, i);
            Object value = callTarget.call(argsPackage);
            output.add(value);
        }
        return output;
    }

    /**
     * This method invalidates the binary caches, and meta-data.
     *
     * @param function
     * @param callTarget
     */
    private static void invalidateCaches(RFunction function, RootCallTarget callTarget) {
        RGPUCache.INSTANCE.getCachedObjects(function).deoptimize();
        StructuredGraph graphToCompile = MarawaccGraalIRCache.getInstance().getCompiledGraph(callTarget.getIDForOpenCL());
        MarawaccGraalIRCache.getInstance().deoptimize(callTarget.getIDForOpenCL());
        // Clear entry in compilation unit
        InternalGraphCache.INSTANCE.deoptimize(graphToCompile);
    }

    /**
     * Run in the interpreter and then JIT when the CFG is prepared for compilation.
     */
    private ArrayList<Object> runJavaOpenCLJIT(PArray<?> input, RootCallTarget callTarget, RFunction function, int nArgs, PArray<?>[] additionalArgs, String[] argsName,
                    Object firstValue, PArray<?> inputPArray, Interoperable interoperable, Object[] lexicalScopes, int totalSize, int inputArgs) throws AcceleratorExecutionException {

        checkIfRFunctionIsInCache(function, callTarget);
        StructuredGraph graphToCompile = MarawaccGraalIRCache.getInstance().getCompiledGraph(callTarget.getIDForOpenCL());
        GraalOpenCLCompilationUnit gpuCompilationUnit = InternalGraphCache.INSTANCE.getGPUCompilationUnit(graphToCompile);

        boolean newAllocation = newAllocationBuffer(input, additionalArgs, function);

        if (graphToCompile != null && gpuCompilationUnit != null) {
            ArrayList<Object> runWithMarawaccAccelerator = runWithMarawaccAccelerator(inputPArray, graphToCompile, gpuCompilationUnit, function, newAllocation);
            return runWithMarawaccAccelerator;
        }

        JITMetaInput meta = new JITMetaInput(firstValue, interoperable, lexicalScopes, inputPArray);
        ArrayList<Object> output = addOutputElement(firstValue);

        for (int i = 1; i < totalSize; i++) {
            Object[] argsPackage = ASTxUtils.createRArguments(nArgs, function, input, additionalArgs, argsName, i);
            Object value = callTarget.call(argsPackage);
            output.add(value);
            ArrayList<Object> checkAndRun = checkAndRunWithOpenCL(gpuCompilationUnit, callTarget, i, meta, function, inputArgs);
            if (checkAndRun != null) {
                return checkAndRun;
            }
        }
        return output;
    }

    @SuppressWarnings("unused")
    private static ArrayList<Object> runAfterDeopt(PArray<?> input, RootCallTarget callTarget, RFunction function, int nArgs, PArray<?>[] additionalArgs, String[] argsName,
                    Object firstValue, int totalSize) {
        checkIfRFunctionIsInCache(function, callTarget);
        ArrayList<Object> output = addOutputElement(firstValue);
        for (int i = 1; i < totalSize; i++) {
            Object[] argsPackage = ASTxUtils.createRArguments(nArgs, function, input, additionalArgs, argsName, i);
            Object value = callTarget.call(argsPackage);
            output.add(value);
        }
        return output;
    }

    private static ArrayList<Object> runAfterDeoptWithID(PArray<?> input, RootCallTarget callTarget, RFunction function, int nArgs, PArray<?>[] additionalArgs, String[] argsName,
                    Object firstValue, int threadID) {
        checkIfRFunctionIsInCache(function, callTarget);
        ArrayList<Object> output = addOutputElement(firstValue);
        Object[] argsPackage = ASTxUtils.createRArguments(nArgs, function, input, additionalArgs, argsName, threadID);
        Object value = callTarget.call(argsPackage);
        output.add(value);
        return output;
    }

    private static ArrayList<Object> runAfterDeoptWithThreadID(RAbstractVector input, RootCallTarget callTarget, RFunction function, int nArgs, RAbstractVector[] additionalArgs, String[] argsName,
                    Object firstValue, int threadID) {
        checkIfRFunctionIsInCache(function, callTarget);
        ArrayList<Object> output = addOutputElement(firstValue);
        Object[] argsPackage = ASTxUtils.createRArguments(nArgs, function, input, additionalArgs, argsName, threadID);
        Object value = callTarget.call(argsPackage);
        output.add(value);
        return output;
    }

    /**
     * It checks if the function was already inserted into the cache. If it is that the case, we
     * return the metadata associated with the value such as the return value, parameters and
     * interoperable objects.
     *
     * @param input
     * @param function
     * @param additionalArgs
     * @return {@link RFunctionMetadata}.
     */
    private static RFunctionMetadata getCachedFunctionMetadata(PArray<?> input, RFunction function, PArray<?>[] additionalArgs) {
        if (RGPUCache.INSTANCE.getCachedObjects(function).getRFunctionMetadata() == null) {
            // Type inference -> execution of the first element
            int nArgs = ASTxUtils.getNumberOfArguments(function);
            String[] argsName = ASTxUtils.getArgumentsNames(function);
            Object[] argsPackage = ASTxUtils.createRArguments(nArgs, function, input, additionalArgs, argsName, 0);
            Object value = function.getTarget().call(argsPackage);

            // Inter-operable objects
            TypeInfo outputType = ASTxUtils.obtainTypeInfo(value);
            InteropTable interop = ASTxUtils.obtainInterop(outputType);

            Class<?>[] typeObject = ASTxUtils.createListSubTypes(interop, value);
            Interoperable interoperable = (interop != null) ? new Interoperable(interop, typeObject) : null;

            RFunctionMetadata metadata = new RFunctionMetadata(nArgs, argsName, argsPackage, value, outputType, interop, typeObject, interoperable);
            RGPUCache.INSTANCE.getCachedObjects(function).insertRFuctionMetadata(metadata);
            return metadata;
        } else {
            return RGPUCache.INSTANCE.getCachedObjects(function).getRFunctionMetadata();
        }
    }

    /**
     * It checks if the function was already inserted into the cache. If it is that the case, we
     * return the meta-data associated with the value such as the return value, parameters and
     * interoperable objects.
     *
     * @param input
     * @param function
     * @param additionalArgs
     * @return {@link RFunctionMetadata}.
     */
    private static RFunctionMetadata getCachedFunctionMetadata(RAbstractVector input, RFunction function, RAbstractVector[] additionalArgs) {
        if (RGPUCache.INSTANCE.getCachedObjects(function).getRFunctionMetadata() == null) {
            // Type inference -> execution of the first element
            int nArgs = ASTxUtils.getNumberOfArguments(function);
            String[] argsName = ASTxUtils.getArgumentsNames(function);
            Object[] argsPackage = ASTxUtils.createRArguments(nArgs, function, input, additionalArgs, argsName, 0);
            Object value = function.getTarget().call(argsPackage);

            // Inter-operable objects
            TypeInfo outputType = ASTxUtils.obtainTypeInfo(value);
            InteropTable interopOutput = ASTxUtils.obtainInterop(outputType);

            Class<?>[] typeObject = ASTxUtils.createListSubTypes(interopOutput, value);
            Interoperable interoperable = (interopOutput != null) ? new Interoperable(interopOutput, typeObject) : null;

            RFunctionMetadata metadata = new RFunctionMetadata(nArgs, argsName, argsPackage, value, outputType, interopOutput, typeObject, interoperable);
            RGPUCache.INSTANCE.getCachedObjects(function).insertRFuctionMetadata(metadata);
            return metadata;

        } else {
            return RGPUCache.INSTANCE.getCachedObjects(function).getRFunctionMetadata();
        }
    }

    private RAbstractVector computeOpenCLMApply(PArray<?> input, RFunction function, RootCallTarget target, PArray<?>[] additionalArgs, Object[] lexicalScopes, int numArgumentsOriginalFunction) {
        // Get the meta-data from the cache
        RFunctionMetadata cachedFunctionMetadata = getCachedFunctionMetadata(input, function, additionalArgs);
        int nArgs = cachedFunctionMetadata.getnArgs();
        String[] argsName = cachedFunctionMetadata.getArgsName();
        Object value = cachedFunctionMetadata.getFirstValue();
        TypeInfo outputType = cachedFunctionMetadata.getOutputType();
        Interoperable interoperable = cachedFunctionMetadata.getInteroperable();

        int totalSize = ASTxUtils.getSize(input, additionalArgs);
        TypeInfoList inputTypeList = ASTxUtils.createTypeInfoListForInputWithPArrays(input, additionalArgs);

        // Marshal from R to OpenCL (PArray)
        long startMarshal = System.nanoTime();
        PArray<?> inputPArrayFormat = ASTxUtils.createPArrays(input, additionalArgs, inputTypeList);
        long endMarshal = System.nanoTime();

        // Execution
        ArrayList<Object> result = null;
        long startExecution = System.nanoTime();
        try {
            result = runJavaOpenCLJIT(input, target, function, nArgs, additionalArgs, argsName, value, inputPArrayFormat, interoperable, lexicalScopes, totalSize, numArgumentsOriginalFunction);
        } catch (AcceleratorExecutionException e) {

            if (ASTxOptions.debug) {
                System.out.println("Running in the DEOPT mode");
            }

            int threadID = e.getThreadID();
            boolean executionValid = false;
            int deoptCounter = 0;
            while (!executionValid) {
                runAfterDeoptWithID(input, target, function, nArgs, additionalArgs, argsName, value, threadID);
                invalidateCaches(function, target);
                try {
                    result = runJavaOpenCLJIT(input, target, function, nArgs, additionalArgs, argsName, value, inputPArrayFormat, interoperable, lexicalScopes, totalSize, numArgumentsOriginalFunction);
                    executionValid = true;
                } catch (AcceleratorExecutionException e1) {
                    threadID = e1.getThreadID();
                    deoptCounter++;
                    if (deoptCounter > 10) {
                        executionValid = true;
                        throw new RuntimeException("Too many deoptimizations");
                    }
                }
            }
        }
        long endExecution = System.nanoTime();

        // Marshal from OpenCL to R
        boolean isGPUExecution = RGPUCache.INSTANCE.getCachedObjects(function).isGPUExecution();
        long startUnmarshal = System.nanoTime();
        RAbstractVector resultFastR = getResultFromPArray(isGPUExecution, outputType, result);
        long endUnmarshal = System.nanoTime();

        if (ASTxOptions.profileOpenCL_ASTx) {
            writeProfilerIntoBuffers(startMarshal, endMarshal, startExecution, endExecution, startUnmarshal, endUnmarshal);
        }
        return resultFastR;
    }

    private static void writeProfilerIntoBuffers(long startMarshal, long endMarshal, long startExecution, long endExecution, long startUnmarshal, long endUnmarshal) {
        // Marshal
        Profiler.getInstance().writeInBuffer(ProfilerType.TRUFFLE_MARSHAL, "start", startMarshal);
        Profiler.getInstance().writeInBuffer(ProfilerType.TRUFFLE_MARSHAL, "end", endMarshal);
        Profiler.getInstance().writeInBuffer(ProfilerType.TRUFFLE_MARSHAL, "end-start", (endMarshal - startMarshal));

        // Execution
        Profiler.getInstance().writeInBuffer(ProfilerType.TRUFFLE_EXECUTE, "start", startExecution);
        Profiler.getInstance().writeInBuffer(ProfilerType.TRUFFLE_EXECUTE, "end", endExecution);
        Profiler.getInstance().writeInBuffer(ProfilerType.TRUFFLE_EXECUTE, "end-start", (endExecution - startExecution));

        // Unmarshal
        Profiler.getInstance().writeInBuffer(ProfilerType.TRUFFLE_UNMARSHAL, "start", startUnmarshal);
        Profiler.getInstance().writeInBuffer(ProfilerType.TRUFFLE_UNMARSHAL, "end", endUnmarshal);
        Profiler.getInstance().writeInBuffer(ProfilerType.TRUFFLE_UNMARSHAL, "end-start", (endUnmarshal - startUnmarshal));
    }

    @SuppressWarnings("deprecation")
    private static TypeInfoList createTypeInfoList(RAbstractVector input, RAbstractVector[] additionalArgs, int extraParams) {
        if (ASTxOptions.usePArrays) {
            return ASTxUtils.createTypeInfoListForInputWithPArrays(input, additionalArgs, extraParams);
        } else {
            return ASTxUtils.createTypeInfoListForInput(input, additionalArgs, extraParams);
        }
    }

    private void getBytesInputData(int nArgs, TypeInfoList inputTypeList) {
        for (int i = 0; i < nArgs; i++) {
            TypeInfo t = inputTypeList.get(i);
            getSizeType(t);
        }
    }

    private RAbstractVector computeOpenCLMApply(RAbstractVector input, RFunction function, RootCallTarget target, RAbstractVector[] additionalArgs, Object[] lexicalScopes,
                    int numArgumentsOriginalFunction) {

        Profiler.getInstance().writeInBuffer(ProfilerType.DEOPTTRACE, "StartRunning", System.nanoTime());

        // Meta-data objects from the cache
        RFunctionMetadata cachedFunctionMetadata = getCachedFunctionMetadata(input, function, additionalArgs);
        int nArgs = cachedFunctionMetadata.getnArgs();
        String[] argsName = cachedFunctionMetadata.getArgsName();
        Object value = cachedFunctionMetadata.getFirstValue();
        TypeInfo outputType = cachedFunctionMetadata.getOutputType();
        Interoperable interoperable = cachedFunctionMetadata.getInteroperable();

        // Get input types list
        int extraParams = nArgs - numArgumentsOriginalFunction;
        TypeInfoList inputTypeList = createTypeInfoList(input, additionalArgs, extraParams);

        getBytesInputData(nArgs, inputTypeList);
        getSizeType(outputType);

        // Marshal from R to OpenCL (PArray)
        long startMarshal = System.nanoTime();
        PArray<?> inputPArray = ASTxUtils.createPArrays(input, additionalArgs, inputTypeList);
        long endMarshal = System.nanoTime();

        // Execution
        ArrayList<Object> result = null;
        long startExecution = System.nanoTime();
        try {
            if (ASTxOptions.runOnASTIntepreterOnly) {
                result = runInASTInterpreter(input, target, function, nArgs, additionalArgs, argsName, value);
            } else {
                result = runJavaOpenCLJIT(input, target, function, nArgs, additionalArgs, argsName, value, inputPArray, interoperable, lexicalScopes, numArgumentsOriginalFunction);
            }
        } catch (AcceleratorExecutionException e) {

            /*
             * Control deoptimizations. First, it runs in the interpreter with the ID that failed.
             * Then, the function is executed again in the normal path. Eventually, the VM compiles
             * the function as usual and compiles/runs the new version on GPU. The new version also
             * can fail, so we catch the exception again and repeat the same process. If it fails
             * more than 10 times, we just deopt and run in the interpreter.
             */
            if (ASTxOptions.debug) {
                System.out.println("Running in the DEOPT mode");
            }
            int threadID = e.getThreadID();
            boolean executionValid = false;
            int deoptCounter = 0;
            while (!executionValid) {
                Profiler.getInstance().writeInBuffer(ProfilerType.DEOPTTRACE, "DEOPT_CACHED", System.nanoTime());
                runAfterDeoptWithThreadID(input, target, function, nArgs, additionalArgs, argsName, value, threadID);
                invalidateCaches(function, target);
                try {
                    Profiler.getInstance().writeInBuffer(ProfilerType.DEOPTTRACE, "RE_RUN", System.nanoTime());
                    result = runJavaOpenCLJIT(input, target, function, nArgs, additionalArgs, argsName, value, inputPArray, interoperable, lexicalScopes, numArgumentsOriginalFunction);
                    executionValid = true;
                } catch (AcceleratorExecutionException e1) {
                    threadID = e1.getThreadID();
                    deoptCounter++;
                    if (deoptCounter > 10) {
                        executionValid = true;
                        throw new RuntimeException("Too many deoptimizations, not possible to run on again");
                    }
                }
            }
        }
        boolean isGPUExecution = RGPUCache.INSTANCE.getCachedObjects(function).isGPUExecution();
        long endExecution = System.nanoTime();

        // Marshal from OpenCL to R
        long startUnmarshal = System.nanoTime();
        RAbstractVector resultFastR = null;
        if (wasBatch) {
            resultFastR = getResultFromPArrayBatch(outputType, result);
        } else {
            resultFastR = getResult(isGPUExecution, outputType, result);
        }
        long endUnmarshal = System.nanoTime();

        if (ASTxOptions.profileOpenCL_ASTx) {
            writeProfilerIntoBuffers(startMarshal, endMarshal, startExecution, endExecution, startUnmarshal, endUnmarshal);
        }
        return resultFastR;
    }

    private void getSizeType(TypeInfo t) {
        if (t == TypeInfo.DOUBLE) {
            typeSizes.add(8);
        } else if (t == TypeInfo.RDoubleVector) {
            typeSizes.add(8);
        } else if (t == TypeInfo.RDoubleSequence) {
            typeSizes.add(8);
        } else if (t == TypeInfo.INT) {
            typeSizes.add(4);
        } else if (t == TypeInfo.RIntVector) {
            typeSizes.add(4);
        } else if (t == TypeInfo.RIntSequence) {
            typeSizes.add(4);
        } else {
            if (ASTxOptions.debug) {
                System.err.println("Data Type not supported yet::" + t);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "deprecation"})
    private static RAbstractVector getResult(boolean wasExecutedOnGPU, TypeInfo outputType, ArrayList<Object> result) {
        if (!wasExecutedOnGPU) {
            // get the output in R-Type format
            return ASTxUtils.unMarshallResultFromArrayList(outputType, result);
        } else if (ASTxOptions.usePArrays) {
            // get the references
            return ASTxUtils.unMarshallFromFullPArrays(outputType, (PArray) result.get(0));
        } else if (ASTxOptions.usePrimitivePArray) {
            // Get the stored primitive array reference
            return ASTxUtils.unmarshalFromPrimitivePArrays(outputType, (PArray) result.get(0));
        } else {
            // Real un-marshal
            return ASTxUtils.unMarshallResultFromPArrays(outputType, (PArray) result.get(0));
        }
    }

    @SuppressWarnings({"rawtypes"})
    private static RAbstractVector getResultFromPArray(boolean gpuExecution, TypeInfo outputType, ArrayList<Object> result) {
        if (!gpuExecution) {
            // get the output in R-Type format from the PArray Format
            return ASTxUtils.unMarshallResultFromArrayList(outputType, result);
        } else {
            // get the references
            return ASTxUtils.unMarshallFromFullPArrays(outputType, (PArray) result.get(0));
        }
    }

    @SuppressWarnings("rawtypes")
    private double[] getDoubleVector(TypeInfo outputType, ArrayList<Object> result) {
        int totalSize = totalSizeWhenBatch;
        double[] finalResultDouble = new double[totalSize];
        int destPos = 0;
        for (Object o : result) {
            double[] r = (double[]) ASTxUtils.primitiveFromFullPArrays(outputType, (PArray) o);
            System.arraycopy(r, 0, finalResultDouble, destPos, r.length);
            destPos += r.length;
        }
        return finalResultDouble;
    }

    @SuppressWarnings("rawtypes")
    private int[] getIntVector(TypeInfo outputType, ArrayList<Object> result) {
        int totalSize = totalSizeWhenBatch;
        int[] finalResult = new int[totalSize];
        int destPos = 0;
        for (Object o : result) {
            int[] r = (int[]) ASTxUtils.primitiveFromFullPArrays(outputType, (PArray) o);
            System.arraycopy(r, 0, finalResult, destPos, r.length);
            destPos += r.length;
        }
        return finalResult;
    }

    private RAbstractVector getResultFromPArrayBatch(TypeInfo outputType, ArrayList<Object> result) {
        if (outputType == TypeInfo.DOUBLE) {
            return RDataFactory.createDoubleVector(getDoubleVector(outputType, result), false);
        } else if (outputType == TypeInfo.INT) {
            return RDataFactory.createIntVector(getIntVector(outputType, result), false);
        } else {
            throw new RuntimeException("Data Type not supported yet: " + outputType);
        }
    }

    private static RFunction scopeRewritting(RFunction function, String[] scopeVars) {
        String newRewrittenFunction = ASTxUtils.rewriteFunction(function, scopeVars);
        Source newSourceFunction = Source.fromText(newRewrittenFunction, R_EVAL_DESCRIPTION).withMimeType(RRuntime.R_APP_MIME);
        try {
            RFunction newRFunction = (RFunction) RContext.getEngine().parseAndEval(newSourceFunction, false);
            return newRFunction;
        } catch (ParseException e) {
            throw new RuntimeException("[Fatal error] the function could not be rewritten");
        }
    }

    private static RAbstractVector[] getAddiotionalInputs(RArgsValuesAndNames args, boolean isRewritten, RVector[] vectors, Object[] lexicalScopes) {
        RAbstractVector[] additionalInputs = null;
        if (isRewritten) {
            additionalInputs = ASTxUtils.getAdditionalArguments(args, isRewritten, vectors, lexicalScopes.length);
        } else {
            additionalInputs = ASTxUtils.getRArrayWithAdditionalArguments(args);
        }
        return additionalInputs;
    }

    private RAbstractVector computeOpenCLMApplyForRVector(RArgsValuesAndNames args, boolean isRewritten, RVector[] vectors, Object[] lexicalScopes, RFunction function,
                    RAbstractVector inputRArray,
                    RootCallTarget target, int numArgumentsOriginalFunction) {
        RAbstractVector mapResult = null;
        RAbstractVector[] additionalInputs = getAddiotionalInputs(args, isRewritten, vectors, lexicalScopes);
        mapResult = computeOpenCLMApply(inputRArray, function, target, additionalInputs, lexicalScopes, numArgumentsOriginalFunction);
        return mapResult;
    }

    private RAbstractVector computeOpenCLMApplyForPArray(RArgsValuesAndNames args, Object[] lexicalScopes, RFunction function, PArray<?> parrayInput,
                    RootCallTarget target, int numArgumentsOriginalFunction) {
        PArray<?>[] additionalInputs = ASTxUtils.getPArrayWithAdditionalArguments(args);
        return computeOpenCLMApply(parrayInput, function, target, additionalInputs, lexicalScopes, numArgumentsOriginalFunction);
    }

    @SuppressWarnings("deprecation")
    private static void checkJVMOptions() {
        if (ASTxOptions.usePArrays) {
            // This will be deprecated
            RVector.WITH_PARRAYS = true;
        }
    }

    private static void printProfiler(long start, long end, String component) {
        // Write profiler information into a buffer
        if (ASTxOptions.profileOpenCL_ASTx) {
            Profiler.getInstance().writeInBuffer(component + " start-end", (end - start));
            Profiler.getInstance().writeInBuffer(component + " start", start);
            Profiler.getInstance().writeInBuffer(component + " end", end);
        }
    }

    private static class MetaData {
        RootCallTarget target = null;
        Object[] lexicalScopes = null;
        RVector[] vectors = null;
        String[] filterScopeVarNames = null;
        String[] scopeVars = null;

        public MetaData(RootCallTarget target, Object[] lexicalScopes, RVector[] vectors, String[] filterScopeVarNames, String[] scopeVars) {
            super();
            this.target = target;
            this.lexicalScopes = lexicalScopes;
            this.vectors = vectors;
            this.filterScopeVarNames = filterScopeVarNames;
            this.scopeVars = scopeVars;
        }

        public RootCallTarget getTarget() {
            return target;
        }

        public Object[] getLexicalScopes() {
            return lexicalScopes;
        }

        public RVector[] getVectors() {
            return vectors;
        }

        public String[] getFilterScopeVarNames() {
            return filterScopeVarNames;
        }

        public String[] getScopeVars() {
            return scopeVars;
        }
    }

    private static MetaData insertIntoCache(RFunction function, RArgsValuesAndNames args, Object firstParam) {
        RootCallTarget target = null;
        Object[] lexicalScopes = null;
        RVector[] vectors = null;
        String[] filterScopeVarNames = null;
        String[] scopeVars = null;
        // Lexical scoping in the AST level
        scopeVars = ASTxUtils.lexicalScopingAST(function);
        ScopeVarInfo valueOfScopeArrays = ASTxUtils.getValueOfScopeArrays(scopeVars, function);
        if (valueOfScopeArrays != null) {
            lexicalScopes = valueOfScopeArrays.getScopeVars();
            filterScopeVarNames = valueOfScopeArrays.getNameVars();
            vectors = valueOfScopeArrays.getVector();
        }
        RCacheObjects cachedObjects = new RCacheObjects(function.getTarget(), scopeVars, lexicalScopes);
        target = RGPUCache.INSTANCE.updateCacheObjects(function, cachedObjects);
        lookupFunction.insert(function, firstParam, args);
        return new MetaData(target, lexicalScopes, vectors, filterScopeVarNames, scopeVars);
    }

    @SuppressWarnings("rawtypes")
    private RAbstractVector execute(RArgsValuesAndNames args, boolean isRewritten, RVector[] vectors, Object[] lexicalScopes, RFunction function, RAbstractVector inputRArray, RootCallTarget target,
                    int numArgumentsOriginalFunction, boolean parrayFormat, PArray parrayInput) {
        RAbstractVector mapResult = null;
        if (!parrayFormat) {
            mapResult = computeOpenCLMApplyForRVector(args, isRewritten, vectors, lexicalScopes, function, inputRArray, target, numArgumentsOriginalFunction);
        } else {
            // Note this path with {@link Parray} as input does not allow the experimental
            // optimisation node scope rewriting.
            mapResult = computeOpenCLMApplyForPArray(args, lexicalScopes, function, parrayInput, target, numArgumentsOriginalFunction);
        }
        return mapResult;
    }

    @Override
    public Object call(RArgsValuesAndNames args) {

        Profiler.getInstance().print("\nIteration: " + iteration++);

        checkJVMOptions();
        compileIndex = 1;
        typeSizes.clear();

        long start = System.nanoTime();

        Object firstParam = args.getArgument(0);
        RFunction function = (RFunction) args.getArgument(1);

        // For debugging
        if (ASTxOptions.printASTforRFunction) {
            ASTxUtils.printAST(function);
        }

        RAbstractVector inputRArray = null;
        PArray<?> parrayInput = null;
        boolean parrayFormat = false;

        if (firstParam instanceof RAbstractVector) {
            inputRArray = (RAbstractVector) firstParam;
        } else if (firstParam instanceof PArray) {
            parrayFormat = true;
            parrayInput = (PArray<?>) firstParam;
        } else {
            throw new RuntimeException("Data type not supported: R Vector or PArray expected, but " + firstParam.getClass() + " found");
        }

        RootCallTarget target = null;
        Object[] lexicalScopes = null;
        RVector[] vectors = null;
        String[] filterScopeVarNames = null;
        String[] scopeVars = null;

        // Get the callTarget from the cache
        if (!RGPUCache.INSTANCE.contains(function)) {
            MetaData insertIntoCache = insertIntoCache(function, args, firstParam);
            target = insertIntoCache.getTarget();
            lexicalScopes = insertIntoCache.getLexicalScopes();
            vectors = insertIntoCache.getVectors();
            filterScopeVarNames = insertIntoCache.getFilterScopeVarNames();
            scopeVars = insertIntoCache.getScopeVars();
        } else {
            target = RGPUCache.INSTANCE.getCallTarget(function);
            if (!lookupFunction.checkData(function, firstParam, args)) {
                System.out.println("Recompile the function to the new data!!!!!!!!!!!!!");
                invalidateCaches(function, target);
                MetaData insertIntoCache = insertIntoCache(function, args, firstParam);
                target = insertIntoCache.getTarget();
                lexicalScopes = insertIntoCache.getLexicalScopes();
                vectors = insertIntoCache.getVectors();
                filterScopeVarNames = insertIntoCache.getFilterScopeVarNames();
                scopeVars = insertIntoCache.getScopeVars();
            } else {
                lexicalScopes = RGPUCache.INSTANCE.getCachedObjects(function).getLexicalScopeVars();
            }
        }

        int numArgumentsOriginalFunction = ASTxUtils.getNumberOfArguments(function);
        boolean isRewritten = false;

        // Function rewriting for the scope variable detection
        if (ASTxOptions.scopeRewriting && (filterScopeVarNames != null)) {
            RFunction scopeRewritting = scopeRewritting(function, filterScopeVarNames);
            if (ASTxOptions.debug) {
                System.out.println("[DEBUG] New R Function rewritten => " + scopeRewritting.getRootNode().getSourceSection().getCode());
            }
            function = scopeRewritting;
            isRewritten = true;
            RCacheObjects cachedObjects = new RCacheObjects(function.getTarget(), scopeVars, lexicalScopes);
            target = RGPUCache.INSTANCE.updateCacheObjects(function, cachedObjects);
        }

        RAbstractVector mapResult = execute(args, isRewritten, vectors, lexicalScopes, function, inputRArray, target, numArgumentsOriginalFunction, parrayFormat, parrayInput);

        compileIndex = 1;

        long end = System.nanoTime();
        printProfiler(start, end, "gpu");
        return mapResult;
    }
}
