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

public class TestrGenBuiltinenvironmentName extends TestBase {

    @Test
    public void testenvironmentName1() {
        assertEval("argv <- list(FALSE); .Internal(environmentName(argv[[1]]))");
    }

    @Test
    public void testenvironmentName2() {
        assertEval("argv <- list(structure(list(c0 = structure(integer(0), .Label = character(0), class = \'factor\')), .Names = \'c0\', row.names = character(0), class = structure(\'integer(0)\', .Names = \'c0\'))); .Internal(environmentName(argv[[1]]))");
    }

    @Test
    public void testenvironmentName3() {
        assertEval("argv <- list(structure(numeric(0), .Dim = c(0L, 0L))); .Internal(environmentName(argv[[1]]))");
    }
}
