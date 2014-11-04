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
public class TestrGenBuiltinflush extends TestBase {

    @Test
    @Ignore
    public void testflush1() {
        assertEval("argv <- list(structure(1L, class = c(\'terminal\', \'connection\'))); .Internal(flush(argv[[1]]))");
    }

    @Test
    @Ignore
    public void testflush2() {
        assertEval("argv <- list(structure(2L, class = c(\'terminal\', \'connection\'))); .Internal(flush(argv[[1]]))");
    }
}
