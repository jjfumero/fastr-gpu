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
public class TestrGenBuiltingregexpr extends TestBase {

    @Test
    public void testgregexpr1() {
        assertEval(Ignored.Unknown, "argv <- list('', 'abc', FALSE, FALSE, FALSE, FALSE); .Internal(gregexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testgregexpr2() {
        assertEval(Ignored.Unknown,
                        "argv <- list('[^\\\\.\\\\w:?$@[\\\\]]+', 'version$m', FALSE, TRUE, FALSE, FALSE); .Internal(gregexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testgregexpr3() {
        assertEval(Ignored.Unknown, "argv <- list('$', 'version$m', FALSE, FALSE, TRUE, FALSE); .Internal(gregexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testgregexpr4() {
        assertEval(Ignored.Unknown,
                        "argv <- list('éè', '«Latin-1 accented chars»: éè øØ å<Å æ<Æ é éè', FALSE, FALSE, TRUE, TRUE); .Internal(gregexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testgregexpr5() {
        assertEval(Ignored.Unknown, "argv <- list('', 'abc', FALSE, TRUE, FALSE, FALSE); .Internal(gregexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testgregexpr6() {
        assertEval(Ignored.Unknown, "argv <- list('', 'abc', FALSE, FALSE, TRUE, FALSE); .Internal(gregexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testgregexpr7() {
        assertEval(Ignored.Unknown,
                        "argv <- list('éè', '«Latin-1 accented chars»: éè øØ å<Å æ<Æ é éè', TRUE, FALSE, FALSE, TRUE); .Internal(gregexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testgregexpr8() {
        assertEval("argv <- list('[[:space:]]?(,|,?[[:space:]]and)[[:space:]]+', character(0), FALSE, FALSE, FALSE, FALSE); .Internal(gregexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testgregexpr9() {
        assertEval(Ignored.Unknown, "argv <- list('\\\\[[^]]*\\\\]', 'FALSE', FALSE, FALSE, FALSE, FALSE); .Internal(gregexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testgregexpr10() {
        assertEval(Ignored.Unknown,
                        "argv <- list('(?<first>[[:upper:]][[:lower:]]+) (?<last>[[:upper:]][[:lower:]]+)', c('  Ben Franklin and Jefferson Davis', '\\tMillard Fillmore'), FALSE, TRUE, FALSE, FALSE); .Internal(gregexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testgregexpr11() {
        assertEval(Ignored.Unknown, "argv <- list('?', 'utils::data', FALSE, FALSE, TRUE, FALSE); .Internal(gregexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testgregexpr12() {
        assertEval(Ignored.Unknown,
                        "argv <- list('[[', 'utils:::.show_help_on_topic_', FALSE, FALSE, TRUE, FALSE); .Internal(gregexpr(argv[[1]], argv[[2]], argv[[3]], argv[[4]], argv[[5]], argv[[6]]))");
    }

    @Test
    public void testgregexpr14() {
        assertEval(Ignored.Unknown, "argv <- structure(list(pattern = '', text = 'abc', fixed = TRUE),     .Names = c('pattern', 'text', 'fixed'));do.call('gregexpr', argv)");
    }

    @Test
    public void testgregexpr15() {
        assertEval(Ignored.Unknown, "argv <- structure(list(pattern = '', text = 'abc'), .Names = c('pattern',     'text'));do.call('gregexpr', argv)");
    }

    @Test
    public void testgregexpr16() {
        assertEval(Ignored.Unknown, "argv <- structure(list(pattern = '', text = 'abc', perl = TRUE),     .Names = c('pattern', 'text', 'perl'));do.call('gregexpr', argv)");
    }

}
