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
package com.oracle.truffle.r.runtime.nodes;

import com.oracle.truffle.api.instrument.WrapperNode;
import com.oracle.truffle.api.nodes.*;

/**
 * Some additional support for instrumentable node.
 */
public interface RInstrumentableNode {

    /**
     * Unwrap a (potentially) wrapped node, returning the child. Since an AST may contain wrapper
     * nodes <b>anywhere</b>, this method <b>must</b> be called before casting or checking the type
     * of a node.
     */
    default RNode unwrap() {
        if (this instanceof WrapperNode) {
            return (RNode) ((WrapperNode) this).getChild();
        } else {
            return (RNode) this;
        }
    }

    default Node unwrapParent() {
        Node p = ((Node) this).getParent();
        if (p instanceof WrapperNode) {
            return p.getParent();
        } else {
            return p;
        }
    }

}
