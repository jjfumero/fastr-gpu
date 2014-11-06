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
public class TestrGenBuiltinseqint extends TestBase {

    @Test
    @Ignore
    public void testseqint1() {
        assertEval("argv <- list(16146, by = 1, length.out = 4);seq.int(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    @Ignore
    public void testseqint2() {
        assertEval("argv <- list(0.9, 0.95, length.out = 16);seq.int(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    @Ignore
    public void testseqint3() {
        assertEval("argv <- list(FALSE);seq.int(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testseqint4() {
        assertEval("argv <- list(1.2e+100, 1.3e+100, length.out = 2);seq.int(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    @Ignore
    public void testseqint5() {
        assertEval("argv <- list(structure(0.88, .Names = \'Other\'), structure(1, .Names = \'Vanilla Cream\'), length.out = 24);seq.int(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    @Ignore
    public void testseqint6() {
        assertEval("argv <- list(953553600, by = 86400, length.out = 10);seq.int(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    @Ignore
    public void testseqint7() {
        assertEval("argv <- list(25L);seq.int(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testseqint8() {
        assertEval("argv <- list(from = 2.0943951023932, to = 2.61799387799149, by = 0.0174532925199433);seq.int(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    @Ignore
    public void testseqint9() {
        assertEval("argv <- list(from = 0, to = 0.793110173512391, length.out = FALSE);seq.int(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    public void testseqint10() {
        assertEval("argv <- list(from = 0, to = structure(-1, .Names = \'c0\'));seq.int(argv[[1]],argv[[2]]);");
    }

    @Test
    @Ignore
    public void testseqint11() {
        assertEval("argv <- list(10L, 99L, 1);seq.int(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    @Ignore
    public void testseqint12() {
        assertEval("argv <- list(1L);seq.int(argv[[1]]);");
    }

    @Test
    public void testseqint13() {
        assertEval("argv <- list(102L, 112L, 1L);seq.int(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    @Ignore
    public void testseqint14() {
        assertEval("argv <- list(from = 0.95, by = -0.120360949612403, length.out = 6);seq.int(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    @Ignore
    public void testseqint15() {
        assertEval("argv <- list(list());seq.int(argv[[1]]);");
    }

    @Test
    @Ignore
    public void testseqint16() {
        assertEval("argv <- list(-0.2, 1, length.out = 7);seq.int(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    @Ignore
    public void testseqint17() {
        assertEval("argv <- list(from = 0.070740277703696, to = 0.793110173512391, length.out = NULL);seq.int(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    @Ignore
    public void testseqint18() {
        assertEval("argv <- list(105L, 112L, 3L);seq.int(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    public void testseqint19() {
        assertEval("argv <- list(0, length.out = 3L);seq.int(argv[[1]],argv[[2]]);");
    }

    @Test
    @Ignore
    public void testseqint20() {
        assertEval("argv <- list(0, structure(345600, tzone = \'GMT\'), 43200);seq.int(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    @Ignore
    public void testseqint21() {
        assertEval("argv <- list(-7, 7, length.out = 11);seq.int(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    public void testseqint22() {
        assertEval("argv <- list(4, 4L);seq.int(argv[[1]],argv[[2]]);");
    }

    @Test
    @Ignore
    public void testseqint23() {
        assertEval("argv <- list(0L, 49, 1);seq.int(argv[[1]],argv[[2]],argv[[3]]);");
    }

    @Test
    @Ignore
    public void testseqint24() {
        assertEval("argv <- list(1, 1, by = 1);seq.int(argv[[1]],argv[[2]],argv[[3]]);");
    }
}
