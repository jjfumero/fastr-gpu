/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 2012-2014, Purdue University
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.*;

import com.oracle.truffle.r.test.*;

// Checkstyle: stop line length check
public class TestBuiltin_makeunique extends TestBase {

    @Test
    public void testmakeunique1() {
        assertEval("argv <- list(c('A', 'B', 'C', 'D', 'E', 'F'), '.'); .Internal(make.unique(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testmakeunique2() {
        assertEval("argv <- list(c('b', 'NA', 'NA'), '.'); .Internal(make.unique(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testmakeunique3() {
        assertEval("argv <- list(c('1', '2', '3', '6', '7', '7', '7', '8', '8', '10', '11', '12', '12', '12', '15', '15', '16', '17', '19', '20', '21', '21', '23'), '.'); .Internal(make.unique(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testmakeunique4() {
        assertEval("argv <- list(character(0), '.'); .Internal(make.unique(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testMakeUnique() {
        assertEval("{ make.unique(\"a\") }");
        assertEval("{ make.unique(character()) }");
        assertEval("{ make.unique(c(\"a\", \"a\")) }");
        assertEval("{ make.unique(c(\"a\", \"a\", \"a\")) }");
        assertEval("{ make.unique(c(\"a\", \"a\"), \"_\") }");
        assertEval(Output.ContainsError, "{ make.unique(1) }");
        assertEval(Output.ContainsError, "{ make.unique(\"a\", 1) }");
        assertEval(Output.ContainsError, "{ make.unique(\"a\", character()) }");
    }
}
