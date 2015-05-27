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
public class TestBuiltin_Syssleep extends TestBase {

    @Test
    public void testSyssleep1() {
        assertEval("argv <- list(0.5); .Internal(Sys.sleep(argv[[1]]))");
    }

    @Test
    public void testSyssleep2() {
        assertEval(Ignored.Unknown, "argv <- list(FALSE); .Internal(Sys.sleep(argv[[1]]))");
    }
}
