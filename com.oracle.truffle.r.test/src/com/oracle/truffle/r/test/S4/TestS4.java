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
package com.oracle.truffle.r.test.S4;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

/**
 * Tests for the S4 object model implementation.
 */
public class TestS4 extends TestBase {
    @Test
    public void testSlotAccess() {
        assertEval("{ `@`(getClass(\"ClassUnionRepresentation\"), virtual) }");
        assertEval("{ `@`(getClass(\"ClassUnionRepresentation\"), \"virtual\") }");
        assertEval(Output.ContainsError, "{ `@`(getClass(\"ClassUnionRepresentation\"), c(\"virtual\", \"foo\")) }");
        assertEval("{ getClass(\"ClassUnionRepresentation\")@virtual }");
        assertEval("{ getClass(\"ClassUnionRepresentation\")@.S3Class }");
        assertEval("{ c(42)@.Data }");
        assertEval("{ x<-42; `@`(x, \".Data\") }");
        assertEval("{ x<-42; `@`(x, .Data) }");
        assertEval(Output.ContainsError, "{ getClass(\"ClassUnionRepresentation\")@foo }");
        assertEval(Output.ContainsError, "{ c(42)@foo }");
        assertEval(Output.ContainsError, "{ x<-c(42); class(x)<-\"bar\"; x@foo }");
        assertEval("{ x<-getClass(\"ClassUnionRepresentation\"); slot(x, \"virtual\") }");
        assertEval(Output.ContainsError, "{ x<-getClass(\"ClassUnionRepresentation\"); slot(x, virtual) }");
        assertEval("{ x<-function() 42; attr(x, \"foo\")<-7; y<-asS4(x); y@foo }");
        assertEval(Output.ContainsError, "{ x<-NULL; `@`(x, foo) }");
        assertEval(Output.ContainsError, "{ x<-NULL; x@foo }");
    }

    @Test
    public void testSlotUpdate() {
        assertEval("{ x<-getClass(\"ClassUnionRepresentation\"); x@virtual<-TRUE; x@virtual }");
        assertEval("{ x<-getClass(\"ClassUnionRepresentation\"); slot(x, \"virtual\", check=TRUE)<-TRUE; x@virtual }");
        assertEval("{ x<-initialize@valueClass; initialize@valueClass<-\"foo\"; initialize@valueClass<-x }");

        assertEval(Output.ContainsError, "{ x<-function() 42; attr(x, \"foo\")<-7; y<-asS4(x); y@foo<-42 }");
        assertEval(Output.ContainsError, "{ x<-NULL; `@<-`(x, foo, \"bar\") }");
        assertEval(Output.ContainsError, "{ x<-NULL; x@foo<-\"bar\" }");

    }

    @Test
    public void testConversions() {
        assertEval("{ x<-42; isS4(x) }");
        assertEval("{ x<-42; y<-asS4(x); isS4(y) }");
        assertEval("{ isS4(NULL) }");
        assertEval("{ asS4(NULL); isS4(NULL }");
    }

    @Test
    public void testAllocation() {
        assertEval("{ new(\"numeric\") }");
    }
}
