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

import static com.oracle.truffle.r.runtime.RBuiltinKind.PRIMITIVE;

import java.util.Arrays;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.utilities.BranchProfile;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RBuiltin;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.RAttributable;
import com.oracle.truffle.r.runtime.data.RAttributes;
import com.oracle.truffle.r.runtime.data.RAttributes.RAttribute;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RLanguage;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.env.REnvironment;

@RBuiltin(name = "attributes", kind = PRIMITIVE, parameterNames = {"obj"})
public abstract class Attributes extends RBuiltinNode {

    private final BranchProfile rownamesBranch = BranchProfile.create();

    @Specialization(guards = "!hasAttributes(container)")
    protected Object attributesNull(@SuppressWarnings("unused") RAbstractContainer container) {
        controlVisibility();
        return RNull.instance;
    }

    @Specialization(guards = "hasAttributes(container)")
    protected Object attributes(RAbstractContainer container) {
        controlVisibility();
        return createResult(container, container instanceof RLanguage);
    }

    /**
     * Unusual cases that it is not worth specializing on as they are not performance-centric,
     * basically any type that is not an {@link RAbstractContainer} but is {@link RAttributable},
     * e.g. {@link REnvironment}.
     */
    @Fallback
    protected Object attributes(Object object) {
        controlVisibility();
        if (object instanceof RAttributable) {
            if (!hasAttributesRA((RAttributable) object)) {
                return RNull.instance;
            } else {
                return createResult((RAttributable) object, false);
            }
        } else {
            throw RError.nyi(this, "object cannot be attributed");
        }
    }

    /**
     * {@code language} objects behave differently regarding "names"; they don't get included.
     */
    private Object createResult(RAttributable attributable, boolean ignoreNames) {
        RAttributes attributes = attributable.getAttributes();
        int size = attributes.size();
        String[] names = new String[size];
        Object[] values = new Object[size];
        int z = 0;
        for (RAttribute attr : attributes) {
            String name = attr.getName();
            if (ignoreNames && name.equals(RRuntime.NAMES_ATTR_KEY)) {
                continue;
            }
            names[z] = name;
            if (name.equals(RRuntime.ROWNAMES_ATTR_KEY)) {
                rownamesBranch.enter();
                values[z] = Attr.getFullRowNames(attr.getValue());
            } else {
                values[z] = attr.getValue();
            }
            z++;
        }
        if (ignoreNames && z != names.length) {
            if (z == 0) {
                return RNull.instance;
            } else {
                names = Arrays.copyOfRange(names, 0, names.length - 1);
                values = Arrays.copyOfRange(values, 0, values.length - 1);
            }
        }
        RList result = RDataFactory.createList(values);
        result.setNames(RDataFactory.createStringVector(names, true));
        return result;
    }

    public static boolean hasAttributes(RAbstractContainer container) {
        return hasAttributesRA(container);
    }

    public static boolean hasAttributesRA(RAttributable attributable) {
        return attributable.getAttributes() != null && attributable.getAttributes().size() > 0;
    }
}
