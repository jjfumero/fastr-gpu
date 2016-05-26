package com.oracle.truffle.r.library.astx.threads;

import java.util.ArrayList;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RNull;

public abstract class RThreadsJoinAll extends RExternalBuiltinNode.Arg0 {

    @Specialization
    public Object joinThreads() {

        ArrayList<Thread> threads = RThreadManager.INSTANCE.getAll();

        try {
            for (Thread t : threads) {
                t.join();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return RNull.instance;

    }
}