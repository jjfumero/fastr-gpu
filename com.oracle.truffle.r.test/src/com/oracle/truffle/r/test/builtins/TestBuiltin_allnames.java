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
public class TestBuiltin_allnames extends TestBase {

    @Test
    public void testallnames1() {
        assertEval(Ignored.MissingBuiltin,
                        "argv <- list(quote(y ~ ((g1) * exp((log(g2/g1)) * (1 - exp(-k * (x - Ta)))/(1 - exp(-k * (Tb - Ta)))))), FALSE, -1L, TRUE); .Internal(all.names(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
    }

    @Test
    public void testallnames2() {
        assertEval(Ignored.MissingBuiltin, "argv <- list(logical(0), logical(0), -1L, TRUE); .Internal(all.names(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
    }

    @Test
    public void testallnames3() {
        assertEval(Ignored.MissingBuiltin, "argv <- list(structure(numeric(0), .Dim = c(0L, 0L)), TRUE, -1L, FALSE); .Internal(all.names(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
    }

    @Test
    public void testallnames4() {
        assertEval(Ignored.MissingBuiltin,
                        "argv <- list(structure(list(c0 = structure(integer(0), .Label = character(0), class = 'factor')), .Names = 'c0', row.names = character(0), class = 'data.frame'), structure(list(c0 = structure(integer(0), .Label = character(0), class = 'factor')), .Names = 'c0', row.names = character(0), class = 'data.frame'), -1L, FALSE); .Internal(all.names(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
    }

    @Test
    public void testallnames5() {
        assertEval(Ignored.MissingBuiltin, "argv <- list(0.1, FALSE, -1L, TRUE); .Internal(all.names(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
    }
}
