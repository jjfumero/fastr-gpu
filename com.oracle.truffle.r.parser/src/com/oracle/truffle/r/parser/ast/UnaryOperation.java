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

import com.oracle.truffle.api.source.*;

public final class UnaryOperation extends Operation {

    private UnaryOperation(SourceSection source, Operator op, ASTNode operand) {
        super(source, op, operand);
    }

    public static ASTNode create(SourceSection source, Operator op, ASTNode operand) {
        assert op.isUnary();
        return new UnaryOperation(source, op, operand);
    }

    @Override
    public <R> R accept(Visitor<R> v) {
        return v.visit(this);
    }
}
