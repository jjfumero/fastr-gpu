package com.oracle.truffle.r.library.gpu.tuples;

import uk.ac.ed.datastructures.tuples.Tuple3;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;

public abstract class ASTxTuple3 extends RExternalBuiltinNode.Arg3 {

    @SuppressWarnings("rawtypes")
    @Specialization
    public Tuple3 createTuple3(Object a, Object b, Object c) {
        Tuple3 tuple = new Tuple3<>(a, b, c);
        System.out.println(tuple);
        return tuple;
    }
}
