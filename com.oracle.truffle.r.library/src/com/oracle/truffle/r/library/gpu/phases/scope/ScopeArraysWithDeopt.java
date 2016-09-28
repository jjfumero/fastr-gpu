/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.library.gpu.phases.scope;

import java.util.ArrayList;
import java.util.Iterator;

import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.iterators.NodeIterable;
import com.oracle.graal.nodes.FixedGuardNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.extended.UnsafeLoadNode;
import com.oracle.graal.nodes.java.ArrayLengthNode;
import com.oracle.graal.nodes.java.LoadFieldNode;
import com.oracle.graal.nodes.java.LoadIndexedNode;
import com.oracle.graal.phases.Phase;

public class ScopeArraysWithDeopt extends Phase {

    private ArrayList<Node> notValidNodes;
    private ArrayList<Class<?>> scopePattern;
    private ArrayList<FixedGuardNode> guardNodes;

    /**
     * The pattern does not include the deopt nodes. Therefore, previous to this phase, it should be
     * a phase where the deopt for the scope are gone.
     */
    public ScopeArraysWithDeopt() {
        notValidNodes = new ArrayList<>();
        scopePattern = new ArrayList<>();
        guardNodes = new ArrayList<>();

        // Pattern for R
        scopePattern.add(LoadFieldNode.class);          // LoadField#FrameWithoutBoxing.tags
        scopePattern.add(ArrayLengthNode.class);        // ArrayLength
        scopePattern.add(LoadIndexedNode.class);        // LoadIdexed
        scopePattern.add(LoadFieldNode.class);          // LoadField#FrameWithoutBoxing.locals
        scopePattern.add(UnsafeLoadNode.class);         // UnsafeLoad
        scopePattern.add(LoadFieldNode.class);          // LoadField#RAttributeStorage.attributes
        scopePattern.add(LoadFieldNode.class);          // LoadField#RDoubleVector.data
    }

    @Override
    protected void run(StructuredGraph graph) {

        NodeIterable<Node> nodes = graph.getNodes();
        Iterator<Node> iterator = nodes.iterator();
        while (iterator.hasNext()) {
            Node node = iterator.next();
            if (node instanceof LoadFieldNode) {
                boolean clear = false;
                ArrayList<Node> auxListNotValid = new ArrayList<>();
                ArrayList<FixedGuardNode> auxGuards = new ArrayList<>();
                int last = 0;
                while (last < scopePattern.size()) {
                    Class<?> compareNode = scopePattern.get(last);
                    if (node.getClass() == compareNode) {
                        auxListNotValid.add(node);
                        node = node.successors().first();
                        last++;
                    } else if (last == scopePattern.size()) {
                        break;
                    } else if (node.getClass() == FixedGuardNode.class) {
                        auxGuards.add((FixedGuardNode) node);
                        node = node.successors().first();
                    } else {
                        clear = true;
                        break;
                    }
                }

                if (!clear) {
                    notValidNodes.addAll(auxListNotValid);
                    guardNodes.addAll(auxGuards);
                }
            }
        }
    }

    public ArrayList<FixedGuardNode> getGuardNodes() {
        return guardNodes;
    }

    public boolean isScopeDetected() {
        return !notValidNodes.isEmpty();
    }

    public ArrayList<Node> getScopedNodes() {
        return notValidNodes;
    }
}
