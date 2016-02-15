package com.oracle.truffle.r.library.gpu;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.library.gpu.utils.AcceleratorRUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractIntVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public abstract class XMap extends RExternalBuiltinNode.Arg3 {

    @SuppressWarnings("unused")
    @Specialization
    public RAbstractVector computeMap(RAbstractIntVector input, RFunction function, RAbstractIntVector inputB) {

        int nArgs = AcceleratorRUtils.getNumberOfArguments(function);
        String[] argsName = AcceleratorRUtils.getArgumentsNames(function);

        String source = AcceleratorRUtils.getSourceCode(function);
        StringBuffer rcodeSource = new StringBuffer(source);

        // Create output
        RIntVector output = RDataFactory.createIntVector(input.getLength());

        CallTarget target = function.getTarget();

        // Process the expression
        for (int i = 0; i < input.getLength(); i++) {
            Object[] argsPackage = AcceleratorRUtils.getArgsPackage(nArgs, function, input, null, argsName, i);
            Object value = target.call(argsPackage);
            output.setElement(i, value);
        }

        // NOTE: force the compilation with no profiling (the lambda should be different)

        return output;
    }
}
