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
public class TestrGenBuiltinchartr extends TestBase {

    @Test
    public void testchartr1() {
        assertEval(Ignored.Unknown, "argv <- list('.', '.', c('0.02', '0.06', '0.11', '0.22', '0.56', '1.1')); .Internal(chartr(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testchartr2() {
        assertEval(Ignored.Unknown, "argv <- list('iXs', 'why', 'MiXeD cAsE 123'); .Internal(chartr(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testchartr3() {
        assertEval(Ignored.Unknown, "argv <- list('a-cX', 'D-Fw', 'MiXeD cAsE 123'); .Internal(chartr(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testchartr4() {
        assertEval(Ignored.Unknown, "argv <- list('.', '.', character(0)); .Internal(chartr(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testchartr6() {
        assertEval(Ignored.Unknown, "argv <- structure(list(old = 'NA', new = 'na', x = c('NA', NA,     'BANANA')), .Names = c('old', 'new', 'x'));do.call('chartr', argv)");
    }

}
