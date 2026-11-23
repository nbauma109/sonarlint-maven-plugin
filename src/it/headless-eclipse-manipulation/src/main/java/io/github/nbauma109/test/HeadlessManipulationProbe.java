package io.github.nbauma109.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.corext.fix.VariableDeclarationFixCore.ModifierChangeOperation;
import org.eclipse.jdt.internal.corext.fix.VariableDeclarationFixCore.VariableDeclarationFinder;
import org.eclipse.jdt.internal.corext.fix.VariableDeclarationFixCore.WrittenNamesFinder;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.VariableDeclarationFixCore;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

public final class HeadlessManipulationProbe {
    private static final String SOURCE = """
            package example;
            public final class EclipseProbe {
                private String name = "example";
                private String changed = "before";
                public String name() { return name; }
                public void change(String replacement) { changed = replacement; }
            }
            """;

    private HeadlessManipulationProbe() {
    }

    public static void main(String[] arguments) throws Exception {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(SOURCE.toCharArray());
        parser.setUnitName("example/EclipseProbe.java");
        parser.setResolveBindings(true);
        parser.setEnvironment(new String[0], new String[0], new String[0], true);
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
        parser.setCompilerOptions(options);

        CompilationUnit unit = (CompilationUnit) parser.createAST(null);
        long errors = Arrays.stream(unit.getProblems())
                .filter(IProblem::isError).count();
        System.out.println("JDT_PARSE_ERRORS=" + errors);
        System.out.println("JDT_COMPILATION_UNIT=" + unit.getClass().getName());

        ICleanUpFix cleanup = VariableDeclarationFixCore.createCleanUp(unit, true, false, false);
        System.out.println("JDT_CORE_FIX_PRESENT=" + (cleanup != null));
        if (cleanup == null) {
            throw new IllegalStateException("Expected Eclipse final-field cleanup");
        }

        HashMap<IBinding, List<SimpleName>> recordedWrites = new HashMap<>();
        unit.accept(new WrittenNamesFinder(recordedWrites));
        List<Integer> fixLines = new ArrayList<>();
        unit.accept(new ASTVisitor() {
            @Override
            public boolean visit(FieldDeclaration declaration) {
                List<ModifierChangeOperation> operations = new ArrayList<>();
                VariableDeclarationFinder finder = new VariableDeclarationFinder(
                        true, false, false, operations, recordedWrites);
                declaration.accept(finder);
                if (!operations.isEmpty()) {
                    for (Object fragment : declaration.fragments()) {
                        VariableDeclarationFragment variable = (VariableDeclarationFragment) fragment;
                        fixLines.add(unit.getLineNumber(variable.getStartPosition()));
                    }
                }
                return false;
            }
        });
        System.out.println("JDT_CORE_FINAL_FIELD_FIX_LINES=" + fixLines);
        if (fixLines.size() != 1) {
            throw new IllegalStateException("Expected one safe field-finality cleanup");
        }
    }
}
