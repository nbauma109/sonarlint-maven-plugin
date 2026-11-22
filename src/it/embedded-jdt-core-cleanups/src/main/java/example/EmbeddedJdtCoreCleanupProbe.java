package example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.corext.fix.VariableDeclarationFixCore;
import org.eclipse.jdt.internal.corext.fix.VariableDeclarationFixCore.ModifierChangeOperation;
import org.eclipse.jdt.internal.corext.fix.VariableDeclarationFixCore.VariableDeclarationFinder;
import org.eclipse.jdt.internal.corext.fix.VariableDeclarationFixCore.WrittenNamesFinder;

public final class EmbeddedJdtCoreCleanupProbe {

    private EmbeddedJdtCoreCleanupProbe() {
    }

    public static void main(String[] arguments) {
        String source = """
                package example;

                final class Candidate {
                    private String initialized = "initial";
                    private String mutable = "original";
                    private int constructorInitialized;

                    Candidate() {
                        this(1);
                    }

                    Candidate(int number) {
                        constructorInitialized = number;
                    }

                    void modify() {
                        mutable = "changed";
                    }
                }
                """;
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setUnitName("example/Candidate.java");
        parser.setSource(source.toCharArray());
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(false);
        parser.setCompilerOptions(Map.of(
                JavaCore.COMPILER_SOURCE, "17",
                JavaCore.COMPILER_COMPLIANCE, "17",
                JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, "17"));
        CompilationUnit unit = (CompilationUnit) parser.createAST(null);
        int errorCount = 0;
        for (var problem : unit.getProblems()) {
            if (problem.isError()) {
                errorCount++;
                System.err.println(problem.getMessage());
            }
        }
        if (errorCount != 0) {
            throw new IllegalStateException("The embedded JDT parser could not resolve the source.");
        }

        HashMap<IBinding, List<SimpleName>> writes = new HashMap<>();
        unit.accept(new WrittenNamesFinder(writes));
        Set<String> eligible = new LinkedHashSet<>();
        unit.accept(new ASTVisitor() {
            @Override
            public boolean visit(FieldDeclaration declaration) {
                List<ModifierChangeOperation> operations = new ArrayList<>();
                declaration.accept(new VariableDeclarationFinder(
                        true, false, false, operations, writes));
                if (!operations.isEmpty()) {
                    for (Object candidate : declaration.fragments()) {
                        if (candidate instanceof VariableDeclarationFragment field) {
                            eligible.add(field.getName().getIdentifier());
                        }
                    }
                }
                return false;
            }
        });
        Set<String> expected = Set.of("initialized", "constructorInitialized");
        if (!eligible.equals(expected)) {
            throw new IllegalStateException("Unexpected JDT core cleanup findings: " + eligible);
        }
        if (Runtime.version().feature() >= 25) {
            String modernSource = """
                    package example;
                    class Java25Candidate extends RuntimeException {
                        Java25Candidate(String message) {
                            int length = message.length();
                            super(Integer.toString(length));
                        }
                    }
                    """;
            ASTParser modernParser = ASTParser.newParser(AST.getJLSLatest());
            modernParser.setKind(ASTParser.K_COMPILATION_UNIT);
            modernParser.setUnitName("example/Java25Candidate.java");
            modernParser.setSource(modernSource.toCharArray());
            modernParser.setEnvironment(new String[0], new String[0], null, true);
            modernParser.setResolveBindings(true);
            modernParser.setCompilerOptions(Map.of(
                    JavaCore.COMPILER_SOURCE, "25",
                    JavaCore.COMPILER_COMPLIANCE, "25",
                    JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, "25"));
            CompilationUnit modernUnit = (CompilationUnit) modernParser.createAST(null);
            for (var problem : modernUnit.getProblems()) {
                if (problem.isError()) {
                    throw new IllegalStateException("Java 25 syntax was not understood: "
                            + problem.getMessage());
                }
            }
            System.out.println("EMBEDDED_JDT_JAVA25_SYNTAX_SUCCESS");
        }
        System.out.println("EMBEDDED_JDT_CORE_FINAL_FIELDS " + String.join(",", eligible));
        System.out.println("EMBEDDED_JDT_CORE_SUCCESS");
    }
}
