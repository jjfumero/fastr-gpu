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

public class TestrGenBuiltinsetseed extends TestBase {

	@Test
	public void testsetseed1(){
		assertEval("argv <- list(1000, 0L, NULL); .Internal(set.seed(argv[[1]], argv[[2]], argv[[3]]))");
	}

	@Test
	public void testsetseed2(){
		assertEval("argv <- list(77, 2L, NULL); .Internal(set.seed(argv[[1]], argv[[2]], argv[[3]]))");
	}

	@Test
	public void testsetseed3(){
		assertEval("argv <- list(123, 6L, NULL); .Internal(set.seed(argv[[1]], argv[[2]], argv[[3]]))");
	}

	@Test
	public void testsetseed4(){
		assertEval("argv <- list(77, 4L, NULL); .Internal(set.seed(argv[[1]], argv[[2]], argv[[3]]))");
	}

	@Test
	public void testsetseed5(){
		assertEval("argv <- list(1000, 1L, NULL); .Internal(set.seed(argv[[1]], argv[[2]], argv[[3]]))");
	}

	@Test
	public void testsetseed6(){
		assertEval("argv <- list(0, NULL, NULL); .Internal(set.seed(argv[[1]], argv[[2]], argv[[3]]))");
	}

	@Test
	public void testsetseed7(){
		assertEval("argv <- list(123, 7L, NULL); .Internal(set.seed(argv[[1]], argv[[2]], argv[[3]]))");
	}

	@Test
	public void testsetseed8(){
		assertEval("argv <- list(NULL, NULL, NULL); .Internal(set.seed(argv[[1]], argv[[2]], argv[[3]]))");
	}
}
