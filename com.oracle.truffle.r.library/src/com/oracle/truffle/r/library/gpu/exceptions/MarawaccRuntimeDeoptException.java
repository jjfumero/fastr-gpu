package com.oracle.truffle.r.library.gpu.exceptions;

public class MarawaccRuntimeDeoptException extends RuntimeException {

    private static final long serialVersionUID = 4752222838471565584L;

    public MarawaccRuntimeDeoptException(String message) {
        super(message);
    }

}
