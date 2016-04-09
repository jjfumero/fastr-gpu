package com.oracle.truffle.r.library.gpu;

import java.util.ArrayList;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.r.library.gpu.cache.RGPUCache;
import com.oracle.truffle.r.library.gpu.exceptions.MarawaccTypeException;
import com.oracle.truffle.r.library.gpu.types.TypeInfo;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public final class GPUTestNode extends RExternalBuiltinNode {

    private static ArrayList<Object> runJavaSequential(RAbstractVector input, RootCallTarget callTarget, RFunction function, int nArgs, RAbstractVector[] additionalArgs, String[] argsName,
                    Object firstValue) {
        ArrayList<Object> output = new ArrayList<>(input.getLength());
        output.add(firstValue);

        // Try to create a new root node
        RootNode rootNode = function.getRootNode();
        RootCallTarget newCallTarget = Truffle.getRuntime().createCallTarget(rootNode);

        for (int i = 1; i < input.getLength(); i++) {
            Object[] argsPackage = ASTxUtils.getArgsPackage(nArgs, function, input, additionalArgs, argsName, i);
            Object val = newCallTarget.call(argsPackage);
// Object val = callTarget.call(argsPackage);
            output.add(val);
        }
        return output;
    }

    public static RAbstractVector computeMap(RAbstractVector input, RFunction function, RootCallTarget target, RAbstractVector[] additionalArgs) {

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

        ArrayList<Object> result = runJavaSequential(input, target, function, nArgs, additionalArgs, argsName, value);
        return ASTxUtils.unMarshallResultFromList(outputType, result);
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
        return computeMap(input, function, target, additionalInputs);
    }
}
