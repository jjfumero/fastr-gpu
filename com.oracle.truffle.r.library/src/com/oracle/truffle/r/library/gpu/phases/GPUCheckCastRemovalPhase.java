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

import java.util.Iterator;

import uk.ac.ed.marawacc.graal.GraalOCLBackendConnector;

import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.iterators.NodeIterable;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.java.CheckCastNode;
import com.oracle.graal.phases.Phase;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;
import com.oracle.graal.phases.tiers.PhaseContext;
import com.oracle.graal.phases.util.Providers;

public class GPUCheckCastRemovalPhase extends Phase {

    private static void deadCodeElimination(StructuredGraph graph) {
        Providers providers = GraalOCLBackendConnector.getHostBackend().getProviders();
        new CanonicalizerPhase().apply(graph, new PhaseContext(providers));
        new DeadCodeEliminationPhase().apply(graph);
    }

    @SuppressWarnings("unused")
    private static void removeIsNullNodes(StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            if (node instanceof IsNullNode) {
                node.replaceAtUsages(null);
                node.safeDelete();
            }
        }
        deadCodeElimination(graph);
    }

    @Override
    protected void run(StructuredGraph graph) {
        NodeIterable<Node> nodes = graph.getNodes();
        Iterator<Node> iterator = nodes.iterator();
        Node prev = iterator.next();
        while (iterator.hasNext()) {
            Node node = iterator.next();
            if (node instanceof CheckCastNode) {
                graph.replaceFixed((FixedWithNextNode) node, prev);
            }
            prev = node;
        }
        deadCodeElimination(graph);
    }
}
