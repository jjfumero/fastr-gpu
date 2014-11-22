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
public class TestrGenBuiltinlistdirs extends TestBase {

    @Test
    @Ignore
    public void testlistdirs1() {
        assertEval("argv <- list(\'/home/lzhao/hg/r-instrumented/library/rpart/doc\', TRUE, FALSE); .Internal(list.dirs(argv[[1]], argv[[2]], argv[[3]]))");
    }
}

