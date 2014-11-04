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
public class TestrGenBuiltinbesselJ extends TestBase {

    @Test
    @Ignore
    public void testbesselJ1() {
        assertEval("argv <- list(logical(0), logical(0)); .Internal(besselJ(argv[[1]], argv[[2]]))");
    }

    @Test
    @Ignore
    public void testbesselJ2() {
        assertEval("argv <- list(FALSE, FALSE); .Internal(besselJ(argv[[1]], argv[[2]]))");
    }

    @Test
    @Ignore
    public void testbesselJ3() {
        assertEval("argv <- list(c(9.5367431640625e-07, 1.9073486328125e-06, 3.814697265625e-06, 7.62939453125e-06, 1.52587890625e-05, 3.0517578125e-05, 6.103515625e-05, 0.0001220703125, 0.000244140625, 0.00048828125, 0.0009765625, 0.001953125, 0.00390625, 0.0078125, 0.015625, 0.03125, 0.0625, 0.125, 0.25, 0.5, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024), 2.5); .Internal(besselJ(argv[[1]], argv[[2]]))");
    }
}
