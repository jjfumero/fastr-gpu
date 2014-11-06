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
public class TestrGenBuiltinchoose extends TestBase {

    @Test
    @Ignore
    public void testchoose1() {
        assertEval("argv <- list(-1, 3); .Internal(choose(argv[[1]], argv[[2]]))");
    }

    @Test
    @Ignore
    public void testchoose2() {
        assertEval("argv <- list(9L, c(-14, -13, -12, -11, -10, -9, -8, -7, -6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24)); .Internal(choose(argv[[1]], argv[[2]]))");
    }

    @Test
    @Ignore
    public void testchoose3() {
        assertEval("argv <- list(logical(0), logical(0)); .Internal(choose(argv[[1]], argv[[2]]))");
    }

    @Test
    @Ignore
    public void testchoose4() {
        assertEval("argv <- list(0.5, 0:10); .Internal(choose(argv[[1]], argv[[2]]))");
    }
}
