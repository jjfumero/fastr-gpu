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
import java.util.Arrays;

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
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.r.library.gpu.cache.CacheGPUExecutor;
import com.oracle.truffle.r.library.gpu.cache.InternalGraphCache;
import com.oracle.truffle.r.library.gpu.cache.RCacheObjects;
import com.oracle.truffle.r.library.gpu.cache.RFunctionMetadata;
import com.oracle.truffle.r.library.gpu.cache.RGPUCache;
import com.oracle.truffle.r.library.gpu.exceptions.MarawaccExecutionException;
import com.oracle.truffle.r.library.gpu.options.ASTxOptions;
import com.oracle.truffle.r.library.gpu.phases.FilterInterpreterNodes;
import com.oracle.truffle.r.library.gpu.phases.scope.ScopeData;
import com.oracle.truffle.r.library.gpu.types.TypeInfo;
import com.oracle.truffle.r.library.gpu.types.TypeInfoList;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils.ScopeVarInfo;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.nodes.function.FunctionDefinitionNode;
import com.oracle.truffle.r.runtime.context.Engine.ParseException;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

/**
 * AST Node to check the connection with Marawacc. This is just a proof of concept.
 */
public final class OpenCLMApply extends RExternalBuiltinNode {

    private boolean gpuExecution = false;
    private static int iteration = 0;
    private final boolean ISTRUFFLE = true;
    private boolean isRewritten = false;

    ArrayList<com.oracle.graal.graph.Node> scopedNodes;

    private boolean isRewritten() {
        return this.isRewritten;
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

        ScopeData scopeData = ASTxUtils.scopeArrayDetection(graphToCompile);

        if (lexicalScope != null) {
            scopeData.setData(lexicalScope);
        }
        GraalAcceleratorOptions.printOffloadKernel = true;

        if (ASTxOptions.debug) {
            scopedNodes = ASTxUtils.applyCompilationPhasesForOpenCLAndDump(graphToCompile);
        } else {
            scopedNodes = ASTxUtils.applyCompilationPhasesForOpenCL(graphToCompile);
        }

        new FilterInterpreterNodes(6).apply(graphToCompile);
        CompilerUtils.dumpGraph(graphToCompile, "Filter");

        GraalGPUCompilationUnit gpuCompilationUnit = GraalGPUCompiler.compileGraphToOpenCL(inputPArray, graphToCompile, callTarget, firstValue, ISTRUFFLE, interoperable, scopeData.getData(),
                        scopedNodes);
        gpuCompilationUnit.setScopeArrays(scopeData.getData());
        gpuCompilationUnit.setScopeNodes(scopedNodes);

        // Insert graph into Truffle OpenCL Cache
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
     * @throws MarawaccExecutionException
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArrayList<Object> runWithMarawaccAccelerator(PArray<?> inputPArray, StructuredGraph graph, GraalGPUCompilationUnit gpuCompilationUnit) throws MarawaccExecutionException {

        GraalGPUExecutor executor = CacheGPUExecutor.INSTANCE.getExecutor(gpuCompilationUnit);
        if (executor == null) {
            executor = new GraalGPUExecutor();
            CacheGPUExecutor.INSTANCE.insert(gpuCompilationUnit, executor);
        }

        AcceleratorPArray copyToDevice = executor.copyToDevice(inputPArray, gpuCompilationUnit.getInputType());
        AcceleratorPArray executeOnTheDevice = executor.executeOnTheDevice(graph, copyToDevice, gpuCompilationUnit.getOuputType(), gpuCompilationUnit.getScopeArrays());
        PArray result = executor.copyToHost(executeOnTheDevice, gpuCompilationUnit.getOuputType());
        PArray<Integer> deopt = executor.getDeoptBuffer();
        if (deopt != null) {
            System.out.println("DEOPT: " + deopt);
            if (deopt.get(0) == 1) {
                throw new MarawaccExecutionException("Deoptimization");
            }
        }
        gpuExecution = true;
        ArrayList<Object> arrayList = new ArrayList<>();
        arrayList.add(result);
        return arrayList;
    }

    private static ArrayList<Object> setOutput(Object firstValue) {
        ArrayList<Object> output = new ArrayList<>();
        output.add(firstValue);
        return output;
    }

    private static void checkFunctionInCache(RFunction function, RootCallTarget callTarget) {
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
            super();
            this.firstValue = firstValue;
            this.interoperable = interoperable;
            this.lexicalScopes = lexicalScopes;
            this.inputPArray = inputPArray;
        }
    }

    private ArrayList<Object> checkAndRun(GraalGPUCompilationUnit gpuCompilationUnit, RootCallTarget callTarget, int index, JITMetaInput meta) throws MarawaccExecutionException {
        /*
         * Check if the graph is prepared for GPU compilation and invoke the compilation and
         * execution. On Stack Replacement (OSR): switch to compiled GPU code
         */
        StructuredGraph graphToCompile = MarawaccGraalIR.getInstance().getCompiledGraph(callTarget.getIDForOpenCL());
        if ((graphToCompile != null) && (gpuCompilationUnit == null)) {
            if (ASTxOptions.debug) {
                System.out.println("[MARAWACC-ASTX] Compiling the Graph to GPU - Iteration: " + index);
            }
            GraalGPUCompilationUnit oclCompileUnit = compileForMarawaccBackend(meta.inputPArray, (OptimizedCallTarget) callTarget, graphToCompile, meta.firstValue, meta.interoperable,
                            meta.lexicalScopes);
            return runWithMarawaccAccelerator(meta.inputPArray, graphToCompile, oclCompileUnit);
        }
        return null;
    }

    // Run in the interpreter and then JIT when the CFG is prepared for compilation
    private ArrayList<Object> runJavaOpenCLJIT(RAbstractVector input, RootCallTarget callTarget, RFunction function, int nArgs, RAbstractVector[] additionalArgs, String[] argsName,
                    Object firstValue, PArray<?> inputPArray, Interoperable interoperable, Object[] lexicalScopes, RVector[] vectors) throws MarawaccExecutionException {

        checkFunctionInCache(function, callTarget);

        StructuredGraph graphToCompile = MarawaccGraalIR.getInstance().getCompiledGraph(callTarget.getIDForOpenCL());
        GraalGPUCompilationUnit gpuCompilationUnit = InternalGraphCache.INSTANCE.getGPUCompilationUnit(graphToCompile);

        if (graphToCompile != null && gpuCompilationUnit != null) {
            return runWithMarawaccAccelerator(inputPArray, graphToCompile, gpuCompilationUnit);
        }

        JITMetaInput meta = new JITMetaInput(firstValue, interoperable, lexicalScopes, inputPArray);
        ArrayList<Object> output = setOutput(firstValue);
        for (int i = 1; i < input.getLength(); i++) {
            Object[] argsPackage = ASTxUtils.createRArguments(nArgs, function, input, additionalArgs, argsName, i);
            Object value = callTarget.call(argsPackage);
            output.add(value);
            ArrayList<Object> checkAndRun = checkAndRun(gpuCompilationUnit, callTarget, i, meta);
            if (checkAndRun != null) {
                return checkAndRun;
            }
        }
        return output;
    }

    // Run in the interpreter and then JIT when the CFG is prepared for compilation
    private ArrayList<Object> runJavaOpenCLJIT(PArray<?> input, RootCallTarget callTarget, RFunction function, int nArgs, PArray<?>[] additionalArgs, String[] argsName,
                    Object firstValue, PArray<?> inputPArray, Interoperable interoperable, Object[] lexicalScopes, int totalSize) throws MarawaccExecutionException {

        checkFunctionInCache(function, callTarget);

        StructuredGraph graphToCompile = MarawaccGraalIR.getInstance().getCompiledGraph(callTarget.getIDForOpenCL());
        GraalGPUCompilationUnit gpuCompilationUnit = InternalGraphCache.INSTANCE.getGPUCompilationUnit(graphToCompile);

        if (graphToCompile != null && gpuCompilationUnit != null) {
            ArrayList<Object> runWithMarawaccAccelerator = runWithMarawaccAccelerator(inputPArray, graphToCompile, gpuCompilationUnit);
            return runWithMarawaccAccelerator;
        }

        JITMetaInput meta = new JITMetaInput(firstValue, interoperable, lexicalScopes, inputPArray);
        ArrayList<Object> output = setOutput(firstValue);

        for (int i = 1; i < totalSize; i++) {
            Object[] argsPackage = ASTxUtils.createRArguments(nArgs, function, input, additionalArgs, argsName, i);
            Object value = callTarget.call(argsPackage);
            output.add(value);
            ArrayList<Object> checkAndRun = checkAndRun(gpuCompilationUnit, callTarget, i, meta);
            if (checkAndRun != null) {
                return checkAndRun;
            }
        }
        return output;
    }

    // Run in the interpreter and then JIT when the CFG is prepared for compilation
    private static ArrayList<Object> runAfterDeopt(PArray<?> input, RootCallTarget callTarget, RFunction function, int nArgs, PArray<?>[] additionalArgs, String[] argsName,
                    Object firstValue, int totalSize) {
        checkFunctionInCache(function, callTarget);
        ArrayList<Object> output = setOutput(firstValue);
        for (int i = 1; i < totalSize; i++) {
            Object[] argsPackage = ASTxUtils.createRArguments(nArgs, function, input, additionalArgs, argsName, i);
            Object value = callTarget.call(argsPackage);
            output.add(value);
        }
        return output;
    }

    // Run in the interpreter and then JIT when the CFG is prepared for compilation
    private static ArrayList<Object> runAfterDeopt(RAbstractVector input, RootCallTarget callTarget, RFunction function, int nArgs, RAbstractVector[] additionalArgs, String[] argsName,
                    Object firstValue, int totalSize) {
        checkFunctionInCache(function, callTarget);
        ArrayList<Object> output = setOutput(firstValue);
        for (int i = 1; i < totalSize; i++) {
            Object[] argsPackage = ASTxUtils.createRArguments(nArgs, function, input, additionalArgs, argsName, i);
            Object value = callTarget.call(argsPackage);
            output.add(value);
        }
        return output;
    }

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

    private RAbstractVector computeOpenCLMApply(PArray<?> input, RFunction function, RootCallTarget target, PArray<?>[] additionalArgs, Object[] lexicalScopes) {

        RFunctionMetadata cachedFunctionMetadata = getCachedFunctionMetadata(input, function, additionalArgs);
        int nArgs = cachedFunctionMetadata.getnArgs();
        String[] argsName = cachedFunctionMetadata.getArgsName();
        Object value = cachedFunctionMetadata.getFirstValue();
        TypeInfo outputType = cachedFunctionMetadata.getOutputType();
        Interoperable interoperable = cachedFunctionMetadata.getInteroperable();

        int totalSize = ASTxUtils.getSize(input, additionalArgs);
        TypeInfoList inputTypeList = ASTxUtils.createTypeInfoListForInputWithPArrays(input, additionalArgs);

        // Marshal
        long startMarshal = System.nanoTime();
        PArray<?> inputPArrayFormat = ASTxUtils.createPArrays(input, additionalArgs, inputTypeList);
        long endMarshal = System.nanoTime();

        // Execution
        ArrayList<Object> result = null;
        long startExecution = System.nanoTime();
        try {
            result = runJavaOpenCLJIT(input, target, function, nArgs, additionalArgs, argsName, value, inputPArrayFormat, interoperable, lexicalScopes, totalSize);
        } catch (MarawaccExecutionException e) {
            // Deoptimization
            System.out.println("Running in the DEOPT mode");
            result = runAfterDeopt(inputPArrayFormat, target, function, nArgs, additionalArgs, argsName, value, totalSize);
        }
        long endExecution = System.nanoTime();

        // Get the result (un-marshal)
        long startUnmarshal = System.nanoTime();
        RAbstractVector resultFastR = getResultFromPArray(outputType, result);
        long endUnmarshal = System.nanoTime();

        // Print profiler
        if (ASTxOptions.profile_OCL_ASTx) {
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

    private RAbstractVector computeOpenCLMApply(RAbstractVector input, RFunction function, RootCallTarget target, RAbstractVector[] additionalArgs, Object[] lexicalScopes, RVector[] vectors) {

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

        TypeInfoList inputTypeList = null;
        if (ASTxOptions.usePArrays) {
            inputTypeList = ASTxUtils.createTypeInfoListForInputWithPArrays(input, additionalArgs);
        } else {
            inputTypeList = ASTxUtils.createTypeInfoListForInput(input, additionalArgs);
        }

        // Marshal
        long startMarshal = System.nanoTime();
        PArray<?> inputPArrayFormat = ASTxUtils.createPArrays(input, additionalArgs, inputTypeList);
        long endMarshal = System.nanoTime();

        // Execution
        ArrayList<Object> result = null;
        long startExecution = System.nanoTime();
        try {
            result = runJavaOpenCLJIT(input, target, function, nArgs, additionalArgs, argsName, value, inputPArrayFormat, interoperable, lexicalScopes, vectors);
        } catch (MarawaccExecutionException e) {
            // Deoptimization
            System.out.println("Running in the DEOPT mode");
            result = runAfterDeopt(input, target, function, nArgs, additionalArgs, argsName, value, input.getLength());
        }
        long endExecution = System.nanoTime();

        // Get the result (un-marshal)
        long startUnmarshal = System.nanoTime();
        RAbstractVector resultFastR = getResult(outputType, result);
        long endUnmarshal = System.nanoTime();

        // Print profiler
        if (ASTxOptions.profile_OCL_ASTx) {

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

    private static RFunction scopeRewritting(RFunction function, String[] scopeVars) {
        String rewriteFunction = ASTxUtils.rewriteFunction(function, scopeVars);
        Source source = Source.fromText(rewriteFunction, "<eval>").withMimeType("application/x-r");
        try {
            RFunction parseAndEval = (RFunction) RContext.getEngine().parseAndEval(source, false);
            return parseAndEval;
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
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

        if (ASTxOptions.printAST) {
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
            throw new RuntimeException("Vector or PArray expected, but " + firstParam.getClass() + " found");
        }

        RootCallTarget target = null;
        Object[] lexicalScopes = null;
        RVector[] vectors = null;
        String[] filterScopeVarNames = null;
        String[] scopeVars = null;

        // Get the callTarget from the cache
        if (!RGPUCache.INSTANCE.contains(function)) {
            // Lexical scoping from the AST level
            scopeVars = ASTxUtils.lexicalScopingAST(function);
            ScopeVarInfo valueOfScopeArrays = ASTxUtils.getValueOfScopeArrays(scopeVars, function);
            if (valueOfScopeArrays != null) {
                lexicalScopes = valueOfScopeArrays.getScopeVars();
                filterScopeVarNames = valueOfScopeArrays.getNameVars();
                vectors = valueOfScopeArrays.getVector();
            }
            RCacheObjects cachedObjects = new RCacheObjects(function.getTarget(), scopeVars, lexicalScopes);
            target = RGPUCache.INSTANCE.updateCacheObjects(function, cachedObjects);
        } else {
            target = RGPUCache.INSTANCE.getCallTarget(function);
            lexicalScopes = RGPUCache.INSTANCE.getCachedObjects(function).getLexicalScopeVars();
        }

        if (ASTxOptions.scopeRewriting) {
            RFunction scopeRewritting = scopeRewritting(function, filterScopeVarNames);
            System.out.println("NEW FUNCTION: " + scopeRewritting.getRootNode().getSourceSection().getCode());
            if (scopeRewritting != null) {
                function = scopeRewritting;
                isRewritten = true;
                RCacheObjects cachedObjects = new RCacheObjects(function.getTarget(), scopeVars, lexicalScopes);
                target = RGPUCache.INSTANCE.updateCacheObjects(function, cachedObjects);
            }
        }

        RAbstractVector mapResult = null;

        // Prepare all inputs in an array of Objects
        if (!parrayFormat) {
            RAbstractVector[] additionalInputs = ASTxUtils.getRArrayWithAdditionalArguments(args);
            if (isRewritten()) {
                RAbstractVector[] foo = new RAbstractVector[additionalInputs.length + lexicalScopes.length];
                for (int i = 0; i < additionalInputs.length; i++) {
                    foo[i] = additionalInputs[i];
                }
                int j = 0;
                for (int i = additionalInputs.length; i < additionalInputs.length + lexicalScopes.length; i++) {
                    foo[i] = vectors[j++];
                }
                additionalInputs = foo;
            }
            mapResult = computeOpenCLMApply(inputRArray, function, target, additionalInputs, lexicalScopes, vectors);
        } else {
            PArray<?>[] additionalInputs = ASTxUtils.getPArrayWithAdditionalArguments(args);
            mapResult = computeOpenCLMApply(parrayInput, function, target, additionalInputs, lexicalScopes);
        }

        long end = System.nanoTime();

        if (ASTxOptions.profile_OCL_ASTx) {
            Profiler.getInstance().writeInBuffer("gpu start-end", (end - start));
            Profiler.getInstance().writeInBuffer("gpu start", start);
            Profiler.getInstance().writeInBuffer("gpu end", end);
        }
        return mapResult;
    }
}
