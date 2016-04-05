package com.oracle.truffle.r.library.gpu.cache;

import java.util.ArrayList;

import uk.ac.ed.jpai.ArrayFunction;

public class MarawaccPackage {

    private ArrayFunction<?, ?> arrayFunction;
    private ArrayList<Object> list;

    public MarawaccPackage(ArrayFunction<?, ?> function) {
        list = new ArrayList<>();
        this.arrayFunction = function;
    }

    public void add(Object o) {
        list.add(o);
    }

    public Object get(int idx) {
        return list.get(idx);
    }

    public ArrayList<Object> getList() {
        return list;
    }

    public ArrayFunction<?, ?> getArrayFunction() {
        return this.arrayFunction;
    }
}
