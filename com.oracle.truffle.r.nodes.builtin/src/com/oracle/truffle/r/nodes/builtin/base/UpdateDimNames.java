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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RInvisibleBuiltinNode;
import com.oracle.truffle.r.nodes.unary.CastStringNode;
import com.oracle.truffle.r.nodes.unary.CastStringNodeGen;
import com.oracle.truffle.r.nodes.unary.CastToVectorNode;
import com.oracle.truffle.r.nodes.unary.CastToVectorNodeGen;
import com.oracle.truffle.r.runtime.RBuiltin;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

@RBuiltin(name = "dimnames<-", kind = PRIMITIVE, parameterNames = {"x", ""})
// 2nd parameter is "value", but should not be matched against, so ""
public abstract class UpdateDimNames extends RInvisibleBuiltinNode {

    @Child private CastStringNode castStringNode;
    @Child private CastToVectorNode castVectorNode;

    private Object castString(Object o) {
        if (castStringNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castStringNode = insert(CastStringNodeGen.create(true, true, false, false));
        }
        return castStringNode.execute(o);
    }

    private RAbstractVector castVector(Object value) {
        if (castVectorNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castVectorNode = insert(CastToVectorNodeGen.create(false));
        }
        return ((RAbstractVector) castVectorNode.execute(value)).materialize();
    }

    public abstract RAbstractContainer executeRAbstractContainer(RAbstractContainer container, Object o);

    public RList convertToListOfStrings(RList oldList) {
        RList list = oldList;
        if (list.isShared()) {
            list = (RList) list.copy();
        }
        for (int i = 0; i < list.getLength(); i++) {
            Object element = list.getDataAt(i);
            if (element != RNull.instance) {
                Object s = castString(castVector(element));
                list.updateDataAt(i, s, null);
            }
        }
        return list;
    }

    @Specialization
    @TruffleBoundary
    protected RAbstractContainer updateDimnamesNull(RAbstractContainer container, @SuppressWarnings("unused") RNull list) {
        RAbstractContainer result = container.materializeNonShared();
        result.setDimNames(null);
        controlVisibility();
        return result;
    }

    @Specialization(guards = "list.getLength() == 0")
    @TruffleBoundary
    protected RAbstractContainer updateDimnamesEmpty(RAbstractContainer container, @SuppressWarnings("unused") RList list) {
        return updateDimnamesNull(container, RNull.instance);
    }

    @Specialization(guards = "list.getLength() > 0")
    protected RAbstractContainer updateDimnames(RAbstractContainer container, RList list) {
        RAbstractContainer result = container.materializeNonShared();
        result.setDimNames(convertToListOfStrings(list));
        controlVisibility();
        return result;
    }

    @Specialization(guards = "!isRList(c)")
    @SuppressWarnings("unused")
    protected RAbstractContainer updateDimnamesError(RAbstractContainer container, Object c) {
        controlVisibility();
        CompilerDirectives.transferToInterpreter();
        throw RError.error(this, RError.Message.DIMNAMES_LIST);
    }
}
