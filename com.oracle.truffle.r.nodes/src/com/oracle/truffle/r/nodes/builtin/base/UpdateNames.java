/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

@RBuiltin(name = "names<-", kind = PRIMITIVE, parameterNames = {"x"})
public abstract class UpdateNames extends RInvisibleBuiltinNode {

    @Child CastStringNode castStringNode;

    private Object castString(VirtualFrame frame, Object o) {
        if (castStringNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castStringNode = insert(CastStringNodeFactory.create(null, false, true, false, false));
        }
        return castStringNode.executeString(frame, o);
    }

    public abstract Object executeStringVector(VirtualFrame frame, RAbstractVector vector, Object o);

    @Specialization
    public RAbstractVector updateNames(VirtualFrame frame, RAbstractVector vector, @SuppressWarnings("unused") RNull names) {
        controlVisibility();
        RVector v = vector.materialize();
        v.setNames(frame, null, getEncapsulatingSourceSection());
        return v;
    }

    @Specialization
    public RAbstractVector updateNames(VirtualFrame frame, RAbstractVector vector, RStringVector names) {
        controlVisibility();
        RVector v = vector.materialize();
        RStringVector namesVector = names;
        if (names.getLength() < v.getLength()) {
            namesVector = names.copyResized(v.getLength(), true);
        }
        v.setNames(frame, namesVector, getEncapsulatingSourceSection());
        return v;
    }

    @Specialization
    public RAbstractVector updateNames(VirtualFrame frame, RAbstractVector vector, String name) {
        controlVisibility();
        RVector v = vector.materialize();
        String[] names = new String[v.getLength()];
        Arrays.fill(names, RRuntime.STRING_NA);
        names[0] = name;
        RStringVector namesVector = RDataFactory.createStringVector(names, names.length > 1);
        v.setNames(frame, namesVector, getEncapsulatingSourceSection());
        return v;
    }

    @Specialization
    public RAbstractVector updateNames(VirtualFrame frame, RAbstractVector vector, Object names) {
        controlVisibility();
        if (names == RNull.instance) {
            return updateNames(frame, vector, RNull.instance);
        }
        if (names instanceof RAbstractVector) {
            return updateNames(frame, vector, (RStringVector) castString(frame, names));
        } else {
            return updateNames(frame, vector, (String) castString(frame, names));
        }
    }
}
