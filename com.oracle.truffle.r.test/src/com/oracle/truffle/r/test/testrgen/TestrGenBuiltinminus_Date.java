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

                                                                 public class TestrGenBuiltinminus_Date extends TestBase {

	@Test
	public void testminus_Date1() {
		assertEval(Ignored.Unknown, "argv <- structure(list(e1 = structure(14580, class = \'Date\'),     e2 = structure(1, units = \'days\', class = \'difftime\')), .Names = c(\'e1\',     \'e2\'));"+
			"do.call(\'-.Date\', argv)");
	}

}

