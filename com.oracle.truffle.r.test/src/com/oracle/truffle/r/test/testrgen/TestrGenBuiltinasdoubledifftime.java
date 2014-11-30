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

                                                                 public class TestrGenBuiltinasdoubledifftime extends TestBase {

	@Test
    @Ignore
	public void testasdoubledifftime1() {
		assertEval("argv <- structure(list(x = structure(16351.8259046444, units = \'days\',     class = \'difftime\', origin = structure(0, class = c(\'POSIXct\',         \'POSIXt\'), tzone = \'GMT\'))), .Names = \'x\');"+
			"do.call(\'as.double.difftime\', argv)");
	}

}

