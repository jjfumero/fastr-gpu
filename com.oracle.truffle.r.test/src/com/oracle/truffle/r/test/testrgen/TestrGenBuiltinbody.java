/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 * 
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.testrgen;

import org.junit.*;

import com.oracle.truffle.r.test.*;

public class TestrGenBuiltinbody extends TestBase {

    @Test
    @Ignore
    public void testbody1() {
        assertEval("argv <- list(function (x, y) {    c(x, y)}); .Internal(body(argv[[1]]))");
    }

    @Test
    @Ignore
    public void testbody2() {
        assertEval("argv <- list(function (object) TRUE); .Internal(body(argv[[1]]))");
    }

    @Test
    @Ignore
    public void testbody3() {
        assertEval("argv <- list(function (from, strict = TRUE) from); .Internal(body(argv[[1]]))");
    }

    @Test
    @Ignore
    public void testbody4() {
        assertEval("argv <- list(.Primitive(\'/\')); .Internal(body(argv[[1]]))");
    }

    @Test
    @Ignore
    public void testbody5() {
        assertEval("argv <- list(structure(list(c0 = structure(integer(0), .Label = character(0), class = \'factor\')), .Names = \'c0\', row.names = character(0), class = \'data.frame\')); .Internal(body(argv[[1]]))");
    }

    @Test
    @Ignore
    public void testbody6() {
        assertEval("argv <- list(structure(numeric(0), .Dim = c(0L, 0L))); .Internal(body(argv[[1]]))");
    }
}
