/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.env.*;
import com.oracle.truffle.r.runtime.env.frame.*;

// @formatter:off
/**
 * Provide access to arguments contained in frames. This is a purely static class. It defines, by
 * means of slot offsets, where in a frame certain information is stored, such as the function
 * executed in the frame.
 *
 * The frame layout, depicted, is as follows:
 * <pre>
 *                            +--------------------+
 * INDEX_ENVIRONMENT       -> | REnvironment       |
 *                            +--------------------+
 * INDEX_FUNCTION          -> | RFunction          |
 *                            +--------------------+
 * INDEX_CALL_SRC          -> | SourceSection      |
 *                            +--------------------+
 * INDEX_CALLER_FRAME   ->    | MaterializedFrame  |
 *                            +--------------------+
 * INDEX_ENCLOSING_FRAME   -> | MaterializedFrame  |
 *                            +--------------------+
 * INDEX_S3_ARGS           -> | S3Args             |
 *                            +--------------------+
 * INDEX_DEPTH             -> | depth              |
 *                            +--------------------+
 * INDEX_IS_IRREGULAR      -> | isIrregular        |
 *                            +--------------------+
 * INDEX_SIGNATURE         -> | ArgumentsSignature |
 *                            +--------------------+
 * INDEX_ARGUMENTS         -> | arg_0              |
 *                            | arg_1              |
 *                            | ...                |
 *                            | arg_(nArgs-1)      |
 *                            +--------------------+
 *
 * If the formal parameter is "..." then the corresponding argument slot will
 * always be of type {@link RArgsValuesAndNames}.
 * </pre>
 *
 * All frame elements should <b>always</b> be accessed through the getter and setter functions
 * defined in this class, as they provide a means of accessing the frame contents that is
 * transparent to layout changes.
 *
 * The INDEX_ENVIRONMENT slot is typically not set for frames associated with function evaluations,
 * because such environment instances are only created on demand. It is however, set for frames
 * associated with packages and the global environment.
 */
// @formatter:on
public final class RArguments {

    @CompilationFinal public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    @CompilationFinal public static final String[] EMPTY_STRING_ARRAY = new String[0];

    @ValueType
    public static final class S3Args {
        public final String generic;
        public final Object clazz;
        public final Object method;
        public final MaterializedFrame callEnv;
        public final MaterializedFrame defEnv;
        public final String group;

        public S3Args(String generic, Object clazz, Object method, MaterializedFrame callEnv, MaterializedFrame defEnv, String group) {
            assert generic != null && callEnv != null : generic + " " + callEnv;
            assert generic.intern() == generic;
            this.generic = generic;
            this.clazz = clazz;
            this.method = method;
            this.callEnv = callEnv;
            this.defEnv = defEnv;
            this.group = group;
        }
    }

    static final int INDEX_ENVIRONMENT = 0;
    static final int INDEX_FUNCTION = 1;
    static final int INDEX_CALL_SRC = 2;
    static final int INDEX_CALLER_FRAME = 3;
    static final int INDEX_ENCLOSING_FRAME = 4;
    static final int INDEX_S3_ARGS = 5;
    static final int INDEX_DEPTH = 6;
    static final int INDEX_IS_IRREGULAR = 7;
    static final int INDEX_SIGNATURE = 8;
    static final int INDEX_ARGUMENTS = 9;

    /**
     * At the least, the array contains the function, enclosing frame, and numbers of arguments and
     * names.
     */
    static final int MINIMAL_ARRAY_LENGTH = INDEX_ARGUMENTS;

    private static final ValueProfile materializedFrameProfile = ValueProfile.createClassProfile();

    private RArguments() {
    }

    /**
     * Get the {@code RArguments} checking for an "eval" frame. An eval can create a pseudo-call
     * where {@code arguments.length == 1} and the real {@code arguments} are at
     * {@code arguments[0]}. See {@code REngine}.
     */
    private static Object[] getRArgumentsWithEvalCheck(Frame frame) {
        Object[] arguments = frame.getArguments();
        if (arguments.length == 1) {
            return materializedFrameProfile.profile((Frame) arguments[0]).getArguments();
        } else {
            return arguments;
        }
    }

    /**
     * Get the {@code arguments} checking for an "eval" frame. An eval can create a pseudo-call
     * where {@code arguments.length == 1} and the real {@code arguments} are at
     * {@code arguments[0]}. See {@code REngine}.
     */
    public static Object[] getRArgumentsWithEvalCheck(Frame frame, ConditionProfile profile) {
        CompilerAsserts.compilationConstant(profile);
        Object[] arguments = frame.getArguments();
        if (profile.profile(arguments.length == 1)) {
            return materializedFrameProfile.profile((Frame) arguments[0]).getArguments();
        } else {
            return arguments;
        }
    }

    private static int getNArgs(Frame frame) {
        return getSignature(frame).getLength();
    }

    private static ArgumentsSignature getSignature(RFunction function) {
        return ((HasSignature) function.getRootNode()).getSignature();
    }

    public static Object[] create(RFunction functionObj, SourceSection callSrc, MaterializedFrame callerFrame, int depth, Object[] evaluatedArgs, ArgumentsSignature signature) {
        MaterializedFrame enclosingFrame = functionObj.getEnclosingFrame();
        return createInternal(functionObj, callSrc, callerFrame, depth, evaluatedArgs, signature, enclosingFrame);
    }

    public static Object[] create(RFunction functionObj, SourceSection callSrc, MaterializedFrame callerFrame, int depth, Object[] evaluatedArgs, ArgumentsSignature signature, S3Args s3Args) {
        Object[] args = create(functionObj, callSrc, callerFrame, depth, evaluatedArgs, signature);
        args[INDEX_S3_ARGS] = s3Args;
        return args;
    }

    public static Object[] createInternal(RFunction functionObj, SourceSection callSrc, MaterializedFrame callerFrame, int depth, Object[] evaluatedArgs, ArgumentsSignature signature,
                    MaterializedFrame enclosingFrame) {
        assert evaluatedArgs != null && signature != null : evaluatedArgs + " " + signature;
        assert evaluatedArgs.length == signature.getLength() : Arrays.toString(evaluatedArgs) + " " + signature;
        assert signature == getSignature(functionObj) : signature + " vs. " + getSignature(functionObj);

        Object[] a = new Object[MINIMAL_ARRAY_LENGTH + evaluatedArgs.length];
        a[INDEX_ENVIRONMENT] = null;
        a[INDEX_FUNCTION] = functionObj;
        a[INDEX_CALL_SRC] = callSrc;
        a[INDEX_CALLER_FRAME] = callerFrame;
        a[INDEX_ENCLOSING_FRAME] = enclosingFrame;
        a[INDEX_DEPTH] = depth;
        a[INDEX_IS_IRREGULAR] = false;
        a[INDEX_SIGNATURE] = signature;
        System.arraycopy(evaluatedArgs, 0, a, INDEX_ARGUMENTS, evaluatedArgs.length);
        // assert envFunctionInvariant(a);
        return a;
    }

    @SuppressWarnings("unused")
    private static boolean envFunctionInvariant(Object[] a) {
        return a[INDEX_ENVIRONMENT] != null || a[INDEX_FUNCTION] != null;
    }

    /**
     * A method for creating an uninitialized array, used only in very special situations as it
     * temporarily violates {@link #envFunctionInvariant}.
     */
    public static Object[] createUnitialized(Object... args) {
        Object[] a = new Object[MINIMAL_ARRAY_LENGTH + args.length];
        a[INDEX_DEPTH] = 0;
        a[INDEX_SIGNATURE] = ArgumentsSignature.empty(args.length);
        a[INDEX_IS_IRREGULAR] = false;
        System.arraycopy(args, 0, a, INDEX_ARGUMENTS, args.length);
        return a;
    }

    public static MaterializedFrame getCallerFrame(Frame frame) {
        Object[] args = getRArgumentsWithEvalCheck(frame);
        return (MaterializedFrame) args[INDEX_CALLER_FRAME];
    }

    public static S3Args getS3Args(Frame frame) {
        return (S3Args) getRArgumentsWithEvalCheck(frame)[INDEX_S3_ARGS];
    }

    public static REnvironment getEnvironment(Frame frame) {
        return (REnvironment) getRArgumentsWithEvalCheck(frame)[INDEX_ENVIRONMENT];
    }

    public static RFunction getFunction(Frame frame) {
        return (RFunction) getRArgumentsWithEvalCheck(frame)[INDEX_FUNCTION];
    }

    public static SourceSection getCallSourceSection(Frame frame) {
        return (SourceSection) getRArgumentsWithEvalCheck(frame)[INDEX_CALL_SRC];
    }

    /**
     * Return a string describing the call that resulted in this frame. Ideally
     * {@link #getCallSourceSection(Frame)} never returns {@code null}, but this method handles the
     * case when it does (e.g. UseMethod dispatch).
     */
    public static String safeGetCallSourceString(Frame frame) {
        SourceSection ss = getCallSourceSection(frame);
        if (ss != null) {
            return ss.getCode();
        } else {
            RFunction function = getFunction(frame);
            if (function != null) {
                return function.getTarget().toString();
            } else {
                return "<unknown call>";
            }
        }
    }

    public static int getDepth(Frame frame) {
        return (Integer) getRArgumentsWithEvalCheck(frame)[INDEX_DEPTH];
    }

    public static boolean getIsIrregular(Frame frame) {
        return (boolean) getRArgumentsWithEvalCheck(frame)[INDEX_IS_IRREGULAR];
    }

    public static Object getArgument(Frame frame, int argIndex) {
        assert (argIndex >= 0 && argIndex < getNArgs(frame));
        return getRArgumentsWithEvalCheck(frame)[INDEX_ARGUMENTS + argIndex];
    }

    public static Object[] getArguments(Frame frame) {
        Object[] args = getRArgumentsWithEvalCheck(frame);
        return Arrays.copyOfRange(args, INDEX_ARGUMENTS, INDEX_ARGUMENTS + ((ArgumentsSignature) args[INDEX_SIGNATURE]).getLength());
    }

    public static void copyArguments(Frame frame, Object[] dest, int pos) {
        Object[] args = getRArgumentsWithEvalCheck(frame);
        System.arraycopy(args, INDEX_ARGUMENTS, dest, pos, ((ArgumentsSignature) args[INDEX_SIGNATURE]).getLength());
    }

    /**
     * <b>Only to be called from AccessArgumentNode!</b>
     *
     * @param frame
     * @param argIndex
     * @param newValue
     */
    public static void setArgument(Frame frame, int argIndex, Object newValue) {
        assert (argIndex >= 0 && argIndex < getNArgs(frame));
        getRArgumentsWithEvalCheck(frame)[INDEX_ARGUMENTS + argIndex] = newValue;
    }

    public static int getArgumentsLength(Frame frame) {
        return getNArgs(frame);
    }

    public static MaterializedFrame getEnclosingFrame(Frame frame) {
        return (MaterializedFrame) getRArgumentsWithEvalCheck(frame)[INDEX_ENCLOSING_FRAME];
    }

    public static MaterializedFrame getEnclosingFrame(Frame frame, ConditionProfile profile) {
        return (MaterializedFrame) getRArgumentsWithEvalCheck(frame, profile)[INDEX_ENCLOSING_FRAME];
    }

    public static ArgumentsSignature getSignature(Frame frame) {
        return (ArgumentsSignature) getRArgumentsWithEvalCheck(frame)[INDEX_SIGNATURE];
    }

    public static void setEnvironment(Frame frame, REnvironment env) {
        getRArgumentsWithEvalCheck(frame)[INDEX_ENVIRONMENT] = env;
    }

    /**
     * Explicitly set the callSrc. Used by {@code REngine.eval}.
     */
    public static void setCallSourceSection(Frame frame, SourceSection callSrc) {
        getRArgumentsWithEvalCheck(frame)[INDEX_CALL_SRC] = callSrc;
    }

    public static void setDepth(Frame frame, int depth) {
        getRArgumentsWithEvalCheck(frame)[INDEX_DEPTH] = depth;
    }

    public static void setIsIrregular(Frame frame, boolean isIrregularFrame) {
        getRArgumentsWithEvalCheck(frame)[INDEX_IS_IRREGULAR] = isIrregularFrame;
    }

    public static void setIsIrregular(Object[] arguments, boolean isIrregularFrame) {
        assert arguments.length > INDEX_ARGUMENTS;
        arguments[INDEX_IS_IRREGULAR] = isIrregularFrame;
    }

    public static void setEnclosingFrame(Frame frame, MaterializedFrame encl) {
        CompilerAsserts.neverPartOfCompilation();
        Object[] arguments = getRArgumentsWithEvalCheck(frame);
        arguments[INDEX_ENCLOSING_FRAME] = encl;
        FrameSlotChangeMonitor.invalidateEnclosingFrame(frame);
    }

    /**
     * Support for the R {@code attach} function. Set {@code newEncl} as the new enclosing frame for
     * {@code frame} and set the enclosing frame for {@code newEncl} to the previous enclosing frame
     * for {@code frame}. assert {@code} does not denote a function.
     */
    public static void attachFrame(Frame frame, MaterializedFrame newEncl) {
        CompilerAsserts.neverPartOfCompilation();
        Object[] arguments = getRArgumentsWithEvalCheck(frame);
        MaterializedFrame encl = (MaterializedFrame) arguments[INDEX_ENCLOSING_FRAME];
        Object[] newArguments = newEncl.getArguments();
        newArguments[INDEX_ENCLOSING_FRAME] = encl;
        arguments[INDEX_ENCLOSING_FRAME] = newEncl;
        FrameSlotChangeMonitor.invalidateEnclosingFrame(newEncl);
        FrameSlotChangeMonitor.invalidateEnclosingFrame(frame);
    }

    /**
     * Support for the R {@code detach} function. Set the enclosing frame for {@code frame} to the
     * enclosing frame of its current enclosing frame. assert {@code} does not denote a function.
     */
    public static void detachFrame(Frame frame) {
        CompilerAsserts.neverPartOfCompilation();
        Object[] arguments = getRArgumentsWithEvalCheck(frame);
        MaterializedFrame encl = (MaterializedFrame) arguments[INDEX_ENCLOSING_FRAME];
        Object[] enclArguments = encl.getArguments();
        arguments[INDEX_ENCLOSING_FRAME] = enclArguments[INDEX_ENCLOSING_FRAME];
        FrameSlotChangeMonitor.invalidateEnclosingFrame(frame);
    }
}
