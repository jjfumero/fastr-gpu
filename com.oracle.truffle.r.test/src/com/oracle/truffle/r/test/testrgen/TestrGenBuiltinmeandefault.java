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

public class TestrGenBuiltinmeandefault extends TestBase {

    @Test
    public void testmeandefault1() {
        assertEval(Ignored.Unknown, "argv <- structure(list(x = structure(c(2L, 1L, 2L, 2L), .Label = c('FALSE',     'TRUE'), class = 'factor')), .Names = 'x');do.call('mean.default', argv)");
    }

}
