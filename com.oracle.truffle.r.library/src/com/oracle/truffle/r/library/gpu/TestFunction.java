package com.oracle.truffle.r.library.gpu;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RFunction;

public abstract class TestFunction extends RExternalBuiltinNode.Arg1 {

    @Specialization
    public String printASTFunction(RFunction function) {
        ASTxUtils.printAST(function);
        return "";
    }
}
