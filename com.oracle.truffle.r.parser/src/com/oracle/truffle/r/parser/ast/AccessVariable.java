/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 2012-2014, Purdue University
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.parser.ast;

import com.oracle.truffle.api.source.SourceSection;

public abstract class AccessVariable extends ASTNode {

    protected AccessVariable(SourceSection source) {
        super(source);
    }

    public static ASTNode create(SourceSection src, String name, boolean shouldCopyValue) {
        return new SimpleAccessVariable(src, name, shouldCopyValue);
    }

    public static ASTNode create(SourceSection src, String tempSymbol) {
        return new SimpleAccessTempVariable(src, tempSymbol);
    }

    public static ASTNode createDotDot(SourceSection src, String name) {
        return new SimpleAccessVariadicComponent(src, name);
    }
}
