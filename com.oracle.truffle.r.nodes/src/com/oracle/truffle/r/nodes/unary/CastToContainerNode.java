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
package com.oracle.truffle.r.nodes.unary;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.env.*;

public abstract class CastToContainerNode extends CastBaseNode {

    public abstract Object executeObject(Object value);

    @Specialization
    @SuppressWarnings("unused")
    protected RNull castNull(RNull rnull) {
        return RNull.instance;
    }

    @Specialization
    protected RFunction castFunction(RFunction f) {
        return f;
    }

    @Specialization
    protected RAbstractVector cast(RAbstractVector vector) {
        return vector;
    }

    @Specialization
    protected RDataFrame cast(RDataFrame dataFrame) {
        return dataFrame;
    }

    @Specialization
    protected RFactor cast(RFactor factor) {
        return factor;
    }

    @Specialization
    protected RExpression cast(RExpression expression) {
        return expression;
    }

    @Specialization
    protected RLanguage cast(RLanguage lang) {
        return lang;
    }

    @Specialization
    protected RPairList cast(RPairList pairlist) {
        return pairlist;
    }

    @Specialization
    protected REnvironment cast(REnvironment env) {
        return env;
    }

}
