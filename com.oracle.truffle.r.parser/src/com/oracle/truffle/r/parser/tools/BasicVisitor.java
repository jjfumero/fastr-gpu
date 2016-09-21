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
package com.oracle.truffle.r.parser.tools;

import com.oracle.truffle.r.parser.ast.ASTNode;
import com.oracle.truffle.r.parser.ast.ArgNode;
import com.oracle.truffle.r.parser.ast.BinaryOperation;
import com.oracle.truffle.r.parser.ast.Break;
import com.oracle.truffle.r.parser.ast.Call;
import com.oracle.truffle.r.parser.ast.Constant;
import com.oracle.truffle.r.parser.ast.FieldAccess;
import com.oracle.truffle.r.parser.ast.For;
import com.oracle.truffle.r.parser.ast.Formula;
import com.oracle.truffle.r.parser.ast.Function;
import com.oracle.truffle.r.parser.ast.FunctionCall;
import com.oracle.truffle.r.parser.ast.If;
import com.oracle.truffle.r.parser.ast.Missing;
import com.oracle.truffle.r.parser.ast.Next;
import com.oracle.truffle.r.parser.ast.Operation;
import com.oracle.truffle.r.parser.ast.Repeat;
import com.oracle.truffle.r.parser.ast.Replacement;
import com.oracle.truffle.r.parser.ast.Sequence;
import com.oracle.truffle.r.parser.ast.SimpleAccessTempVariable;
import com.oracle.truffle.r.parser.ast.SimpleAccessVariable;
import com.oracle.truffle.r.parser.ast.SimpleAccessVariadicComponent;
import com.oracle.truffle.r.parser.ast.SimpleAssignVariable;
import com.oracle.truffle.r.parser.ast.UnaryOperation;
import com.oracle.truffle.r.parser.ast.UpdateField;
import com.oracle.truffle.r.parser.ast.UpdateVector;
import com.oracle.truffle.r.parser.ast.Visitor;
import com.oracle.truffle.r.parser.ast.While;

public class BasicVisitor<R> implements Visitor<R> {

    public R visit(ASTNode n) {
        n.visitAll(this);
        return null;
    }

    @Override
    public R visit(Sequence n) {
        return visit((ASTNode) n);
    }

    @Override
    public R visit(If n) {
        return visit((ASTNode) n);
    }

    @Override
    public R visit(Repeat n) {
        return visit((ASTNode) n);
    }

    @Override
    public R visit(While n) {
        return visit((ASTNode) n);
    }

    @Override
    public R visit(For n) {
        return visit((ASTNode) n);
    }

    @Override
    public R visit(Next n) {
        return visit((ASTNode) n);
    }

    @Override
    public R visit(Break n) {
        return visit((ASTNode) n);
    }

    public R visit(Operation op) {
        return visit((ASTNode) op);
    }

    public R visit(BinaryOperation op) {
        return visit((Operation) op);
    }

    @Override
    public R visit(UnaryOperation op) {
        return visit((ASTNode) op);
    }

    @Override
    public R visit(Constant c) {
        return visit((ASTNode) c);
    }

    @Override
    public R visit(SimpleAccessVariable n) {
        return visit((ASTNode) n);
    }

    @Override
    public R visit(SimpleAccessTempVariable n) {
        return visit((ASTNode) n);
    }

    @Override
    public R visit(SimpleAccessVariadicComponent n) {
        return visit((ASTNode) n);
    }

    @Override
    public R visit(FieldAccess n) {
        return visit((ASTNode) n);
    }

    @Override
    public R visit(SimpleAssignVariable n) {
        return visit((ASTNode) n);
    }

    @Override
    public R visit(Replacement n) {
        return visit((ASTNode) n);
    }

    @Override
    public R visit(UpdateVector u) {
        return visit((ASTNode) u);
    }

    @Override
    public R visit(UpdateField u) {
        return visit((ASTNode) u);
    }

    public R visit(Call n) {
        return visit((ASTNode) n);
    }

    @Override
    public R visit(FunctionCall n) {
        return visit((Call) n);
    }

    @Override
    public R visit(Function n) {
        return visit((ASTNode) n);
    }

    @Override
    public R visit(ArgNode n) {
        return visit((ASTNode) n);
    }

    @Override
    public R visit(Formula n) {
        return visit((ASTNode) n);
    }

    @Override
    public R visit(Missing m) {
        return visit((ASTNode) m);
    }

}
