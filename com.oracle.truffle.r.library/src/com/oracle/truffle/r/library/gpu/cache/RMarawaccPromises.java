package com.oracle.truffle.r.library.gpu.cache;

import java.util.ArrayList;
import java.util.HashMap;

import uk.ac.ed.jpai.ArrayFunction;

public class RMarawaccPromises {

    public static final RMarawaccPromises INSTANCE = new RMarawaccPromises();

    private ArrayList<MarawaccPackage> promises;
    private HashMap<ArrayFunction<?, ?>, Integer> index;
    private int size;

    private RMarawaccPromises() {
        promises = new ArrayList<>();
        index = new HashMap<>();
    }

    public void addPromise(MarawaccPackage marawaccPackage) {
        promises.add(marawaccPackage);
        index.put(marawaccPackage.getArrayFunction(), size);
        size++;
    }

    @SuppressWarnings("rawtypes")
    public MarawaccPackage getPackageForArrayFunction(ArrayFunction arrayFunction) {
        return promises.get(index.get(arrayFunction));
    }

    public MarawaccPackage getPackage(int idx) {
        return promises.get(idx);
    }

    public MarawaccPackage getLast() {
        return promises.get(size - 1);
    }

    public void clean() {
        promises.clear();
        index.clear();
        size = 0;
    }
}
