package com.oracle.truffle.r.library.gpu.utils;

/**
 * Print source code info __LINE__ macro as C/C++ style.
 *
 */
public final class __LINE__ {

    public static String print() {
        StackTraceElement ste = Thread.currentThread().getStackTrace()[2];
        String where = ste.getClassName() + "#" + ste.getMethodName() + ":" + ste.getLineNumber() + " ";
        return where;
    }

}
