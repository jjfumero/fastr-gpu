/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.runtime.*;

/**
 * Provides the generic mechanism for associating attributes with a R object. It does no special
 * analysis of the "name" of the attribute; that is left to other classes, e.g. {@link RVector}.
 */
public abstract class RAttributes implements Iterable<RAttributes.RAttribute> {

    public interface RAttribute {
        String getName();

        Object getValue();
    }

    @ValueType
    private static class AttrInstance implements RAttribute {
        private final String name;
        private Object value;

        AttrInstance(String name, Object value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public String toString() {
            return name + "=" + value;
        }
    }

    public abstract void put(String name, Object value);

    public abstract Object get(String name);

    public abstract RAttributes copy();

    public abstract int size();

    public abstract boolean isEmpty();

    public abstract void clear();

    public abstract void remove(String name);

    public abstract Iterator<RAttribute> iterator();

    private static final ConditionProfile statsProfile = ConditionProfile.createBinaryProfile();

    public static RAttributes create() {
        return new RAttributesImpl();
    }

    /**
     * The implementation class which is separate to avoid a circularity that would result from the
     * {@code Iterable} in the abstract class.
     */
    private static class RAttributesImpl extends RAttributes {

        RAttributesImpl() {
            if (statsProfile.profile(stats != null)) {
                stats.init();
            }
        }

        private RAttributesImpl(RAttributesImpl attrs) {
            if (attrs.size != 0) {
                size = attrs.size;
                names = Arrays.copyOf(attrs.names, size);
                values = Arrays.copyOf(attrs.values, size);
            }
        }

        private int find(String name) {
            for (int i = 0; i < size; i++) {
                if (names[i] != null && names[i].equals(name)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public void put(String newName, Object newValue) {
            int pos = find(newName);
            if (pos == -1) {
                if (size == names.length) {
                    names = Arrays.copyOf(names, (size + 1) * 2);
                    values = Arrays.copyOf(values, (size + 1) * 2);
                    assert names.length == values.length;
                }
                pos = size++;
                names[pos] = newName;
            }
            values[pos] = newValue;
            if (statsProfile.profile(stats != null)) {
                stats.update(this);
            }
        }

        private static final String[] EMPTY_STRING_ARRAY = new String[0];
        private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

        private String[] names = EMPTY_STRING_ARRAY;
        private Object[] values = EMPTY_OBJECT_ARRAY;
        private int size;

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            return size == 0;
        }

        @Override
        public void remove(String name) {
            int pos = find(name);
            if (pos != -1) {
                size--;
                for (int i = pos; i < size; i++) {
                    names[i] = names[i + 1];
                    values[i] = values[i + 1];
                }
                names[size] = null;
                values[size] = null;
            }
        }

        @Override
        public Object get(String name) {
            int pos = find(name);
            return pos == -1 ? null : values[pos];
        }

        @Override
        public void clear() {
            names = EMPTY_STRING_ARRAY;
            values = EMPTY_OBJECT_ARRAY;
            size = 0;
        }

        @Override
        public RAttributes copy() {
            return new RAttributesImpl(this);
        }

        /**
         * An iterator for the attributes, specified in terms of {@code Map.Entry<String, Object> }
         * to avoid copying in the normal case.
         */
        @Override
        public Iterator<RAttribute> iterator() {
            return new Iter();
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            StringBuffer sb = new StringBuffer().append('{');
            for (int i = 0; i < size; i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                sb.append(names[i]).append('=').append(values[i]);
            }
            sb.append('}');
            return sb.toString();
        }

        private class Iter implements Iterator<RAttribute> {
            int index;

            Iter() {
                index = 0;
            }

            @Override
            public boolean hasNext() {
                return index < size;
            }

            @Override
            public RAttribute next() {
                return new AttrInstance(names[index], values[index++]);
            }

        }

        @TruffleBoundary
        private static NoSuchElementException noSuchElement() {
            throw new NoSuchElementException();
        }
    }

    // Performance analysis

    @CompilationFinal private static PerfHandler stats;

    static {
        RPerfAnalysis.register(new PerfHandler());
    }

    /**
     * Collects data on the maximum size of the attribute set. So only interested in adding not
     * removing attributes.
     */
    public static class PerfHandler implements RPerfAnalysis.Handler {
        private static final RPerfAnalysis.Histogram hist = new RPerfAnalysis.Histogram(5);

        @TruffleBoundary
        void init() {
            hist.inc(0);
        }

        @TruffleBoundary
        void update(RAttributesImpl attr) {
            // incremented size by 1
            int s = attr.size();
            int effectivePrevSize = hist.effectiveBucket(s - 1);
            int effectiveSizeNow = hist.effectiveBucket(s);
            hist.dec(effectivePrevSize);
            hist.inc(effectiveSizeNow);
        }

        public void initialize() {
            stats = this;
        }

        public String getName() {
            return "attributes";

        }

        public void report() {
            System.out.printf("RAttributes: %d, max size %d%n", hist.getTotalCount(), hist.getMaxSize());
            hist.report();
        }

    }
}
