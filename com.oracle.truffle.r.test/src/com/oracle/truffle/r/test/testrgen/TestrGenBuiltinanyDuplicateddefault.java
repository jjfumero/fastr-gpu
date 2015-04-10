/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 * 
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, Oracle and/or its affiliates
 * All rights reserved.
 */
package com.oracle.truffle.r.test.testrgen;

import org.junit.*;

import com.oracle.truffle.r.test.*;

// Checkstyle: stop line length check

public class TestrGenBuiltinanyDuplicateddefault extends TestBase {

    @Test
    public void testanyDuplicateddefault1() {
        assertEval("argv <- structure(list(x = c(1, NA, 3, NA, 3), incomparables = c(3,     NA)), .Names = c('x', 'incomparables'));do.call('anyDuplicated.default', argv)");
    }

}
