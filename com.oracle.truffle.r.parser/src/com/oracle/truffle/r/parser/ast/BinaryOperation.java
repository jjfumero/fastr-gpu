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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.api.source.SourceSection;

public final class BinaryOperation extends Operation {

    private final ASTNode rhs;

    private BinaryOperation(SourceSection source, Operator op, ASTNode left, ASTNode right) {
        super(source, op, left);
        this.rhs = right;
    }

    public ASTNode getRHS() {
        return rhs;
    }

    @Override
    public <R> R accept(Visitor<R> v) {
        return v.visit(this);
    }

    @Override
    public <R> List<R> visitAll(Visitor<R> v) {
        return Arrays.asList(getLHS().accept(v), getRHS().accept(v));
    }

    public static ASTNode create(SourceSection src, Operator op, ASTNode left, ASTNode right) {
        return new BinaryOperation(src, op, left, right);
    }

    public static ASTNode create(SourceSection src, String op, ASTNode left, ASTNode right) {
        switch (op) {
            case "%o%":
                return new BinaryOperation(src, Operator.OUTER_MULT, left, right);
            case "%*%":
                return new BinaryOperation(src, Operator.MATMULT, left, right);
            case "%/%":
                return new BinaryOperation(src, Operator.INTEGER_DIV, left, right);
            case "%in%":
                return new BinaryOperation(src, Operator.IN, left, right);
            default:
                // user-defined operator
                List<ArgNode> args = new ArrayList<>();
                args.add(ArgNode.create(left.getSource(), null, left));
                args.add(ArgNode.create(right.getSource(), null, right));
                return new FunctionCall(src, op, args);
        }
    }
}
