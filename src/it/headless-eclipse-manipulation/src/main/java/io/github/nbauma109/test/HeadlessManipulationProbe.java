package io.github.nbauma109.test;

import java.util.Arrays;
import java.util.Map;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.VariableDeclarationFixCore;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;

public final class HeadlessManipulationProbe {
    private static final String SOURCE = """
            package example;
            public final class EclipseProbe {
                private String name = "example";
                public String name() { return name; }
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

        try {
            CompilationUnitChange change = cleanup.createChange(null);
            System.out.println("JDT_CORE_CHANGE_PRESENT=" + (change != null));
        } catch (Exception | LinkageError failure) {
            System.out.println("JDT_CORE_CHANGE_FAILED=" + failure.getClass().getName()
                    + ":" + failure.getMessage());
            failure.printStackTrace(System.out);
        }
    }
}
