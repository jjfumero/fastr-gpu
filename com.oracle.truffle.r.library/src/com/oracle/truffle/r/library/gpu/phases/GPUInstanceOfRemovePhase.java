package com.oracle.truffle.r.library.gpu.phases;

import uk.ac.ed.accelerator.common.GraalAcceleratorOptions;
import uk.ac.ed.marawacc.graal.GraalOCLBackendConnector;

import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.java.InstanceOfNode;
import com.oracle.graal.phases.Phase;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;
import com.oracle.graal.phases.tiers.PhaseContext;
import com.oracle.graal.phases.util.Providers;

public class GPUInstanceOfRemovePhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {

        if (GraalAcceleratorOptions.printMessagesFromFastR) {
            System.out.println("[ASTx] GPUInstanceOfRemovePhase");
        }

        for (Node node : graph.getNodes()) {
            if (node instanceof InstanceOfNode) {
                node.replaceAtUsages(null);
                node.safeDelete();
            }
        }

        for (Node node : graph.getNodes()) {
            if (node instanceof IsNullNode) {
                node.replaceAtUsages(null);
                node.safeDelete();
            }
        }

        Providers providers = GraalOCLBackendConnector.getProviders();
        new CanonicalizerPhase().apply(graph, new PhaseContext(providers));
        new DeadCodeEliminationPhase().apply(graph);

    }
}
