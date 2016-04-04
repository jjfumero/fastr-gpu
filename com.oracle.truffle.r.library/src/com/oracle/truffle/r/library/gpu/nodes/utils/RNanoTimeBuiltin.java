package com.oracle.truffle.r.library.gpu.nodes.utils;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RDouble;

public abstract class RNanoTimeBuiltin extends RExternalBuiltinNode.Arg0 {

    @Specialization
    public RDouble getTime() {
        // We need to box the long in an RDouble
        return (RDouble.valueOf(System.nanoTime()));
    }
}
