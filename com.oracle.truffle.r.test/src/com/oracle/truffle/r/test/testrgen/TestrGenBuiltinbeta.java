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
public class TestrGenBuiltinbeta extends TestBase {

    @Test
    public void testbeta1() {
        assertEval(Ignored.Unknown, "argv <- list(FALSE, FALSE); .Internal(beta(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testbeta2() {
        assertEval(Ignored.Unknown, "argv <- list(logical(0), logical(0)); .Internal(beta(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testbeta4() {
        assertEval(Ignored.Unknown, "argv <- structure(list(a = 0.01, b = 171), .Names = c('a', 'b'));do.call('beta', argv)");
    }

    @Test
    public void testbeta5() {
        assertEval(Ignored.Unknown, "argv <- structure(list(a = 1e-200, b = 1e-200), .Names = c('a',     'b'));do.call('beta', argv)");
    }

}
