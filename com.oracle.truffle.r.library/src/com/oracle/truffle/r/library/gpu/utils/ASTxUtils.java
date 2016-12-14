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
package com.oracle.truffle.r.library.gpu.utils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import uk.ac.ed.accelerator.common.GraalAcceleratorOptions;
import uk.ac.ed.accelerator.profiler.Profiler;
import uk.ac.ed.accelerator.profiler.ProfilerType;
import uk.ac.ed.datastructures.common.PArray;
import uk.ac.ed.datastructures.common.PArray.StorageMode;
import uk.ac.ed.datastructures.common.RuntimeObjectTypeInfo;
import uk.ac.ed.datastructures.common.TypeFactory;
import uk.ac.ed.datastructures.interop.InteropTable;
import uk.ac.ed.datastructures.tuples.Tuple;
import uk.ac.ed.datastructures.tuples.Tuple10;
import uk.ac.ed.datastructures.tuples.Tuple11;
import uk.ac.ed.datastructures.tuples.Tuple2;
import uk.ac.ed.datastructures.tuples.Tuple3;
import uk.ac.ed.datastructures.tuples.Tuple4;
import uk.ac.ed.datastructures.tuples.Tuple5;
import uk.ac.ed.datastructures.tuples.Tuple6;
import uk.ac.ed.datastructures.tuples.Tuple7;
import uk.ac.ed.datastructures.tuples.Tuple8;
import uk.ac.ed.datastructures.tuples.Tuple9;
import uk.ac.ed.marawacc.graal.CompilerUtils;

import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.library.gpu.exceptions.MarawaccRuntimeTypeException;
import com.oracle.truffle.r.library.gpu.exceptions.MarawaccTypeException;
import com.oracle.truffle.r.library.gpu.options.ASTxOptions;
import com.oracle.truffle.r.library.gpu.phases.GPUBoxingEliminationPhase;
import com.oracle.truffle.r.library.gpu.phases.GPUCheckCastRemovalPhase;
import com.oracle.truffle.r.library.gpu.phases.GPUFixedGuardRemovalPhase;
import com.oracle.truffle.r.library.gpu.phases.GPUFrameStateEliminationPhase;
import com.oracle.truffle.r.library.gpu.phases.GPUInstanceOfRemovePhase;
import com.oracle.truffle.r.library.gpu.phases.scope.CleanFixedGuardNodes;
import com.oracle.truffle.r.library.gpu.phases.scope.ScopeArraysDetectionPhase;
import com.oracle.truffle.r.library.gpu.phases.scope.ScopeArraysWithDeopt;
import com.oracle.truffle.r.library.gpu.phases.scope.ScopeData;
import com.oracle.truffle.r.library.gpu.phases.scope.ScopeDetectionPhase;
import com.oracle.truffle.r.library.gpu.scope.ASTLexicalScoping;
import com.oracle.truffle.r.library.gpu.scope.ASTxPrinter;
import com.oracle.truffle.r.library.gpu.types.TypeInfo;
import com.oracle.truffle.r.library.gpu.types.TypeInfoList;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RArguments;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.context.Engine.ParseException;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleSequence;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RIntSequence;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.RSequence.TypeOfSequence;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.RVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

/**
 * ASTx utility class. Methods for {@link RFunction} inspection, marshal and un-marshal.
 *
 */
public class ASTxUtils {

    private static final String regExpForArguments = "^function[ ]*\\(([a-zA-Z]+([, ]*[a-zA-Z0-9]+)*)\\)";
    private static final Pattern pattern = Pattern.compile(regExpForArguments);

    private static final String signature = "^function[ ]*.*\\)";
    private static final Pattern patternSignature = Pattern.compile(signature);

    /**
     * It returns the number of arguments for an {@link RFunction}.
     *
     * @param function
     * @return int
     */
    public static int getNumberOfArguments(RFunction function) {
        // If function is builtin
        if (function.getRBuiltin() != null) {
            return function.getRBuiltin().getSignature().getLength();
        }

        String sourceCode = function.getTarget().getRootNode().getSourceSection().getCode();
        Matcher matcher = pattern.matcher(sourceCode);
        int nArgs = 1;
        if (matcher.find()) {
            String[] args = matcher.group(1).split(",");
            nArgs = args.length;
        }
        return nArgs;
    }

    /**
     * It returns the name of the arguments for an {@link RFunction}.
     *
     * @param function
     * @return String[]
     */
    public static String[] getArgumentsNames(RFunction function) {

        String[] args = null;

        // If function is builtin
        if (function.getRBuiltin() != null) {
            int numArgs = function.getRBuiltin().getSignature().getLength();
            args = new String[numArgs];
            for (int i = 0; i < numArgs; i++) {
                args[i] = function.getRBuiltin().getSignature().getName(i);
            }
            return args;
        }

        String sourceCode = function.getRootNode().getSourceSection().getCode();
        Matcher matcher = pattern.matcher(sourceCode);

        if (matcher.find()) {
            args = matcher.group(1).replace(" ", "").split(",");
        }
        return args;
    }

    public static String rewriteFunction(RFunction function, String[] additionalArguments) {
        String newSourceCode = null;
        String sourceCode = function.getRootNode().getSourceSection().getCode();
        Matcher matcher = pattern.matcher(sourceCode);

        String argsAdd = "";
        for (String s : additionalArguments) {
            argsAdd += ", " + s;
        }

        if (matcher.find()) {
            String group = matcher.group(1);
            String newGroup = "function(" + group + argsAdd + ")";
            Matcher m = patternSignature.matcher(sourceCode);
            if (m.find()) {
                newSourceCode = sourceCode.replace(m.group(0), newGroup);
            }
        }
        return newSourceCode;
    }

    /**
     * It builds the {@link RArguments} for the function calling.
     *
     * @param nArgs
     * @param function
     * @param input
     * @param args
     * @param nameArgs
     * @return Object[]
     */
    public static Object[] createRArguments(int nArgs, RFunction function, RAbstractVector input, RAbstractVector[] args, String[] nameArgs) {
        // prepare args for the function with varargs
        Object[] argsRFunction = new Object[nArgs];
        argsRFunction[0] = input.getDataAtAsObject(0);

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                argsRFunction[i + 1] = args[i].getDataAtAsObject(0);
            }
        }

        // Create the package
        Object[] argsPackage = RArguments.create(function, null, null, 0, argsRFunction, ArgumentsSignature.get(nameArgs), null);
        return argsPackage;
    }

    public static Object[] createRArguments(int nArgs, RFunction function, RAbstractVector input, RAbstractVector[] args, String[] nameArgs, int idx) {
        // prepare args for the function with varargs
        Object[] argsRFunction = new Object[nArgs];
        argsRFunction[0] = input.getDataAtAsObject(idx);
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                argsRFunction[i + 1] = args[i].getDataAtAsObject(idx);
            }
        }
        // Create the package
        Object[] argsPackage = RArguments.create(function, null, null, 0, argsRFunction, ArgumentsSignature.get(nameArgs), null);
        return argsPackage;
    }

    public static Object[] createRArguments(int nArgs, RFunction function, RAbstractVector input, RAbstractVector[] args, String[] nameArgs, int idx, RVector[] lexicalScopes) {
        // prepare args for the function with varargs
        Object[] argsRFunction = new Object[nArgs];

        argsRFunction[0] = input.getDataAtAsObject(idx);
        int last = 0;
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                argsRFunction[i + 1] = args[i].getDataAtAsObject(idx);
                last = i;
            }
        }

        for (int j = 0; j < lexicalScopes.length; j++) {
            argsRFunction[last + 1] = lexicalScopes[j];
            last++;
        }

        // Create the package
        Object[] argsPackage = RArguments.create(function, null, null, 0, argsRFunction, ArgumentsSignature.get(nameArgs), null);
        return argsPackage;
    }

    @SuppressWarnings("unchecked")
    public static Object getFromSequence(PArray<?> input, int idx) {
        if (input.getClassObject() == Integer.class) {
            int value = ((PArray<Integer>) input).get(0) + ((PArray<Integer>) input).get(1) * idx;
            return value;
        } else if (input.getClassObject() == Double.class) {
            double value = ((PArray<Double>) input).get(0) + ((PArray<Double>) input).get(1) * idx;
            return value;
        } else {
            throw new RuntimeException("Error, data type not supported yet: " + input.getClassObject());
        }
    }

    @SuppressWarnings("unchecked")
    private static Object getSequence(PArray<?> parray, int idx) {
        if (parray.getClassObject() == Integer.class) {
            return (((PArray<Integer>) parray).get(0) + ((PArray<Integer>) parray).get(1) * idx);
        } else if (parray.getClassObject() == Double.class) {
            return (((PArray<Double>) parray).get(0) + ((PArray<Double>) parray).get(1) * idx);
        } else {
            throw new RuntimeException("Error, data type not supported yet: " + parray.getClassObject());
        }
    }

    /**
     * Prepare {@link RArguments} for the function.
     *
     * @param nArgs
     * @param function
     * @param input
     * @param args
     * @param nameArgs
     * @param idx
     *
     * @return Object[]
     */
    public static Object[] createRArguments(int nArgs, RFunction function, PArray<?> input, PArray<?>[] args, String[] nameArgs, int idx) {
        // prepare args for the function with varargs
        Object[] argsRFunction = new Object[nArgs];

        if (!input.isSequence()) {
            argsRFunction[0] = input.get(idx);
        } else {
            argsRFunction[0] = getSequence(input, idx);
        }

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (!args[i].isSequence()) {
                    argsRFunction[i + 1] = args[i].get(idx);
                } else {
                    argsRFunction[i + 1] = getSequence(args[i], idx);
                }
            }
        }

        Object[] argsPackage = RArguments.create(function, null, null, 0, argsRFunction, ArgumentsSignature.get(nameArgs), null);
        return argsPackage;
    }

    public static Object[] createRArguments(int nArgs, RFunction function, Object input, RAbstractVector[] args, String[] nameArgs, int idx) {
        // prepare args for the function with varargs
        Object[] argsRFunction = new Object[nArgs];
        argsRFunction[0] = input;
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                argsRFunction[i + 1] = args[i].getDataAtAsObject(idx);
            }
        }
        // Create the package
        Object[] argsPackage = RArguments.create(function, null, null, 0, argsRFunction, ArgumentsSignature.get(nameArgs), null);
        return argsPackage;
    }

    public static Object[] createRArgumentsForReduction(int nArgs, int neutral, RFunction function, RAbstractVector input, RAbstractVector[] args, String[] nameArgs, int idx) {
        // prepare args for the function with varargs
        Object[] argsRFunction = new Object[nArgs];
        // First we insert the neutral element
        argsRFunction[0] = neutral;
        argsRFunction[1] = input.getDataAtAsObject(idx);
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                argsRFunction[i + 2] = args[i].getDataAtAsObject(idx);
            }
        }
        // Create the package
        Object[] argsPackage = RArguments.create(function, null, null, 0, argsRFunction, ArgumentsSignature.get(nameArgs), null);
        return argsPackage;
    }

    public static Object[] createRArgumentsForReduction(int nArgs, int neutral, RFunction function, Object input, RAbstractVector[] args, String[] nameArgs, int idx) {
        // prepare args for the function with varargs
        Object[] argsRFunction = new Object[nArgs];
        // First we insert the neutral element
        argsRFunction[0] = neutral;
        argsRFunction[1] = input;
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                argsRFunction[i + 2] = args[i].getDataAtAsObject(idx);
            }
        }
        // Create the package
        Object[] argsPackage = RArguments.create(function, null, null, 0, argsRFunction, ArgumentsSignature.get(nameArgs), null);
        return argsPackage;
    }

    public static Object[] createRArgumentsForReduction(int nArgs, Object neutral, RFunction function, RAbstractVector input, RAbstractVector[] args, String[] nameArgs, int idx) {
        // prepare args for the function with varargs
        Object[] argsRFunction = new Object[nArgs];
        // First we insert the neutral element
        argsRFunction[0] = neutral;
        argsRFunction[1] = input.getDataAtAsObject(idx);

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                argsRFunction[i + 2] = args[i].getDataAtAsObject(idx);
            }
        }
        // Create the package
        Object[] argsPackage = RArguments.create(function, null, null, 0, argsRFunction, ArgumentsSignature.get(nameArgs), null);
        return argsPackage;
    }

    @SuppressWarnings("rawtypes")
    private static Object[] createObjectArrayFromTuple(int nArgs, Object input, int startIndex) {
        Object[] argsRFunction = new Object[nArgs];
        if (!(input instanceof Tuple)) {
            argsRFunction[startIndex] = input;
        } else if (input instanceof Tuple2) {
            argsRFunction[startIndex] = ((Tuple2) input)._1();
            argsRFunction[startIndex + 1] = ((Tuple2) input)._2();
        } else if (input instanceof Tuple3) {
            argsRFunction[startIndex] = ((Tuple3) input)._1();
            argsRFunction[startIndex + 1] = ((Tuple3) input)._2();
            argsRFunction[startIndex + 2] = ((Tuple3) input)._3();
        } else if (input instanceof Tuple4) {
            argsRFunction[startIndex] = ((Tuple4) input)._1();
            argsRFunction[startIndex + 1] = ((Tuple4) input)._2();
            argsRFunction[startIndex + 2] = ((Tuple4) input)._3();
            argsRFunction[startIndex + 3] = ((Tuple4) input)._4();
        } else if (input instanceof Tuple5) {
            argsRFunction[startIndex] = ((Tuple5) input)._1();
            argsRFunction[startIndex + 1] = ((Tuple5) input)._2();
            argsRFunction[startIndex + 2] = ((Tuple5) input)._3();
            argsRFunction[startIndex + 3] = ((Tuple5) input)._4();
            argsRFunction[startIndex + 4] = ((Tuple5) input)._5();
        }
        return argsRFunction;
    }

    /**
     * Prepare args for the function with varargs.
     *
     * @param nArgs
     * @param function
     * @param input
     * @param nameArgs
     * @return Object[]
     */
    public static Object[] createRArguments(int nArgs, RFunction function, Object input, String[] nameArgs) {
        int startIDX = 0;
        Object[] argsRFunction = createObjectArrayFromTuple(nArgs, input, startIDX);
        Object[] argsPackage = RArguments.create(function, null, null, 0, argsRFunction, ArgumentsSignature.get(nameArgs), null);
        return argsPackage;
    }

    public static Object[] createRArguments(int nArgs, RFunction function, Object acc, Object input, String[] nameArgs) {
        int startIndex = 1;
        Object[] argsRFunction = createObjectArrayFromTuple(nArgs, input, startIndex);

        argsRFunction[0] = acc;
        Object[] argsPackage = RArguments.create(function, null, null, 0, argsRFunction, ArgumentsSignature.get(nameArgs), null);
        return argsPackage;
    }

    public static String getSourceCode(RFunction function) {
        String source = null;
        if (function.getRBuiltin() != null) {
            source = function.getRBuiltin().getName();
        } else {
            SourceSection sourceSection = function.getTarget().getRootNode().getSourceSection();
            source = sourceSection.toString();
        }
        return source;
    }

    public static TypeInfo typeInferenceWithPArrays(RAbstractVector input) throws MarawaccTypeException {
        TypeInfo type = null;
        if (input instanceof RIntSequence) {
            type = TypeInfo.RIntSequence;
        } else if (input instanceof RIntVector) {
            type = TypeInfo.RIntVector;
        } else if (input instanceof RDoubleSequence) {
            type = TypeInfo.RDoubleSequence;
        } else if (input instanceof RDoubleVector) {
            type = TypeInfo.RDoubleVector;
        } else if (input instanceof RLogicalVector) {
            type = TypeInfo.BOOLEAN;
        } else {
            throw new MarawaccTypeException("Data type not supported: " + input.getClass());
        }
        return type;
    }

    public static TypeInfo typeInferenceWithPArrays(PArray<?> input) throws MarawaccTypeException {
        TypeInfo type = null;
        if (input.getClassObject() == Integer.class) {
            type = TypeInfo.INT;
        } else if (input.getClassObject() == Double.class) {
            type = TypeInfo.DOUBLE;
        } else if (input.getClassObject() == Boolean.class) {
            type = TypeInfo.BOOLEAN;
        } else {
            throw new MarawaccTypeException("Data type not supported: " + input.getClass());
        }
        return type;
    }

    public static TypeInfo typeInference(RAbstractVector input) throws MarawaccTypeException {
        TypeInfo type = null;
        if (input instanceof RIntSequence) {
            type = TypeInfo.RIntSequence;
        } else if (input instanceof RIntVector) {
            type = TypeInfo.RIntVector;
        } else if (input instanceof RDoubleSequence) {
            type = TypeInfo.RDoubleSequence;
        } else if (input instanceof RDoubleVector) {
            type = TypeInfo.RDoubleVector;
        } else if (input instanceof RLogicalVector) {
            type = TypeInfo.BOOLEAN;
        } else {
            throw new MarawaccTypeException("Data type not supported: " + input.getClass());
        }
        return type;
    }

    public static TypeInfoList typeInference(RAbstractVector input, RAbstractVector[] additionalArgs) throws MarawaccTypeException {
        TypeInfoList list = new TypeInfoList();
        list.add(typeInference(input));
        if (additionalArgs != null) {
            for (int i = 0; i < additionalArgs.length; i++) {
                list.add(typeInference(additionalArgs[i]));
            }
        }
        return list;
    }

    public static TypeInfoList typeInference(RAbstractVector input, RAbstractVector[] additionalArgs, int extraParams) throws MarawaccTypeException {
        TypeInfoList list = new TypeInfoList();
        list.add(typeInference(input));
        if (additionalArgs != null) {
            for (int i = 0; i < (additionalArgs.length - extraParams); i++) {
                list.add(typeInference(additionalArgs[i]));
            }
        }
        return list;
    }

    public static TypeInfoList typeInferenceWithPArray(RAbstractVector input, RAbstractVector[] additionalArgs) throws MarawaccTypeException {
        TypeInfoList list = new TypeInfoList();
        list.add(typeInferenceWithPArrays(input));
        if (additionalArgs != null) {
            for (int i = 0; i < additionalArgs.length; i++) {
                list.add(typeInferenceWithPArrays(additionalArgs[i]));
            }
        }
        return list;
    }

    public static TypeInfoList typeInferenceWithPArray(RAbstractVector input, RAbstractVector[] additionalArgs, int extraParams) throws MarawaccTypeException {
        TypeInfoList list = new TypeInfoList();
        list.add(typeInferenceWithPArrays(input));
        if (additionalArgs != null) {
            for (int i = 0; i < (additionalArgs.length - extraParams); i++) {
                list.add(typeInferenceWithPArrays(additionalArgs[i]));
            }
        }
        return list;
    }

    public static TypeInfoList typeInferenceWithPArray(PArray<?> input, PArray<?>[] additionalArgs) throws MarawaccTypeException {
        TypeInfoList list = new TypeInfoList();
        list.add(typeInferenceWithPArrays(input));
        if (additionalArgs != null) {
            for (int i = 0; i < additionalArgs.length; i++) {
                list.add(typeInferenceWithPArrays(additionalArgs[i]));
            }
        }
        return list;
    }

    public static void printTypeError(Object value) throws MarawaccTypeException {
        System.err.println("Data type not supported: " + value.getClass() + " [ " + __LINE__.print() + "]");
        throw new MarawaccTypeException("Data type not supported: " + value.getClass() + " [ " + __LINE__.print() + "]");
    }

    /**
     * Analysis when the Tuple contains the name="tupleX" attribute
     *
     * @param value
     * @return {@link TypeInfo}
     * @throws MarawaccTypeException
     */
    @SuppressWarnings("unused")
    private static TypeInfo listTupleAnalysis(Object value) throws MarawaccTypeException {
        TypeInfo type = null;
        try {
            RList list = ((RList) value);
            RStringVector names = list.getNames();
            if (names.getDataAt(0).equals("name")) {
                if (list.getDataAt(0).equals("tuple2")) {
                    type = TypeInfo.TUPLE2;
                } else if (list.getDataAt(0).equals("tuple3")) {
                    type = TypeInfo.TUPLE3;
                } else {
                    printTypeError(value);
                }
            }
        } catch (Exception e) {
            type = TypeInfo.LIST;
            printTypeError(value);
        }
        return type;
    }

    private static TypeInfo listTupleOutputAnalysis(Object value) throws MarawaccTypeException {
        TypeInfo type = null;
        try {
            RList list = ((RList) value);
            int length = list.getLength();
            if (length == 2) {
                type = TypeInfo.TUPLE2;
            } else if (length == 3) {
                type = TypeInfo.TUPLE3;
            } else if (length == 4) {
                type = TypeInfo.TUPLE4;
            } else if (length == 5) {
                type = TypeInfo.TUPLE5;
            } else if (length == 6) {
                type = TypeInfo.TUPLE6;
            } else if (length == 7) {
                type = TypeInfo.TUPLE7;
            } else if (length == 8) {
                type = TypeInfo.TUPLE8;
            } else if (length == 9) {
                type = TypeInfo.TUPLE9;
            } else if (length == 10) {
                type = TypeInfo.TUPLE10;
            } else if (length == 11) {
                type = TypeInfo.TUPLE11;
            } else {
                printTypeError(value);
            }
        } catch (Exception e) {
            type = TypeInfo.LIST;
            printTypeError(value);
        }
        return type;
    }

    public static TypeInfo typeInference(Object value) throws MarawaccTypeException {
        TypeInfo type = null;
        if (value instanceof Integer) {
            type = TypeInfo.INT;
        } else if (value instanceof Double) {
            type = TypeInfo.DOUBLE;
        } else if (value instanceof Boolean) {
            type = TypeInfo.BOOLEAN;
        } else if (value instanceof RList) {
            type = listTupleOutputAnalysis(value);
        } else if (value instanceof RDoubleVector) {
            type = TypeInfo.DOUBLE_VECTOR;
        } else if (value instanceof RDoubleSequence) {
            type = TypeInfo.DOUBLE;
        } else if (value instanceof RIntSequence) {
            type = TypeInfo.INT;
        } else {
            printTypeError(value);
        }
        return type;
    }

    /**
     * Un-marshal to {@link RIntVector} from {@link ArrayList} of objects.
     *
     * @param list
     * @return {@link RIntVector}
     */
    public static RIntVector getIntVector(ArrayList<Object> list) {
        int[] array = list.stream().mapToInt(i -> (Integer) i).toArray();
        return RDataFactory.createIntVector(array, false);
    }

    // XXX: Review the semantic of this operation.
    public static RList getRList(ArrayList<Object> list) {
        RList output = RDataFactory.createList(list.toArray());
        return output;
    }

    // XXX: Review the semantic of this operation.
    public static RList getRList(PArray<?> array) {
        RList output = RDataFactory.createList(array.size());
        for (int i = 0; i < array.size(); i++) {
            output.setElement(i, array.get(i));
        }
        return output;
    }

    /**
     * This method is time consuming (un-marshaling data from PArray to Tuple2)
     *
     * @param array
     * @return {@link RList}
     */
    public static RList getRListFromTuple2(PArray<Tuple2<?, ?>> array) {
        RList output = RDataFactory.createList(array.size());
        for (int i = 0; i < array.size(); i++) {
            Object[] contentTuple = new Object[2];
            contentTuple[0] = array.get(i)._1;
            contentTuple[1] = array.get(i)._2;
            RList v = RDataFactory.createList(contentTuple);
            output.setElement(i, v);
        }
        return output;
    }

    /**
     * Method for updating the references when a {@link PArray} is represented as primitive type.
     *
     * @param array
     * @return {@link RList}
     */
    public static RList composeRListFromTuple2(PArray<Tuple2<?, ?>> array) {

        if (!array.isPrimitiveArray(0)) {
            // DEOPT TO THE OLD STRATEGY
            System.out.println("UNMARSHAL OLD");
            return getRListFromTuple2(array);
        }

        long start = System.nanoTime();
        RuntimeObjectTypeInfo runtimeObjectTypeInfo = array.getRuntimeObjectTypeInfo();
        RuntimeObjectTypeInfo[] nestedTypes = runtimeObjectTypeInfo.getNestedTypes();

        int i = 0;
        final int TUPLE_DIM = 2;
        Object[] data = new Object[array.size() * TUPLE_DIM];
        for (RuntimeObjectTypeInfo r : nestedTypes) {
            if (r.getClassObject() == Double.class) {
                double[] asDoubleArray = array.asDoubleArray(i);
                final int j = i;
                IntStream.range(0, array.size()).parallel().forEach(idx -> {
                    data[idx * TUPLE_DIM + j] = asDoubleArray[idx];
                });

            } else if (r.getClassObject() == Integer.class) {
                int[] asIntegerArray = array.asIntegerArray(0);
                final int j = i;
                IntStream.range(0, array.size()).parallel().forEach(idx -> {
                    data[idx * TUPLE_DIM + j] = asIntegerArray[idx];
                });
            } else {
                throw new RuntimeException("Data type not supported yet: " + r.getClassObject());
            }

            i++;
        }

        RList list = RDataFactory.createList(data, new int[]{TUPLE_DIM, array.size()});
        long end = System.nanoTime();

        if (ASTxOptions.profileOpenCL_ASTx) {
            Profiler.getInstance().writeInBuffer(ProfilerType.TRUFFLE_RLIST_CONVERSION, "start", start);
            Profiler.getInstance().writeInBuffer(ProfilerType.TRUFFLE_RLIST_CONVERSION, "end", end);
            Profiler.getInstance().writeInBuffer(ProfilerType.TRUFFLE_RLIST_CONVERSION, "total", (end - start));
        }

        return list;
    }

    public static RList composeRListFromTuple6(PArray<Tuple6<?, ?, ?, ?, ?, ?>> array) {

        if (!array.isPrimitiveArray(0)) {
            // DEOPT TO THE OLD STRATEGY
            return getRListFromTuple6(array);
        }

        final int TUPLE_DIM = 6;

        RuntimeObjectTypeInfo runtimeObjectTypeInfo = array.getRuntimeObjectTypeInfo();
        RuntimeObjectTypeInfo[] nestedTypes = runtimeObjectTypeInfo.getNestedTypes();

        int i = 0;
        Object[] data = new Object[array.size() * TUPLE_DIM];
        for (RuntimeObjectTypeInfo r : nestedTypes) {

            if (r.getClassObject() == Double.class) {
                double[] asDoubleArray = array.asDoubleArray(i);
                final int j = i;
                IntStream.range(0, array.size()).parallel().forEach(idx -> {
                    data[idx * TUPLE_DIM + j] = asDoubleArray[idx];
                });

            } else if (r.getClassObject() == Integer.class) {
                int[] asIntegerArray = array.asIntegerArray(0);
                final int j = i;
                IntStream.range(0, array.size()).parallel().forEach(idx -> {
                    data[idx * TUPLE_DIM + j] = asIntegerArray[idx];
                });

            } else {
                throw new RuntimeException("Data type not supported yet: " + r.getClassObject());
            }

            i++;
        }

        RList list = RDataFactory.createList(data, new int[]{TUPLE_DIM, array.size()});
        return list;
    }

    /**
     * This method is time consuming (unmarshalling data from PArray to Tuple2)
     *
     * @param array
     * @return {@link RList}
     */
    public static RList getRListFromTuple3(PArray<Tuple3<?, ?, ?>> array) {
        RList output = RDataFactory.createList(array.size());
        for (int i = 0; i < array.size(); i++) {
            Object[] contentTuple = new Object[3];
            contentTuple[0] = array.get(i)._1;
            contentTuple[1] = array.get(i)._2;
            contentTuple[2] = array.get(i)._3;
            RList v = RDataFactory.createList(contentTuple);
            output.setElement(i, v);
        }
        return output;
    }

    /**
     * This method is time consuming (unmarshalling data from PArray to Tuple2)
     *
     * @param array
     * @return {@link RList}
     */
    public static RList getRListFromTuple4(PArray<Tuple4<?, ?, ?, ?>> array) {
        RList output = RDataFactory.createList(array.size());
        for (int i = 0; i < array.size(); i++) {
            Object[] contentTuple = new Object[4];
            contentTuple[0] = array.get(i)._1;
            contentTuple[1] = array.get(i)._2;
            contentTuple[2] = array.get(i)._3;
            contentTuple[3] = array.get(i)._4;
            RList v = RDataFactory.createList(contentTuple);
            output.setElement(i, v);
        }
        return output;
    }

    /**
     * This method is time consuming (unmarshalling data from PArray to Tuple2)
     *
     * @param array
     * @return {@link RList}
     */
    public static RList getRListFromTuple5(PArray<Tuple5<?, ?, ?, ?, ?>> array) {
        RList output = RDataFactory.createList(array.size());
        for (int i = 0; i < array.size(); i++) {
            Object[] contentTuple = new Object[5];
            contentTuple[0] = array.get(i)._1;
            contentTuple[1] = array.get(i)._2;
            contentTuple[2] = array.get(i)._3;
            contentTuple[3] = array.get(i)._4;
            contentTuple[4] = array.get(i)._5;
            RList v = RDataFactory.createList(contentTuple);
            output.setElement(i, v);
        }
        return output;
    }

    /**
     * This method is time consuming (unmarshalling data from PArray to Tuple2)
     *
     * @param array
     * @return {@link RList}
     */
    public static RList getRListFromTuple6(PArray<Tuple6<?, ?, ?, ?, ?, ?>> array) {
        RList output = RDataFactory.createList(array.size());
        for (int i = 0; i < array.size(); i++) {
            Object[] contentTuple = new Object[6];
            contentTuple[0] = array.get(i)._1;
            contentTuple[1] = array.get(i)._2;
            contentTuple[2] = array.get(i)._3;
            contentTuple[3] = array.get(i)._4;
            contentTuple[4] = array.get(i)._5;
            contentTuple[5] = array.get(i)._6;
            RList v = RDataFactory.createList(contentTuple);
            output.setElement(i, v);
        }
        return output;
    }

    /**
     * Un-marshal to {@link RDoubleVector} from {@link ArrayList} of objects.
     *
     * @param list
     * @return {@link RDoubleVector}
     */
    public static RDoubleVector getDoubleVector(ArrayList<Object> list) {
        double[] array = list.stream().mapToDouble(i -> (Double) i).toArray();
        return RDataFactory.createDoubleVector(array, false);
    }

    /**
     * Un-marshal to {@link RIntVector} from {@link PArray} of Integers.
     *
     * @param array
     * @return {@link RIntVector}
     */
    public static RIntVector getIntVector(PArray<Integer> array) {
        return RDataFactory.createIntVector(array.asIntegerArray(), false);
    }

    /**
     * Un-marshal to {@link RIntVector} from {@link PArray} of Integers.
     *
     * @param array
     * @return {@link RIntVector}
     */
    public static RIntVector getIntVectorFromPArray(PArray<Integer> array) {
        return RDataFactory.createIntVector(array.asIntegerArray(), false);
    }

    /**
     * Un-marshal to {@link RDoubleVector} from {@link PArray} of Doubles.
     *
     * @param array
     * @return {@link RDoubleVector}
     */
    public static RDoubleVector getDoubleVector(PArray<Double> array) {
        return RDataFactory.createDoubleVector(array.asDoubleArray(), false);
    }

    /**
     * Un-marshal to {@link RDoubleVector} from {@link PArray} of Doubles.
     *
     * @param array
     * @return {@link RDoubleVector}
     */
    public static RDoubleVector getDoubleVectorFromPArray(PArray<Double> array) {
        double[] asDoubleArray = array.asDoubleArray();
        RDoubleVector createDoubleVector = RDataFactory.createDoubleVector(asDoubleArray, false);
        return createDoubleVector;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static RAbstractVector unMarshallFromFullPArrays(TypeInfo type, PArray result) {
        if (type == TypeInfo.INT) {
            return getIntVectorFromPArray(result);
        } else if (type == TypeInfo.DOUBLE) {
            return getDoubleVectorFromPArray(result);
        } else if (type == TypeInfo.TUPLE2) {
            return getRListFromTuple2(result);
        } else if (type == TypeInfo.TUPLE3) {
            return getRListFromTuple3(result);
        } else if (type == TypeInfo.TUPLE4) {
            return getRListFromTuple4(result);
        } else if (type == TypeInfo.TUPLE5) {
            return getRListFromTuple5(result);
        } else if (type == TypeInfo.TUPLE6) {
            return getRListFromTuple6(result);
        } else {
            throw new MarawaccRuntimeTypeException("Data type not supported yet " + result.get(0).getClass() + " [ " + __LINE__.print() + "]");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static RAbstractVector unMarshallResultFromPArrays(TypeInfo type, PArray result) {
        if (type == TypeInfo.INT) {
            return getIntVector(result);
        } else if (type == TypeInfo.DOUBLE) {
            return getDoubleVector(result);
        } else if (type == TypeInfo.LIST) {
            return getRList(result);
        } else if (type == TypeInfo.TUPLE2) {
            return getRListFromTuple2(result);
        } else if (type == TypeInfo.TUPLE3) {
            return getRListFromTuple3(result);
        } else if (type == TypeInfo.TUPLE4) {
            return getRListFromTuple4(result);
        } else if (type == TypeInfo.TUPLE5) {
            return getRListFromTuple5(result);
        } else if (type == TypeInfo.TUPLE6) {
            return getRListFromTuple6(result);
        } else {
            throw new MarawaccRuntimeTypeException("Data type not supported yet " + result.get(0).getClass() + " [ " + __LINE__.print() + "]");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static RAbstractVector unmarshalFromPrimitivePArrays(TypeInfo type, PArray result) {
        if (type == TypeInfo.INT) {
            return getIntVector(result);
        } else if (type == TypeInfo.DOUBLE) {
            return getDoubleVector(result);
        } else if (type == TypeInfo.TUPLE2) {
            return composeRListFromTuple2(result);
        } else if (type == TypeInfo.TUPLE6) {
            return composeRListFromTuple6(result);
        } else {
            throw new MarawaccRuntimeTypeException("Data type not supported yet " + result.get(0).getClass() + " [ " + __LINE__.print() + "]");
        }
    }

    public static RAbstractVector unMarshallResultFromArrayList(TypeInfo type, ArrayList<Object> result) {
        if (type == TypeInfo.INT) {
            return getIntVector(result);
        } else if (type == TypeInfo.DOUBLE) {
            return getDoubleVector(result);
        } else if (type == TypeInfo.LIST) {
            return getRList(result);
        } else if (type == TypeInfo.TUPLE2) {
            return getRList(result);
        } else if (type == TypeInfo.TUPLE3) {
            return getRList(result);
        } else if (type == TypeInfo.TUPLE4) {
            return getRList(result);
        } else if (type == TypeInfo.TUPLE5) {
            return getRList(result);
        } else if (type == TypeInfo.TUPLE6) {
            return getRList(result);
        } else {
            throw new MarawaccRuntimeTypeException("Data type not supported yet " + result.get(0).getClass() + " [ " + __LINE__.print() + "]");
        }
    }

    public static void printPArray(PArray<?> array) {
        System.out.println(array);
    }

    public static int getValueForRepetitionSequence(RIntSequence input) {
        int value = 0;
        if (input.getType() != null) {
            if (input.getType() == TypeOfSequence.Flag) {
                value = input.getRepetitions();
            } else if (input.getType() == TypeOfSequence.Compass) {
                value = input.getMax();
            }
        }
        return value;
    }

    public static PArray<?> buildIntPArrayForSequence(RIntSequence input) {
        // Build the PArray with the meta-data needed for the sequence computation
        PArray<Integer> parray = new PArray<>(3, TypeFactory.Integer(), StorageMode.OPENCL_BYTE_BUFFER);

        int start = input.start();
        int stride = input.stride();
        parray.setSequence(true);
        parray.setTotalSize(input.getLength());
        parray.put(0, start);
        parray.put(1, stride);

        if (ASTxOptions.useTypeOfSequences) {
            // Set the type of optimize sequence
            if (input.getType() != TypeOfSequence.Basic) {
                int value = getValueForRepetitionSequence(input);
                parray.put(2, value);

                // Set type of sequence. It could be: < compass | flag >
                if (input.getType() == TypeOfSequence.Compass) {
                    parray.setCompass(true);    // e.g.: 1 2 3 4 1 2 ...
                } else {
                    parray.setFlag(true);       // e.g.: 1 1 1 2 2 2 ...
                }

            } else {
                // Set 0 in the position 2
                parray.put(2, 0);
            }
        }
        return parray;
    }

    public static PArray<?> buildIntPArrayForSequencePrimitve(RAbstractVector input) {
        PArray<Integer> parray = new PArray<>(3, TypeFactory.Integer(), StorageMode.OPENCL_BYTE_BUFFER, false);
        int start = ((RIntSequence) input).start();
        int stride = ((RIntSequence) input).stride();
        parray.setSequence(true);

        int value = getValueForRepetitionSequence((RIntSequence) input);

        int[] array = new int[]{start, stride, value};
        parray.setIntArray(array);
        parray.setTotalSize(input.getLength());
        return parray;
    }

    public static double getValueForRepetitionSequence(RDoubleSequence input) {
        double value = 0;
        if (input.getType() != null) {
            if (input.getType() == TypeOfSequence.Flag) {
                value = input.getRepetitions();
            } else if (input.getType() == TypeOfSequence.Compass) {
                value = input.getMax();
            }
        }
        return value;
    }

    public static PArray<?> buildDoublePArrayForSequence(RDoubleSequence input) {
        PArray<Double> parray = new PArray<>(3, TypeFactory.Double(), StorageMode.OPENCL_BYTE_BUFFER);
        double start = input.start();
        double stride = input.stride();
        parray.put(0, start);
        parray.put(1, stride);
        parray.setTotalSize(input.getLength());
        parray.setSequence(true);

        if (ASTxOptions.useTypeOfSequences) {
            // Set the type of optimize sequence
            if (input.getType() != TypeOfSequence.Basic) {
                double value = getValueForRepetitionSequence(input);
                parray.put(2, value);

                // Set type of sequence. It could be: < compass | flag >
                if (input.getType() == TypeOfSequence.Compass) {
                    parray.setCompass(true);    // e.g.: 1 2 3 4 1 2 ...
                } else {
                    parray.setFlag(true);       // e.g.: 1 1 1 2 2 2 ...
                }
            } else {
                // Set 0 in the position 2
                parray.put(2, 0.0);
            }
        }

        return parray;
    }

    public static PArray<?> getReferencePArrayWithOptimizationsSequence(TypeInfo type, RAbstractVector input) {
        switch (type) {
            case RIntSequence:
                return buildIntPArrayForSequence((RIntSequence) input);
            case RDoubleSequence:
                return buildDoublePArrayForSequence((RDoubleSequence) input);
            case RIntVector:
                PArray<Integer> parrayI = new PArray<>(input.getLength(), TypeFactory.Integer(), StorageMode.OPENCL_BYTE_BUFFER);
                // Real marshal
                for (int k = 0; k < input.getLength(); k++) {
                    parrayI.put(k, (int) ((RIntVector) input).getDataAtAsObject(k));
                }
                return parrayI;
            case RDoubleVector:
                // return ((RDoubleVector) input).getPArray();
                PArray<Double> parrayD = new PArray<>(input.getLength(), TypeFactory.Double(), StorageMode.OPENCL_BYTE_BUFFER);
                // Real marshal
                for (int k = 0; k < input.getLength(); k++) {
                    parrayD.put(k, (double) ((RDoubleVector) input).getDataAtAsObject(k));
                }
                return parrayD;
            default:
                throw new MarawaccRuntimeTypeException("Data type not supported: " + input.getClass() + " [ " + __LINE__.print() + "]");
        }

    }

    public static PArray<?> getReferencePArray(TypeInfo type, RAbstractVector input) {
        switch (type) {
            case RIntSequence:
                return ((RIntSequence) input).getPArray();
            case RDoubleSequence:
                return ((RDoubleSequence) input).getPArray();
            case RIntVector:
                return ((RIntVector) input).getPArray();
            case RDoubleVector:
                return ((RDoubleVector) input).getPArray();
            default:
                throw new MarawaccRuntimeTypeException("Data type not supported: " + input.getClass() + " [ " + __LINE__.print() + "]");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static PArray<?> marshalSimplePArrays(TypeInfo type, RAbstractVector input) {
        PArray parray = null;
        switch (type) {
            case INT:
            case RIntSequence:
            case RIntVector:
                parray = new PArray<>(input.getLength(), TypeFactory.Integer(), StorageMode.OPENCL_BYTE_BUFFER);
                break;
            case DOUBLE:
            case RDoubleSequence:
            case RDoubleVector:
                parray = new PArray<>(input.getLength(), TypeFactory.Double(), StorageMode.OPENCL_BYTE_BUFFER);
                break;
            case BOOLEAN:
                parray = new PArray<>(input.getLength(), TypeFactory.Boolean(), StorageMode.OPENCL_BYTE_BUFFER);
                break;
            default:
                throw new MarawaccRuntimeTypeException("Data type not supported: " + input.getClass() + " [ " + __LINE__.print() + "]");
        }

        // Real marshal
        for (int k = 0; k < parray.size(); k++) {
            parray.put(k, input.getDataAtAsObject(k));
        }
        return parray;
    }

    private static int[] materializeIntSequence(RIntSequence sequence) {
        final int start = sequence.start();
        final int stride = sequence.stride();
        int[] array = new int[sequence.getLength()];
        IntStream.range(0, array.length).parallel().forEach(i -> array[i] = start + stride * i);
        return array;
    }

    private static double[] materializeDoubleSequence(RDoubleSequence sequence) {
        final double start = sequence.start();
        final double stride = sequence.stride();
        double[] array = new double[sequence.getLength()];
        IntStream.range(0, array.length).parallel().forEach(i -> array[i] = start + stride * i);
        return array;
    }

    @SuppressWarnings({"rawtypes"})
    public static PArray<?> primitivePArraySimple(TypeInfo type, RAbstractVector input) {
        PArray parray = null;
        switch (type) {
            case RIntSequence:
                if (ASTxOptions.optimizeRSequence) {
                    parray = buildIntPArrayForSequence((RIntSequence) input);
                    // Guarantee the new parray primitive branch in marawacc
                    GraalAcceleratorOptions.newPArraysPrimitive = true;
                } else {
                    int[] array = materializeIntSequence((RIntSequence) input);
                    parray = new PArray<>(input.getLength(), TypeFactory.Integer(), StorageMode.OPENCL_BYTE_BUFFER, false);
                    parray.setIntArray(array);
                }
                break;
            case RIntVector:
                int[] dataInt = ((RIntVector) input).getDataWithoutCopying();
                parray = new PArray<>(input.getLength(), TypeFactory.Integer(), StorageMode.OPENCL_BYTE_BUFFER, false);
                parray.setIntArray(dataInt);
                break;
            case RDoubleSequence:
                if (ASTxOptions.optimizeRSequence) {
                    parray = buildDoublePArrayForSequence((RDoubleSequence) input);
                    GraalAcceleratorOptions.newPArraysPrimitive = true;
                } else {
                    double[] array = materializeDoubleSequence((RDoubleSequence) input);
                    parray = new PArray<>(input.getLength(), TypeFactory.Integer(), StorageMode.OPENCL_BYTE_BUFFER, false);
                    parray.setDoubleArray(array);
                }
                break;
            case RDoubleVector:
                double[] dataDouble = ((RDoubleVector) input).getDataWithoutCopying();
                parray = new PArray<>(input.getLength(), TypeFactory.Double(), StorageMode.OPENCL_BYTE_BUFFER, false);
                parray.setDoubleArray(dataDouble);
                break;
            default:
                throw new MarawaccRuntimeTypeException("Data type not supported: " + input.getClass() + " [ " + __LINE__.print() + "]");
        }
        return parray;
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

    /**
     * Ideally the R data structure will contain the parray itself (so it willbe prepared and this
     * is just to return the buffer).
     *
     * @param input
     * @param additionalArgs
     * @param infoList
     * @return {@link PArray}
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static PArray<?> marshalWithTuples(RAbstractVector input, RAbstractVector[] additionalArgs, TypeInfoList infoList) {
        String returns = composeReturnType(infoList);
        PArray parray = new PArray<>(input.getLength(), TypeFactory.Tuple(returns), StorageMode.OPENCL_BYTE_BUFFER);
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
            case 5:
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, new Tuple5<>(input.getDataAtAsObject(k), additionalArgs[0].getDataAtAsObject(k), additionalArgs[1].getDataAtAsObject(k), additionalArgs[2].getDataAtAsObject(k),
                                    additionalArgs[3].getDataAtAsObject(k)));
                }
                return parray;
            case 6:
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, new Tuple6<>(input.getDataAtAsObject(k), additionalArgs[0].getDataAtAsObject(k), additionalArgs[1].getDataAtAsObject(k), additionalArgs[2].getDataAtAsObject(k),
                                    additionalArgs[3].getDataAtAsObject(k), additionalArgs[4].getDataAtAsObject(k)));
                }
                return parray;

            case 7:
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, new Tuple7<>(input.getDataAtAsObject(k), additionalArgs[0].getDataAtAsObject(k), additionalArgs[1].getDataAtAsObject(k), additionalArgs[2].getDataAtAsObject(k),
                                    additionalArgs[3].getDataAtAsObject(k), additionalArgs[4].getDataAtAsObject(k), additionalArgs[5].getDataAtAsObject(k)));
                }
                return parray;

            case 8:
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, new Tuple8<>(input.getDataAtAsObject(k), additionalArgs[0].getDataAtAsObject(k), additionalArgs[1].getDataAtAsObject(k), additionalArgs[2].getDataAtAsObject(k),
                                    additionalArgs[3].getDataAtAsObject(k), additionalArgs[4].getDataAtAsObject(k), additionalArgs[5].getDataAtAsObject(k), additionalArgs[6].getDataAtAsObject(k)));
                }
                return parray;

            case 9:
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, new Tuple9<>(input.getDataAtAsObject(k), additionalArgs[0].getDataAtAsObject(k), additionalArgs[1].getDataAtAsObject(k), additionalArgs[2].getDataAtAsObject(k),
                                    additionalArgs[3].getDataAtAsObject(k), additionalArgs[4].getDataAtAsObject(k), additionalArgs[5].getDataAtAsObject(k), additionalArgs[6].getDataAtAsObject(k),
                                    additionalArgs[7].getDataAtAsObject(k)));
                }
                return parray;

            case 10:
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, new Tuple10<>(input.getDataAtAsObject(k), additionalArgs[0].getDataAtAsObject(k), additionalArgs[1].getDataAtAsObject(k), additionalArgs[2].getDataAtAsObject(k),
                                    additionalArgs[3].getDataAtAsObject(k), additionalArgs[4].getDataAtAsObject(k), additionalArgs[5].getDataAtAsObject(k), additionalArgs[6].getDataAtAsObject(k),
                                    additionalArgs[7].getDataAtAsObject(k), additionalArgs[8].getDataAtAsObject(k)));
                }
                return parray;

            case 11:
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, new Tuple11<>(input.getDataAtAsObject(k), additionalArgs[0].getDataAtAsObject(k), additionalArgs[1].getDataAtAsObject(k), additionalArgs[2].getDataAtAsObject(k),
                                    additionalArgs[3].getDataAtAsObject(k), additionalArgs[4].getDataAtAsObject(k), additionalArgs[5].getDataAtAsObject(k), additionalArgs[6].getDataAtAsObject(k),
                                    additionalArgs[7].getDataAtAsObject(k), additionalArgs[8].getDataAtAsObject(k), additionalArgs[9].getDataAtAsObject(k)));
                }
                return parray;

            default:
                throw new MarawaccRuntimeTypeException("Tuple not supported yet: " + infoList.size() + " [ " + __LINE__.print() + "]");
        }
    }

    @SuppressWarnings("rawtypes")
    private static void insertCorrectArray(TypeInfo typeInfo, PArray parray, RAbstractVector input, int idx) {
        if (typeInfo == TypeInfo.RIntVector) {
            parray.setIntArray(idx, ((RIntVector) input).getDataWithoutCopying());
        } else if (typeInfo == TypeInfo.RIntSequence) {
            if (!ASTxOptions.optimizeRSequence) {
                int[] array = materializeIntSequence((RIntSequence) input);
                parray.setIntArray(idx, array);
            } else {
                // 1. Build parray intermediate with sequence
                // 2. Store totalSize
                // 3. Update the reference to the parray instead of the setIntArray with
                // sequence enable

                PArray parraySequence = buildIntPArrayForSequence((RIntSequence) input);
                boolean sequence = true;
                parray.setTotalSize(input.getLength());

                if (parraySequence.isCompass()) {
                    parray.setBufferCompassSequence(idx, parraySequence.getArrayReference(), sequence);
                } else if (parraySequence.isFlag()) {
                    parray.setBufferFlagSequence(idx, parraySequence.getArrayReference(), sequence);
                } else {
                    parray.setBuffer(idx, parraySequence.getArrayReference(), sequence);
                }

                // Guarantee the new parray primitive branch in marawacc
                GraalAcceleratorOptions.newPArraysPrimitive = true;

            }
        } else if (typeInfo == TypeInfo.RDoubleVector) {
            parray.setDoubleArray(idx, ((RDoubleVector) input).getDataWithoutCopying());
        } else if (typeInfo == TypeInfo.RDoubleSequence) {
            if (!ASTxOptions.optimizeRSequence) {
                double[] array = materializeDoubleSequence((RDoubleSequence) input);
                parray.setDoubleArray(idx, array);
            } else {
                GraalAcceleratorOptions.newPArraysPrimitive = true;
                PArray parraySequence = buildDoublePArrayForSequence((RDoubleSequence) input);
                parray.setTotalSize(input.getLength());
                parray.setBuffer(idx, parraySequence.getArrayReference(), true);
            }
        } else {
            throw new MarawaccRuntimeTypeException("Tuple not supported yet: " + typeInfo + " [ " + __LINE__.print() + "]");
        }
    }

    @SuppressWarnings({"rawtypes"})
    public static PArray<?> primitivePArraysWithTuples(RAbstractVector input, RAbstractVector[] additionalArgs, TypeInfoList infoList) {
        String returns = composeReturnType(infoList);

        // PArray with no buffer allocation
        PArray parray = new PArray<>(input.getLength(), TypeFactory.Tuple(returns), StorageMode.OPENCL_BYTE_BUFFER, false);
        if (infoList.size() == 2) {
            insertCorrectArray(infoList.get(0), parray, input, 0);
            insertCorrectArray(infoList.get(1), parray, additionalArgs[0], 1);
            return parray;
        } else if (infoList.size() > 2) {
            int max = infoList.size() - 1;
            insertCorrectArray(infoList.get(0), parray, input, 0);
            for (int i = 0; i < max; i++) {
                insertCorrectArray(infoList.get(i + 1), parray, additionalArgs[i], i + 1);
            }
            return parray;
        } else {
            throw new MarawaccRuntimeTypeException("Tuple not supported yet: " + infoList.size() + " [ " + __LINE__.print() + "]");
        }
    }

    /**
     * Ideally the R data structure will contain the parray itself (so it will be prepared and this
     * is just to return the buffer).
     *
     * @param input
     * @param additionalArgs
     * @param infoList
     * @return {@link PArray}
     */
    @SuppressWarnings({"rawtypes"})
    public static PArray<?> marshalUpdateReferenceWithTuples(RAbstractVector input, RAbstractVector[] additionalArgs, TypeInfoList infoList) {
        String returns = composeReturnType(infoList);
        PArray parray = new PArray<>(input.getLength(), TypeFactory.Tuple(returns), false);
        boolean sequence = true;

        switch (infoList.size()) {
            case 2:
                PArray a = null;
                if (input instanceof RIntSequence) {
                    a = buildIntPArrayForSequence((RIntSequence) input);
                } else if (input instanceof RDoubleSequence) {
                    a = buildDoublePArrayForSequence((RDoubleSequence) input);
                } else {
                    a = input.getPArray();
                    sequence = false;
                }

                PArray b = null;
                if (additionalArgs[0] instanceof RIntSequence) {
                    b = buildIntPArrayForSequence((RIntSequence) additionalArgs[0]);
                } else if (additionalArgs[0] instanceof RDoubleSequence) {
                    b = buildDoublePArrayForSequence((RDoubleSequence) additionalArgs[0]);
                } else {
                    b = input.getPArray();
                    sequence = false;
                }

                parray.setBuffer(0, a.getArrayReference(), a.isSequence());
                parray.setBuffer(1, b.getArrayReference(), b.isSequence());

                parray.setSequence(sequence);
                parray.setTotalSize(input.getLength());

                return parray;
            default:
                throw new MarawaccRuntimeTypeException("Tuple not supported yet: " + infoList.size() + " [ " + __LINE__.print() + "]");
        }
    }

    public static boolean isPArraySequence(PArray<?> array) {
        return array.isSequence();
    }

    public static boolean isFullPArraySequence(PArray<?> array, PArray<?>[] additionalArgs) {
        boolean isPArray = false;
        isPArray |= isPArraySequence(array);
        for (PArray<?> p : additionalArgs) {
            isPArray |= isPArraySequence(p);
        }
        return isPArray;
    }

    public static PArray<?> marshalUpdateReferenceWithTuples(PArray<?> input, PArray<?>[] additionalArgs, TypeInfoList infoList, int totalSize) {
        String returns = composeReturnType(infoList);
        PArray<?> parray = new PArray<>(totalSize, TypeFactory.Tuple(returns), false);
        boolean sequence = isFullPArraySequence(input, additionalArgs);
        PArray<?> b = additionalArgs[0];

        // I support the cases we need for the experiments. It is just a matter of time to complete
        // it and also test cases.
        switch (infoList.size()) {
            case 2:
                parray.setBuffer(0, input.getArrayReference(), input.isSequence());
                parray.setBuffer(1, b.getArrayReference(), b.isSequence());
                parray.setSequence(sequence);
                parray.setTotalSize(totalSize);
                return parray;
            case 6:
                PArray<?> c = additionalArgs[1];
                PArray<?> d = additionalArgs[2];
                PArray<?> e = additionalArgs[3];
                PArray<?> f = additionalArgs[4];
                parray.setBuffer(0, input.getArrayReference(), input.isSequence());
                parray.setBuffer(1, b.getArrayReference(), b.isSequence());
                parray.setBuffer(2, c.getArrayReference(), c.isSequence());
                parray.setBuffer(3, d.getArrayReference(), d.isSequence());
                parray.setBuffer(4, e.getArrayReference(), e.isSequence());
                parray.setBuffer(5, f.getArrayReference(), f.isSequence());
                parray.setSequence(sequence);
                parray.setTotalSize(totalSize);
                return parray;
            default:
                throw new MarawaccRuntimeTypeException("Tuple not supported yet: " + infoList.size() + " [ " + __LINE__.print() + "]");
        }
    }

    /**
     * Given the RVector, it creates the PArray. For future work is to extend the R data types to
     * include in the object layout the PArray information.
     *
     * @param input
     * @param additionalArgs
     * @param infoList
     * @return {@link PArray}
     */
    @SuppressWarnings("rawtypes")
    public static PArray<?> marshal(RAbstractVector input, RAbstractVector[] additionalArgs, TypeInfoList infoList) {
        PArray parray = null;
        if (additionalArgs == null || infoList.size() == 1) {
            parray = marshalSimplePArrays(infoList.get(0), input);
        } else {
            parray = marshalWithTuples(input, additionalArgs, infoList);
        }
        return parray;
    }

    @SuppressWarnings("rawtypes")
    public static PArray<?> createPArrayForPrimitives(RAbstractVector input, RAbstractVector[] additionalArgs, TypeInfoList infoList) {
        PArray parray = null;
        if (additionalArgs == null || infoList.size() == 1) {
            parray = primitivePArraySimple(infoList.get(0), input);
        } else {
            parray = primitivePArraysWithTuples(input, additionalArgs, infoList);
        }
        return parray;
    }

    /**
     * Given the RVector, it creates the PArray. For future work is to extend the R data types to
     * include in the object layout the PArray information.
     *
     * @param input
     * @param additionalArgs
     * @param infoList
     * @return {@link PArray}
     */
    @SuppressWarnings("rawtypes")
    public static PArray<?> marshalWithReferences(RAbstractVector input, RAbstractVector[] additionalArgs, TypeInfoList infoList) {
        PArray parray = null;
        if (additionalArgs == null && infoList.size() == 1) {
            parray = getReferencePArray(infoList.get(0), input);
        } else {
            parray = marshalUpdateReferenceWithTuples(input, additionalArgs, infoList);
        }
        return parray;
    }

    public static PArray<?> marshalWithReferences(PArray<?> input, PArray<?>[] additionalArgs, TypeInfoList infoList, int totalSize) {
        if (additionalArgs == null) {
            return input;
        } else {
            return marshalUpdateReferenceWithTuples(input, additionalArgs, infoList, totalSize);
        }
    }

    /**
     * Given the RVector, it creates the PArray. For future work is to extend the R data types to
     * include in the object layout the PArray information.
     *
     * @param input
     * @param additionalArgs
     * @param infoList
     * @return {@link PArray}
     */
    @SuppressWarnings("rawtypes")
    public static PArray<?> marshalWithReferencesAndSequenceOptimize(RAbstractVector input, RAbstractVector[] additionalArgs, TypeInfoList infoList) {
        PArray parray = null;
        if (additionalArgs == null) {
            parray = getReferencePArrayWithOptimizationsSequence(infoList.get(0), input);
        } else {
            parray = marshalUpdateReferenceWithTuples(input, additionalArgs, infoList);
        }
        return parray;
    }

    public static class ScopeVarInfo {
        private Object[] scopeVars;
        private String[] nameVars;
        private RVector[] vector;

        public ScopeVarInfo(Object[] scopeVars, String[] nameVars, RVector[] vector) {
            super();
            this.scopeVars = scopeVars;
            this.nameVars = nameVars;
            this.vector = vector;
        }

        public Object[] getScopeVars() {
            return scopeVars;
        }

        public String[] getNameVars() {
            return nameVars;
        }

        public RVector[] getVector() {
            return vector;
        }

    }

    /**
     * Given an array of scopeVars and the function, this method evaluates this variables and
     * returns an array with only the arrays.
     *
     * @param scopeVars
     * @param function
     * @return Object[]
     */
    public static ScopeVarInfo getValueOfScopeArrays(String[] scopeVars, RFunction function) {
        LinkedList<Object> scopes = new LinkedList<>();
        LinkedList<String> varNames = new LinkedList<>();
        LinkedList<RVector> rVectors = new LinkedList<>();
        for (String var : scopeVars) {
            StringBuffer scopeVar = new StringBuffer(var);
            Source source = Source.fromText(scopeVar, "<eval>").withMimeType(RRuntime.R_APP_MIME);
            MaterializedFrame frame = function.getEnclosingFrame();
            Object val = null;
            try {
                val = RContext.getEngine().parseAndEval(source, frame, false);
                boolean added = false;
                if (val instanceof RVector) {
                    rVectors.add((RVector) val);
                    if (val instanceof RDoubleVector) {
                        scopes.add(((RDoubleVector) val).getDataCopy());
                        added = true;
                    } else if (val instanceof RIntVector) {
                        scopes.add(((RIntVector) val).getDataCopy());
                        added = true;
                    } else {
                        throw new RuntimeException("Data type not supported yet");
                    }
                }
                if (added) {
                    varNames.add(var);
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        RVector[] vectors = new RVector[rVectors.size()];
        int i = 0;
        for (RVector v : rVectors) {
            vectors[i++] = v;
        }

        if (scopes.isEmpty()) {
            return null;
        }

        ScopeVarInfo scopeVarInfo = new ScopeVarInfo(scopes.toArray(), varNames.stream().toArray(String[]::new), vectors);

        return scopeVarInfo;
    }

    public static void printAST(RFunction function) {
        Node root = function.getTarget().getRootNode();
        ASTxPrinter printAST = new ASTxPrinter();
        RSyntaxNode.accept(root, 0, printAST);
    }

    public static String[] lexicalScopingAST(RFunction function) {
        ASTLexicalScoping lexicalScoping = new ASTLexicalScoping();
        lexicalScoping.apply(function);
        String[] scopeVars = lexicalScoping.scopeVars();
        return scopeVars;
    }

    private static RAbstractVector[] getRExtraArgumentsFromRList(RArgsValuesAndNames args) {
        int total = ((RList) args.getArgument(2)).getLength();
        RAbstractVector[] additionalInputs = new RAbstractVector[total];

        for (int i = 0; i < total; i++) {
            additionalInputs[i] = (RAbstractVector) ((RList) args.getArgument(2)).getDataAt(i);
        }
        return additionalInputs;
    }

    private static RAbstractVector[] getRExtraArgumentsFromRVector(RArgsValuesAndNames args) {
        RAbstractVector[] additionalInputs = new RAbstractVector[args.getLength() - 2];
        for (int i = 0; i < additionalInputs.length; i++) {
            additionalInputs[i] = (RAbstractVector) args.getArgument(i + 2);
        }
        return additionalInputs;
    }

    public static RAbstractVector[] getRArrayWithAdditionalArguments(RArgsValuesAndNames args) {
        if (args.getLength() > 2) {
            if (args.getArgument(2) instanceof RList) {
                return getRExtraArgumentsFromRList(args);
            } else {
                return getRExtraArgumentsFromRVector(args);
            }
        }
        return null;
    }

    public static RAbstractVector[] getAdditionalArguments(RArgsValuesAndNames args, boolean isRewritten, RVector[] vectors, int lenthScope) {
        RAbstractVector[] additionalInputs = ASTxUtils.getRArrayWithAdditionalArguments(args);
        int baseSize = additionalInputs == null ? 0 : additionalInputs.length;
        if (isRewritten) {
            RAbstractVector[] copy = new RAbstractVector[baseSize + lenthScope];
            for (int i = 0; i < baseSize; i++) {
                copy[i] = additionalInputs[i];
            }
            int j = 0;
            for (int i = baseSize; i < (baseSize + lenthScope); i++) {
                copy[i] = vectors[j++];
            }
            additionalInputs = copy;
        }
        return additionalInputs;
    }

    public static PArray<?>[] getPArrayWithAdditionalArguments(RArgsValuesAndNames args) {
        PArray<?>[] additionalInputs = null;
        if (args.getLength() > 2) {
            additionalInputs = new PArray<?>[args.getLength() - 2];
            for (int i = 0; i < additionalInputs.length; i++) {
                additionalInputs[i] = (PArray<?>) args.getArgument(i + 2);
            }
        }
        return additionalInputs;
    }

    public static PArray<?>[] getAdditionalArgumentsPArray(RArgsValuesAndNames args, boolean isRewritten, PArray<?>[] vectors, int lenthScope) {
        PArray<?>[] additionalInputs = ASTxUtils.getPArrayWithAdditionalArguments(args);
        if (isRewritten) {
            PArray<?>[] copy = new PArray<?>[additionalInputs.length + lenthScope];
            for (int i = 0; i < additionalInputs.length; i++) {
                copy[i] = additionalInputs[i];
            }
            int j = 0;
            for (int i = additionalInputs.length; i < additionalInputs.length + lenthScope; i++) {
                copy[i] = vectors[j++];
            }
            additionalInputs = copy;
        }
        return additionalInputs;
    }

    public static TypeInfo obtainTypeInfo(Object value) {
        TypeInfo outputType = null;
        try {
            outputType = ASTxUtils.typeInference(value);
        } catch (MarawaccTypeException e) {
            // TODO: DEOPTIMIZATION
            throw new RuntimeException("Interop data type not supported yet: " + value.getClass());
        }
        return outputType;
    }

    public static InteropTable obtainInterop(TypeInfo outputType) {
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

    public static Class<?>[] createListSubTypes(InteropTable interop, Object value) {
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
    public static Class<?>[] createListSubTypesWithName(InteropTable interop, Object value) {
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

    public static TypeInfoList createTypeInfoListForInput(RAbstractVector input, RAbstractVector[] additionalArgs) {
        TypeInfoList inputTypeList = null;
        try {
            inputTypeList = ASTxUtils.typeInference(input, additionalArgs);
        } catch (MarawaccTypeException e) {
            // TODO: DEOPTIMIZE
            e.printStackTrace();
        }
        return inputTypeList;
    }

    public static TypeInfoList createTypeInfoListForInput(RAbstractVector input, RAbstractVector[] additionalArgs, int extraParams) {
        TypeInfoList inputTypeList = null;
        try {
            inputTypeList = ASTxUtils.typeInference(input, additionalArgs, extraParams);
        } catch (MarawaccTypeException e) {
            // TODO: DEOPTIMIZE
            e.printStackTrace();
        }
        return inputTypeList;
    }

    public static TypeInfoList createTypeInfoListForInputWithPArrays(RAbstractVector input, RAbstractVector[] additionalArgs) {
        TypeInfoList inputTypeList = null;
        try {
            inputTypeList = ASTxUtils.typeInferenceWithPArray(input, additionalArgs);
        } catch (MarawaccTypeException e) {
            // TODO: DEOPTIMIZE
            e.printStackTrace();
        }
        return inputTypeList;
    }

    public static TypeInfoList createTypeInfoListForInputWithPArrays(RAbstractVector input, RAbstractVector[] additionalArgs, int extraParams) {
        TypeInfoList inputTypeList = null;
        try {
            inputTypeList = ASTxUtils.typeInferenceWithPArray(input, additionalArgs, extraParams);
        } catch (MarawaccTypeException e) {
            // TODO: DEOPTIMIZE
            e.printStackTrace();
        }
        return inputTypeList;
    }

    public static TypeInfoList createTypeInfoListForInputWithPArrays(PArray<?> input, PArray<?>[] additionalArgs) {
        TypeInfoList inputTypeList = null;
        try {
            inputTypeList = ASTxUtils.typeInferenceWithPArray(input, additionalArgs);
        } catch (MarawaccTypeException e) {
            // TODO: DEOPTIMIZE
            e.printStackTrace();
        }
        return inputTypeList;
    }

    @SuppressWarnings("deprecation")
    public static PArray<?> createPArrays(RAbstractVector input, RAbstractVector[] additionalArgs, TypeInfoList inputTypeList) {
        PArray<?> inputPArrayFormat = null;
        if (ASTxOptions.usePArrays && ASTxOptions.optimizeRSequence) {
            // Optimise with RSequences data types (openCL logic to compute the data) and no
            // input copy.

            // XXX: This branch is deprecated

            inputPArrayFormat = ASTxUtils.marshalWithReferencesAndSequenceOptimize(input, additionalArgs, inputTypeList);
        } else if (ASTxOptions.usePArrays && !ASTxOptions.optimizeRSequence) {
            // RTypes with PArray information

            // XXX: This branch is deprecated

            inputPArrayFormat = ASTxUtils.marshalWithReferences(input, additionalArgs, inputTypeList);
        } else if (ASTxOptions.usePrimitivePArray) {
            // No marshal, just passing primitive vectors, we completely save the marshal and
            // unmarshal
            inputPArrayFormat = ASTxUtils.createPArrayForPrimitives(input, additionalArgs, inputTypeList);
        } else {
            // real marshal
            inputPArrayFormat = ASTxUtils.marshal(input, additionalArgs, inputTypeList);
        }
        return inputPArrayFormat;
    }

    @SuppressWarnings("rawtypes")
    public static int getSize(PArray input, PArray[] additionalArgs) {
        int totalSize = input.size();
        if (input.isSequence()) {
            totalSize = input.getTotalSizeWhenSequence();
        } else if (additionalArgs != null) {
            for (PArray<?> p : additionalArgs) {
                if (p.isSequence()) {
                    totalSize = p.getTotalSizeWhenSequence();
                    break;
                }
            }
        }
        return totalSize;
    }

    @SuppressWarnings("rawtypes")
    public static PArray<?> createPArrays(PArray input, PArray[] additionalArgs, TypeInfoList inputTypeList) {
        int totalSize = getSize(input, additionalArgs);
        PArray<?> inputPArrayFormat = ASTxUtils.marshalWithReferences(input, additionalArgs, inputTypeList, totalSize);
        return inputPArrayFormat;
    }

    /**
     * It searches for constant array nodes and get its references for copying in into the OpenCL
     * buffers later on.
     *
     * @param graph
     * @return {@link ScopeData}
     */
    public static ScopeData scopeArrayConstantDetection(StructuredGraph graph) {
        ScopeDetectionPhase scopeDetection = new ScopeDetectionPhase();
        scopeDetection.apply(graph);
        ScopeData scopeData = new ScopeData(scopeDetection.getDataArray());
        return scopeData;
    }

    public static ArrayList<com.oracle.graal.graph.Node> applyCompilationPhasesForOpenCLAndDump(StructuredGraph graph) {

        CompilerUtils.dumpGraph(graph, "beforeOptomisations");

        if (ASTxOptions.oldCleanPhases) {
            new GPUFrameStateEliminationPhase().apply(graph);
            CompilerUtils.dumpGraph(graph, "GPUFrameStateEliminationPhase");

            new GPUInstanceOfRemovePhase().apply(graph);
            CompilerUtils.dumpGraph(graph, "GPUInstanceOfRemovePhase");

            new GPUCheckCastRemovalPhase().apply(graph);
            CompilerUtils.dumpGraph(graph, "GPUCheckCastRemovalPhase");

            new GPUFixedGuardRemovalPhase().apply(graph);
            CompilerUtils.dumpGraph(graph, "GPUFixedGuardRemovalPhase");
        }

        new GPUBoxingEliminationPhase().apply(graph);
        CompilerUtils.dumpGraph(graph, "GPUBoxingEliminationPhase");

        // New Experimental scope phase
        ScopeArraysWithDeopt scopeWithDeopt = new ScopeArraysWithDeopt();
        scopeWithDeopt.apply(graph);

        ArrayList<com.oracle.graal.graph.Node> scopedNodes = null;
        if (scopeWithDeopt.isScopeDetected()) {
            CleanFixedGuardNodes clean = new CleanFixedGuardNodes(scopeWithDeopt.getGuardNodes());
            clean.apply(graph);
            CompilerUtils.dumpGraph(graph, "Scope Clean Phase");

            ScopeArraysDetectionPhase arraysDetectionPhase = new ScopeArraysDetectionPhase();
            arraysDetectionPhase.apply(graph);
            if (arraysDetectionPhase.isScopeDetected()) {
                scopedNodes = arraysDetectionPhase.getScopedNodes();
            }
        }
        return scopedNodes;
    }

    public static ArrayList<com.oracle.graal.graph.Node> applyCompilationPhasesForOpenCL(StructuredGraph graph) {
        new GPUFrameStateEliminationPhase().apply(graph);
        if (ASTxOptions.oldCleanPhases) {
            new GPUInstanceOfRemovePhase().apply(graph);
            new GPUCheckCastRemovalPhase().apply(graph);
            new GPUFixedGuardRemovalPhase().apply(graph);
        }
        new GPUBoxingEliminationPhase().apply(graph);

        // New Experimental scope phase
        ScopeArraysWithDeopt scopeWithDeopt = new ScopeArraysWithDeopt();
        scopeWithDeopt.apply(graph);

        ArrayList<com.oracle.graal.graph.Node> scopedNodes = null;
        if (scopeWithDeopt.isScopeDetected()) {
            CleanFixedGuardNodes clean = new CleanFixedGuardNodes(scopeWithDeopt.getGuardNodes());
            clean.apply(graph);

            ScopeArraysDetectionPhase arraysDetectionPhase = new ScopeArraysDetectionPhase();
            arraysDetectionPhase.apply(graph);
            if (arraysDetectionPhase.isScopeDetected()) {
                scopedNodes = arraysDetectionPhase.getScopedNodes();
            }
        }
        return scopedNodes;
    }

    private ASTxUtils() {
        // empty constructor
    }
}
