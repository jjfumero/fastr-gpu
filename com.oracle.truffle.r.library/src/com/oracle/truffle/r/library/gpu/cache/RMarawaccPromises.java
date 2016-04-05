package com.oracle.truffle.r.library.gpu.cache;

import java.util.ArrayList;

public class RMarawaccPromises {

    public static final RMarawaccPromises INSTANCE = new RMarawaccPromises();

    private ArrayList<MarawaccPackage> promises;

    private RMarawaccPromises() {
        this.promises = new ArrayList<>();
    }

    public void addPromise(MarawaccPackage marawaccPackage) {
        promises.add(marawaccPackage);
    }

    public MarawaccPackage getPackage(int idx) {
        return promises.get(idx);
    }

    public MarawaccPackage getLast() {
        return promises.get(promises.size() - 1);
    }

    public void clean() {
        this.promises.clear();
    }
}
