package com.oracle.truffle.r.library.gpu;

import java.util.ArrayList;

import uk.ac.ed.datastructures.common.PArray;
import uk.ac.ed.datastructures.common.TypeFactory;
import uk.ac.ed.datastructures.tuples.Tuple2;
import uk.ac.ed.datastructures.tuples.Tuple3;
import uk.ac.ed.datastructures.tuples.Tuple4;
import uk.ac.ed.jpai.ArrayFunction;
import uk.ac.ed.jpai.Marawacc;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.r.library.gpu.cache.RGPUCache;
import com.oracle.truffle.r.library.gpu.options.ASTxOptions;
import com.oracle.truffle.r.library.gpu.types.TypeInfo;
import com.oracle.truffle.r.library.gpu.types.TypeInfoList;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public final class MarawaccReduceBuiltin extends RExternalBuiltinNode {

    private static <T, R> ArrayFunction<T, R> createMarawaccLambda(int nArgs, RootCallTarget callTarget, RFunction rFunction, String[] nameArgs, int neutral) {
        @SuppressWarnings("unchecked")
        ArrayFunction<T, R> function = (ArrayFunction<T, R>) Marawacc.reduce((x, y) -> {
            Object[] argsPackage = ASTxUtils.getArgsPackage(nArgs, rFunction, x, y, nameArgs);
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
    private static PArray<?> marshalSimple(TypeInfoList infoList, RAbstractVector input) {
        PArray parray = null;
        TypeInfo type = infoList.get(0);
        switch (type) {
            case INT:
                parray = new PArray<>(input.getLength(), TypeFactory.Integer());
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, input.getDataAtAsObject(k));
                }
                return parray;
            case DOUBLE:
                parray = new PArray<>(input.getLength(), TypeFactory.Double());
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, input.getDataAtAsObject(k));
                }
                return parray;
            case BOOLEAN:
                parray = new PArray<>(input.getLength(), TypeFactory.Boolean());
                for (int k = 0; k < parray.size(); k++) {
                    parray.put(k, input.getDataAtAsObject(k));
                }
                return parray;
            default:
                throw new RuntimeException("Data type not supported");
        }
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static PArray<?> marshalWithTuples(RAbstractVector input, RAbstractVector[] additionalArgs, TypeInfoList infoList) {
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
            default:
                throw new RuntimeException("Tuple number not supported yet");
        }
    }

    @SuppressWarnings("rawtypes")
    private static PArray<?> marshall(RAbstractVector input, RAbstractVector[] additionalArgs, TypeInfoList infoList) {
        PArray parray = null;
        if (additionalArgs == null) {
            // Simple PArray
            parray = marshalSimple(infoList, input);
        } else {
            // Tuples
            parray = marshalWithTuples(input, additionalArgs, infoList);
        }
        return parray;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static PArray<?> runMarawaccThreads(RAbstractVector input, RootCallTarget callTarget, RFunction rFunction, String[] nameArgs, int nThreads,
                    RAbstractVector[] additionalArgs, TypeInfoList infoList) {
        ArrayFunction composeLambda = createMarawaccLambda(infoList.size() + 1, callTarget, rFunction, nameArgs, nThreads);
        PArray pArrayInput = marshall(input, additionalArgs, infoList);
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
            Object[] argsPackage = ASTxUtils.getArgsPackageForReduction(nArgs, accumulator, function, input, additionalArgs, argsName, i);
            accumulator = target.call(argsPackage);
        }
        ArrayList<Object> output = new ArrayList<>();
        output.add(accumulator);
        return output;
    }

    public static RAbstractVector computeReduction(RAbstractVector input, RFunction function, RootCallTarget target, RAbstractVector[] additionalArgs, int neutral) {

        int nArgs = ASTxUtils.getNumberOfArguments(function);
        String[] argsName = ASTxUtils.getArgumentsNames(function);

        Object[] argsPackage = ASTxUtils.getArgsPackageForReduction(nArgs, neutral, function, input, additionalArgs, argsName, 0);
        Object value = function.getTarget().call(argsPackage);

        TypeInfoList inputTypeList = ASTxUtils.typeInference(input, additionalArgs);
        TypeInfo outputType = ASTxUtils.typeInference(value);

        if (!ASTxOptions.runMarawaccThreads) {
            // Run sequential
            ArrayList<Object> result = runJavaSequential(input, target, function, nArgs, additionalArgs, argsName, value);
            return ASTxUtils.unMarshallResultFromList(outputType, result);
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
