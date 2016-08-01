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
package com.oracle.truffle.r.library.gpu.phases;

import java.util.ArrayList;

import uk.ac.ed.marawacc.graal.GraalOCLBackendConnector;

import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.extended.UnsafeLoadNode;
import com.oracle.graal.phases.Phase;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;
import com.oracle.graal.phases.tiers.PhaseContext;
import com.oracle.graal.phases.util.Providers;

public class ScopeCleanPhase extends Phase {

    private ArrayList<Node> scopedNodes;

    public ScopeCleanPhase(ArrayList<Node> scopedNodes) {
        this.scopedNodes = scopedNodes;
    }

    private void removeNodes(StructuredGraph graph) {
        System.out.println(scopedNodes);
        for (Node node : graph.getNodes()) {

            for (Node s : scopedNodes) {
                if (s.equals(node)) {
                    if (node instanceof FixedWithNextNode) {
                        node.replaceAtUsages(null);
                        graph.removeFixed((FixedWithNextNode) node);

                    } else if (node instanceof FloatingNode) {
                        node.replaceAtUsages(null);
                        node.safeDelete();
                    }
                    break;
                }
            }
        }
    }

    private void removeUnsafeLoad(StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            if (node instanceof UnsafeLoadNode) {
                node.replaceAtUsages(null);
                node.safeDelete();
            }
        }
        Providers providers = GraalOCLBackendConnector.getProviders();
        new CanonicalizerPhase().apply(graph, new PhaseContext(providers));
        new DeadCodeEliminationPhase().apply(graph);
    }

    @Override
    protected void run(StructuredGraph graph) {
        if (scopedNodes != null) {
            removeNodes(graph);
        }
        // removeUnsafeLoad(graph);
    }
}
