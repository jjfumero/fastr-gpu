package com.oracle.truffle.r.library.gpu.nodes.utils;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNodeVisitor;

public class ASTxPrinter implements RSyntaxNodeVisitor {

    public boolean visit(RSyntaxNode node, int depth) {
        for (int i = 0; i < depth; i++) {
            System.out.print(' ');
        }
        System.out.print(node.getClass().getSimpleName());
        SourceSection ss = ((Node) node).getSourceSection();
        // All syntax nodes should have source sections
        if (ss == null) {
            System.out.print(" *** null source section");
        } else {
            printSourceCode(ss);
        }

        System.out.println();
        return true;
    }

    private static void printSourceCode(SourceSection ss) {
        String code = ss.getCode();
        if (code.length() > 20) {
            code = code.substring(0, 20) + " ....";
        }
        code = code.replace("\n", "\\n ");
        System.out.print(" : ");
        System.out.print(code.length() == 0 ? "<EMPTY>" : code);
    }
}
