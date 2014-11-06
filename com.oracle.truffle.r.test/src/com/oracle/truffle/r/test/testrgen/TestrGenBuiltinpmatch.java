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
public class TestrGenBuiltinpmatch extends TestBase {

    @Test
    public void testpmatch1() {
        assertEval("argv <- list(\'kendall\', c(\'pearson\', \'kendall\', \'spearman\'), 0L, TRUE); .Internal(pmatch(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
    }

    @Test
    public void testpmatch2() {
        assertEval("argv <- list(\'month\', c(\'secs\', \'mins\', \'hours\', \'days\', \'weeks\', \'months\', \'years\', \'DSTdays\'), NA_integer_, FALSE); .Internal(pmatch(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
    }

    @Test
    public void testpmatch3() {
        assertEval("argv <- list(c(NA_character_, NA_character_, NA_character_, NA_character_), \'NA\', NA_integer_, TRUE); .Internal(pmatch(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
    }

    @Test
    public void testpmatch4() {
        assertEval("argv <- list(\'maximum\', \'euclidian\', NA_integer_, FALSE); .Internal(pmatch(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
    }

    @Test
    public void testpmatch5() {
        assertEval("argv <- list(\'fanny.object.\', \'fanny.object\', 0L, FALSE); .Internal(pmatch(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
    }

    @Test
    public void testpmatch6() {
        assertEval("argv <- list(c(\'alpha\', \'col\', \'border\', \'lty\', \'lwd\'), c(\'col\', \'border\', \'alpha\', \'size\', \'height\', \'angle\', \'density\'), NA_integer_, TRUE); .Internal(pmatch(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
    }

    @Test
    public void testpmatch7() {
        assertEval("argv <- list(\'unique.\', \'unique.array\', 0L, FALSE); .Internal(pmatch(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
    }

    @Test
    public void testpmatch8() {
        assertEval("argv <- list(character(0), c(\'labels\', \'col\', \'alpha\', \'adj\', \'cex\', \'lineheight\', \'font\'), NA_integer_, TRUE); .Internal(pmatch(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
    }
}
