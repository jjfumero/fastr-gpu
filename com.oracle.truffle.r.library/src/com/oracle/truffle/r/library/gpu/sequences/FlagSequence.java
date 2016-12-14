package com.oracle.truffle.r.library.gpu.sequences;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RIntSequence;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

/**
 * Repetition: 1 1 1 1 2 2 2 2 ...
 */
public abstract class FlagSequence extends RExternalBuiltinNode.Arg3 {

    @SuppressWarnings("unused")
    private static int[] createSequence(int start, int max, int repetitions) {
        int[] vector = new int[max * repetitions];

        int idx = 0;
        for (int i = start; i <= max; i++) {
            for (int j = 0; j < repetitions; j++) {
                vector[idx++] = i;
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
    protected RAbstractVector buildSequenceOfRepetitions(Object startObject, Object maxObject, Object repetitionsObject) {
        int start = extractValue(startObject);
        int max = extractValue(maxObject);
        int repetitions = extractValue(repetitionsObject);
        RIntSequence sequence = RDataFactory.createIntSequenceFlag(start, 1, max * repetitions, repetitions);
        return sequence;
    }
}
