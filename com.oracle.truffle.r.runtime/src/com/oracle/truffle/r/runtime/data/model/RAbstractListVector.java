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
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RNull;

public interface RAbstractListVector extends RAbstractVector {

    @Override
    Object getDataAtAsObject(int index);

    default Object getDataAtAsObject(Object store, int i) {
        return getDataAtAsObject(i);
    }

    RList materialize();

    default boolean checkCompleteness() {
        return true;
    }

    default RType getRType() {
        return RType.List;
    }

    default Class<?> getElementClass() {
        return Object.class;
    }

    @SuppressWarnings("unused")
    default void setDataAt(Object store, int index, Object value) {
        throw new UnsupportedOperationException();
    }

    default void setNA(Object store, int index) {
        setDataAt(store, index, RNull.instance);
    }

}
