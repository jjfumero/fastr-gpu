/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.runtime.RBuiltinKind.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

@RBuiltin(name = "nzchar", kind = PRIMITIVE, parameterNames = {"x"})
public abstract class NZChar extends RBuiltinNode {
    @Child private CastStringNode convertString;

    private String coerceContent(Object content) {
        if (convertString == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            convertString = insert(CastStringNodeGen.create(false, true, false, false));
        }
        try {
            return (String) convertString.execute(content);
        } catch (ConversionFailedException e) {
            throw RError.error(this, RError.Message.TYPE_EXPECTED, RType.Character.getName());
        }
    }

    private static byte isNonZeroLength(String s) {
        return s.length() > 0 ? RRuntime.LOGICAL_TRUE : RRuntime.LOGICAL_FALSE;
    }

    @Specialization
    protected RLogicalVector rev(@SuppressWarnings("unused") RNull value) {
        controlVisibility();
        return RDataFactory.createEmptyLogicalVector();
    }

    @Specialization
    protected byte rev(int value) {
        controlVisibility();
        return isNonZeroLength(coerceContent(value));
    }

    @Specialization
    protected byte rev(double value) {
        controlVisibility();
        return isNonZeroLength(coerceContent(value));
    }

    @Specialization
    protected byte rev(byte value) {
        controlVisibility();
        return isNonZeroLength(coerceContent(value));
    }

    @Specialization
    protected RLogicalVector rev(RStringVector vector) {
        controlVisibility();
        int len = vector.getLength();
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++) {
            result[i] = isNonZeroLength(vector.getDataAt(i));
        }
        return RDataFactory.createLogicalVector(result, RDataFactory.COMPLETE_VECTOR);
    }

    @Specialization
    protected RLogicalVector rev(RAbstractVector vector) {
        controlVisibility();
        int len = vector.getLength();
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++) {
            result[i] = isNonZeroLength(coerceContent(vector.getDataAtAsObject(i)));
        }
        return RDataFactory.createLogicalVector(result, RDataFactory.COMPLETE_VECTOR);
    }
}
