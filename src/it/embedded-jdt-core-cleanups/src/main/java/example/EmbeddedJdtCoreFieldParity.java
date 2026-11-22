package example;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.internal.corext.fix.VariableDeclarationFixCore.ModifierChangeOperation;
import org.eclipse.jdt.internal.corext.fix.VariableDeclarationFixCore.VariableDeclarationFinder;
import org.eclipse.jdt.internal.corext.fix.VariableDeclarationFixCore.WrittenNamesFinder;

public final class EmbeddedJdtCoreFieldParity {

    private static final Set<String> EXPECTED = Set.of(
            "decompilers/cfr/CFRDataSource.java:30",
            "decompilers/cfr/CFRDataSource.java:31",
            "decompilers/cfr/CFRDataSource.java:32",
            "decompilers/cfr/CFROutputStreamFactory.java:32",
            "decompilers/cfr/CFROutputStreamFactory.java:33",
            "decompilers/cfr/CFRSettings.java:29",
            "decompilers/fernflower/FernflowerSettings.java:34",
            "decompilers/vineflower/VineflowerSettings.java:35");

    private EmbeddedJdtCoreFieldParity() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected project directory and Maven classpath file.");
        }
        Path root = Path.of(arguments[0]).toAbsolutePath().normalize()
                .resolve("src/main/java");
        Path project = root.getParent().getParent().getParent();
        String rawClasspath = Files.readString(
                Path.of(arguments[1]).toAbsolutePath().normalize(), StandardCharsets.UTF_8).trim();
        List<String> classpath = new ArrayList<>();
        classpath.add(project.resolve("target/classes").toString());
        if (!rawClasspath.isEmpty()) {
            classpath.addAll(List.of(rawClasspath.split(java.util.regex.Pattern.quote(File.pathSeparator))));
        }
        Set<String> actual = new LinkedHashSet<>();
        try (Stream<Path> sources = Files.walk(root)) {
            for (Path file : sources.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java")).toList()) {
                analyzeFile(root, file, classpath, actual);
            }
        }
        for (String entry : actual) {
            System.out.println("EMBEDDED_JDT_FIELD " + entry);
        }
        Set<String> missing = new LinkedHashSet<>(EXPECTED);
        missing.removeAll(actual);
        Set<String> extra = new LinkedHashSet<>(actual);
        extra.removeAll(EXPECTED);
        System.out.println("EMBEDDED_JDT_MATCHED " + (EXPECTED.size() - missing.size()));
        System.out.println("EMBEDDED_JDT_MISSING " + missing);
        System.out.println("EMBEDDED_JDT_EXTRA " + extra);
        if (!missing.isEmpty() || !extra.isEmpty()) {
            throw new IllegalStateException("Embedded JDT Core Manipulation differs from pinned SonarCloud finality findings.");
        }
        System.out.println("EMBEDDED_JDT_EIGHT_FIELD_PARITY_SUCCESS");
    }

    private static void analyzeFile(Path root, Path file, List<String> classpath,
            Set<String> actual) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setUnitName(root.relativize(file).toString().replace(File.separatorChar, '/'));
        parser.setSource(source.toCharArray());
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(false);
        parser.setEnvironment(classpath.toArray(String[]::new),
                new String[] {root.toString()}, new String[] {"UTF-8"}, true);
        parser.setCompilerOptions(Map.of(
                JavaCore.COMPILER_SOURCE, "25",
                JavaCore.COMPILER_COMPLIANCE, "25",
                JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, "25"));
        CompilationUnit unit = (CompilationUnit) parser.createAST(null);
        for (var problem : unit.getProblems()) {
            if (problem.isError()) {
                throw new IllegalStateException("JDT could not analyze " + file
                        + ": " + problem.getMessage());
            }
        }
        HashMap<IBinding, List<SimpleName>> writtenNames = new HashMap<>();
        unit.accept(new WrittenNamesFinder(writtenNames));
        unit.accept(new ASTVisitor() {
            @Override
            public boolean visit(FieldDeclaration field) {
                if (field.fragments().size() != 1) {
                    return false;
                }
                List<ModifierChangeOperation> fixes = new ArrayList<>();
                field.accept(new VariableDeclarationFinder(
                        true, false, false, fixes, writtenNames));
                if (!fixes.isEmpty()) {
                    String shortPath = root.relativize(file).toString().replace(File.separatorChar, '/');
                    int line = unit.getLineNumber(field.getStartPosition());
                    String key = shortPath.substring(shortPath.indexOf("decompilers/")) + ":" + line;
                    actual.add(key);
                }
                return false;
            }
        });
    }
}
