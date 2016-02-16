package com.oracle.truffle.r.library.gpu.compiler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.data.RFunction;

public final class AccTruffleCompiler {

    private static final class Compiler {
        private final Class<?> optimizedCallTarget;
        private final Class<?> graalTruffleRuntime;
        private final Method compileMethod;

        private Compiler() {
            try {
                optimizedCallTarget = Class.forName("com.oracle.graal.truffle.OptimizedCallTarget", false, Truffle.getRuntime().getClass().getClassLoader());
                graalTruffleRuntime = Class.forName("com.oracle.graal.truffle.GraalTruffleRuntime", false, Truffle.getRuntime().getClass().getClassLoader());
                compileMethod = graalTruffleRuntime.getDeclaredMethod("compile", optimizedCallTarget, boolean.class);
            } catch (ClassNotFoundException | IllegalArgumentException | NoSuchMethodException | SecurityException e) {
                throw Utils.fail("fastr.compile: failed to find 'compile' method");
            }
        }

        static Compiler getCompiler() {
            if (System.getProperty("fastr.truffle.compile", "true").equals("true") && Truffle.getRuntime().getName().contains("Graal")) {
                return new Compiler();
            } else {
                Utils.warn("fastr.compile not supported in this environment");
                return null;
            }
        }

        boolean compile(CallTarget callTarget, boolean background) throws InvocationTargetException, IllegalAccessException {
            if (optimizedCallTarget.isInstance(callTarget)) {
                compileMethod.invoke(Truffle.getRuntime(), callTarget, background);
                return true;
            } else {
                return false;
            }
        }
    }

    private static final Compiler compiler = Compiler.getCompiler();

    public static boolean compileFunction(RFunction function) throws InvocationTargetException, IllegalAccessException {
        byte background = RRuntime.LOGICAL_FALSE;
        if (compiler != null) {
            return compiler.compile(function.getTarget(), background == RRuntime.LOGICAL_FALSE);
        }
        return false;
    }
}