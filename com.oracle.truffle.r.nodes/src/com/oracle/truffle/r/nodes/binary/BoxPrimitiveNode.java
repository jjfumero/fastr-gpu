/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.binary;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.unary.ApplyCastNode;
import com.oracle.truffle.r.nodes.unary.CastNode;
import com.oracle.truffle.r.runtime.data.RDouble;
import com.oracle.truffle.r.runtime.data.RInteger;
import com.oracle.truffle.r.runtime.data.RLogical;
import com.oracle.truffle.r.runtime.data.RString;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.nodes.RNode;

/**
 * Boxes all Java primitive values to a class that supports {@link RAbstractVector} and their typed
 * analogies.
 */
public abstract class BoxPrimitiveNode extends CastNode {

    @Override
    public abstract Object execute(Object operand);

    protected BoxPrimitiveNode() {
    }

    @Specialization
    protected static RAbstractVector doInt(int vector) {
        return RInteger.valueOf(vector);
    }

    @Specialization
    protected static RAbstractVector doDouble(double vector) {
        return RDouble.valueOf(vector);
    }

    @Specialization
    protected static RAbstractVector doLogical(byte vector) {
        return RLogical.valueOf(vector);
    }

    @Specialization
    protected static RAbstractVector doString(String value) {
        return RString.valueOf(value);
    }

    /*
     * For the limit we use the number of primitive specializations - 1. After that its better to
     * check !isPrimitve.
     */
    @Specialization(limit = "3", guards = "vector.getClass() == cachedClass")
    protected static Object doCached(Object vector, @Cached("vector.getClass()") Class<?> cachedClass) {
        assert (!isPrimitive(vector));
        return cachedClass.cast(vector);
    }

    @Specialization(contains = "doCached", guards = "!isPrimitive(vector)")
    protected static Object doGeneric(Object vector) {
        return vector;
    }

    public static BoxPrimitiveNode create() {
        return BoxPrimitiveNodeGen.create();
    }

    public static ApplyCastNode create(RNode operand) {
        return new ApplyCastNode(create(), operand);
    }

    protected static boolean isPrimitive(Object value) {
        return (value instanceof Integer) || (value instanceof Double) || (value instanceof Byte) || (value instanceof String);
    }
}
