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
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.extended.UnsafeLoadNode;
import com.oracle.graal.nodes.java.ArrayLengthNode;
import com.oracle.graal.nodes.java.LoadFieldNode;
import com.oracle.graal.nodes.java.LoadIndexedNode;
import com.oracle.graal.phases.Phase;

public class ScopeArraysDetectionPhase extends Phase {

    private ArrayList<Node> notValidNodes;
    private ArrayList<Class<?>> scopePattern;

    public ScopeArraysDetectionPhase() {
        notValidNodes = new ArrayList<>();
        scopePattern = new ArrayList<>();

        // Pattern
        scopePattern.add(LoadFieldNode.class);
        scopePattern.add(ArrayLengthNode.class);
        scopePattern.add(LoadIndexedNode.class);
        scopePattern.add(LoadFieldNode.class);
        scopePattern.add(UnsafeLoadNode.class);
        scopePattern.add(LoadFieldNode.class);
        scopePattern.add(LoadFieldNode.class);
    }

    @Override
    protected void run(StructuredGraph graph) {
        NodeIterable<Node> nodes = graph.getNodes();
        Iterator<Node> iterator = nodes.iterator();
        while (iterator.hasNext()) {
            Node node = iterator.next();
            if (node instanceof LoadFieldNode) {
                boolean clear = false;
                ArrayList<Node> auxNotValid = new ArrayList<>();
                for (Class<?> comp : scopePattern) {
                    if (node.getClass() == comp) {
                        auxNotValid.add(node);
                        node = node.successors().first();
                    } else {
                        clear = true;
                        break;
                    }
                }
                if (!clear) {
                    notValidNodes.addAll(auxNotValid);
                }
            }
        }
    }

    public boolean isScopeDetected() {
        return !notValidNodes.isEmpty();
    }

    public ArrayList<Node> getScopedNodes() {
        return notValidNodes;
    }
}
