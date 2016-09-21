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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.runtime.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.unary.CastStringNode;
import com.oracle.truffle.r.nodes.unary.CastStringNodeGen;
import com.oracle.truffle.r.nodes.unary.ConversionFailedException;
import com.oracle.truffle.r.runtime.RBuiltin;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RAttributeProfiles;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RFactor;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.model.RAbstractStringVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

// TODO interpret "type" and "allowNA" arguments
@RBuiltin(name = "nchar", kind = INTERNAL, parameterNames = {"x", "type", "allowNA"})
public abstract class NChar extends RBuiltinNode {

    @Child private CastStringNode convertString;

    private final RAttributeProfiles attrProfiles = RAttributeProfiles.create();

    private String coerceContent(Object content) {
        if (convertString == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            convertString = insert(CastStringNodeGen.create(false, false, false, false));
        }
        try {
            return (String) convertString.executeString(content);
        } catch (ConversionFailedException e) {
            throw RError.error(this, RError.Message.TYPE_EXPECTED, RType.Character.getName());
        }
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RIntVector nchar(RNull value, String type, byte allowNA) {
        controlVisibility();
        return RDataFactory.createEmptyIntVector();
    }

    @SuppressWarnings("unused")
    @Specialization
    protected int nchar(int value, String type, byte allowNA) {
        controlVisibility();
        return coerceContent(value).length();
    }

    @SuppressWarnings("unused")
    @Specialization
    protected int nchar(double value, String type, byte allowNA) {
        controlVisibility();
        return coerceContent(value).length();
    }

    @SuppressWarnings("unused")
    @Specialization
    protected int nchar(byte value, String type, byte allowNA) {
        controlVisibility();
        return coerceContent(value).length();
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "vector.getLength() == 0")
    protected RIntVector ncharL0(RAbstractStringVector vector, String type, byte allowNA) {
        controlVisibility();
        return RDataFactory.createEmptyIntVector();
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "vector.getLength() == 1")
    protected int ncharL1(RAbstractStringVector vector, String type, byte allowNA) {
        controlVisibility();
        return vector.getDataAt(0).length();
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "vector.getLength() > 1")
    protected RIntVector nchar(RAbstractStringVector vector, String type, byte allowNA) {
        controlVisibility();
        int len = vector.getLength();
        int[] result = new int[len];
        for (int i = 0; i < len; i++) {
            result[i] = vector.getDataAt(i).length();
        }
        return RDataFactory.createIntVector(result, vector.isComplete(), vector.getNames(attrProfiles));
    }

    @SuppressWarnings("unused")
    @Fallback
    protected RIntVector nchar(Object obj, Object type, Object allowNA) {
        controlVisibility();
        if (obj instanceof RFactor) {
            throw RError.error(this, RError.Message.REQUIRES_CHAR_VECTOR, "nchar");
        }
        if (obj instanceof RAbstractVector) {
            RAbstractVector vector = (RAbstractVector) obj;
            int len = vector.getLength();
            int[] result = new int[len];
            for (int i = 0; i < len; i++) {
                result[i] = coerceContent(vector.getDataAtAsObject(i)).length();
            }
            return RDataFactory.createIntVector(result, vector.isComplete(), vector.getNames(attrProfiles));
        } else {
            throw RError.error(this, RError.Message.CANNOT_COERCE, RRuntime.classToString(obj.getClass(), false), "character");
        }
    }

}
