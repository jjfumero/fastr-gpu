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

import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.api.source.SourceSection;

/**
 * UpdateField AST, for $ operator at top level LHS of assignment.
 */
public class UpdateField extends ASTNode {

    private final FieldAccess vector;
    private final ASTNode rhs;
    private final boolean isSuper;

    UpdateField(SourceSection src, boolean isSuper, FieldAccess vector, ASTNode rhs) {
        super(src);
        this.vector = vector;
        this.rhs = rhs;
        this.isSuper = isSuper;
    }

    public FieldAccess getVector() {
        return vector;
    }

    public ASTNode getRHS() {
        return rhs;
    }

    public boolean isSuper() {
        return isSuper;
    }

    @Override
    public <R> R accept(Visitor<R> v) {
        return v.visit(this);
    }

    @Override
    public <R> List<R> visitAll(Visitor<R> v) {
        return Arrays.asList(rhs.accept(v), vector.accept(v));
    }
}
