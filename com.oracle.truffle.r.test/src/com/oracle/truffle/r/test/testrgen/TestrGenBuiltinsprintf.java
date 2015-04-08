/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.testrgen;

import org.junit.*;

import com.oracle.truffle.r.test.*;

// Checkstyle: stop line length check
public class TestrGenBuiltinsprintf extends TestBase {

    @Test
    public void testsprintf1() {
        assertEval("argv <- list(\'%s is not TRUE\', \'identical(fxy, c(1, 2, 3))\'); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf2() {
        assertEval(Ignored.Unknown, "argv <- list(\'%1.0f\', 3.14159265358979); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf3() {
        assertEval(Ignored.Unknown, "argv <- list(\'min 10-char string '%10s'\', c(\'a\', \'ABC\', \'and an even longer one\')); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf4() {
        assertEval("argv <- list(\'%o\', integer(0)); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf5() {
        assertEval("argv <- list(\'%*s\', 1, \'\'); .Internal(sprintf(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testsprintf6() {
        assertEval("argv <- list(\'p,L,S = (%2d,%2d,%2d): \', TRUE, TRUE, FALSE); .Internal(sprintf(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
    }

    @Test
    public void testsprintf7() {
        assertEval(Ignored.Unknown, "argv <- list(\'p,L,S = (%2d,%2d,%2d): \', TRUE, FALSE, NA); .Internal(sprintf(argv[[1]], argv[[2]], argv[[3]], argv[[4]]))");
    }

    @Test
    public void testsprintf8() {
        assertEval(Ignored.Unknown, "argv <- list(\'plot_%02g\', 1L); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf9() {
        assertEval(Ignored.Unknown, "argv <- list(\'tools:::.createExdotR(\\\'%s\\\', \\\'%s\\\', silent = TRUE, use_gct = %s, addTiming = %s)\', structure(\'KernSmooth\', .Names = \'Package\'), \'/home/lzhao/hg/r-instrumented/library/KernSmooth\', FALSE, FALSE); .Internal(sprintf(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]]))");
    }

    @Test
    public void testsprintf10() {
        assertEval(Ignored.Unknown, "argv <- list(\'%.0f%% said yes (out of a sample of size %.0f)\', 66.666, 3); .Internal(sprintf(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testsprintf11() {
        assertEval("argv <- list(\'%1$d %1$x %1$X\', 0:15); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf12() {
        assertEval("argv <- list(\'%03o\', 1:255); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf13() {
        assertEval("argv <- list(\'%d y value <= 0 omitted from logarithmic plot\', 1L); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf14() {
        assertEval("argv <- list(\'%o\', 1:255); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf15() {
        assertEval("argv <- list(\'%s-class.Rd\', structure(\'foo\', .Names = \'foo\')); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf16() {
        assertEval("argv <- list(\'checkRd: (%d) %s\', -3, \'evalSource.Rd:157: Unnecessary braces at ‘{\\\'sourceEnvironment\\\'}’\'); .Internal(sprintf(argv[[1]], argv[[2]], argv[[3]]))");
    }

    @Test
    public void testsprintf17() {
        assertEval("argv <- list(\'tools:::check_compiled_code(\\\'%s\\\')\', \'/home/lzhao/hg/r-instrumented/library/foreign\'); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf18() {
        assertEval(Ignored.Unknown, "argv <- list(\'%5g\', structure(c(18, 18, 0, 14, 4, 12, 12, 0, 4, 8, 26, 23, 3, 18, 5, 8, 5, 3, 0, 5, 21, 0, 21, 0, 0), .Dim = c(5L, 5L), .Dimnames = list(NULL, c(\'\', \'\', \'\', \'\', \'\')))); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf19() {
        assertEval("argv <- list(\'%G\', 3.14159265358979e-06); .Internal(sprintf(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testsprintf21() {
        assertEval("argv <- structure(list(fmt = \'%9.4g\', 12345.6789), .Names = c(\'fmt\',     \'\'));" + "do.call(\'sprintf\', argv)");
    }
}
