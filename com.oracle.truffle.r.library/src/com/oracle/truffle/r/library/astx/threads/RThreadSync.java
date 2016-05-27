package com.oracle.truffle.r.library.astx.threads;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.model.RAbstractIntVector;

public abstract class RThreadSync extends RExternalBuiltinNode.Arg1 {

    @Specialization
    public Object syncThreads(RAbstractIntVector ids) {
        for (int i = 0; i < ids.getLength(); i++) {
            int idxThread = ids.getDataAt(i);
            RThreadManager.INSTANCE.join(idxThread);
        }
        return RNull.instance;
    }
}