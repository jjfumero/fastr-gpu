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
public class TestBuiltin_sysparent extends TestBase {

    @Test
    public void testsysparent1() {
        assertEval("argv <- list(2); .Internal(sys.parent(argv[[1]]))");
    }

    @Test
    public void testSysParent() {
        assertEval("{ sys.parent() }");
        assertEval("{ f <- function() sys.parent() ; f() }");
        assertEval("{ f <- function() sys.parent() ; g <- function() f() ; g() }");
        assertEval("{ f <- function() sys.parent() ; g <- function() f() ; h <- function() g() ; h() }");
        assertEval("{ f <- function(x=sys.parent()) x ; g <- function() f() ; h <- function() g() ; h() }");
        assertEval("{ f <- function(x) x ; g <- function(y) f(y) ; h <- function(z=sys.parent()) g(z) ; h() }");
        assertEval("{ u <- function() sys.parent() ; f <- function(x) x ; g <- function(y) f(y) ; h <- function(z=u()) g(z) ; h() }");
    }
}
