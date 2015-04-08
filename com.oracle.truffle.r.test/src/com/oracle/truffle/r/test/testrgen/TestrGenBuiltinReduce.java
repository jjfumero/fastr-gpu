/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates
 * All rights reserved.
 */
package com.oracle.truffle.r.test.testrgen;

import org.junit.*;

import com.oracle.truffle.r.test.*;

// Checkstyle: stop line length check

public class TestrGenBuiltinReduce extends TestBase {

    @Test
    public void testReduce1() {
        assertEval("argv <- structure(list(f = '+', x = 1:7, accumulate = TRUE),     .Names = c('f', 'x', 'accumulate'));do.call('Reduce', argv)");
    }

    @Test
    public void testReduce2() {
        assertEval(Ignored.Unknown,
                        "argv <- structure(list(f = function(f, ...) f(...), x = list(.Primitive('log'),     .Primitive('exp'), .Primitive('acos'), .Primitive('cos')),     init = 0, right = TRUE), .Names = c('f', 'x', 'init', 'right'));"
                                        + "do.call('Reduce', argv)");
    }
}
