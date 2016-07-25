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

import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.calc.ReinterpretNode;
import com.oracle.graal.phases.Phase;

public class GPUCleanPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {

// ParameterNode parameterNode = graph.getParameter(1);
//
// System.out.println(GraphUtil.unproxify((parameterNode.asNode())));

        for (Node node : graph.getNodes()) {

            if (node instanceof ReinterpretNode) {
                // node.markDeleted();
            } else if (node instanceof ConstantNode) {
                // node.markDeleted();
            }

// for (Node n : node.cfgSuccessors()) {
// System.out.println("\t " + n);
// }
        }

// new DeadCodeEliminationPhase().apply(graph);
    }
}
