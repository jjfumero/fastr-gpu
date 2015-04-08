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
public class TestrGenBuiltinrep extends TestBase {

    @Test
    public void testrep1() {
        assertEval("argv <- list(NA, 7); .Internal(rep_len(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testrep2() {
        assertEval("argv <- list(NA, 4L); .Internal(rep_len(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testrep3() {
        assertEval("argv <- list(-Inf, 1L); .Internal(rep_len(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testrep4() {
        assertEval(Ignored.Unknown, "argv <- list(list(), 0L); .Internal(rep_len(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testrep5() {
        assertEval("argv <- list(FALSE, FALSE); .Internal(rep_len(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testrep6() {
        assertEval("argv <- list(c(4.60173175921079, 4.46741031725783, 4.30749719409961, 4.12438637683712, 4.51499342053481, 4.24874137138388, 3.92699081698724, 3.6052402625906, 3.92699081698724), 9L); .Internal(rep_len(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testrep7() {
        assertEval("argv <- list(c(3L, 6L), 2L); .Internal(rep_len(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testrep8() {
        assertEval(Ignored.Unknown, "argv <- list(list(c('                  ', '                ')), 1L); .Internal(rep_len(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testrep9() {
        assertEval(Ignored.Unknown, "argv <- list(structure(1:4, .Label = c('A', 'B', 'C', 'D'), class = 'factor', .Names = c('a', 'b', 'c', 'd')), 10); .Internal(rep_len(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testrep10() {
        assertEval("argv <- list(c(NA, 3L, 4L), 3L); .Internal(rep_len(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testrep11() {
        assertEval("argv <- list(c(NA, NA, 30, -30), 4L); .Internal(rep_len(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testrep12() {
        assertEval("argv <- list(c(2, 3, 4, 5, 6, 7, 12, 22), 8L); .Internal(rep_len(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testrep13() {
        assertEval("argv <- list(c('50-54', '55-59', '60-64', '65-69', '70-74'), 20L); .Internal(rep_len(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testrep14() {
        assertEval("argv <- list(987.338461538462, 2L); .Internal(rep_len(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testrep15() {
        assertEval("argv <- list(1:5, 15); .Internal(rep_len(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testrep16() {
        assertEval("argv <- list(c(NA, 'green', 'black', 'blue'), 4L); .Internal(rep_len(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testrep17() {
        assertEval("argv <- list(1, 2L); .Internal(rep_len(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testrep18() {
        assertEval("argv <- list(0, 0L); .Internal(rep_len(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testrep19() {
        assertEval("argv <- list(FALSE, 1L); .Internal(rep_len(argv[[1]], argv[[2]]))");
    }

    @Test
    public void testrep21() {
        assertEval("argv <- structure(list(1:5, each = 2), .Names = c('', 'each'));do.call('rep', argv)");
    }

    @Test
    public void testrep22() {
        assertEval("argv <- list(structure(c(1L, 1L, 1L, 2L, 2L, 2L), .Label = c('Batch1',     'Batch2'), class = 'factor'), 2);do.call('rep', argv)");
    }

    @Test
    public void testrep23() {
        assertEval("argv <- list(structure(c(11.3164921459501, 9.56444166646261,     23.868524352596, 8.592077957758, 0.187318691429722, -11.3963997363604,     -6.26079624982537, 6.05560822307356, -6.03903226622761, 4.13503361306269),     .Names = c('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j')),     15);"
                        + "do.call('rep', argv)");
    }

    @Test
    public void testrep24() {
        assertEval("argv <- list(0, 2000);do.call('rep', argv)");
    }

    @Test
    public void testrep25() {
        assertEval("argv <- list(0 - (0+2i), 13);do.call('rep', argv)");
    }

    @Test
    public void testrep26() {
        assertEval("argv <- list(c(1, 2, 3, 4, 7), c(3, 4, 5, 4, 2));do.call('rep', argv)");
    }

    @Test
    public void testrep27() {
        assertEval("argv <- list(1:14, c(3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4));do.call('rep', argv)");
    }

    @Test
    public void testrep28() {
        assertEval("argv <- structure(list(c(2, 2, -1, -1, -1, -1, 0, 0), each = 48),     .Names = c('', 'each'));do.call('rep', argv)");
    }

    @Test
    public void testrep29() {
        assertEval("argv <- list(c('A', 'B'), c(48L, 44L));do.call('rep', argv)");
    }

    @Test
    public void testrep30() {
        assertEval("argv <- structure(list(c('a', 'b', 'c'), each = 3), .Names = c('',     'each'));do.call('rep', argv)");
    }

}
