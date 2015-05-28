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

public class TestBuiltin_eval extends TestBase {

    @Test
    public void testEval() {
        assertEval("{ eval(2 ^ 2 ^ 3)}");
        assertEval("{ a <- 1; eval(a) }");
        assertEval("{ a <- 1; eval(a + 1) }");
        assertEval("{ a <- 1; eval(expression(a + 1)) }");
        assertEval("{ f <- function(x) { eval(x) }; f(1) }");
        assertEval("{ eval(x <- 1); ls() }");
        assertEval("{ ne <- new.env(); eval(x <- 1, ne); ls() }");
        assertEval("{ ne <- new.env(); evalq(x <- 1, ne); ls(ne) }");
        assertEval("{ ne <- new.env(); evalq(envir=ne, expr=x <- 1); ls(ne) }");
        assertEval("{ e1 <- new.env(); assign(\"x\", 100, e1); e2 <- new.env(parent = e1); evalq(x, e2) }");

        assertEval("{ f <- function(z) {z}; e<-as.call(c(expression(f), 7)); eval(e) }");

        assertEval("{ f<-function(...) { substitute(list(...)) }; eval(f(c(1,2))) }");
        assertEval("{ f<-function(...) { substitute(list(...)) }; x<-1; eval(f(c(x,2))) }");
        assertEval("{ f<-function(...) { substitute(list(...)) }; eval(f(c(x=1,2))) }");

        assertEval("{ g<-function() { f<-function() { 42 }; substitute(f()) } ; eval(g()) }");
        assertEval("{ g<-function(y) { f<-function(x) { x }; substitute(f(y)) } ; eval(g(42)) }");
        assertEval("{ eval({ xx <- pi; xx^2}) ; xx }");

        // should print two values, xx^2 and xx
        assertEval(Ignored.Unknown, "eval({ xx <- pi; xx^2}) ; xx");
    }
}
