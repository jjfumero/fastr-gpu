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

import static com.oracle.truffle.r.runtime.RBuiltinKind.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.RAttributes.RAttribute;
import com.oracle.truffle.r.runtime.data.model.*;

@RBuiltin(name = "attr", kind = PRIMITIVE, parameterNames = {"x", "which", "exact"})
public abstract class Attr extends RBuiltinNode {

    private final ConditionProfile searchPartialProfile = ConditionProfile.createBinaryProfile();
    private final BranchProfile errorProfile = BranchProfile.create();
    protected final RAttributeProfiles attrProfiles = RAttributeProfiles.create();

    @CompilationFinal private String cachedName = "";
    @CompilationFinal private String cachedInternedName = "";

    private String intern(String name) {
        if (cachedName == null) {
            // unoptimized case
            return name.intern();
        }
        if (cachedName == name) {
            // cached case
            return cachedInternedName;
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        // Checkstyle: stop StringLiteralEquality
        if (cachedName == "") {
            // Checkstyle: resume StringLiteralEquality
            cachedName = name;
            cachedInternedName = name.intern();
        } else {
            cachedName = null;
            cachedInternedName = null;
        }
        return name.intern();
    }

    private static Object searchKeyPartial(RAttributes attributes, String name) {
        Object val = RNull.instance;
        for (RAttribute e : attributes) {
            if (e.getName().startsWith(name)) {
                if (val == RNull.instance) {
                    val = e.getValue();
                } else {
                    // non-unique match
                    return RNull.instance;
                }
            }
        }
        return val;
    }

    private Object attrRA(RAttributable attributable, String name) {
        RAttributes attributes = attributable.getAttributes();
        if (attributes == null) {
            return RNull.instance;
        } else {
            Object result = attributes.get(name);
            if (searchPartialProfile.profile(result == null)) {
                return searchKeyPartial(attributes, name);
            }
            return result;
        }
    }

    @Specialization
    protected RNull attr(RNull container, @SuppressWarnings("unused") String name) {
        return container;
    }

    @Specialization(guards = "!isRowNamesAttr(container, name)")
    protected Object attr(RAbstractContainer container, String name) {
        controlVisibility();
        return attrRA(container, intern(name));
    }

    public static Object getFullRowNames(Object a) {
        if (a == RNull.instance) {
            return RNull.instance;
        } else {
            RAbstractVector rowNames = (RAbstractVector) a;
            return rowNames.getElementClass() == RInteger.class && rowNames.getLength() == 2 && RRuntime.isNA(((RAbstractIntVector) rowNames).getDataAt(0)) ? RDataFactory.createIntSequence(1, 1,
                            Math.abs(((RAbstractIntVector) rowNames).getDataAt(1))) : a;
        }
    }

    @Specialization(guards = "isRowNamesAttr(container,  name)")
    protected Object attrRowNames(RAbstractContainer container, @SuppressWarnings("unused") String name) {
        controlVisibility();
        RAttributes attributes = container.getAttributes();
        if (attributes == null) {
            return RNull.instance;
        } else {
            return getFullRowNames(container.getRowNames(attrProfiles));
        }
    }

    @Specialization(guards = {"!emptyName(name)", "isRowNamesAttr(container, name)"})
    protected Object attrRowNames(RAbstractContainer container, RStringVector name) {
        return attrRowNames(container, name.getDataAt(0));
    }

    @Specialization(guards = {"!emptyName(name)", "!isRowNamesAttr(container, name)"})
    protected Object attr(RAbstractContainer container, RStringVector name) {
        return attr(container, name.getDataAt(0));
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "emptyName(name)")
    protected Object attrEmtpyName(RAbstractContainer container, RStringVector name) {
        controlVisibility();
        throw RError.error(getEncapsulatingSourceSection(), RError.Message.EXACTLY_ONE_WHICH);
    }

    /**
     * All other, non-performance centric, {@link RAttributable} types.
     */
    @Fallback
    protected Object attr(Object object, Object name) {
        controlVisibility();
        String sname = RRuntime.asString(name);
        if (sname == null) {
            throw RError.error(getEncapsulatingSourceSection(), RError.Message.MUST_BE_CHARACTER, "which");
        }
        if (object instanceof RAttributable) {
            return attrRA((RAttributable) object, intern(sname));
        } else {
            errorProfile.enter();
            throw RError.nyi(getEncapsulatingSourceSection(), "object cannot be attributed");
        }
    }

    protected boolean isRowNamesAttr(@SuppressWarnings("unused") RAbstractContainer container, String name) {
        return name.equals(RRuntime.ROWNAMES_ATTR_KEY);
    }

    protected boolean isRowNamesAttr(RAbstractContainer container, RStringVector name) {
        return isRowNamesAttr(container, name.getDataAt(0));
    }

    protected boolean emptyName(RStringVector name) {
        return name.getLength() == 0;
    }
}
