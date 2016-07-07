package com.oracle.truffle.r.library.gpu.tuples;

import uk.ac.ed.datastructures.tuples.Tuple2;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;

public abstract class ASTxTuple2 extends RExternalBuiltinNode.Arg2 {

    @Specialization
    public Tuple2<?, ?> createTuple2(Object a, Object b) {
        Tuple2<?, ?> tuple = new Tuple2<>(a, b);
        System.out.println(tuple);
        return tuple;
    }
}
