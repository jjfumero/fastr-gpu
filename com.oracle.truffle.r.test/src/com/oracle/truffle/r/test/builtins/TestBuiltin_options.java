/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_options extends TestBase {

    @Test
    public void testoptions1() {
        assertEval("argv <- list('survfit.print.n'); .Internal(options(argv[[1]]))");
    }

    @Test
    public void testoptions2() {
        assertEval("argv <- list('contrasts'); .Internal(options(argv[[1]]))");
    }

    @Test
    public void testoptions3() {
        assertEval("argv <- list('str'); .Internal(options(argv[[1]]))");
    }

    @Test
    public void testoptions4() {
        assertEval(Ignored.Unknown, "argv <- list('ts.eps'); .Internal(options(argv[[1]]))");
    }

    @Test
    public void testoptions5() {
        assertEval(Ignored.Unknown, "argv <- list(NULL); .Internal(options(argv[[1]]))");
    }
}
