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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;

public abstract class CastSymbolNode extends CastBaseNode {

    @Child private ToStringNode toString = ToStringNodeGen.create();

    public abstract Object executeSymbol(Object o);

    private String toString(Object value) {
        return toString.executeString(value, true, ToStringNode.DEFAULT_SEPARATOR);
    }

    @Specialization
    protected RSymbol doNull(@SuppressWarnings("unused") RNull value) {
        throw RError.error(this, RError.Message.INVALID_TYPE_LENGTH, "symbol", 0);
    }

    @Specialization
    protected RSymbol doInteger(int value) {
        return backQuote(toString(value));
    }

    @Specialization
    protected RSymbol doDouble(double value) {
        return backQuote(toString(value));
    }

    @Specialization
    protected RSymbol doLogical(byte value) {
        return backQuote(toString(value));
    }

    @TruffleBoundary
    @Specialization
    protected RSymbol doString(String value) {
        // TODO: see if this is going to hit us performance-wise
        return RDataFactory.createSymbol(value.intern());
    }

    @Specialization
    protected RSymbol doStringVector(RStringVector value) {
        // Only element 0 interpreted
        return doString(value.getDataAt(0));
    }

    @Specialization
    protected RSymbol doIntegerVector(RIntVector value) {
        return doInteger(value.getDataAt(0));
    }

    @Specialization
    protected RSymbol doDoubleVector(RDoubleVector value) {
        return doDouble(value.getDataAt(0));
    }

    @Specialization
    protected RSymbol doLogicalVector(RLogicalVector value) {
        return doLogical(value.getDataAt(0));
    }

    @TruffleBoundary
    private static RSymbol backQuote(String s) {
        String quotedString = "`" + s + "`";
        return RDataFactory.createSymbol(quotedString.intern());
    }

    public static CastSymbolNode createNonPreserving() {
        return CastSymbolNodeGen.create(false, false, false);
    }
}
