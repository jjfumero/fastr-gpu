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
package com.oracle.truffle.r.runtime.data;

import com.oracle.truffle.r.runtime.*;

/**
 * An adaptor class for the several R types that are attributable. Only useful for classes that
 * don't already inherit from another class, otherwise just cut and paste this code.
 */
public abstract class RAttributeStorage implements RAttributable {

    protected RAttributes attributes;

    public final RAttributes getAttributes() {
        return attributes;
    }

    public final RAttributes initAttributes() {
        if (attributes == null) {
            attributes = RAttributes.create();
        }
        return attributes;
    }

    public final void initAttributes(String[] names, Object[] values) {
        assert this.attributes == null;
        this.attributes = RAttributes.createInitialized(names, values);
    }

    public final Object getAttribute(String name) {
        RAttributes attr = attributes;
        return attr == null ? null : attr.get(name);
    }

    public abstract RStringVector getImplicitClass();

    @Override
    public final RStringVector getClassHierarchy() {
        RStringVector v = (RStringVector) getAttribute(RRuntime.CLASS_ATTR_KEY);
        if (v == null) {
            return getImplicitClass();
        } else {
            return v;
        }
    }
}
