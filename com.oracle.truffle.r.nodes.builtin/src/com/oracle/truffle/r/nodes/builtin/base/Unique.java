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

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.access.ConstantNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

import java.util.*;

import static com.oracle.truffle.r.runtime.RBuiltinKind.INTERNAL;

// Implements default S3 method
@RBuiltin(name = "unique", kind = INTERNAL, parameterNames = {"x", "incomparables", "fromLast", "nmax", "..."})
// TODO A more efficient implementation is in order; GNU R uses hash tables so perhaps we should
// consider using one of the existing libraries that offer hash table implementations for primitive
// types
public abstract class Unique extends RBuiltinNode {

    private static final long BIG_THRESHOLD = 100;

    private final ConditionProfile bigProfile = ConditionProfile.createBinaryProfile();

    @Override
    public RNode[] getParameterValues() {
        // x, incomparables = FALSE, fromLast = FALSE, nmax = NA, ...
        return new RNode[]{ConstantNode.create(RMissing.instance), ConstantNode.create(RRuntime.LOGICAL_FALSE), ConstantNode.create(RRuntime.LOGICAL_FALSE), ConstantNode.create(RRuntime.INT_NA),
                        ConstantNode.create(RMissing.instance)};
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RNull doUnique(RNull vec, byte incomparables, byte fromLast, byte nmax, RArgsValuesAndNames vararg) {
        return vec;
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RStringVector doUnique(RAbstractStringVector vec, byte incomparables, byte fromLast, byte nmax, RArgsValuesAndNames vararg) {
        if (bigProfile.profile(vec.getLength() * (long) vec.getLength() > BIG_THRESHOLD)) {
            Utils.NonRecursiveHashSet<String> set = new Utils.NonRecursiveHashSet<>(vec.getLength());
            String[] data = new String[vec.getLength()];
            int ind = 0;
            for (int i = 0; i < vec.getLength(); i++) {
                String val = vec.getDataAt(i);
                if (!set.add(val)) {
                    data[ind++] = val;
                }
            }
            return RDataFactory.createStringVector(Arrays.copyOf(data, ind), vec.isComplete());
        } else {
            ArrayList<String> dataList = new ArrayList<>(vec.getLength());
            for (int i = 0; i < vec.getLength(); i++) {
                String s = vec.getDataAt(i);
                if (!dataList.contains(s)) {
                    dataList.add(s);
                }
            }
            String[] data = new String[dataList.size()];
            dataList.toArray(data);
            return RDataFactory.createStringVector(data, vec.isComplete());
        }
    }

    // these are intended to stay private as they will go away once we figure out which external
    // library to use

    private static class IntArray {
        int[] backingArray;
        int index;

        public IntArray(int len) {
            this.backingArray = new int[len];
            index = 0;
        }

        public boolean contains(int val) {
            for (int i = 0; i < index; i++) {
                if (backingArray[i] == val) {
                    return true;
                }
            }
            return false;
        }

        public void add(int val) {
            backingArray[index++] = val;
        }

        public int[] toArray() {
            int[] newArray = new int[index];
            for (int i = 0; i < index; i++) {
                newArray[i] = backingArray[i];
            }
            return newArray;
        }
    }

    private static class DoubleArray {
        double[] backingArray;
        int index;

        public DoubleArray(int len) {
            this.backingArray = new double[len];
            index = 0;
        }

        public boolean contains(double val) {
            for (int i = 0; i < index; i++) {
                if (backingArray[i] == val) {
                    return true;
                }
            }
            return false;
        }

        public void add(double val) {
            backingArray[index++] = val;
        }

        public double[] toArray() {
            double[] newArray = new double[index];
            for (int i = 0; i < index; i++) {
                newArray[i] = backingArray[i];
            }
            return newArray;
        }
    }

    private static class DoubleArrayForComplex {
        double[] backingArray;
        int index;

        public DoubleArrayForComplex(int len) {
            this.backingArray = new double[len << 1];
            index = 0;
        }

        public boolean contains(RComplex val) {
            for (int i = 0; i < index; i++) {
                if (backingArray[i << 1] == val.getRealPart() && backingArray[i << 1 + 1] == val.getImaginaryPart()) {
                    return true;
                }
            }
            return false;
        }

        public void add(RComplex val) {
            backingArray[index++] = val.getRealPart();
            backingArray[index++] = val.getImaginaryPart();
        }

        public double[] toArray() {
            double[] newArray = new double[index];
            for (int i = 0; i < index; i++) {
                newArray[i] = backingArray[i];
            }
            return newArray;
        }
    }

    private static class ByteArray {
        byte[] backingArray;
        int index;

        public ByteArray(int len) {
            this.backingArray = new byte[len];
            index = 0;
        }

        public boolean contains(byte val) {
            for (int i = 0; i < index; i++) {
                if (backingArray[i] == val) {
                    return true;
                }
            }
            return false;
        }

        public void add(byte val) {
            backingArray[index++] = val;
        }

        public byte[] toArray() {
            byte[] newArray = new byte[index];
            for (int i = 0; i < index; i++) {
                newArray[i] = backingArray[i];
            }
            return newArray;
        }
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RIntVector doUnique(RAbstractIntVector vec, byte incomparables, byte fromLast, byte nmax, RArgsValuesAndNames vararg) {
        if (bigProfile.profile(vec.getLength() * (long) vec.getLength() > BIG_THRESHOLD)) {
            NonRecursiveHashSetInt set = new NonRecursiveHashSetInt();
            int[] data = new int[16];
            int ind = 0;
            for (int i = 0; i < vec.getLength(); i++) {
                int val = vec.getDataAt(i);
                if (!set.add(val)) {
                    if (ind == data.length) {
                        data = Arrays.copyOf(data, data.length << 1);
                    }
                    data[ind++] = val;
                }
            }
            return RDataFactory.createIntVector(Arrays.copyOf(data, ind), vec.isComplete());
        } else {
            IntArray dataList = new IntArray(vec.getLength());
            for (int i = 0; i < vec.getLength(); i++) {
                int val = vec.getDataAt(i);
                if (!dataList.contains(val)) {
                    dataList.add(val);
                }
            }
            return RDataFactory.createIntVector(dataList.toArray(), vec.isComplete());
        }
    }

    private static final class NonRecursiveHashSetInt {

        private int[] keys;
        private int size;
        private boolean containsZero;

        public NonRecursiveHashSetInt() {
            keys = new int[64];
        }

        public boolean add(int key) {
            if (key == 0) {
                if (containsZero) {
                    return true;
                } else {
                    containsZero = true;
                    return false;
                }
            }
            int ind = Math.abs(Integer.hashCode(key)) & (keys.length - 1);
            while (true) {
                if (keys[ind] == 0) {
                    keys[ind] = key;
                    size++;
                    if (size << 1 == keys.length) {
                        int[] newKeys = new int[keys.length << 1];
                        for (int rehashKey : keys) {
                            if (rehashKey != 0) {
                                int tmpInd = Math.abs(Integer.hashCode(rehashKey)) & (newKeys.length - 1);
                                while (newKeys[tmpInd] != 0) {
                                    assert newKeys[tmpInd] != rehashKey;
                                    tmpInd = (tmpInd + 1) & (newKeys.length - 1);
                                }
                                newKeys[tmpInd] = rehashKey;
                            }
                        }

                        keys = newKeys;
                    }
                    return false;
                } else if (key == keys[ind]) {
                    return true;
                } else {
                    ind = (ind + 1) & (keys.length - 1);
                }
            }
        }
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RDoubleVector doUnique(RAbstractDoubleVector vec, byte incomparables, byte fromLast, byte nmax, RArgsValuesAndNames vararg) {
        if (bigProfile.profile(vec.getLength() * (long) vec.getLength() > BIG_THRESHOLD)) {
            Utils.NonRecursiveHashSetDouble set = new Utils.NonRecursiveHashSetDouble(vec.getLength());
            double[] data = new double[vec.getLength()];
            int ind = 0;
            for (int i = 0; i < vec.getLength(); i++) {
                double val = vec.getDataAt(i);
                if (!set.add(val)) {
                    data[ind++] = val;
                }
            }
            return RDataFactory.createDoubleVector(Arrays.copyOf(data, ind), vec.isComplete());
        } else {
            DoubleArray dataList = new DoubleArray(vec.getLength());
            for (int i = 0; i < vec.getLength(); i++) {
                double val = vec.getDataAt(i);
                if (!dataList.contains(val)) {
                    dataList.add(val);
                }
            }
            return RDataFactory.createDoubleVector(dataList.toArray(), vec.isComplete());
        }
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RLogicalVector doUnique(RAbstractLogicalVector vec, byte incomparables, byte fromLast, byte nmax, RArgsValuesAndNames vararg) {
        ByteArray dataList = new ByteArray(vec.getLength());
        for (int i = 0; i < vec.getLength(); i++) {
            byte val = vec.getDataAt(i);
            if (!dataList.contains(val)) {
                dataList.add(val);
            }
        }
        return RDataFactory.createLogicalVector(dataList.toArray(), vec.isComplete());
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RComplexVector doUnique(RAbstractComplexVector vec, byte incomparables, byte fromLast, byte nmax, RArgsValuesAndNames vararg) {
        if (bigProfile.profile(vec.getLength() * (long) vec.getLength() > BIG_THRESHOLD)) {
            Utils.NonRecursiveHashSet<RComplex> set = new Utils.NonRecursiveHashSet<>(vec.getLength());
            double[] data = new double[vec.getLength() * 2];
            int ind = 0;
            for (int i = 0; i < vec.getLength(); i++) {
                RComplex val = vec.getDataAt(i);
                if (!set.add(val)) {
                    data[ind++] = val.getRealPart();
                    data[ind++] = val.getImaginaryPart();
                }
            }
            return RDataFactory.createComplexVector(Arrays.copyOf(data, ind), vec.isComplete());
        } else {
            DoubleArrayForComplex dataList = new DoubleArrayForComplex(vec.getLength());
            for (int i = 0; i < vec.getLength(); i++) {
                RComplex s = vec.getDataAt(i);
                if (!dataList.contains(s)) {
                    dataList.add(s);
                }
            }
            return RDataFactory.createComplexVector(dataList.toArray(), vec.isComplete());
        }
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RRawVector doUnique(RAbstractRawVector vec, byte incomparables, byte fromLast, byte nmax, RArgsValuesAndNames vararg) {
        if (bigProfile.profile(vec.getLength() * (long) vec.getLength() > BIG_THRESHOLD)) {
            Utils.NonRecursiveHashSet<RRaw> set = new Utils.NonRecursiveHashSet<>(vec.getLength());
            byte[] data = new byte[vec.getLength()];
            int ind = 0;
            for (int i = 0; i < vec.getLength(); i++) {
                RRaw val = vec.getDataAt(i);
                if (!set.add(val)) {
                    data[ind++] = val.getValue();
                }
            }
            return RDataFactory.createRawVector(Arrays.copyOf(data, ind));
        } else {
            ByteArray dataList = new ByteArray(vec.getLength());
            for (int i = 0; i < vec.getLength(); i++) {
                byte val = vec.getDataAt(i).getValue();
                if (!dataList.contains(val)) {
                    dataList.add(val);
                }
            }
            return RDataFactory.createRawVector(dataList.toArray());
        }
    }

}
