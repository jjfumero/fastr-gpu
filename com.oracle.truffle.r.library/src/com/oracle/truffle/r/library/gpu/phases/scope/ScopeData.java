package com.oracle.truffle.r.library.gpu.phases.scope;

public class ScopeData {

    private Object[] scopeArray;

    public ScopeData(Object[] data) {
        this.scopeArray = data;
    }

    public Object[] getData() {
        return scopeArray;
    }

    public void setData(Object[] data) {
        this.scopeArray = data;
    }
}
