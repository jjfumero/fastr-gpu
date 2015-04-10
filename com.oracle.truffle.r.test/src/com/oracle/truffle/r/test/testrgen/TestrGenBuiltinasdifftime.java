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

public class TestrGenBuiltinasdifftime extends TestBase {

    @Test
    public void testasdifftime1() {
        assertEval(Ignored.Unknown, "argv <- structure(list(tim = c('0:3:20', '11:23:15')), .Names = 'tim');do.call('as.difftime', argv)");
    }

    @Test
    public void testasdifftime2() {
        assertEval(Ignored.Unknown, "argv <- structure(list(tim = c('3:20', '23:15', '2:'), format = '%H:%M'),     .Names = c('tim', 'format'));do.call('as.difftime', argv)");
    }

}
