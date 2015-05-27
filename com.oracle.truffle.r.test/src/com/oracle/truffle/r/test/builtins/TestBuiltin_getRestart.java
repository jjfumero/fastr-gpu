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
package com.oracle.truffle.r.test.builtins;

import org.junit.*;

import com.oracle.truffle.r.test.*;

// Checkstyle: stop line length check
public class TestBuiltin_getRestart extends TestBase {

    @Test
    public void testgetRestart1() {
        assertEval(Ignored.Unknown, "argv <- list(2L); .Internal(.getRestart(argv[[1]]))");
    }

    @Test
    public void testgetRestart2() {
        assertEval(Ignored.Unknown, "argv <- list(1L); .Internal(.getRestart(argv[[1]]))");
    }
}
