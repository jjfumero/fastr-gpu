/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995-2015, The R Core Team
 * Copyright (c) 2003, The R Foundation
 * Copyright (c) 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.runtime;

import java.util.*;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.context.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.env.*;
import com.oracle.truffle.r.runtime.nodes.*;

/**
 * The details of error handling, including condition handling. Derived from GnUR src/main/errors.c.
 * The public methods in this class are primarily intended for use by the {@code .Internal}
 * functions in {@code ConditionFunctions}. Generally the {@link RError} class should be used for
 * error and warning reporting.
 *
 * FastR does not have access to the call that generated the error or warning as an AST (cf GnuR's
 * CLOSXP pairlist), only the {@link SourceSection} associated with the call. There is a need to
 * pass a value that denotes the call back out to R, e.g. as an argument to
 * {@code .handleSimpleError}. The R code mostly treats this as an opaque object, typically passing
 * it back into a {@code .Internal} that calls this class, but it sometimes calls {@code deparse} on
 * the value if it is not {@link RNull#instance}. Either way it must be a valid {@link RType} to be
 * passed as an argument. For better or worse, we use an {@link RPairList}. We handle the
 * {@code deparse} special case explicitly in our implementation of {@code deparse}.
 * <p>
 * TODO Consider using an {@link RLanguage} object to denote the call (somehow).
 */
public class RErrorHandling {

    private static final int IN_HANDLER = 3;
    private static final RStringVector RESTART_CLASS = RDataFactory.createStringVectorFromScalar("restart");

    public static class Warnings {
        private static ArrayList<Warning> list = new ArrayList<>();

        public int size() {
            return list.size();
        }

        Warning get(int index) {
            return list.get(index);
        }

        public void add(Warning warning) {
            list.add(warning);
        }

        public void clear() {
            list.clear();
        }
    }

    /**
     * Holds all the context-specific state that is relevant for error/warnings. Simple value class
     * for which geterrs/setters are unnecessary.
     */
    public static class ContextStateImpl implements RContext.ContextState {
        /**
         * Values is either NULL or an RPairList, for {@code restarts}.
         */
        private Object restartStack = RNull.instance;
        /**
         * Values is either NULL or an RPairList, for {@code conditions}.
         */
        private Object handlerStack = RNull.instance;
        /**
         * Current list of (deferred) warnings.
         */
        private Warnings warnings = new Warnings();
        /**
         * Max warnings accumulated.
         */
        private int maxWarnings = 50;
        /**
         * Set/get by seterrmessage/geterrmessage builtins.
         */
        private String errMsg;
        /**
         * {@code true} if we are already processing an error.
         */
        private int inError;
        /**
         * {@code true} if we are already processing a warning.
         */
        private boolean inWarning;
        /**
         * {@code true} if the warning should be output immediately.
         */
        private boolean immediateWarning;
        /**
         * {@code true} if the warning should be output on one line.
         */
        @SuppressWarnings("unused") private boolean noBreakWarning;
        /**
         * {@code true} if in {@link #printWarnings}.
         */
        private boolean inPrintWarning;

        /**
         * {@code .signalSimpleWarning} in "conditions.R".
         */
        private RFunction dotSignalSimpleWarning;

        /**
         * Initialize and return the value of {@link #dotSignalSimpleWarning}. This is lazy because
         * when this instance is created, the {@link REnvironment} context state has not been set
         * up, so we can't look up anything in the base env.
         */
        RFunction getDotSignalSimpleWarning() {
            if (dotSignalSimpleWarning == null) {
                Object f = REnvironment.baseEnv().findFunction(".signalSimpleWarning");
                dotSignalSimpleWarning = (RFunction) RContext.getRRuntimeASTAccess().forcePromise(f);
            }
            return dotSignalSimpleWarning;
        }

        public static ContextStateImpl newContext(@SuppressWarnings("unused") RContext context) {
            return new ContextStateImpl();
        }
    }

    /**
     * A temporary class used to accumulate warnings in deferred mode, Eventually these are
     * converted to a list and stored in {@code last.warning} in {@code baseenv}.
     */
    private static class Warning {
        final String message;
        final Object call;

        Warning(String message, Object call) {
            this.message = message;
            this.call = call;
        }
    }

    private static final Object RESTART_TOKEN = new Object();

    private static ContextStateImpl getRErrorHandlingState() {
        return RContext.getInstance().stateRErrorHandling;
    }

    public static Object getHandlerStack() {
        return getRErrorHandlingState().handlerStack;
    }

    public static Object getRestartStack() {
        return getRErrorHandlingState().restartStack;
    }

    public static void restoreStacks(Object savedHandlerStack, Object savedRestartStack) {
        ContextStateImpl errorHandlingState = getRErrorHandlingState();
        errorHandlingState.handlerStack = savedHandlerStack;
        errorHandlingState.restartStack = savedRestartStack;
    }

    public static Object createHandlers(RStringVector classes, RList handlers, REnvironment parentEnv, Object target, byte calling) {
        Object oldStack = getRestartStack();
        Object newStack = oldStack;
        RList result = RDataFactory.createList(new Object[]{RNull.instance, RNull.instance, RNull.instance});
        int n = handlers.getLength();
        for (int i = n - 1; i >= 0; i--) {
            String klass = classes.getDataAt(i);
            Object handler = handlers.getDataAt(i);
            RList entry = mkHandlerEntry(klass, parentEnv, handler, target, result, calling);
            newStack = RDataFactory.createPairList(entry, newStack);
        }
        getRErrorHandlingState().handlerStack = newStack;
        return oldStack;
    }

    private static final int ENTRY_CLASS = 0;
    private static final int ENTRY_CALLING_ENVIR = 1;
    private static final int ENTRY_HANDLER = 2;
    private static final int ENTRY_TARGET_ENVIR = 3;
    private static final int ENTRY_RETURN_RESULT = 4;

    private static final int RESULT_COND = 0;
    private static final int RESULT_CALL = 1;
    private static final int RESULT_HANDLER = 2;

    private static RList mkHandlerEntry(String klass, REnvironment parentEnv, Object handler, Object rho, RList result, byte calling) {
        Object[] data = new Object[5];
        data[ENTRY_CLASS] = klass;
        data[ENTRY_CALLING_ENVIR] = parentEnv;
        data[ENTRY_HANDLER] = handler;
        data[ENTRY_TARGET_ENVIR] = rho;
        data[ENTRY_RETURN_RESULT] = result;
        RList entry = RDataFactory.createList(data);
        entry.setGPBits(calling);
        return entry;

    }

    private static boolean isCallingEntry(RList entry) {
        return entry.getGPBits() != 0;
    }

    @TruffleBoundary
    public static String geterrmessage() {
        return getRErrorHandlingState().errMsg;
    }

    @TruffleBoundary
    public static void seterrmessage(String msg) {
        getRErrorHandlingState().errMsg = msg;
    }

    @TruffleBoundary
    public static void addRestart(RList restart) {
        assert restartExit(restart) instanceof String;
        getRErrorHandlingState().restartStack = RDataFactory.createPairList(restart, getRestartStack());
    }

    private static String castString(Object value) {
        if (value instanceof String) {
            return (String) value;
        } else if (value instanceof RAbstractStringVector) {
            RAbstractStringVector c = (RAbstractStringVector) value;
            if (c.getLength() > 0) {
                return c.getDataAt(0);
            }
        }
        return null;
    }

    private static Object restartExit(RList restart) {
        return restart.getDataAt(0);
    }

    private static MaterializedFrame restartFrame(RList restart) {
        return ((REnvironment) restart.getDataAt(1)).getFrame();
    }

    public static Object getRestart(int index) {
        Object list = getRestartStack();
        int i = index;
        while (list != RNull.instance && i > 1) {
            RPairList pList = (RPairList) list;
            list = pList.cdr();
            i--;
        }
        if (list != RNull.instance) {
            return ((RPairList) list).car();
        } else if (i == 1) {
            Object[] data = new Object[]{"abort", RNull.instance};
            RList result = RDataFactory.createList(data);
            result.setClassAttr(RESTART_CLASS, false);
            return result;
        } else {
            return RNull.instance;
        }
    }

    public static Object invokeRestart(RList restart, Object args) {
        ContextStateImpl errorHandlingState = getRErrorHandlingState();
        Object exit = restartExit(restart);
        if (exit == RNull.instance) {
            errorHandlingState.restartStack = RNull.instance;
            // jump to top top level
            throw RInternalError.unimplemented();
        } else {
            while (errorHandlingState.restartStack != RNull.instance) {
                RPairList pList = (RPairList) errorHandlingState.restartStack;
                RList car = (RList) pList.car();
                if (exit.equals(restartExit(car))) {
                    errorHandlingState.restartStack = pList.cdr();
                    throw new ReturnException(args, RArguments.getDepth(restartFrame(restart)));
                }
                errorHandlingState.restartStack = pList.cdr();
            }
            return null;
        }
    }

    @TruffleBoundary
    public static void signalCondition(RList cond, String msg, Object call) {
        ContextStateImpl errorHandlingState = getRErrorHandlingState();
        Object oldStack = errorHandlingState.handlerStack;
        RPairList pList;
        while ((pList = findConditionHandler(cond)) != null) {
            RList entry = (RList) pList.car();
            errorHandlingState.handlerStack = pList.cdr();
            if (isCallingEntry(entry)) {
                Object h = entry.getDataAt(ENTRY_HANDLER);
                if (h == RESTART_TOKEN) {
                    errorcallDfltWithCall(fromCall(call), Message.GENERIC, msg);
                } else {
                    RFunction hf = (RFunction) h;
                    RContext.getEngine().evalFunction(hf, cond);
                }
            } else {
                throw gotoExitingHandler(cond, call, entry);
            }
        }
        errorHandlingState.handlerStack = oldStack;
    }

    /**
     * Called from {@link RError} to initiate the condition handling logic.
     *
     */
    static void signalError(RBaseNode callObj, Message msg, Object... args) {
        Object call = findCaller(callObj);
        String fMsg = formatMessage(msg, args);
        ContextStateImpl errorHandlingState = getRErrorHandlingState();
        Object oldStack = errorHandlingState.handlerStack;
        RPairList pList;
        while ((pList = findSimpleErrorHandler()) != null) {
            RList entry = (RList) pList.car();
            errorHandlingState.handlerStack = pList.cdr();
            errorHandlingState.errMsg = fMsg;
            if (isCallingEntry(entry)) {
                if (entry.getDataAt(ENTRY_HANDLER) == RESTART_TOKEN) {
                    return;
                } else {
                    RFunction handler = (RFunction) entry.getDataAt(2);
                    RStringVector errorMsgVec = RDataFactory.createStringVectorFromScalar(fMsg);
                    RContext.getRRuntimeASTAccess().callback(handler, new Object[]{errorMsgVec, call});
                }
            } else {
                throw gotoExitingHandler(RNull.instance, call, entry);
            }
        }
        errorHandlingState.handlerStack = oldStack;
    }

    private static ReturnException gotoExitingHandler(Object cond, Object call, RList entry) throws ReturnException {
        REnvironment rho = (REnvironment) entry.getDataAt(ENTRY_TARGET_ENVIR);
        RList result = (RList) entry.getDataAt(ENTRY_RETURN_RESULT);
        Object[] resultData = result.getDataWithoutCopying();
        resultData[RESULT_COND] = cond;
        resultData[RESULT_CALL] = call;
        resultData[RESULT_HANDLER] = entry.getDataAt(ENTRY_HANDLER);
        throw new ReturnException(result, RArguments.getDepth(rho.getFrame()));
    }

    private static RPairList findSimpleErrorHandler() {
        Object list = getHandlerStack();
        while (list != RNull.instance) {
            RPairList pList = (RPairList) list;
            RList entry = (RList) pList.car();
            String klass = (String) entry.getDataAt(0);
            if (klass.equals("simpleError") || klass.equals("error") || klass.equals("condition")) {
                return pList;
            }
            list = pList.cdr();
        }
        return null;
    }

    private static RPairList findConditionHandler(RList cond) {
        // GnuR checks whether this is a string vector - in FastR it's statically typed to be
        RStringVector classes = cond.getClassHierarchy();
        Object list = getHandlerStack();
        while (list != RNull.instance) {
            RPairList pList = (RPairList) list;
            RList entry = (RList) pList.car();
            String klass = (String) entry.getDataAt(0);
            for (int i = 0; i < classes.getLength(); i++) {
                if (klass.equals(classes.getDataAt(i))) {
                    return pList;
                }
            }
            list = pList.cdr();
        }
        return null;

    }

    @TruffleBoundary
    public static void dfltStop(String msg, Object call) {
        errorcallDfltWithCall(fromCall(call), Message.GENERIC, msg);
    }

    @TruffleBoundary
    public static void dfltWarn(String msg, Object call) {
        warningcallDfltWithCall(fromCall(call), Message.GENERIC, msg);
    }

    /**
     * Check a {@code call} value.
     *
     * @param call Either {@link RNull#instance} or an {@link RLanguage}.
     * @return {@code null} iff {@code call == RNull.instance} else cast to {@link RLanguage}.
     */
    private static Object fromCall(Object call) {
        if (!(call == RNull.instance || call instanceof RLanguage)) {
            throw RInternalError.shouldNotReachHere();
        }
        return call;
    }

    private static Object findCaller(RBaseNode callObj) {
        return RContext.getRRuntimeASTAccess().findCaller(callObj);
    }

    static RError errorcallDflt(boolean showCall, RBaseNode callObj, Message msg, Object... objects) throws RError {
        return errorcallDfltWithCall(showCall ? findCaller(callObj) : RNull.instance, msg, objects);
    }

    static RError errorcallDflt(RBaseNode callObj, Message msg, Object... objects) throws RError {
        return errorcallDfltWithCall(findCaller(callObj), msg, objects);
    }

    /**
     * The default error handler. This is where all the error message formatting is done and the
     * output.
     */
    static RError errorcallDfltWithCall(Object call, Message msg, Object... objects) throws RError {
        String fmsg = formatMessage(msg, objects);

        String errorMessage = createErrorMessage(call, fmsg);

        ContextStateImpl errorHandlingState = getRErrorHandlingState();
        if (errorHandlingState.inError > 0) {
            // recursive error
            if (errorHandlingState.inError == IN_HANDLER) {
                Utils.writeStderr("Error during wrapup: ", false);
                Utils.writeStderr(errorMessage, true);
            }
            if (errorHandlingState.warnings.size() > 0) {
                errorHandlingState.warnings.clear();
                Utils.writeStderr("Lost warning messages", true);
            }
            throw new RError(errorMessage);
        }

        Utils.writeStderr(errorMessage, true);

        if (getRErrorHandlingState().warnings.size() > 0) {
            Utils.writeStderr("In addition: ", false);
            printWarnings(false);
        }

        // we are not quite done - need to check for options(error=expr)
        Object errorExpr = RContext.getInstance().stateROptions.getValue("error");
        if (errorExpr != RNull.instance) {
            int oldInError = errorHandlingState.inError;
            try {
                errorHandlingState.inError = IN_HANDLER;
                MaterializedFrame materializedFrame = safeCurrentFrame();
                // type already checked in ROptions
                if (errorExpr instanceof RFunction) {
                    // Called with no arguments, but defaults will be applied
                    RFunction errorFunction = (RFunction) errorExpr;
                    ArgumentsSignature argsSig = RContext.getRRuntimeASTAccess().getArgumentsSignature(errorFunction);
                    Object[] evaluatedArgs;
                    if (errorFunction.isBuiltin()) {
                        evaluatedArgs = RContext.getRRuntimeASTAccess().getBuiltinDefaultParameterValues(errorFunction);
                    } else {
                        evaluatedArgs = new Object[argsSig.getLength()];
                        for (int i = 0; i < evaluatedArgs.length; i++) {
                            evaluatedArgs[i] = RMissing.instance;
                        }
                    }
                    RContext.getEngine().evalFunction(errorFunction, evaluatedArgs);
                } else if (errorExpr instanceof RLanguage || errorExpr instanceof RExpression) {
                    if (errorExpr instanceof RLanguage) {
                        RContext.getEngine().eval((RLanguage) errorExpr, materializedFrame);
                    } else if (errorExpr instanceof RExpression) {
                        RContext.getEngine().eval((RExpression) errorExpr, materializedFrame);
                    }
                } else {
                    // Checked when set
                    throw RInternalError.shouldNotReachHere();
                }
            } finally {
                errorHandlingState.inError = oldInError;
            }
        }
        throw new RError(errorMessage);
    }

    private static MaterializedFrame safeCurrentFrame() {
        Frame frame = Utils.getActualCurrentFrame();
        return frame == null ? REnvironment.globalEnv().getFrame() : frame.materialize();
    }

    /**
     * Entry point for the {@code warning} {@code .Internal}.
     *
     * @param showCall {@true} iff call to be included in message
     * @param message the message
     * @param immediate {@code true} iff the output should be immediate
     * @param noBreakWarning TODOx
     */
    public static void warningcallInternal(boolean showCall, RBaseNode callObj, String message, boolean immediate, boolean noBreakWarning) {
        // TODO handle noBreakWarning
        ContextStateImpl errorHandlingState = getRErrorHandlingState();
        boolean immediateWarningSave = errorHandlingState.immediateWarning;
        try {
            errorHandlingState.immediateWarning = immediate;
            warningcall(showCall, callObj, RError.Message.GENERIC, message);
        } finally {
            errorHandlingState.immediateWarning = immediateWarningSave;
        }
    }

    static void warningcall(boolean showCall, RBaseNode callObj, Message msg, Object... args) {
        ContextStateImpl errorHandlingState = getRErrorHandlingState();
        Object call = showCall ? findCaller(callObj) : RNull.instance;
        RStringVector warningMessage = RDataFactory.createStringVectorFromScalar(formatMessage(msg, args));
        /*
         * Warnings generally do not prevent results being printed. However, this call into R will
         * destroy any visibility setting made by the calling builtin prior to this call. So we save
         * and restore it across the call.
         */
        boolean visibility = RContext.getInstance().isVisible();
        try {
            RFunction f = errorHandlingState.getDotSignalSimpleWarning();
            RContext.getRRuntimeASTAccess().callback(f, new Object[]{warningMessage, call});
        } finally {
            RContext.getInstance().setVisible(visibility);
        }
    }

    static void warningcallDflt(Message msg, Object... args) {
        warningcallDfltWithCall(findCaller(null), msg, args);
    }

    static void warningcallDfltWithCall(Object call, Message msg, Object... args) {
        ContextStateImpl errorHandlingState = getRErrorHandlingState();
        if (errorHandlingState.inWarning) {
            return;
        }
        Object s = RContext.getInstance().stateROptions.getValue("warning.expression");
        if (s != RNull.instance) {
            if (!(s instanceof RLanguage || s instanceof RExpression)) {
                // TODO
            }
            throw RInternalError.unimplemented();
        }

        // ensured in ROptions

        Object value = RContext.getInstance().stateROptions.getValue("warn");
        int w = 0;
        if (value != RNull.instance) {
            w = ((RAbstractIntVector) value).getDataAt(0);
        }
        if (w == RRuntime.INT_NA) {
            w = 0;
        }
        if (w <= 0 && errorHandlingState.immediateWarning) {
            w = 1;
        }

        if (w < 0 || errorHandlingState.inWarning || errorHandlingState.inError > 0) {
            /*
             * ignore if w<0 or already in here
             */
            return;
        }

        try {
            errorHandlingState.inWarning = true;
            String fmsg = formatMessage(msg, args);
            String message = createWarningMessage(call, fmsg);
            if (w >= 2) {
                throw RInternalError.unimplemented();
            } else if (w == 1) {
                Utils.writeStderr(message, true);
            } else if (w == 0) {
                errorHandlingState.warnings.add(new Warning(fmsg, call));
            }
        } finally {
            errorHandlingState.inWarning = false;
        }
    }

    @TruffleBoundary
    public static void printWarnings(boolean suppress) {
        ContextStateImpl errorHandlingState = getRErrorHandlingState();
        Warnings warnings = errorHandlingState.warnings;
        if (suppress) {
            warnings.clear();
            return;
        }
        int nWarnings = warnings.size();
        if (nWarnings == 0) {
            return;
        }
        if (errorHandlingState.inPrintWarning) {
            if (nWarnings > 0) {
                warnings.clear();
                Utils.writeStderr("Lost warning messages", true);
            }
            return;
        }
        try {
            errorHandlingState.inPrintWarning = true;
            if (nWarnings == 1) {
                Utils.writeStderr("Warning message:", true);
                Warning warning = warnings.get(0);
                if (warning.call == RNull.instance) {
                    Utils.writeStderr(warning.message, true);
                } else {
                    String callSource = RContext.getRRuntimeASTAccess().getCallerSource((RLanguage) warning.call);
                    Utils.writeStderr(String.format("In %s : %s", callSource, warning.message), true);
                }
            } else if (nWarnings <= 10) {
                Utils.writeStderr("Warning messages:", true);
                for (int i = 0; i < nWarnings; i++) {
                    Utils.writeStderr((i + 1) + ":", true);
                    Utils.writeStderr("  " + warnings.get(i).message, true);
                }
            } else {
                if (nWarnings < errorHandlingState.maxWarnings) {
                    Utils.writeStderr(String.format("There were %d warnings (use warnings() to see them)", nWarnings), true);
                } else {
                    Utils.writeStderr(String.format("There were %d or more warnings (use warnings() to see the first %d)", nWarnings, errorHandlingState.maxWarnings), true);
                }
            }
            Object[] wData = new Object[nWarnings];
            String[] names = new String[nWarnings];
            for (int i = 0; i < nWarnings; i++) {
                wData[i] = warnings.get(i).call;
                names[i] = warnings.get(i).message;
            }
            RList lw = RDataFactory.createList(wData, RDataFactory.createStringVector(names, RDataFactory.COMPLETE_VECTOR));
            REnvironment.baseEnv().safePut("last.warning", lw);
        } finally {
            errorHandlingState.inPrintWarning = false;
            warnings.clear();
        }
    }

    public static void printDeferredWarnings() {
        if (getRErrorHandlingState().warnings.size() > 0) {
            Utils.writeStderr("In addition: ", false);
            printWarnings(false);
        }
    }

    /**
     * Converts a {@link RError.Message}, that possibly requires arguments into a {@link String}.
     */
    static String formatMessage(RError.Message msg, Object... args) {
        return msg.hasArgs ? String.format(msg.message, args) : msg.message;
    }

    static String wrapMessage(String preamble, String message) {
        // TODO find out about R's line-wrap policy
        // (is 74 a given percentage of console width?)
        if (preamble.length() + 1 + message.length() >= 74) {
            // +1 is for the extra space following the colon
            return preamble + "\n  " + message;
        } else {
            return preamble + " " + message;
        }
    }

    static String createErrorMessage(Object call, String formattedMsg) {
        return createKindMessage("Error", call, formattedMsg);
    }

    static String createWarningMessage(Object call, String formattedMsg) {
        return createKindMessage("Warning", call, formattedMsg);
    }

    /**
     * Creates an error message suitable for output to the user, taking into account {@code src},
     * which may be {@code null}.
     */
    static String createKindMessage(String kind, Object call, String formattedMsg) {
        String preamble = kind;
        String errorMsg = null;
        if (call == RNull.instance) {
            // generally means top-level of shell or similar
            preamble += ": ";
            errorMsg = preamble + formattedMsg;
        } else {
            RLanguage rl = (RLanguage) call;
            preamble += " in " + RContext.getRRuntimeASTAccess().getCallerSource(rl) + " :";
            errorMsg = wrapMessage(preamble, formattedMsg);
        }
        return errorMsg;
    }

}
