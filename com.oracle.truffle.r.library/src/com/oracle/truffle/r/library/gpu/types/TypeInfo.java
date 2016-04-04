package com.oracle.truffle.r.library.gpu.types;

public enum TypeInfo {

    INT("Integer"),
    DOUBLE("Double"),
    BOOLEAN("Boolean"),
    NULL("null"); // Not used, just from R side

    private String javaTypeString;

    TypeInfo(String str) {
        this.javaTypeString = str;
    }

    public String getJavaType() {
        return this.javaTypeString;
    }

    @Override
    public String toString() {
        return this.javaTypeString;
    }
}
