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

public class TestBuiltin_switch extends TestBase {

    @Test
    public void testswitch1() {
        assertEval("argv <- structure(list('forward', forward = 'posS', reverse = 'negS'),     .Names = c('', 'forward', 'reverse'));do.call('switch', argv)");
    }

    @Test
    public void testswitch2() {
        assertEval("argv <- list(3L);do.call('switch', argv)");
    }

    @Test
    public void testswitch4() {
        assertEval("argv <- list(2L, TRUE, FALSE, FALSE);do.call('switch', argv)");
    }

    @Test
    public void testSwitch() {
        assertEval("{ test1 <- function(type) { switch(type, mean = 1, median = 2, trimmed = 3) };test1(\"median\")}");
        assertEval("{switch(3,1,2,3)}");
        assertEval("{switch(4,1,2,3)}");
        assertEval("{switch(4,1,2,z)}");
        assertEval("{ test1 <- function(type) { switch(type, mean = mean(c(1,2,3,4)), median = 2, trimmed = 3) };test1(\"mean\")}");
        assertEval("{ u <- \"uiui\" ; switch(u, \"iuiu\" = \"ieps\", \"uiui\" = \"miep\") }");
        assertEval("{ answer<-\"no\";switch(as.character(answer), yes=, YES=1, no=, NO=2,3) }");
        assertEval("{ x <- \"<\"; v <- switch(x, \"<=\" =, \"<\" =, \">\" = TRUE, FALSE); v }");
        assertEval("{ x <- \"<\"; switch(x, \"<=\" =, \"<\" =, \">\" = TRUE, FALSE) }");
        assertEval("{ x <- \"<\"; switch(x, \"<=\" =, \"<\" =, \">\" =, FALSE) }");
        assertEval("{ a <- NULL ; switch(mode(a), NULL=\"naught\") }");
        assertEval("{ a <- NULL ; switch(mode(a), NULL=) }");
        assertEval(Output.ContainsError, "{ x <- \"!\"; v <- switch(x, v77, \"<=\" =, \"<\" =, \">\" = 99, v55)}");
        assertEval(Output.ContainsError, "{ x <- \"!\"; v <- switch(x, \"\"=v77, \"<=\" =, \"<\" =, \">\" = 99, v55)}");
    }
}
