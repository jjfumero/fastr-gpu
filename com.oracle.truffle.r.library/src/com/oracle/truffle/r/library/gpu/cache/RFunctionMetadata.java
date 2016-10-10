/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.library.gpu.cache;

import uk.ac.ed.datastructures.interop.InteropTable;
import uk.ac.ed.datastructures.interop.Interoperable;

import com.oracle.truffle.r.library.gpu.types.TypeInfo;

public class RFunctionMetadata {

    private int nArgs;
    private String[] argsName;
    private Object[] argsPackageFirstInput;
    private Object firstValue;

    private TypeInfo outputType;
    private InteropTable interop;

    private Class<?>[] typeObject;
    private Interoperable interoperable;

    public RFunctionMetadata(int nArgs, String[] argsName, Object[] argsPackageFirstInput, Object firstValue, TypeInfo outputType, InteropTable interop, Class<?>[] typeObject,
                    Interoperable interoperable) {
        this.nArgs = nArgs;
        this.argsName = argsName;
        this.argsPackageFirstInput = argsPackageFirstInput;
        this.firstValue = firstValue;
        this.outputType = outputType;
        this.interop = interop;
        this.typeObject = typeObject;
        this.interoperable = interoperable;
    }

    public TypeInfo getOutputType() {
        return outputType;
    }

    public InteropTable getInterop() {
        return interop;
    }

    public Class<?>[] getTypeObject() {
        return typeObject;
    }

    public Interoperable getInteroperable() {
        return interoperable;
    }

    public int getnArgs() {
        return nArgs;
    }

    public String[] getArgsName() {
        return argsName;
    }

    public Object[] getArgsPackageFirstInput() {
        return argsPackageFirstInput;
    }

    public Object getFirstValue() {
        return firstValue;
    }

}
