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
package com.oracle.truffle.r.library.gpu.types;

public enum TypeInfo {

    // FastR specific
    RIntegerSequence("Integer", "RIntSequence"),
    RIntVector("Integer", "RIntVector"),
    RDoubleSequence("Double", "RDoubleSequence"),
    RDoubleVector("Double", "RDoubleVector"),
    LIST("RList", "list"),

    // Basic data types
    INT("Integer", "int"),
    DOUBLE("Double", "double"),
    FLOAT("Float", "float"),
    SHORT("Short", "short"),
    LONG("Long", "long"),
    DOUBLE_VECTOR("Double[]", "double[]"),
    BOOLEAN("Boolean", "boolean"),

    // Tuples
    TUPLE2("Tuple2", "T"),
    TUPLE3("Tuple3", "T"),
    TUPLE4("Tuple4", "T"),
    TUPLE5("Tuple5", "T"),
    TUPLE6("Tuple6", "T"),
    TUPLE7("Tuple7", "T"),
    TUPLE8("Tuple8", "T"),
    TUPLE9("Tuple9", "T"),
    TUPLE10("Tuple10", "T"),
    TUPLE11("Tuple11", "T"),

    TUPLE_GENERIC_TYPE("T", "T"),

    NULL("null", "null"); // Not used, just from R side

    private String javaTypeString;
    private String genericType;

    TypeInfo(String name, String type) {
        this.javaTypeString = name;
        this.genericType = type;
    }

    public String getJavaType() {
        return this.javaTypeString;
    }

    public String getGenericType() {
        return this.genericType;
    }

    @Override
    public String toString() {
        return javaTypeString + " : " + genericType;
    }
}
