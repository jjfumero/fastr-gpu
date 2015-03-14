/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * This is a placeholder class for an S4 object (GnuR S4SXP). It has no functionality at present but
 * is needed as such objects are generated when unserializing the "methods" package.
 */
public class RS4Object implements RAttributable {

    private RAttributes attributes;

    RS4Object() {

    }

    public RAttributes initAttributes() {
        if (attributes == null) {
            attributes = RAttributes.create();
        }
        return attributes;
    }

    public RAttributes getAttributes() {
        return attributes;
    }

    public RType getRType() {
        return RType.S4Object;
    }

}
