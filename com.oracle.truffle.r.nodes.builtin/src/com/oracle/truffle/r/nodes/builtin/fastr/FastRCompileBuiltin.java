/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.nodes.builtin.fastr;

import static com.oracle.truffle.r.runtime.RBuiltinKind.*;

import java.lang.reflect.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;

@RBuiltin(name = "fastr.compile", kind = PRIMITIVE, parameterNames = {"func"})
public abstract class FastRCompileBuiltin extends RBuiltinNode {

    private static final class Compiler {
        private final Class<?> optimizedCallTarget;
        private final Method compileMethod;

        private Compiler() {
            Class<?> clazz = null;
            Method method = null;
            try {
                clazz = Class.forName("com.oracle.graal.truffle.OptimizedCallTarget", false, Truffle.getRuntime().getClass().getClassLoader());
                method = clazz.getDeclaredMethod("compile");
            } catch (ClassNotFoundException | IllegalArgumentException | NoSuchMethodException | SecurityException e) {
                Utils.fail("DebugCompileBuiltin failed to find compile method");
            }
            optimizedCallTarget = clazz;
            compileMethod = method;
        }

        static Compiler getCompiler() {
            if (System.getProperty("fastr.truffle.compile", "true").equals("true") && Truffle.getRuntime().getName().contains("Graal")) {
                return new Compiler();
            } else {
                Utils.warn("DebugCompileBuiltin not supported in this environment");
                return null;
            }
        }

        boolean compile(CallTarget callTarget) throws InvocationTargetException, IllegalAccessException {
            if (optimizedCallTarget.isInstance(callTarget)) {
                compileMethod.invoke(callTarget);
                return true;
            } else {
                return false;
            }
        }
    }

    private static final Compiler compiler = Compiler.getCompiler();

    @Specialization
    protected byte compileFunction(RFunction function) {
        controlVisibility();
        if (compiler != null) {
            try {
                if (compiler.compile(function.getTarget())) {
                    return RRuntime.LOGICAL_TRUE;
                }
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.GENERIC, e.toString());
            }
        }
        return RRuntime.LOGICAL_FALSE;
    }

    @Specialization
    protected byte compileFunction(@SuppressWarnings("unused") Object arg) {
        controlVisibility();
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.INVALID_ARGUMENT, "function");
    }
}
