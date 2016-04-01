package com.oracle.truffle.r.library.gpu.utils;

import java.util.ArrayList;

import uk.ac.ed.datastructures.common.PArray;

import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RIntVector;

public class FactoryDataUtils {

    // Unmarshall to RIntVector
    public static RIntVector getIntVector(ArrayList<Object> list) {
        int[] array = list.stream().mapToInt(i -> (Integer) i).toArray();
        return RDataFactory.createIntVector(array, false);
    }

    // Unmarshall to RDoubleVector
    public static RDoubleVector getDoubleVector(ArrayList<Object> list) {
        double[] array = list.stream().mapToDouble(i -> (Double) i).toArray();
        return RDataFactory.createDoubleVector(array, false);
    }

    // Unmarshall to RIntVector
    public static RIntVector getIntVector(PArray<?> array) {
        int[] output = new int[array.size()];
        for (int i = 0; i < output.length; i++) {
            output[i] = (int) array.get(i);
        }
        return RDataFactory.createIntVector(output, false);
    }

    // Unmarshall to RDoubleVector
    public static RDoubleVector getDoubleVector(PArray<?> array) {
        double[] output = new double[array.size()];
        for (int i = 0; i < output.length; i++) {
            output[i] = (double) array.get(i);
        }
        return RDataFactory.createDoubleVector(output, false);
    }

}
