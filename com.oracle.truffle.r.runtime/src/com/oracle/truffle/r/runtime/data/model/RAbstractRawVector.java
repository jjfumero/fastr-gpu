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
package com.oracle.truffle.r.runtime.data.model;

import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RRaw;
import com.oracle.truffle.r.runtime.data.RRawVector;

public interface RAbstractRawVector extends RAbstractVector {

    @Override
    default Object getDataAtAsObject(int index) {
        return getDataAt(index);
    }

    default byte getRawDataAt(@SuppressWarnings("unused") Object store, int index) {
        return getRawDataAt(index);
    }

    @SuppressWarnings("unused")
    default void setRawDataAt(Object store, int index, byte value) {
        throw new UnsupportedOperationException();
    }

    default void setNA(Object store, int index) {
    }

    RRaw getDataAt(int index);

    byte getRawDataAt(int index);

    RRawVector materialize();

    default boolean checkCompleteness() {
        return true;
    }

    default RType getRType() {
        return RType.Raw;
    }

    default Class<?> getElementClass() {
        return RRaw.class;
    }
}
