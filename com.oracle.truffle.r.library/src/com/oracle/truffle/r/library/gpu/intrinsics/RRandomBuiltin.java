package com.oracle.truffle.r.library.gpu.intrinsics;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RDouble;

public abstract class RRandomBuiltin extends RExternalBuiltinNode.Arg1 {

    @Specialization
    public RDouble generateRandom(Object x) {
        long seed = castInt(castVector(x));
        seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
        seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
        // this generates a number between 0 and 1 (with an awful entropy)
        float random = (seed & 0x0FFFFFFF) / 268435455f;
        RDouble randomValue = RDouble.valueOf(random);
        return randomValue;
    }
}
