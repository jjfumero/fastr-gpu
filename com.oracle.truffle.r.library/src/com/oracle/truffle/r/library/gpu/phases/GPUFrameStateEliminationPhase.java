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

import uk.ac.ed.accelerator.common.GraalAcceleratorOptions;
import uk.ac.ed.marawacc.graal.GraalOCLBackendConnector;

import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.phases.Phase;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;
import com.oracle.graal.phases.tiers.PhaseContext;
import com.oracle.graal.phases.util.Providers;

public class GPUFrameStateEliminationPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {

        if (GraalAcceleratorOptions.printMessagesFromFastR) {
            System.out.println("[ASTx] GPUFrameStateElimination");
        }

        for (FrameState frameStateNode : graph.getNodes(FrameState.TYPE)) {
            frameStateNode.replaceAtUsages(null);
            frameStateNode.safeDelete();
        }

        Providers providers = GraalOCLBackendConnector.getProviders();
        new CanonicalizerPhase().apply(graph, new PhaseContext(providers));
        new DeadCodeEliminationPhase().apply(graph);
    }
}
