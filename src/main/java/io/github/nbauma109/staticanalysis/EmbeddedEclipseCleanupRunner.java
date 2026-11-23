package io.github.nbauma109.staticanalysis;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Runs the genuine JDT Core private-field cleanup without an Eclipse workspace.
 * The analysis uses the cleanup's visitor and never applies edits to sources.
 */
final class EmbeddedEclipseCleanupRunner {

    private static final String FINAL_DECLARATIONS = "cleanup.make_variable_declarations_final";
    private static final String FINAL_PRIVATE_FIELDS = "cleanup.make_private_fields_final";
    private static final String CLEANUP = "VariableDeclarationCleanUpCore";

    private EmbeddedEclipseCleanupRunner() {
    }

    static EclipseCleanupRunner.Result run(Path project, List<String> roots,
            List<String> classpath, String sourceLevel, Charset encoding, Path profile,
            String duplicatePolicy) throws IOException {
        if (!Set.of("prefer-sonar", "none").contains(duplicatePolicy)) {
            throw new IOException("Unsupported Eclipse duplicate policy: " + duplicatePolicy);
        }
        if (!enablePrivateFinality(profile)) {
            return new EclipseCleanupRunner.Result(List.of(), 0);
        }

        List<Path> sourceDirectories = existingSourceRoots(project, roots);
        List<EclipseCleanupRunner.Diagnostic> findings = new ArrayList<>();
        String[] sourcePaths = sourceDirectories.stream().map(Path::toString).toArray(String[]::new);
        String[] encodings = new String[sourcePaths.length];
        Arrays.fill(encodings, encoding.name());
        String[] classpathEntries = classpath.toArray(new String[0]);

        for (Path root : sourceDirectories) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> Files.isRegularFile(path)
                        && path.toString().endsWith(".java")).toList()) {
                    analyzeFile(project, file, sourceLevel, encoding, sourcePaths, encodings,
                            classpathEntries, findings);
                }
            }
        }

        findings.sort(Comparator.comparing(EclipseCleanupRunner.Diagnostic::file)
                .thenComparingInt(EclipseCleanupRunner.Diagnostic::line)
                .thenComparingInt(EclipseCleanupRunner.Diagnostic::column));
        return new EclipseCleanupRunner.Result(List.copyOf(findings), 0);
    }

    private static List<Path> existingSourceRoots(Path project, List<String> roots) {
        List<Path> available = new ArrayList<>();
        for (String root : roots) {
            Path directory = project.resolve(root).toAbsolutePath().normalize();
            if (Files.isDirectory(directory)) {
                available.add(directory);
            }
        }
        return available;
    }

    private static void analyzeFile(Path project, Path file, String sourceLevel, Charset encoding,
            String[] sourceRoots, String[] rootEncodings, String[] classpath,
            List<EclipseCleanupRunner.Diagnostic> findings) throws IOException {
        String contents = Files.readString(file, encoding);
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(contents.toCharArray());
        parser.setUnitName(file.getFileName().toString());
        parser.setResolveBindings(true);
        parser.setEnvironment(classpath, sourceRoots, rootEncodings, true);
        Map<String, String> compilerOptions = JavaCore.getOptions();
        String compliance = "8".equals(sourceLevel) ? JavaCore.VERSION_1_8 : sourceLevel;
        JavaCore.setComplianceOptions(compliance, compilerOptions);
        parser.setCompilerOptions(compilerOptions);

        CompilationUnit unit;
        try {
            unit = (CompilationUnit) parser.createAST(null);
        } catch (RuntimeException exception) {
            throw new IOException("Unable to parse Java source with JDT: " + file, exception);
        }
        HashMap<IBinding, List<SimpleName>> writtenNames = new HashMap<>();
        try {
            unit.accept(new WrittenNamesFinder(writtenNames));
            unit.accept(new ASTVisitor() {
                @Override
                public boolean visit(FieldDeclaration declaration) {
                    if (declaration.fragments().size() != 1) {
                        return false;
                    }
                    List<ModifierChangeOperation> operations = new ArrayList<>();
                    declaration.accept(new VariableDeclarationFinder(
                            true, false, false, operations, writtenNames));
                    if (!operations.isEmpty()) {
                        addDiagnostic(project, file, declaration, unit, findings);
                    }
                    return false;
                }
            });
        } catch (RuntimeException exception) {
            throw new IOException("Unable to evaluate JDT private-field cleanup: " + file, exception);
        }
    }

    private static void addDiagnostic(Path project, Path file, FieldDeclaration declaration,
            CompilationUnit unit, List<EclipseCleanupRunner.Diagnostic> findings) {
        int offset = declaration.getStartPosition();
        if (offset < 0) {
            return;
        }
        String name = project.relativize(file).toString().replace(File.separatorChar, '/');
        int line = unit.getLineNumber(offset);
        int column = unit.getColumnNumber(offset) + 1;
        findings.add(new EclipseCleanupRunner.Diagnostic(name, line, column, CLEANUP,
                "Make this private field final.", offset, offset));
    }

    private static boolean enablePrivateFinality(Path profile) throws IOException {
        if (profile == null) {
            return true;
        }
        if (!Files.isRegularFile(profile)) {
            throw new IOException("Eclipse cleanup profile does not exist: " + profile);
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(profile.toFile());
            NodeList settings = document.getElementsByTagName("setting");
            boolean finalDeclarations = false;
            boolean finalPrivate = false;
            for (int index = 0; index < settings.getLength(); index++) {
                Element element = (Element) settings.item(index);
                String key = element.getAttribute("id");
                if (!Boolean.parseBoolean(element.getAttribute("value"))) {
                    continue;
                }
                if (FINAL_DECLARATIONS.equals(key)) {
                    finalDeclarations = true;
                } else if (FINAL_PRIVATE_FIELDS.equals(key)) {
                    finalPrivate = true;
                } else {
                    throw new IOException("The embedded Eclipse engine does not support cleanup: " + key);
                }
            }
            return finalDeclarations && finalPrivate;
        } catch (IOException exception) {
            throw exception;
        } catch (SAXException | ParserConfigurationException exception) {
            throw new IOException("Unable to read Eclipse cleanup profile: " + profile, exception);
        }
    }
}
