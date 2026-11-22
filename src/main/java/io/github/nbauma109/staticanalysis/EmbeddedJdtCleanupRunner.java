package io.github.nbauma109.staticanalysis;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.internal.corext.fix.VariableDeclarationFixCore;

/**
 * Runs selected Eclipse JDT core cleanups without an Eclipse installation or workspace.
 */
final class EmbeddedJdtCleanupRunner {

    private static final String PRIVATE_FINALITY_RULE = "java:S9438";

    private EmbeddedJdtCleanupRunner() {
    }

    static List<SupplementalAnalyzer.Finding> analyze(Path project, List<Path> sources,
            List<String> sourceRoots, List<String> classpath, Charset charset,
            Consumer<String> log) throws IOException {
        if (sources.isEmpty()) {
            return List.of();
        }
        List<Path> roots = new ArrayList<>();
        for (String root : sourceRoots) {
            Path directory = project.resolve(root).toAbsolutePath().normalize();
            if (Files.isDirectory(directory)) {
                roots.add(directory);
            }
        }
        if (roots.isEmpty()) {
            return List.of();
        }
        String[] classpathEntries = classpath.toArray(String[]::new);
        String[] sourceDirectories = roots.stream().map(Path::toString).toArray(String[]::new);
        String[] encodings = new String[roots.size()];
        java.util.Arrays.fill(encodings, charset.name());
        Map<String, String> compilerOptions = new HashMap<>(JavaCore.getOptions());
        JavaCore.setComplianceOptions(JavaCore.VERSION_17, compilerOptions);

        List<SupplementalAnalyzer.Finding> findings = new ArrayList<>();
        for (Path source : sources) {
            Path normalizedSource = source.toAbsolutePath().normalize();
            Path sourceRoot = findSourceRoot(roots, normalizedSource);
            if (sourceRoot == null) {
                continue;
            }
            CompilationUnit unit = parse(normalizedSource, sourceRoot, classpathEntries,
                    sourceDirectories, encodings, compilerOptions, charset);
            if (hasSyntaxErrors(unit)) {
                log.accept("Embedded JDT cannot parse " + project.relativize(normalizedSource)
                        + "; retaining the local compiler-backed rule.");
                continue;
            }
            unit.accept(new ASTVisitor() {
                @Override
                public boolean visit(FieldDeclaration field) {
                    if (eligibleDeclaration(field) && isFinalityCleanupApplicable(unit, field)) {
                        String relative = project.relativize(normalizedSource).toString()
                                .replace('\\', '/');
                        long line = unit.getLineNumber(field.getStartPosition());
                        findings.add(new SupplementalAnalyzer.Finding(relative, line,
                                PRIVATE_FINALITY_RULE, "Make this private field final."));
                    }
                    return false;
                }
            });
        }
        findings.sort(Comparator.comparing(SupplementalAnalyzer.Finding::file)
                .thenComparingLong(SupplementalAnalyzer.Finding::line));
        log.accept("Embedded Eclipse JDT core cleanups found " + findings.size()
                + " private-field finality issue(s).");
        return List.copyOf(findings);
    }

    private static Path findSourceRoot(List<Path> roots, Path source) {
        for (Path root : roots) {
            if (source.startsWith(root)) {
                return root;
            }
        }
        return null;
    }

    private static CompilationUnit parse(Path source, Path sourceRoot,
            String[] classpath, String[] sources, String[] encodings,
            Map<String, String> options, Charset charset) throws IOException {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(Files.readString(source, charset).toCharArray());
        parser.setUnitName(sourceRoot.relativize(source).toString().replace('\\', '/'));
        parser.setEnvironment(classpath, sources, encodings, true);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        parser.setCompilerOptions(options);
        return (CompilationUnit) parser.createAST(null);
    }

    private static boolean hasSyntaxErrors(CompilationUnit unit) {
        for (org.eclipse.jdt.core.compiler.IProblem problem : unit.getProblems()) {
            if (problem.isError()) {
                return true;
            }
        }
        return false;
    }

    private static boolean eligibleDeclaration(FieldDeclaration field) {
        return Modifier.isPrivate(field.getModifiers()) && !Modifier.isFinal(field.getModifiers());
    }

    private static boolean isFinalityCleanupApplicable(CompilationUnit unit, FieldDeclaration field) {
        return Objects.nonNull(VariableDeclarationFixCore.createChangeModifierToFinalFix(
                unit, new ASTNode[] { field }));
    }
}
