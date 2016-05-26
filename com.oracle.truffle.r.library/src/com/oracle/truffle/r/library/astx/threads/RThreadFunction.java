package com.oracle.truffle.r.library.astx.threads;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.model.RAbstractIntVector;

public abstract class RThreadFunction extends RExternalBuiltinNode.Arg2 {

    @Specialization
    public Object createThreadAndRun(RFunction function, RAbstractIntVector input) {

        int nArgs = ASTxUtils.getNumberOfArguments(function);
        String[] argsName = ASTxUtils.getArgumentsNames(function);
        Object[] argsPackage = ASTxUtils.getArgsPackage(nArgs, function, input, null, argsName, 0);

        Thread thread = new Thread(() -> {
            System.out.println("[ASTx] Thread: " + Thread.currentThread().getName());
            function.getTarget().call(argsPackage);
            System.out.println("[ASTx] Thread: " + Thread.currentThread().getName());
        });

        thread.start();     // Annotate the thread into a Hash for runtime management

        RThreadManager.INSTANCE.addThread(thread);

        return RNull.instance;
    }
}
