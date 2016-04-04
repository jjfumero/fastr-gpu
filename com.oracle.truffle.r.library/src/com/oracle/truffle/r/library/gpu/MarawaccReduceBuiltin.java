package com.oracle.truffle.r.library.gpu;

import java.util.ArrayList;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.r.library.gpu.cache.RGPUCache;
import com.oracle.truffle.r.library.gpu.types.TypeInfo;
import com.oracle.truffle.r.library.gpu.types.TypeInfoList;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public final class MarawaccReduceBuiltin extends RExternalBuiltinNode {

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

        // Run sequential
        ArrayList<Object> result = runJavaSequential(input, target, function, nArgs, additionalArgs, argsName, value);
        return ASTxUtils.unMarshallResultFromList(outputType, result);
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
