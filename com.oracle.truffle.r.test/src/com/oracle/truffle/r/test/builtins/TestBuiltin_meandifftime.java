/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.*;

import com.oracle.truffle.r.test.*;

// Checkstyle: stop line length check

public class TestBuiltin_meandifftime extends TestBase {

    @Test
    public void testmeandifftime1() {
        assertEval("argv <- structure(list(x = structure(c(31, NA, NA, 31), units = 'days',     class = 'difftime'), na.rm = TRUE), .Names = c('x', 'na.rm'));do.call('mean.difftime', argv)");
    }

}
