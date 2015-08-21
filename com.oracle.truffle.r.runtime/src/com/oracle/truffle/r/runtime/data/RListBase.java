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
package com.oracle.truffle.r.runtime.data;

import java.util.*;

import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ops.na.*;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public abstract class RListBase extends RVector implements RAbstractListVector {

    protected final Object[] data;

    RListBase(Object[] data, int[] dims, RStringVector names) {
        super(false, data.length, dims, names);
        this.data = data;
        assert verify();
    }

    @Override
    public final int getLength() {
        return data.length;
    }

    public Object[] getInternalStore() {
        return data;
    }

    @Override
    public Object getDataAtAsObject(Object store, int index) {
        assert store == data;
        return ((Object[]) store)[index];
    }

    @Override
    public void setDataAt(Object store, int index, Object value) {
        assert store == data;
        ((Object[]) store)[index] = value;
    }

    @Override
    public String toString() {
        return toString(i -> RRuntime.toString(getDataAt(i)));
    }

    @Override
    protected final boolean internalVerify() {
        return true;
    }

    /**
     * Intended for external calls where a copy is not needed. WARNING: think carefully before using
     * this method rather than {@link #getDataCopy()}.
     */
    public final Object[] getDataWithoutCopying() {
        return data;
    }

    public final Object[] getDataCopy() {
        Object[] copy = new Object[data.length];
        System.arraycopy(data, 0, copy, 0, data.length);
        return copy;
    }

    /**
     * Return vector data (copying if necessary) that's guaranteed not to be shared with any other
     * vector instance (but maybe non-temporary in terms of vector's sharing mode).
     *
     * @return vector data
     */
    public final Object[] getDataNonShared() {
        return isShared() ? getDataCopy() : getDataWithoutCopying();

    }

    /**
     * Return vector data (copying if necessary) that's guaranteed to be "fresh" (temporary in terms
     * of vector sharing mode).
     *
     * @return vector data
     */
    public final Object[] getDataTemp() {
        return isTemporary() ? getDataWithoutCopying() : getDataCopy();
    }

    public final Object getDataAt(int i) {
        return data[i];
    }

    @Override
    protected final String getDataAtAsString(int index) {
        return RRuntime.toString(getDataAt(index));
    }

    public final RListBase updateDataAt(int i, Object right, @SuppressWarnings("unused") NACheck rightNACheck) {
        assert !this.isShared();
        data[i] = right;
        return this;
    }

    @Override
    public final RListBase updateDataAtAsObject(int i, Object o, NACheck naCheck) {
        return updateDataAt(i, o, naCheck);

    }

    @Override
    public final void transferElementSameType(int toIndex, RAbstractVector fromVector, int fromIndex) {
        RListBase other = (RListBase) fromVector;
        data[toIndex] = other.data[fromIndex];
    }

    public final Class<?> getElementClass() {
        return Object.class;
    }

    @TruffleBoundary
    public final Object getNameAt(int index) {
        if (names != null && names != null) {
            String name = names.getDataAt(index);
            if (name == RRuntime.STRING_NA) {
                return "$" + RRuntime.NA_HEADER;
            } else if (name.equals(RRuntime.NAMES_ATTR_EMPTY_VALUE)) {
                return "[[" + Integer.toString(index + 1) + "]]";
            } else if (name.matches("^[a-zA-Z.][a-zA-Z0-9_.]*$")) {
                return "$" + name;
            } else {
                return "$`" + name + "`";
            }
        } else {
            return "[[" + Integer.toString(index + 1) + "]]";
        }
    }

    @Override
    public final Object getDataAtAsObject(int index) {
        return this.getDataAt(index);
    }

    protected final Object[] copyResizedData(int size, boolean fillNA) {
        Object[] newData = Arrays.copyOf(data, size);
        return resizeData(newData, this.data, this.getLength(), fillNA);
    }

    private static Object[] resizeData(Object[] newData, Object[] oldData, int oldDataLength, boolean fillNA) {
        if (newData.length > oldDataLength) {
            if (fillNA) {
                for (int i = oldDataLength; i < newData.length; i++) {
                    newData[i] = RNull.instance;
                }
            } else {
                for (int i = oldData.length, j = 0; i < newData.length; ++i, j = Utils.incMod(j, oldData.length)) {
                    newData[i] = oldData[j];
                }
            }
        }
        return newData;
    }

    public final boolean checkCompleteness() {
        return true;
    }

    @Override
    public final void setElement(int i, Object value) {
        data[i] = value;
    }
}
