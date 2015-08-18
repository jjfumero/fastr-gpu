/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995-2012, The R Core Team
 * Copyright (c) 2003, The R Foundation
 * Copyright (c) 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.runtime;

import java.util.*;
import java.util.Map.Entry;

import com.oracle.truffle.r.runtime.RCmdOptions.RCmdOption;
import com.oracle.truffle.r.runtime.RContext.ContextKind;
import com.oracle.truffle.r.runtime.data.*;

/**
 * Central location for all R options, that is for the {@code options(...)} and {@code getOption}
 * builtins.
 *
 * An unset option does not appear in the map but is represented as the value {@link RNull#instance}
 * . Setting with {@link RNull#instance} removes the option from the map and, therefore, from being
 * visible in a call to {@code options()}. N.B. An option in the {@link #CHECKED_OPTIONS_SET} set
 * can never be removed and this is handled by checking the value passed on update, where
 * {@link RNull#instance} is illegal.
 *
 */
public class ROptions {

    public static final class ContextStateImpl implements RContext.ContextState {
        /**
         * The current values for a given context.
         */
        private final HashMap<String, Object> map;

        ContextStateImpl(HashMap<String, Object> map) {
            this.map = map;
        }

        public Set<Entry<String, Object>> getValues() {
            Set<Map.Entry<String, Object>> result = new HashSet<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (entry.getValue() != null) {
                    result.add(entry);
                }
            }
            return result;
        }

        public Object getValue(String name) {
            Object value = map.get(name);
            if (value == null) {
                value = RNull.instance;
            }
            return value;
        }

        public Object setValueNoCheck(String name, Object value) {
            Object previous = map.get(name);
            assert value != null;
            if (value == RNull.instance) {
                map.remove(name);
            } else {
                map.put(name, value);
            }
            return previous;
        }

        public Object setValue(String name, Object value) throws OptionsException {
            Object coercedValue = value;
            if (CHECKED_OPTIONS_SET.contains(name)) {
                coercedValue = check(name, value);
            }
            return setValueNoCheck(name, coercedValue);
        }

        public static ContextStateImpl newContext(RContext context, REnvVars envVars) {
            HashMap<String, Object> map = new HashMap<>();
            if (context.getKind() == ContextKind.SHARE_NOTHING) {
                applyDefaults(map, context.getOptions(), envVars);
            } else {
                map.putAll(context.getParent().stateROptions.map);
            }
            return new ContextStateImpl(map);
        }
    }

    @SuppressWarnings("serial")
    public static final class OptionsException extends RError.RErrorException {
        private OptionsException(RError.Message msg, Object... args) {
            super(msg, args);
        }

        private static OptionsException createInvalid(String name) {
            return new OptionsException(RError.Message.INVALID_VALUE_FOR, name);
        }
    }

    private static final Set<String> CHECKED_OPTIONS_SET = new HashSet<>(Arrays.asList("width", "deparse.cutoff", "digits", "expressions", "keep.source", "editor", "continue", "prompt", "contrasts",
                    "check.bounds", "warn", "warning.length", "warning.expression", "max.print", "nwarnings", "error", "show.error.messages", "echo", "OutDec", "max.contour.segments",
                    "rl_word_breaks", "warnPartialMatchDollar", "warnPartialMatchArgs", "warnPartialMatchAttr", "showWarnCalls", "showErrorCalls", "showNCalls", "par.ask.default",
                    "browserNLdisabled", "CBoundsCheck"));

    private static void applyDefaults(HashMap<String, Object> map, RCmdOptions options, REnvVars envVars) {
        map.put("add.smooth", RDataFactory.createLogicalVectorFromScalar(true));
        map.put("check.bounds", RDataFactory.createLogicalVectorFromScalar(false));
        map.put("continue", RDataFactory.createStringVector("+ "));
        map.put("deparse.cutoff", RDataFactory.createIntVectorFromScalar(60));
        map.put("digits", RDataFactory.createIntVectorFromScalar(7));
        map.put("echo", RDataFactory.createLogicalVectorFromScalar(options.getBoolean(RCmdOption.SLAVE) ? false : true));
        map.put("encoding", RDataFactory.createStringVector("native.enc"));
        map.put("expressions", RDataFactory.createIntVectorFromScalar(5000));
        boolean keepPkgSource = optionFromEnvVar("R_KEEP_PKG_SOURCE", envVars);
        map.put("keep.source", RDataFactory.createLogicalVectorFromScalar(keepPkgSource));
        map.put("keep.source.pkgs", RDataFactory.createLogicalVectorFromScalar(keepPkgSource));
        map.put("OutDec", RDataFactory.createStringVector("."));
        map.put("prompt", RDataFactory.createStringVector("> "));
        map.put("verbose", RDataFactory.createLogicalVectorFromScalar(false));
        map.put("nwarnings", RDataFactory.createIntVectorFromScalar(50));
        map.put("warning.length", RDataFactory.createIntVectorFromScalar(1000));
        map.put("width", RDataFactory.createIntVectorFromScalar(80));
        map.put("browserNLdisabled", RDataFactory.createLogicalVectorFromScalar(false));
        boolean cBoundsCheck = optionFromEnvVar("R_C_BOUNDS_CHECK", envVars);
        map.put("CBoundsCheck", RDataFactory.createLogicalVectorFromScalar(cBoundsCheck));
    }

    private static boolean optionFromEnvVar(String envVar, REnvVars envVars) {
        return "yes".equals(envVars.get(envVar));
    }

    private static Object check(String name, Object value) throws OptionsException {
        Object coercedValue = value;
        switch (name) {
            case "width": {
                int intValue = RRuntime.asInteger(value);
                if (intValue < 10 || intValue > 10000) {
                    throw OptionsException.createInvalid(name);
                } else {
                    coercedValue = RDataFactory.createIntVectorFromScalar(intValue);
                }
                break;
            }

            case "deparse.cutoff": {
                coercedValue = RRuntime.asInteger(value);
                break;
            }

            case "digits": {
                int intValue = RRuntime.asInteger(value);
                if (intValue < 0 || intValue > 22) {
                    throw OptionsException.createInvalid(name);
                } else {
                    coercedValue = intValue;
                }
                break;
            }

            case "expressions": {
                int intValue = RRuntime.asInteger(value);
                if (intValue < 25 || intValue > 50000) {
                    throw OptionsException.createInvalid(name);
                } else {
                    coercedValue = intValue;
                }
                break;
            }

            case "browserNLdisabled":
            case "CBoundsCheck":
            case "warnPartialMatchDollar":
            case "warnPartialMatchArgs":
            case "warnPartialMatchAttr":
            case "showWarnCalls":
            case "showErrorCalls":
            case "show.error.messages":
            case "check.bounds":
            case "keep.source": {
                Object valueAbs = RRuntime.asAbstractVector(value);
                if (!(valueAbs instanceof RLogicalVector && ((RLogicalVector) valueAbs).getLength() == 1)) {
                    throw OptionsException.createInvalid(name);
                } else {
                    coercedValue = valueAbs;
                }
                break;
            }

            case "editor":
            case "continue":
            case "prompt": {
                Object valueAbs = RRuntime.asAbstractVector(value);
                // TODO supposed to be coerced
                if (valueAbs instanceof RStringVector) {
                    String p = ((RStringVector) valueAbs).getDataAt(0);
                    if (p.length() == 0 || RRuntime.isNA(p)) {
                        throw OptionsException.createInvalid(name);
                    } else {
                        coercedValue = valueAbs;
                    }
                } else {
                    throw OptionsException.createInvalid(name);
                }
                break;
            }

            case "contrasts": {
                Object valueAbs = RRuntime.asAbstractVector(value);
                if (!(valueAbs instanceof RStringVector && ((RStringVector) valueAbs).getLength() == 2)) {
                    throw OptionsException.createInvalid(name);
                } else {
                    coercedValue = valueAbs;
                }
                break;
            }

            case "warn": {
                Object valueAbs = RRuntime.asAbstractVector(value);
                if (valueAbs instanceof RIntVector && ((RIntVector) valueAbs).getLength() == 1) {
                    coercedValue = valueAbs;
                } else if (valueAbs instanceof RDoubleVector && ((RDoubleVector) valueAbs).getLength() == 1) {
                    coercedValue = RDataFactory.createIntVectorFromScalar((int) ((RDoubleVector) valueAbs).getDataAt(0));
                } else {
                    throw OptionsException.createInvalid(name);
                }
                break;
            }

            case "warning.length": {
                int intValue = RRuntime.asInteger(value);
                if (intValue < 100 || intValue > 8170) {
                    throw OptionsException.createInvalid(name);
                } else {
                    coercedValue = intValue;
                }
                break;
            }

            case "warning.expression": {
                if (!(value instanceof RLanguage || value instanceof RExpression)) {
                    throw OptionsException.createInvalid(name);
                }
                break;
            }

            case "max.print": {
                int intValue = RRuntime.asInteger(value);
                if (intValue < 1) {
                    throw OptionsException.createInvalid(name);
                } else {
                    coercedValue = intValue;
                }
                break;
            }

            case "nwarnings": {
                int intValue = RRuntime.asInteger(value);
                if (intValue < 1) {
                    throw OptionsException.createInvalid(name);
                } else {
                    // TODO affect RErrorHandling?
                    coercedValue = intValue;
                }
                break;
            }

            case "error": {
                if (!(value == RNull.instance || value instanceof RFunction || value instanceof RLanguage || value instanceof RExpression)) {
                    throw OptionsException.createInvalid(name);
                }
                break;
            }

            case "echo": {
                Object echoValue = RRuntime.asAbstractVector(value);
                if (!(echoValue instanceof RLogicalVector && ((RLogicalVector) echoValue).getLength() == 1)) {
                    throw OptionsException.createInvalid(name);
                }
                break;
            }

            case "OutDec": {
                Object valueAbs = RRuntime.asAbstractVector(value);
                if (!(valueAbs instanceof RStringVector && ((RStringVector) valueAbs).getLength() == 1 && ((RStringVector) valueAbs).getDataAt(0).length() == 1)) {
                    throw OptionsException.createInvalid(name);
                } else {
                    coercedValue = valueAbs;
                }
                break;
            }

            case "max.contour.segments": {
                int intValue = RRuntime.asInteger(value);
                if (intValue < 0) {
                    throw OptionsException.createInvalid(name);
                } else {
                    coercedValue = intValue;
                }
                break;
            }

            case "rl_word_breaks": {
                Object valueAbs = RRuntime.asAbstractVector(value);
                if (!(valueAbs instanceof RStringVector && ((RStringVector) valueAbs).getLength() == 1)) {
                    throw OptionsException.createInvalid(name);
                } else {
                    coercedValue = valueAbs;
                }
                break;
            }

            case "showNCalls": {
                int intValue = RRuntime.asInteger(value);
                if (intValue < 30 || intValue > 500) {
                    throw OptionsException.createInvalid(name);
                } else {
                    coercedValue = intValue;
                }
                break;
            }

            case "par.ask.default": {
                throw new OptionsException(RError.Message.GENERIC, "\"par.ask.default\" has been replaced by \"device.ask.default\"");
            }

            default:
                throw RInternalError.shouldNotReachHere();
        }
        return coercedValue;
    }

}
