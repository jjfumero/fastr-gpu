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
public class TestrGenBuiltincommentassign extends TestBase {

    @Test
    public void testcommentassign1() {
        assertEval(Ignored.Unknown, "argv <- list(structure(1:12, .Dim = 3:4, comment = c(\'This is my very important data from experiment #0234\', \'Jun 5, 1998\')), c(\'This is my very important data from experiment #0234\', \'Jun 5, 1998\')); .Internal(`comment<-`(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testcommentassign2() {
        assertEval(Ignored.Unknown, "argv <- list(character(0), NULL); .Internal(`comment<-`(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testcommentassign3() {
        assertEval(Ignored.Unknown, "argv <- list(logical(0), NULL); .Internal(`comment<-`(argv[[1]], argv[[2]]))");
    }
}

