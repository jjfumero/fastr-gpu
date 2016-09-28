package com.oracle.truffle.r.library.gpu.phases.scope;

import java.util.ArrayList;

import uk.ac.ed.marawacc.graal.GraalOCLBackendConnector;

import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.FixedGuardNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.phases.Phase;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;
import com.oracle.graal.phases.tiers.PhaseContext;
import com.oracle.graal.phases.util.Providers;

public class CleanFixedGuardNodes extends Phase {

    private ArrayList<FixedGuardNode> guards;
    private ArrayList<Node> conditions;

    public CleanFixedGuardNodes(ArrayList<FixedGuardNode> guards) {
        this.guards = guards;
        this.conditions = new ArrayList<>();
    }

    private static void deadCodeElimination(StructuredGraph graph) {
        Providers providers = GraalOCLBackendConnector.getProviders();
        new CanonicalizerPhase().apply(graph, new PhaseContext(providers));
        new DeadCodeEliminationPhase().apply(graph);
    }

    public void cleanConditions(StructuredGraph graph) {
        // Get the conditions
        for (FixedGuardNode node : guards) {
            LogicNode condition = node.condition();
            conditions.add(condition);
        }

        // Clean conditions
        if (!conditions.isEmpty()) {
            for (Node node : conditions) {
                node.replaceAtUsages(null);
                node.safeDelete();
            }
            deadCodeElimination(graph);
        }
    }

    public void cleanGuards(StructuredGraph graph) {
        if (!guards.isEmpty()) {
            for (Node n : guards) {
                n.replaceAtUsages(null);
                graph.removeFixed((FixedWithNextNode) n);
            }
            deadCodeElimination(graph);
        }
    }

    @Override
    public void run(StructuredGraph graph) {
        // cleanConditions(graph);
        cleanGuards(graph);
    }
}
