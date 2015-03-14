/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995-2012, The R Core Team
 * Copyright (c) 2003, The R Foundation
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.runtime;

import java.text.*;
import java.util.*;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.env.frame.*;

import edu.umd.cs.findbugs.annotations.*;

public class RRuntime {

    //@formatter:off
    // Parts of the welcome message originate from GNU R.
    public static final String WELCOME_MESSAGE =
        "FastR version " + RVersionNumber.FULL + "\n" +
        "Copyright (c) 2013-5, Oracle and/or its affiliates\n" +
        "Copyright (c) 1995-2015, The R Core Team\n" +
        "Copyright (c) 2015 The R Foundation\n" +
        "Copyright (c) 2012-4 Purdue University\n" +
        "Copyright (c) 1997-2002, Makoto Matsumoto and Takuji Nishimura\n" +
        "All rights reserved.\n" +
        "\n" +
        "FastR is free software and comes with ABSOLUTELY NO WARRANTY.\n" +
        "You are welcome to redistribute it under certain conditions.\n" +
        "Type 'license()' or 'licence()' for distribution details.\n" +
        "\n" +
        "R is a collaborative project with many contributors.\n" +
        "Type 'contributors()' for more information.\n" +
        "\n" +
        "Type 'q()' to quit R.";

    public static final String LICENSE =
        "This software is distributed under the terms of the GNU General Public License\n" +
        "Version 2, June 1991. The terms of the license are in a file called COPYING\n" +
        "which you should have received with this software. A copy of the license can be\n" +
        "found at http://www.gnu.org/licenses/gpl-2.0.html.\n" +
        "\n" +
        "'Share and Enjoy.'";
    //@formatter:on

    public static final String STRING_NA = new String("NA");
    public static final String STRING_NaN = new String("NaN");
    public static final String STRING_TRUE = new String("TRUE");
    public static final String STRING_FALSE = new String("FALSE");
    public static final int INT_NA = Integer.MIN_VALUE;
    public static final int INT_MIN_VALUE = Integer.MIN_VALUE + 1;
    public static final int INT_MAX_VALUE = Integer.MAX_VALUE;

    // R's NA is a special instance of IEEE's NaN
    public static final long NA_LONGBITS = 0x7ff00000000007a2L;
    public static final double DOUBLE_NA = Double.longBitsToDouble(NA_LONGBITS);
    public static final double EPSILON = Math.pow(2.0, -52.0);

    public static final double COMPLEX_NA_REAL_PART = DOUBLE_NA;
    public static final double COMPLEX_NA_IMAGINARY_PART = 0.0;

    public static final byte LOGICAL_TRUE = 1;
    public static final byte LOGICAL_FALSE = 0;
    public static final byte LOGICAL_NA = -1;

    public static final String CLASS_SYMBOL = "name";
    public static final String CLASS_LANGUAGE = "call";
    public static final String CLASS_EXPRESSION = "expression";

    @CompilationFinal public static final String[] STRING_ARRAY_SENTINEL = new String[0];
    public static final String DEFAULT = "default";

    public static final String NAMES_ATTR_KEY = "names";
    public static final String NAMES_ATTR_EMPTY_VALUE = "";

    public static final String LEVELS_ATTR_KEY = "levels";

    public static final String NA_HEADER = "<NA>";

    public static final String DIM_ATTR_KEY = "dim";
    public static final String DIMNAMES_ATTR_KEY = "dimnames";
    public static final String DIMNAMES_LIST_ELEMENT_NAME_PREFIX = "$dimnames";

    public static final String CLASS_ATTR_KEY = "class";
    public static final String PREVIOUS_ATTR_KEY = "previous";
    public static final String ROWNAMES_ATTR_KEY = "row.names";

    @CompilationFinal public static final String[] CLASS_INTEGER = new String[]{"integer", "numeric"};
    @CompilationFinal public static final String[] CLASS_DOUBLE = new String[]{"double", "numeric"};

    public static final String CLASS_ORDERED = "ordered";

    public static final int LEN_METHOD_NAME = 512;

    public static final String RDotGeneric = ".Generic";

    public static final String RDotMethod = ".Method";

    public static final String RDotClass = ".Class";

    public static final String RDotGenericCallEnv = ".GenericCallEnv";

    public static final String RDotGenericDefEnv = ".GenericDefEnv";

    public static final String RDotGroup = ".Group";

    public static final String RDOT = ".";

    public static final String SYSTEM_DATE_FORMAT = "EEE MMM dd HH:mm:ss yyyy";

    public static final String NULL = "NULL";

    @CompilationFinal private static final String[] numberStringCache = new String[4096];
    private static final int MIN_CACHED_NUMBER = -numberStringCache.length / 2;
    private static final int MAX_CACHED_NUMBER = numberStringCache.length / 2 - 1;

    static {
        for (int i = 0; i < numberStringCache.length; i++) {
            numberStringCache[i] = String.valueOf(i + MIN_CACHED_NUMBER);
        }
    }

    /**
     * Create an {@link VirtualFrame} for a non-function environment, e.g., a package frame or the
     * global environment.
     */
    public static VirtualFrame createNonFunctionFrame() {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        FrameSlotChangeMonitor.initializeNonFunctionFrameDescriptor(frameDescriptor, false);
        return Truffle.getRuntime().createVirtualFrame(RArguments.createUnitialized(), frameDescriptor);
    }

    public static RComplex createComplexNA() {
        return RDataFactory.createComplex(COMPLEX_NA_REAL_PART, COMPLEX_NA_IMAGINARY_PART);
    }

    public static boolean isNAorNaN(double d) {
        return isNA(d) || Double.isNaN(d);
    }

    @TruffleBoundary
    // TODO refactor this into RType so it is complete and more efficient
    public static String classToString(Class<?> c, boolean numeric) {
        if (c == RLogical.class) {
            return RType.Logical.getName();
        } else if (c == RInt.class) {
            return RType.Integer.getName();
        } else if (c == RDouble.class) {
            return numeric ? RType.Numeric.getName() : RType.Double.getName();
        } else if (c == RComplex.class) {
            return RType.Complex.getName();
        } else if (c == RRaw.class) {
            return RType.Raw.getName();
        } else if (c == RString.class) {
            return RType.Character.getName();
        } else if (c == RFunction.class) {
            return RType.Function.getName();
        } else {
            throw new RuntimeException("internal error, unknown class: " + c);
        }
    }

    @TruffleBoundary
    public static String classToString(Class<?> c) {
        return classToString(c, true);
    }

    @TruffleBoundary
    // TODO refactor this into RType so it is complete and more efficient
    public static String classToStringCap(Class<?> c) {
        if (c == RLogical.class) {
            return "Logical";
        } else if (c == RInt.class) {
            return "Integer";
        } else if (c == RDouble.class) {
            return "Numeric";
        } else if (c == RComplex.class) {
            return "Complex";
        } else if (c == RRaw.class) {
            return "Raw";
        } else if (c == RString.class) {
            return "Character";
        } else {
            throw new RuntimeException("internal error, unknown class: " + c);
        }
    }

    public static boolean isFinite(double d) {
        return !isNAorNaN(d) && !Double.isInfinite(d);
    }

    public static boolean doubleIsInt(double d) {
        long longValue = (long) d;
        return longValue == d && ((int) longValue & 0xffffffff) == longValue;
    }

    public static byte asLogical(boolean b) {
        return b ? LOGICAL_TRUE : LOGICAL_FALSE;
    }

    public static boolean fromLogical(byte b) {
        return b == LOGICAL_TRUE;
    }

    // conversions from logical

    public static int logical2intNoCheck(byte value) {
        return value;
    }

    public static int logical2int(byte value) {
        return isNA(value) ? INT_NA : logical2intNoCheck(value);
    }

    public static double logical2doubleNoCheck(byte value) {
        return value;
    }

    public static double logical2double(byte value) {
        return isNA(value) ? DOUBLE_NA : logical2doubleNoCheck(value);
    }

    public static RComplex logical2complexNoCheck(byte value) {
        return RDataFactory.createComplex(value, 0);
    }

    public static RComplex logical2complex(byte value) {
        return isNA(value) ? createComplexNA() : logical2complexNoCheck(value);
    }

    public static String logicalToStringNoCheck(byte operand) {
        assert operand == LOGICAL_TRUE || operand == LOGICAL_FALSE;
        return operand == LOGICAL_TRUE ? STRING_TRUE : STRING_FALSE;
    }

    public static String logicalToString(byte operand) {
        return isNA(operand) ? STRING_NA : logicalToStringNoCheck(operand);
    }

    // conversions from raw

    public static byte raw2logical(RRaw value) {
        return value.getValue() == 0 ? LOGICAL_FALSE : LOGICAL_TRUE;
    }

    public static int raw2int(RRaw value) {
        return value.getValue() & 0xFF;
    }

    public static double raw2double(RRaw value) {
        return int2double(value.getValue() & 0xFF);
    }

    public static RComplex raw2complex(RRaw r) {
        return int2complex(raw2int(r));
    }

    @TruffleBoundary
    public static String rawToString(RRaw operand) {
        return intToString(raw2int(operand));
    }

    // conversions from string

    @TruffleBoundary
    public static int string2intNoCheck(String s, boolean exceptionOnFail) {
        // FIXME use R rules
        try {
            return Integer.decode(s);  // decode supports hex constants
        } catch (NumberFormatException e) {
            if (exceptionOnFail) {
                throw e;
            } else {
                RContext.getInstance().getAssumptions().naIntroduced.invalidate();
            }
        }
        return INT_NA;
    }

    @TruffleBoundary
    public static int string2intNoCheck(String s) {
        return string2intNoCheck(s, false);
    }

    @TruffleBoundary
    public static int string2int(String s) {
        return isNA(s) ? INT_NA : string2intNoCheck(s);
    }

    @TruffleBoundary
    public static double string2doubleNoCheck(String v, boolean exceptionOnFail) {
        // FIXME use R rules
        if ("Inf".equals(v)) {
            return Double.POSITIVE_INFINITY;
        } else if ("NaN".equals(v)) {
            return Double.NaN;
        } else if ("NA_real_".equals(v)) {
            return DOUBLE_NA;
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            if (v.startsWith("0x")) {
                try {
                    return int2double(Integer.decode(v));
                } catch (NumberFormatException ein) {
                }
            }
            if (exceptionOnFail) {
                throw e;
            } else {
                RContext.getInstance().getAssumptions().naIntroduced.invalidate();
            }
        }
        return DOUBLE_NA;
    }

    @TruffleBoundary
    public static double string2doubleNoCheck(String v) {
        return string2doubleNoCheck(v, false);
    }

    @TruffleBoundary
    public static double string2double(String v) {
        if (isNA(v)) {
            return DOUBLE_NA;
        } else {
            return string2doubleNoCheck(v);
        }
    }

    public static byte string2logicalNoCheck(String s, boolean exceptionOnFail) {
        switch (s) {
            case "TRUE":
            case "T":
            case "True":
            case "true":
                return LOGICAL_TRUE;
            case "FALSE":
            case "F":
            case "False":
            case "false":
                return LOGICAL_FALSE;
            default:
                if (exceptionOnFail) {
                    throw new NumberFormatException();
                } else {
                    RContext.getInstance().getAssumptions().naIntroduced.invalidate();
                }
                return LOGICAL_NA;
        }
    }

    public static byte string2logicalNoCheck(String s) {
        return string2logicalNoCheck(s, false);
    }

    public static byte string2logical(String s) {
        return isNA(s) ? LOGICAL_NA : string2logicalNoCheck(s);
    }

    @TruffleBoundary
    public static RComplex string2complexNoCheck(String v) {
        double doubleValue = string2doubleNoCheck(v);
        if (!RRuntime.isNA(doubleValue)) {
            return RDataFactory.createComplex(doubleValue, 0.0);
        } else {
            try {
                int startIdx = 0;
                char firstChar = v.charAt(0);
                boolean negativeReal = firstChar == '-';
                if (firstChar == '+' || negativeReal) {
                    startIdx++;
                }

                int plusIdx = v.indexOf("+", startIdx);
                int minusIdx = v.indexOf("-", startIdx);
                int iIdx = v.indexOf("i", startIdx);
                int signIdx = getSignIdx(plusIdx, minusIdx);
                boolean negativeImaginary = minusIdx > 0;

                double realPart = Double.parseDouble(v.substring(startIdx, signIdx));
                double imaginaryPart = Double.parseDouble(v.substring(signIdx + 1, iIdx));

                return RDataFactory.createComplex(realPart * (negativeReal ? -1 : 1), imaginaryPart * (negativeImaginary ? -1 : 1));
            } catch (NumberFormatException ex) {
                return createComplexNA();
            }
        }
    }

    @TruffleBoundary
    public static RComplex string2complex(String v) {
        return isNA(v) ? createComplexNA() : string2complexNoCheck(v);
    }

    @TruffleBoundary
    public static RRaw string2raw(String v) {
        if (v.length() == 2 && (Utils.isIsoLatinDigit(v.charAt(0)) || Utils.isRomanLetter(v.charAt(0))) && (Utils.isIsoLatinDigit(v.charAt(1)) || Utils.isRomanLetter(v.charAt(1)))) {
            return RDataFactory.createRaw(Byte.parseByte(v, 16));
        } else {
            return RDataFactory.createRaw((byte) 0);
        }
    }

    // conversions from int

    public static double int2doubleNoCheck(int i) {
        return i;
    }

    public static double int2double(int i) {
        return isNA(i) ? DOUBLE_NA : int2doubleNoCheck(i);
    }

    public static byte int2logicalNoCheck(int i) {
        return i == 0 ? LOGICAL_FALSE : LOGICAL_TRUE;
    }

    public static byte int2logical(int i) {
        return isNA(i) ? LOGICAL_NA : int2logicalNoCheck(i);
    }

    public static RComplex int2complexNoCheck(int i) {
        return RDataFactory.createComplex(i, 0);
    }

    public static RComplex int2complex(int i) {
        return isNA(i) ? createComplexNA() : int2complexNoCheck(i);
    }

    @TruffleBoundary
    public static String intToStringNoCheck(int operand) {
        if (operand >= MIN_CACHED_NUMBER && operand <= MAX_CACHED_NUMBER) {
            return numberStringCache[operand - MIN_CACHED_NUMBER];
        } else {
            return String.valueOf(operand);
        }
    }

    public static String intToString(int operand) {
        return isNA(operand) ? STRING_NA : intToStringNoCheck(operand);
    }

    public static int int2rawIntValue(int i) {
        return isNA(i) ? 0 : i & 0xFF;
    }

    public static RRaw int2raw(int i) {
        return RDataFactory.createRaw((byte) int2rawIntValue(i));
    }

    // conversions from double

    public static int double2intNoCheck(double d) {
        return (int) d;
    }

    public static int double2int(double d) {
        return isNA(d) ? INT_NA : double2intNoCheck(d);
    }

    public static byte double2logicalNoCheck(double d) {
        return d == 0.0 ? LOGICAL_FALSE : LOGICAL_TRUE;
    }

    public static byte double2logical(double d) {
        return isNA(d) ? LOGICAL_NA : double2logicalNoCheck(d);
    }

    public static RComplex double2complexNoCheck(double d) {
        return RDataFactory.createComplex(d, 0);
    }

    public static RComplex double2complex(double d) {
        return isNAorNaN(d) ? createComplexNA() : double2complexNoCheck(d);
    }

    @TruffleBoundary
    public static String doubleToString(double operand, int digitsBehindDot) {
        return isNA(operand) ? STRING_NA : doubleToStringNoCheck(operand, digitsBehindDot);
    }

    @TruffleBoundary
    public static String doubleToStringNoCheck(double operand, int digitsBehindDot) {
        if (doubleIsInt(operand)) {
            return intToStringNoCheck((int) operand);
        }
        if (operand == Double.POSITIVE_INFINITY) {
            return "Inf";
        }
        if (operand == Double.NEGATIVE_INFINITY) {
            return "-Inf";
        }
        if (Double.isNaN(operand)) {
            return STRING_NaN;
        }

        /*
         * DecimalFormat format = new DecimalFormat(); format.setMaximumIntegerDigits(12);
         * format.setMaximumFractionDigits(12); format.setGroupingUsed(false); return
         * format.format(operand);
         */
        if (operand < 1000000000000L && ((long) operand) == operand) {
            return Long.toString((long) operand);
        }
        if (operand > 1000000000000L) {
            return String.format((Locale) null, "%.6e", operand);
        }
// if (true || operand < 0.0001) {
// // not quite correct but better than nothing for now...
// return String.format((Locale) null, "%.22e", new BigDecimal(operand));
// }
        if (digitsBehindDot == -1) {
            return Double.toString(operand);
        } else {
            StringBuilder sb = new StringBuilder("#.");
            for (int i = 0; i < digitsBehindDot; i++) {
                sb.append('#');
            }
            DecimalFormat df = new DecimalFormat(sb.toString());
            return df.format(operand);
        }
    }

    public static String doubleToStringNoCheck(double operand) {
        return doubleToStringNoCheck(operand, -1);
    }

    public static String doubleToString(double operand) {
        return isNA(operand) ? STRING_NA : doubleToStringNoCheck(operand);
    }

    public static int double2rawIntValue(double operand) {
        return isNA(operand) ? 0 : ((int) operand) & 0xFF;
    }

    public static RRaw double2raw(double operand) {
        return RDataFactory.createRaw((byte) double2rawIntValue(operand));
    }

    // conversions from complex

    public static byte complex2logicalNoCheck(RComplex c) {
        return c.getRealPart() == 0.0 && c.getImaginaryPart() == 0.0 ? LOGICAL_FALSE : LOGICAL_TRUE;
    }

    public static byte complex2logical(RComplex c) {
        return isNA(c) ? LOGICAL_NA : complex2logicalNoCheck(c);
    }

    public static int complex2intNoCheck(RComplex c) {
        return double2intNoCheck(c.getRealPart());
    }

    public static int complex2int(RComplex c) {
        return isNA(c) ? LOGICAL_NA : complex2intNoCheck(c);
    }

    public static double complex2doubleNoCheck(RComplex c) {
        return double2intNoCheck(c.getRealPart());
    }

    public static double complex2double(RComplex c) {
        return isNA(c) ? LOGICAL_NA : complex2doubleNoCheck(c);
    }

    @TruffleBoundary
    public static String complexToStringNoCheck(RComplex operand) {
        return doubleToString(operand.getRealPart()) + "+" + doubleToString(operand.getImaginaryPart()) + "i";
    }

    @TruffleBoundary
    public static String complexToString(RComplex operand) {
        return isNA(operand) ? STRING_NA : complexToStringNoCheck(operand);
    }

    public static int complex2rawIntValue(RComplex c) {
        return isNA(c) ? 0 : ((int) c.getRealPart() & 0xFF);
    }

    public static RRaw complex2raw(RComplex c) {
        return RDataFactory.createRaw((byte) complex2rawIntValue(c));
    }

    private static int getSignIdx(int plusIdx, int minusIdx) throws NumberFormatException {
        if (plusIdx < 0) {
            if (minusIdx < 0) {
                throw new NumberFormatException();
            }
            return minusIdx;
        } else {
            if (minusIdx < 0) {
                return plusIdx;
            }
            throw new NumberFormatException();
        }
    }

    @TruffleBoundary
    public static String toString(Object object) {
        if (object instanceof Integer) {
            return intToString((int) object);
        } else if (object instanceof Double) {
            return doubleToString((double) object);
        } else if (object instanceof Byte) {
            return logicalToString((byte) object);
        }

        return object.toString();
    }

    @TruffleBoundary
    public static String toString(StringBuilder sb) {
        return sb.toString();
    }

    @SuppressFBWarnings(value = "ES_COMPARING_PARAMETER_STRING_WITH_EQ", justification = "string NA is intended to be treated as an identity")
    public static boolean isNA(String value) {
        return value == STRING_NA;
    }

    @SuppressFBWarnings(value = "ES_COMPARING_PARAMETER_STRING_WITH_EQ", justification = "string NaN is intended to be treated as an identity")
    public static boolean isNaN(String value) {
        return value == STRING_NaN;
    }

    public static boolean isNA(byte value) {
        return value == LOGICAL_NA;
    }

    public static boolean isNA(int value) {
        return value == INT_NA;
    }

    public static boolean isNA(double value) {
        return Double.doubleToRawLongBits(value) == NA_LONGBITS;
    }

    public static boolean isNA(RComplex value) {
        return isNA(value.getRealPart());
    }

    public static boolean isComplete(String value) {
        return !isNA(value);
    }

    public static boolean isComplete(byte value) {
        return !isNA(value);
    }

    public static boolean isComplete(int value) {
        return !isNA(value);
    }

    public static boolean isComplete(double value) {
        return !isNA(value);
    }

    public static boolean isComplete(RComplex value) {
        return !isNA(value);
    }

    @TruffleBoundary
    public static String quoteString(String value) {
        return isNA(value) ? STRING_NA : "\"" + value + "\"";
    }

    public static FrameSlotKind getSlotKind(Object value) {
        if (value == null) {
            return FrameSlotKind.Illegal;
        }
        if (value instanceof Byte) {
            return FrameSlotKind.Byte;
        } else if (value instanceof Integer) {
            return FrameSlotKind.Int;
        } else if (value instanceof Double) {
            return FrameSlotKind.Double;
        } else {
            return FrameSlotKind.Object;
        }
    }

    /**
     * Checks and converts an object into a String.
     */
    public static String asString(Object obj) {
        if (obj instanceof String) {
            return (String) obj;
        } else if (obj instanceof RStringVector) {
            return ((RStringVector) obj).getDataAt(0);
        } else {
            return null;
        }
    }

    public static boolean checkType(Object obj, RType type) {
        if (type == RType.Any) {
            return true;
        }
        if (type == RType.Function || type == RType.Closure || type == RType.Builtin || type == RType.Special) {
            return obj instanceof RFunction;
        }
        if (type == RType.Character) {
            return obj instanceof String || obj instanceof RStringVector;
        }
        if (type == RType.Logical) {
            return obj instanceof Byte;
        }
        if (type == RType.Integer || type == RType.Double || type == RType.Numeric) {
            return obj instanceof Integer || obj instanceof Double;
        }
        return false;
    }

    /**
     * Runtime variant of DSL support for converting scalar values to {@link RAbstractVector}.
     */
    public static Object asAbstractVector(Object obj) {
        if (obj instanceof Integer) {
            return RDataFactory.createIntVectorFromScalar((Integer) obj);
        } else if (obj instanceof Double) {
            return RDataFactory.createDoubleVectorFromScalar((Double) obj);
        } else if (obj instanceof Byte) {
            return RDataFactory.createLogicalVectorFromScalar((Byte) obj);
        } else if (obj instanceof String) {
            return RDataFactory.createStringVectorFromScalar((String) obj);
        } else if (obj instanceof RComplex) {
            RComplex complex = (RComplex) obj;
            return RDataFactory.createComplexVector(new double[]{complex.getRealPart(), complex.getImaginaryPart()}, RDataFactory.COMPLETE_VECTOR);
        } else if (obj instanceof RRaw) {
            return RDataFactory.createRawVector(new byte[]{((RRaw) obj).getValue()});
        } else {
            return obj;
        }
    }

}
