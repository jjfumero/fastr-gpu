package com.oracle.truffle.r.library.gpu;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.library.gpu.utils.AcceleratorRUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractIntVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public abstract class ParallelMap extends RExternalBuiltinNode.Arg3 {

    @SuppressWarnings("unused")
    @Specialization
    public RAbstractVector computeMap(RAbstractIntVector input, RFunction function, RAbstractIntVector inputB) {

        int nArgs = AcceleratorRUtils.getNumberOfArguments(function);
        String[] argsName = AcceleratorRUtils.getArgumentsNames(function);

        String source = null;
        if (function.getRBuiltin() != null) {
            source = function.getRBuiltin().getName();
        } else {
            SourceSection sourceSection = function.getTarget().getRootNode().getSourceSection();
            source = sourceSection.toString();
        }

        StringBuffer rcodeSource = new StringBuffer(source);
        RIntVector output = RDataFactory.createIntVector(input.getLength());

        for (int i = 0; i < input.getLength(); i++) {
            Object[] argsPackage = AcceleratorRUtils.getArgsPackage(nArgs, function, input, null, argsName, i);
            Object value = function.getTarget().call(argsPackage);
            output.setElement(i, value);
        }

        return output;
    }
}
