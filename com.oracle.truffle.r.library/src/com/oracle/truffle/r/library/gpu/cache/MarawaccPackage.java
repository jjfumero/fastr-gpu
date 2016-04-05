package com.oracle.truffle.r.library.gpu.cache;

import uk.ac.ed.datastructures.common.PArray;
import uk.ac.ed.jpai.ArrayFunction;

import com.oracle.truffle.r.library.gpu.types.TypeInfo;

public class MarawaccPackage {

    private ArrayFunction<?, ?> arrayFunction;
    private TypeInfo type;
    @SuppressWarnings("rawtypes") private PArray pArray;
    private Object vector;

    public MarawaccPackage(ArrayFunction<?, ?> function) {
        this.arrayFunction = function;
    }

    public void setTypeInfo(TypeInfo t) {
        this.type = t;
    }

    public TypeInfo getTypeInfo() {
        return this.type;
    }

    @SuppressWarnings("rawtypes")
    public PArray getpArray() {
        return pArray;
    }

    @SuppressWarnings("rawtypes")
    public void setpArray(PArray pArray) {
        this.pArray = pArray;
    }

    public ArrayFunction<?, ?> getArrayFunction() {
        return this.arrayFunction;
    }

    public void setRVector(Object value) {
        this.vector = value;
    }

    public Object getRVector() {
        return this.vector;
    }
}
