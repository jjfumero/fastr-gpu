package com.oracle.truffle.r.library.gpu;

import java.util.stream.IntStream;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RList;

public abstract class RListProbe extends RExternalBuiltinNode.Arg0 {

    @Specialization
    public RList checkRList() {

        // Create c(list(1, 2), list(3, 4))

        double[] a = new double[10];
        double[] b = new double[10];
        IntStream.range(0, 10).parallel().forEach(i -> {
            a[i] = i;
            b[i] = i + 100;
        });

        Object[] d = new Object[20];

        IntStream.range(0, 10).parallel().forEach(i -> {
            d[i * 2] = a[i];
            d[(i * 2) + 1] = b[i];
        });

        RList list = RDataFactory.createList(d, new int[]{2, 10});
        return list;
    }
}
