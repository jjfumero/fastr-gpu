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

import uk.ac.ed.datastructures.common.PArray;
import uk.ac.ed.datastructures.common.TypeFactory;
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

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.library.gpu.exceptions.MarawaccRuntimeTypeException;
import com.oracle.truffle.r.library.gpu.exceptions.MarawaccTypeException;
import com.oracle.truffle.r.library.gpu.types.TypeInfo;
import com.oracle.truffle.r.library.gpu.types.TypeInfoList;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RArguments;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.context.Engine.ParseException;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleSequence;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RIntSequence;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RLogicalVector;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.RVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

/**
 * ASTx utility class. Methods for {@link RFunction} inspection, marshal and un-marshal.
 *
 */
public class ASTxUtils {

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

        String regExpForArguments = "^function[ ]*\\(([a-zA-Z]+([, ]*[a-zA-Z0-9]+)*)\\)";
        Pattern pattern = Pattern.compile(regExpForArguments);
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
        String regExpForArguments = "^function[ ]*\\(([a-zA-Z]+([, ]*[a-zA-Z0-9]+)*)\\)";
        Pattern pattern = Pattern.compile(regExpForArguments);
        Matcher matcher = pattern.matcher(sourceCode);

        if (matcher.find()) {
            args = matcher.group(1).replace(" ", "").split(",");
        }
        return args;
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
            type = TypeInfo.RIntegerSequence;
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

    public static TypeInfo typeInference(RAbstractVector input) throws MarawaccTypeException {
        TypeInfo type = null;
        if (input instanceof RIntSequence) {
            type = TypeInfo.INT;
        } else if (input instanceof RIntVector) {
            type = TypeInfo.INT;
        } else if (input instanceof RDoubleSequence) {
            type = TypeInfo.DOUBLE;
        } else if (input instanceof RDoubleVector) {
            type = TypeInfo.DOUBLE;
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
     * This method is time consuming (unmarshalling data from PArray to Tuple2)
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
        return RDataFactory.createDoubleVector(array.asDoubleArray(), false);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static RAbstractVector unMarshallFromFullPArrays(TypeInfo type, PArray result) {
        if (type == TypeInfo.INT) {
            return getIntVectorFromPArray(result);
        } else if (type == TypeInfo.DOUBLE) {
            return getDoubleVectorFromPArray(result);
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

    public static void printPArray(PArray<?> result) {
        System.out.println(result);
    }

    public static PArray<?> buildIntPArrayForSequence(RAbstractVector input) {
        PArray<Integer> parray = new PArray<>(2, TypeFactory.Integer());
        int start = ((RIntSequence) input).start();
        int stride = ((RIntSequence) input).stride();
        parray.setSequence(true);
        parray.setTotalSize(input.getLength());
        parray.put(0, start);
        parray.put(1, stride);
        return parray;
    }

    public static PArray<?> buildDoublePArrayForSequence(RAbstractVector input) {
        PArray<Double> parray = new PArray<>(2, TypeFactory.Double());
        double start = ((RDoubleSequence) input).start();
        double stride = ((RDoubleSequence) input).stride();
        parray.put(0, start);
        parray.put(1, stride);
        parray.setTotalSize(input.getLength());
        parray.setSequence(true);
        return parray;
    }

    public static PArray<?> getReferencePArrayWithOptimizationsSequence(TypeInfo type, RAbstractVector input) {
        switch (type) {
            case RIntegerSequence:
                return buildIntPArrayForSequence(input);
            case RDoubleSequence:
                return buildDoublePArrayForSequence(input);
            case RIntVector:
                return ((RIntVector) input).getPArray();
            case RDoubleVector:
                return ((RDoubleVector) input).getPArray();
            default:
                throw new MarawaccRuntimeTypeException("Data type not supported: " + input.getClass() + " [ " + __LINE__.print() + "]");
        }
    }

    public static PArray<?> getReferencePArray(TypeInfo type, RAbstractVector input) {
        switch (type) {
            case RIntegerSequence:
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
            case RIntegerSequence:
            case RIntVector:
                parray = new PArray<>(input.getLength(), TypeFactory.Integer());
                break;
            case DOUBLE:
            case RDoubleSequence:
            case RDoubleVector:
                parray = new PArray<>(input.getLength(), TypeFactory.Double());
                break;
            case BOOLEAN:
                parray = new PArray<>(input.getLength(), TypeFactory.Boolean());
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
        PArray parray = new PArray<>(input.getLength(), TypeFactory.Tuple(returns));
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
        boolean sequence = false;

        switch (infoList.size()) {
            case 2:
                PArray a = null;
                if (input instanceof RIntSequence) {
                    a = buildIntPArrayForSequence(input);
                    sequence = true;
                } else if (input instanceof RDoubleSequence) {
                    a = buildDoublePArrayForSequence(input);
                    sequence = true;
                } else {
                    a = input.getPArray();
                }

                PArray b = null;
                if (additionalArgs[0] instanceof RIntSequence) {
                    b = buildIntPArrayForSequence(additionalArgs[0]);
                    sequence = true;
                } else if (additionalArgs[0] instanceof RDoubleSequence) {
                    b = buildDoublePArrayForSequence(additionalArgs[0]);
                    sequence = true;
                } else {
                    b = input.getPArray();
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
        if (additionalArgs == null) {
            parray = marshalSimplePArrays(infoList.get(0), input);
        } else {
            parray = marshalWithTuples(input, additionalArgs, infoList);
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
        if (additionalArgs == null) {
            parray = getReferencePArray(infoList.get(0), input);
        } else {
            parray = marshalUpdateReferenceWithTuples(input, additionalArgs, infoList);
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
    public static PArray<?> marshalWithReferencesAndSequenceOptimize(RAbstractVector input, RAbstractVector[] additionalArgs, TypeInfoList infoList) {
        PArray parray = null;
        if (additionalArgs == null) {
            parray = getReferencePArrayWithOptimizationsSequence(infoList.get(0), input);
        } else {
            parray = marshalUpdateReferenceWithTuples(input, additionalArgs, infoList);
        }
        return parray;
    }

    /**
     * Given an array of scopeVars and the function, this method evaluates this variables and
     * returns an array with only the arrays.
     *
     * @param scopeVars
     * @param function
     * @return Object[]
     */
    public static Object[] getValueOfScopeArrays(String[] scopeVars, RFunction function) {
        LinkedList<Object> scopes = new LinkedList<>();
        for (String var : scopeVars) {
            StringBuffer scopeVar = new StringBuffer(var);
            Source source = Source.fromText(scopeVar, "<eval>").withMimeType(RRuntime.R_APP_MIME);
            MaterializedFrame frame = function.getEnclosingFrame();
            Object val = null;
            try {
                val = RContext.getEngine().parseAndEval(source, frame, false);
                if (val instanceof RVector) {

                    if (val instanceof RDoubleVector) {
                        scopes.add(((RDoubleVector) val).getDataCopy());
                    } else if (val instanceof RIntVector) {
                        scopes.add(((RIntVector) val).getDataCopy());
                    } else {
                        throw new RuntimeException("Data type not supported yet");
                    }
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        if (scopes.isEmpty()) {
            return null;
        }
        return scopes.toArray();
    }

    private ASTxUtils() {
        // empty constructor
    }
}
