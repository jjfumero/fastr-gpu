package com.oracle.truffle.r.library.gpu.types;

import java.util.ArrayList;

public class TypeInfoList {

    private ArrayList<TypeInfo> list;

    public TypeInfoList() {
        list = new ArrayList<>();
    }

    public void add(TypeInfo type) {
        list.add(type);
    }

    public TypeInfo get(int idx) {
        return list.get(idx);
    }

    public int size() {
        return list.size();
    }
}
