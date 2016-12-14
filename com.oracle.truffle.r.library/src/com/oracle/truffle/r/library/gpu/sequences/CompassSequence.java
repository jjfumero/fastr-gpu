package com.oracle.truffle.r.library.gpu.sequences;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RIntSequence;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public abstract class CompassSequence extends RExternalBuiltinNode.Arg3 {

    @SuppressWarnings("unused")
    private static int[] createSequence(int start, int max, int repetitions) {
        int[] vector = new int[max * repetitions];
        int idx = 0;
        for (int i = start; i <= max; i++) {
            for (int j = 0; j < repetitions; j++) {
                vector[idx++] = j;
            }
        }
        return vector;
    }

    private static int extractValue(Object value) {
        if (value instanceof Double) {
            return ((Double) value).intValue();
        } else if (value instanceof Integer) {
            return ((Integer) value).intValue();
        }
        throw new RuntimeException("Type not supported: " + value.getClass());
    }

    @Specialization
    protected RAbstractVector buildCompassSequence(Object startObject, Object maxObject, Object repetitionsObject) {
        int start = extractValue(startObject);
        int stride = 1;
        int max = extractValue(maxObject);
        int repetitions = extractValue(repetitionsObject);
        RIntSequence sequence = RDataFactory.createIntSequenceCompass(start, stride, max * repetitions, max, repetitions);
        return sequence;
    }
}