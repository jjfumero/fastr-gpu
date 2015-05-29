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
public class TestBuiltin_bitwiseOr extends TestBase {

    @Test
    public void testbitwiseOr1() {
        assertEval("argv <- list(15L, 7L); .Internal(bitwiseOr(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testBitwiseFunctions() {
        assertEval("{ bitwOr(c(10,11,12,13,14,15), c(1,1,1,1,1,1)) }");
        assertEval("{ bitwOr(c(25,57,66), c(10,20,30,40,50,60)) }");
        // Error message mismatch
        assertEval(Output.ContainsError, "{ bitwOr(c(1,2,3,4), c(3+3i)) }");
    }
}
