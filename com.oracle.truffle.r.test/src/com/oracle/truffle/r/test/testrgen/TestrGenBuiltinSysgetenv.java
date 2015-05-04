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

// Checkstyle: stop line length check
public class TestrGenBuiltinSysgetenv extends TestBase {

    @Test
    public void testSysgetenv1() {
        assertEval("argv <- list('EDITOR', ''); .Internal(Sys.getenv(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testSysgetenv2() {
        assertEval("argv <- list('SWEAVE_OPTIONS', NA_character_); .Internal(Sys.getenv(argv[[1]], argv[[2]]))");
    }
}
