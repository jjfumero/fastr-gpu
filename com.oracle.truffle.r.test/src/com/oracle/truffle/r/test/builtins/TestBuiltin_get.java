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
public class TestBuiltin_get extends TestBase {

    @Test
    public void testGet() {
        assertEval("{y<-function(){y<-2;get(\"y\",mode=\"integer\")};y();}");
        assertEval("{y<-function(){y<-2;get(\"y\",mode=\"closure\")};y();}");
        assertEval("{y<-function(){y<-2;get(\"y\",mode=\"integer\",inherits=FALSE);get(\"y\",mode=\"integer\",inherits=FALSE)};y();}");
        assertEval("{y<-function(){y<-2;get(\"y\",mode=\"double\")};y();}");
        assertEval("{y<-function(){y<-2;get(\"y\",mode=\"double\",inherits=FALSE)};y();}");
        assertEval(Output.ContainsError, "{ get(\"dummy\") }");
        assertEval(Output.ContainsError, "{ x <- 33 ; f <- function() { if (FALSE) { x <- 22  } ; get(\"x\", inherits = FALSE) } ; f() }");
        assertEval(Output.ContainsError, "{ x <- 33 ; f <- function() { get(\"x\", inherits = FALSE) } ; f() }");
        assertEval("{ get(\".Platform\", globalenv())$endian }");
        assertEval("{ get(\".Platform\")$endian }");
        assertEval(Output.ContainsError, "{y<-function(){y<-2;get(\"y\",mode=\"closure\",inherits=FALSE);};y();}");
    }
}
