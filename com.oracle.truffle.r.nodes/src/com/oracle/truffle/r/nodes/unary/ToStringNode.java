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
package com.oracle.truffle.r.nodes.unary;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.env.*;

@TypeSystemReference(RTypes.class)
public abstract class ToStringNode extends Node {

    public static final String DEFAULT_SEPARATOR = ", ";

    @Child private ToStringNode recursiveToString;

    private String toStringRecursive(Object o, boolean quotes, String separator) {
        if (recursiveToString == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            recursiveToString = insert(ToStringNodeGen.create());
        }
        return recursiveToString.executeString(o, quotes, separator);
    }

    public abstract String executeString(Object o, boolean quotes, String separator);

    @SuppressWarnings("unused")
    @Specialization
    protected String toString(String value, boolean quotes, String separator) {
        if (RRuntime.isNA(value)) {
            return value;
        }
        if (quotes) {
            return RRuntime.quoteString(value);
        }
        return value;

    }

    @SuppressWarnings("unused")
    @Specialization
    protected String toString(RNull vector, boolean quotes, String separator) {
        return "NULL";
    }

    @SuppressWarnings("unused")
    @Specialization
    protected String toString(RFunction function, boolean quotes, String separator) {
        return RRuntime.toString(function);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected String toString(RSymbol symbol, boolean quotes, String separator) {
        return symbol.getName();
    }

    @SuppressWarnings("unused")
    @Specialization
    protected String toString(RComplex complex, boolean quotes, String separator) {
        return complex.toString();
    }

    @SuppressWarnings("unused")
    @Specialization
    protected String toString(RRaw raw, boolean quotes, String separator) {
        return raw.toString();
    }

    @SuppressWarnings("unused")
    @Specialization
    protected String toString(int operand, boolean quotes, String separator) {
        return RRuntime.intToString(operand);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected String toString(double operand, boolean quotes, String separator) {
        return RRuntime.doubleToString(operand);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected String toString(byte operand, boolean quotes, String separator) {
        return RRuntime.logicalToString(operand);
    }

    @FunctionalInterface
    private interface ElementFunction {
        String apply(int index, boolean quotes, String separator);
    }

    @TruffleBoundary
    private static String createResultForVector(RAbstractVector vector, boolean quotes, String separator, String empty, ElementFunction elementFunction) {
        int length = vector.getLength();
        if (length == 0) {
            return empty;
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                b.append(separator);
            }
            b.append(elementFunction.apply(i, quotes, separator));
        }
        return RRuntime.toString(b);
    }

    @Specialization
    protected String toString(RIntVector vector, boolean quotes, String separator) {
        return createResultForVector(vector, quotes, separator, "integer(0)", (index, q, s) -> toString(vector.getDataAt(index), q, s));
    }

    @TruffleBoundary
    @Specialization
    protected String toString(RDoubleVector vector, boolean quotes, String separator) {
        return createResultForVector(vector, quotes, separator, "numeric(0)", (index, q, s) -> toString(vector.getDataAt(index), q, s));
    }

    @TruffleBoundary
    @Specialization
    protected String toString(RStringVector vector, boolean quotes, String separator) {
        return createResultForVector(vector, quotes, separator, "character(0)", (index, q, s) -> toString(vector.getDataAt(index), q, s));
    }

    @Specialization
    protected String toString(RLogicalVector vector, boolean quotes, String separator) {
        return createResultForVector(vector, quotes, separator, "logical(0)", (index, q, s) -> toString(vector.getDataAt(index), q, s));
    }

    @Specialization
    protected String toString(RRawVector vector, boolean quotes, String separator) {
        return createResultForVector(vector, quotes, separator, "raw(0)", (index, q, s) -> toString(vector.getDataAt(index), q, s));
    }

    @Specialization
    protected String toString(RComplexVector vector, boolean quotes, String separator) {
        return createResultForVector(vector, quotes, separator, "complex(0)", (index, q, s) -> toString(vector.getDataAt(index), q, s));
    }

    @Specialization
    protected String toString(RList vector, boolean quotes, String separator) {
        return createResultForVector(vector, quotes, separator, "list()", (index, q, s) -> {
            Object value = vector.getDataAt(index);
            if (value instanceof RList) {
                RList l = (RList) value;
                if (l.getLength() == 0) {
                    return "list()";
                } else {
                    return "list(" + toStringRecursive(l, q, s) + ')';
                }
            } else {
                return toStringRecursive(value, q, s);
            }
        });
    }

    @Specialization
    protected String toString(RIntSequence vector, boolean quotes, String separator) {
        return toStringRecursive(vector.createVector(), quotes, separator);
    }

    @Specialization
    protected String toString(RDoubleSequence vector, boolean quotes, String separator) {
        return toStringRecursive(vector.createVector(), quotes, separator);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected String toString(REnvironment env, boolean quotes, String separator) {
        return env.toString();
    }
}
