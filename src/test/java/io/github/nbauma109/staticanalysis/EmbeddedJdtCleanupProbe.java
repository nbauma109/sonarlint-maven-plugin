package io.github.nbauma109.staticanalysis;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.internal.corext.fix.VariableDeclarationFixCore;

/**
 * Explores whether we can run Eclipse field finality cleanup directly without an Eclipse workspace.
 */
public final class EmbeddedJdtCleanupProbe {

    private static final Map<String, Set<Integer>> EXPECTED = Map.of(
            "decompilers/cfr/CFRDataSource.java", Set.of(30, 31, 32),
            "decompilers/cfr/CFROutputStreamFactory.java", Set.of(32, 33),
            "decompilers/cfr/CFRSettings.java", Set.of(29),
            "decompilers/fernflower/FernflowerSettings.java", Set.of(34),
            "decompilers/vineflower/VineflowerSettings.java", Set.of(35));

    private static final String PACKAGE_PATH = "com/heliosdecompiler/transformerapi/";

    private EmbeddedJdtCleanupProbe() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Provide the transformer-api project directory.");
        }
        Path project = Path.of(arguments[0]).toAbsolutePath().normalize();
        Path sourceRoot = project.resolve("src/main/java");
        String classpathProperty = System.getenv().getOrDefault("PROBE_CLASSPATH", "");
        String[] classpath = classpathProperty.isBlank() ? new String[0]
                : classpathProperty.split(java.util.regex.Pattern.quote(File.pathSeparator));
        Set<String> actual = new TreeSet<>();
        List<String> paths = new ArrayList<>(EXPECTED.keySet());
        paths.sort(Comparator.naturalOrder());
        for (String relativePath : paths) {
            Path source = sourceRoot.resolve(PACKAGE_PATH + relativePath);
            probeSource(sourceRoot, source, classpath, actual);
        }
        Set<String> expected = new TreeSet<>();
        for (Map.Entry<String, Set<Integer>> entry : EXPECTED.entrySet()) {
            for (Integer line : entry.getValue()) {
                expected.add(entry.getKey() + ":" + line);
            }
        }
        System.out.println("EMBEDDED_JDT_EXPECTED " + expected.size());
        System.out.println("EMBEDDED_JDT_FOUND " + actual.size());
        System.out.println("EMBEDDED_JDT_MISSING " + difference(expected, actual));
        System.out.println("EMBEDDED_JDT_UNEXPECTED " + difference(actual, expected));
        if (!actual.equals(expected)) {
            throw new IllegalStateException("Embedded JDT cleanup parity differs from SonarCloud.");
        }
    }

    private static void probeSource(Path sourceRoot, Path source, String[] classpath,
            Set<String> actual) throws IOException {
        String fileName = sourceRoot.relativize(source).toString().replace('\\', '/');
        char[] javaSource = Files.readString(source, StandardCharsets.UTF_8).toCharArray();
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(javaSource);
        parser.setUnitName(fileName);
        parser.setEnvironment(classpath,
                new String[] { sourceRoot.toString() },
                new String[] { StandardCharsets.UTF_8.name() }, true);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_25, options);
        parser.setCompilerOptions(options);
        CompilationUnit unit = (CompilationUnit) parser.createAST(null);
        unit.accept(new ASTVisitor() {
            @Override
            public boolean visit(FieldDeclaration field) {
                if (VariableDeclarationFixCore.createChangeModifierToFinalFix(
                        unit, new ASTNode[] {field}) != null) {
                    int line = unit.getLineNumber(field.getStartPosition());
                    String relativeFile = fileName.substring(PACKAGE_PATH.length());
                    String location = relativeFile + ":" + line;
                    actual.add(location);
                    System.out.println("EMBEDDED_JDT_ISSUE " + location);
                }
                return false;
            }
        });
        long problems = Arrays.stream(unit.getProblems())
                .filter(problem -> problem.isError())
                .count();
        System.out.println("EMBEDDED_JDT_SOURCE " + fileName + " problems=" + problems);
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> difference = new TreeSet<>(left);
        difference.removeAll(right);
        return difference;
    }
}
