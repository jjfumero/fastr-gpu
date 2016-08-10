package com.oracle.truffle.r.library.gpu.nodes.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Scanner;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.r.library.gpu.utils.ASTxUtils;
import com.oracle.truffle.r.nodes.access.WriteCurrentVariableNode;
import com.oracle.truffle.r.nodes.access.variables.ReadVariableNode;
import com.oracle.truffle.r.nodes.control.ReplacementNode;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNodeVisitor;

public class ASTLexicalScoping {

    private HashSet<String> writes;
    private HashSet<String> reads;
    private HashSet<String> scopes;
    private HashSet<String> primitives;
    private HashSet<String> replacements;
    private Node root;
    private RFunction function;

    public ASTLexicalScoping() {
        writes = new HashSet<>();
        reads = new HashSet<>();
        scopes = new HashSet<>();
        primitives = new HashSet<>();
        replacements = new HashSet<>();
    }

    public enum Primitives {
        // Include here the full list
        LIST("list"),
        RETURN("return"),
        RPAREN("["),
        RDOUBLEPAREN("[["),
        AMPERSAND("&&");

        String lexeme;

        Primitives(String lexeme) {
            this.lexeme = lexeme;
        }

        public String lexeme() {
            return lexeme;
        }

        @Override
        public String toString() {
            return lexeme;
        }
    }

    private class LexicalScoping implements RSyntaxNodeVisitor {
        ArrayList<Node> nodes;

        public LexicalScoping() {
            nodes = new ArrayList<>();
        }

        public boolean visit(RSyntaxNode node, int depth) {
            nodes.add((Node) node);
            return true;
        }

        public ArrayList<Node> getNodes() {
            return nodes;
        }
    }

    private void filterWrite(ArrayList<Node> allNodes) {
        for (Node node : allNodes) {
            if (node.getClass() == WriteCurrentVariableNode.class) {
                WriteCurrentVariableNode var = (WriteCurrentVariableNode) node;
                if (!writes.contains(var.getName())) {
                    writes.add((String) var.getName());
                }
            }
        }
    }

    private void filterRead(ArrayList<Node> allNodes) {
        for (Node node : allNodes) {
            if (node.getClass() == ReadVariableNode.class) {
                ReadVariableNode var = (ReadVariableNode) node;
                String identifier = var.getIdentifier();

                if (writes.contains(identifier)) {
                    reads.add(identifier);
                } else {
                    if (!primitives.contains(identifier)) {
                        scopes.add(identifier);
                    }
                }
            }
        }
    }

    private void filterReplacement(ArrayList<Node> allNodes) {
        for (Node node : allNodes) {
            if (node.getClass() == ReplacementNode.class) {
                ReplacementNode var = (ReplacementNode) node;
                String code = var.getSourceSection().getCode();
                Scanner scanner = new Scanner(code);
                String id = null;
                scanner.useDelimiter("\\[");
                if (scanner.hasNext()) {
                    id = scanner.next();
                }

                if (writes.contains(id)) {
                    replacements.add(id);
                } else {
                    if (!primitives.contains(id)) {
                        scopes.add(id);
                    }
                }
            }
        }
    }

    public String[] scopeVars() {
        String[] out = new String[scopes.size()];
        int idx = 0;
        for (String s : scopes) {
            out[idx++] = s;
        }
        return out;
    }

    public void apply(RFunction rFunction) {

        this.function = rFunction;
        String[] argumentsNames = ASTxUtils.getArgumentsNames(function);
        this.root = rFunction.getRootNode();

        LexicalScoping scoping = new LexicalScoping();
        RSyntaxNode.accept(root, 0, scoping);
        ArrayList<Node> allNodes = scoping.getNodes();

        Primitives[] values = Primitives.values();
        for (Primitives p : values) {
            primitives.add(p.lexeme());
        }

        for (String s : argumentsNames) {
            writes.add(s);
        }

        filterWrite(allNodes);
        filterRead(allNodes);
        filterReplacement(allNodes);

        System.out.println(scopes);

    }
}
