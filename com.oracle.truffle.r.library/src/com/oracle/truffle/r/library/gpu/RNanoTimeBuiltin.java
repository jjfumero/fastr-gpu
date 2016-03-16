package com.oracle.truffle.r.library.gpu;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RDouble;

public abstract class RNanoTimeBuiltin extends RExternalBuiltinNode.Arg0 {

    @Specialization
    @TruffleBoundary
    public RDouble getTime() {
        long time = System.nanoTime();
        RDouble value = RDouble.valueOf(time);
        return value;
    }
}
