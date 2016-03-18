package com.oracle.truffle.r.library.gpu;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RDouble;

public abstract class RNanoTimeBuiltin extends RExternalBuiltinNode.Arg0 {

    @Specialization
    public RDouble getTime() {
        long time = System.nanoTime();

        // We need to box the long in an RDouble
        RDouble value = RDouble.valueOf(time);

        return value;
    }
}
