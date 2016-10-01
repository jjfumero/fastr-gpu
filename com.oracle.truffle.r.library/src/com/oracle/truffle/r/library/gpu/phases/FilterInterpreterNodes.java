package com.oracle.truffle.r.library.gpu.phases;

import java.util.ArrayList;

import jdk.vm.ci.meta.Constant;
import uk.ac.ed.marawacc.graal.GraalOCLBackendConnector;

import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FixedGuardNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.java.CheckCastNode;
import com.oracle.graal.nodes.java.LoadIndexedNode;
import com.oracle.graal.phases.Phase;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;
import com.oracle.graal.phases.tiers.PhaseContext;
import com.oracle.graal.phases.util.Providers;

public class FilterInterpreterNodes extends Phase {

    private int idx;
    private ArrayList<Node> subTree = new ArrayList<>();
    private ArrayList<Node> checkCastNodes = new ArrayList<>();
    private ArrayList<Node> isNullNodes = new ArrayList<>();
    private ArrayList<Node> guardNodes = new ArrayList<>();
    private ArrayList<Node> prevList = new ArrayList<>();

    public FilterInterpreterNodes(int idx) {
        this.idx = idx;
    }

    private static void deadCodeElimination(StructuredGraph graph) {
        Providers providers = GraalOCLBackendConnector.getHostBackend().getProviders();
        new CanonicalizerPhase().apply(graph, new PhaseContext(providers));
        new DeadCodeEliminationPhase().apply(graph);
    }

    private static void removeIsNullNode(Node node) {
        if (node instanceof IsNullNode) {
            node.replaceAtUsages(null);
            node.safeDelete();
        }
    }

    private static void removeGuards(Node node, StructuredGraph graph) {
        if (node instanceof FixedGuardNode) {
            node.replaceAtUsages(null);
            graph.removeFixed((FixedWithNextNode) node);
        }
    }

    private static void removeCheckCast(Node node, Node prev, StructuredGraph graph) {
        if (node instanceof CheckCastNode) {
            graph.replaceFixed((FixedWithNextNode) node, prev);
        }
    }

    @Override
    protected void run(StructuredGraph graph) {

        // Look at the LoadIndexed
        for (Node node : graph.getNodes()) {
            if (node instanceof LoadIndexedNode) {
                LoadIndexedNode loadIndexed = (LoadIndexedNode) node;
                ValueNode index = loadIndexed.index();
                if (index instanceof ConstantNode) {
                    ConstantNode constant = (ConstantNode) index;
                    Constant value = constant.getValue();
                    if (value.toValueString().equals(new Integer(idx).toString())) {
                        subTree.add(node);
                    }
                }
            }
        }

        int i = 0;
        if (!subTree.isEmpty()) {
            Node node = subTree.get(0);
            Node first = node.successors().first();
            boolean notFound = true;
            while (notFound) {
                boolean added = false;
                if (first.getClass() == CheckCastNode.class || first.getClass() == FixedGuardNode.class) {
                    if (first instanceof CheckCastNode) {
                        Node guard = first.successors().first();
                        if (guard instanceof FixedGuardNode) {
                            LogicNode condition = ((FixedGuardNode) guard).condition();
                            if (condition instanceof IsNullNode) {
                                added = true;
                                guardNodes.add(guard);
                                isNullNodes.add(condition);
                                checkCastNodes.add(first);
                                prevList.add(first.predecessor());
                            }
                        }
                    }
                    if (added) {
                        first = guardNodes.get(guardNodes.size() - 1).successors().first();
                    } else {
                        i++;
                        first = subTree.get(i).successors().first();
                    }
                } else {
                    notFound = false;
                }
            }
        }

        System.out.println(prevList);

        for (Node n : isNullNodes) {
            removeIsNullNode(n);
        }
        deadCodeElimination(graph);

        i = 0;
        for (Node n : checkCastNodes) {
            removeCheckCast(n, prevList.get(i), graph);
            i++;
        }
        deadCodeElimination(graph);

        for (Node n : guardNodes) {
            removeGuards(n, graph);
        }
        deadCodeElimination(graph);

        isNullNodes.clear();
        checkCastNodes.clear();
        guardNodes.clear();

    }
}
