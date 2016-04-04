package com.oracle.truffle.r.library.gpu.types;

public enum RGPUType {
    INT("int"),
    DOUBLE("double"),
    BOOLEAN("bool"),
    NULL("null"); // Not used, just from R side

    private String str;

    RGPUType(String str) {
        this.str = str;
    }

    @Override
    public String toString() {
        return this.str;
    }
}
