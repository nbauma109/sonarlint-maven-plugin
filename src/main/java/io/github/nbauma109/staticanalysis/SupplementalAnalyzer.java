package io.github.nbauma109.staticanalysis;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

final class SupplementalAnalyzer {

    private SupplementalAnalyzer() {
    }

    record Finding(String file, long line, String rule, String message) {
    }

    static List<Finding> analyze(Path baseDirectory, List<Path> sources, List<String> classpath,
            String sourceVersion, Set<String> enabledRules) throws IOException {
        if (sources.isEmpty() || enabledRules.isEmpty()) {
            return List.of();
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("A Java Development Kit is required for supplemental source checks.");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<String> options = compilerOptions(classpath, sourceVersion);
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
            Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromPaths(sources);
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics, options, null, units);
            List<CompilationUnitTree> parsed = new ArrayList<>();
            for (CompilationUnitTree unit : task.parse()) {
                parsed.add(unit);
            }
            task.analyze();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    throw new IOException("Supplemental analysis could not resolve the Java sources: "
                            + diagnostic.getMessage(null));
                }
            }
            Trees trees = Trees.instance(task);
            List<Finding> findings = new ArrayList<>();
            for (CompilationUnitTree unit : parsed) {
                Scanner scanner = new Scanner(trees, unit, baseDirectory, enabledRules, sourceVersion);
                scanner.scan(unit, null);
                findings.addAll(scanner.finish());
            }
            return List.copyOf(findings);
        } catch (RuntimeException exception) {
            throw new IOException("Supplemental Java analysis failed: " + exception.getMessage(), exception);
        }
    }

    private static List<String> compilerOptions(List<String> classpath, String sourceVersion) {
        List<String> options = new ArrayList<>(List.of("-proc:none", "-implicit:none"));
        if (!classpath.isEmpty()) {
            options.add("-classpath");
            options.add(String.join(File.pathSeparator, classpath));
        }
        if (sourceVersion != null && !sourceVersion.isBlank()) {
            options.add("--release");
            options.add(sourceVersion);
        }
        return options;
    }

    private static final class Scanner extends TreePathScanner<Void, Void> {
        private final Trees trees;
        private final CompilationUnitTree unit;
        private final Path baseDirectory;
        private final Set<String> enabledRules;
        private final int languageVersion;
        private final List<Finding> findings = new ArrayList<>();
        private final Map<VariableElement, VariableTree> fields = new HashMap<>();
        private final Map<VariableElement, List<ExecutableElement>> mutations = new HashMap<>();
        private final Map<TypeElement, Set<ExecutableElement>> constructors = new HashMap<>();
        private final Map<ExecutableElement, ExecutableElement> constructorDelegations = new HashMap<>();
        private final Map<ExecutableElement, MethodTree> methods = new HashMap<>();
        private final Map<ExecutableElement, MethodUsage> usages = new HashMap<>();

        Scanner(Trees trees, CompilationUnitTree unit, Path baseDirectory,
                Set<String> enabledRules, String sourceVersion) {
            this.trees = trees;
            this.unit = unit;
            this.baseDirectory = baseDirectory;
            this.enabledRules = enabledRules;
            this.languageVersion = parseSourceVersion(sourceVersion);
        }

        private static int parseSourceVersion(String sourceVersion) {
            if (sourceVersion == null || sourceVersion.isBlank()) {
                return Runtime.version().feature();
            }
            String normalized = sourceVersion.startsWith("1.")
                    ? sourceVersion.substring(2) : sourceVersion;
            return Integer.parseInt(normalized);
        }

        @Override
        public Void visitClass(ClassTree tree, Void unused) {
            if (enabledRules.contains("java:S9398") && tree.getKind() == Tree.Kind.CLASS) {
                TreePath parent = getCurrentPath().getParentPath();
                if (parent != null && parent.getLeaf().getKind() == Tree.Kind.INTERFACE
                        && !tree.getModifiers().getFlags().contains(Modifier.ABSTRACT)
                        && !tree.getSimpleName().toString().endsWith("Builder")) {
                    report(tree, "java:S9398", "Move this class out of this interface.");
                }
            }
            return super.visitClass(tree, unused);
        }

        @Override
        public Void visitMethod(MethodTree tree, Void unused) {
            Element symbol = trees.getElement(getCurrentPath());
            if (symbol instanceof ExecutableElement method) {
                if (symbol.getKind() == ElementKind.CONSTRUCTOR
                        && symbol.getEnclosingElement() instanceof TypeElement owner) {
                    constructors.computeIfAbsent(owner, ignored -> new LinkedHashSet<>()).add(method);
                    recordConstructorDelegation(method, tree);
                }
                if (enabledRules.contains("java:S3398") && symbol.getKind() == ElementKind.METHOD
                        && symbol.getModifiers().contains(Modifier.PRIVATE)) {
                    methods.put(method, tree);
                }
            }
            return super.visitMethod(tree, unused);
        }

        private void recordConstructorDelegation(ExecutableElement constructor, MethodTree tree) {
            if (tree.getBody() == null || tree.getBody().getStatements().isEmpty()) {
                return;
            }
            Tree statement = tree.getBody().getStatements().get(0);
            if (!(statement instanceof ExpressionStatementTree expression)
                    || !(expression.getExpression() instanceof MethodInvocationTree call)
                    || !(call.getMethodSelect() instanceof IdentifierTree identifier)
                    || !"this".contentEquals(identifier.getName())) {
                return;
            }
            TreePath statementPath = new TreePath(new TreePath(
                    new TreePath(getCurrentPath(), tree.getBody()), statement), call);
            Element target = trees.getElement(statementPath);
            if (target instanceof ExecutableElement delegated
                    && target.getKind() == ElementKind.CONSTRUCTOR) {
                constructorDelegations.put(constructor, delegated);
            }
        }

        @Override
        public Void visitVariable(VariableTree tree, Void unused) {
            if (enabledRules.contains("java:S9438")) {
                TreePath parent = getCurrentPath().getParentPath();
                Element symbol = trees.getElement(getCurrentPath());
                if (parent != null && parent.getLeaf() instanceof ClassTree
                        && symbol instanceof VariableElement field
                        && symbol.getKind() == ElementKind.FIELD
                        && symbol.getModifiers().contains(Modifier.PRIVATE)
                        && !symbol.getModifiers().contains(Modifier.FINAL)) {
                    fields.put(field, tree);
                }
            }
            return super.visitVariable(tree, unused);
        }

        @Override
        public Void visitAssignment(AssignmentTree tree, Void unused) {
            recordMutation(tree.getVariable(), true);
            return super.visitAssignment(tree, unused);
        }

        @Override
        public Void visitCompoundAssignment(CompoundAssignmentTree tree, Void unused) {
            recordMutation(tree.getVariable(), false);
            return super.visitCompoundAssignment(tree, unused);
        }

        @Override
        public Void visitUnary(UnaryTree tree, Void unused) {
            switch (tree.getKind()) {
                case PREFIX_INCREMENT, PREFIX_DECREMENT, POSTFIX_INCREMENT, POSTFIX_DECREMENT ->
                    recordMutation(tree.getExpression(), false);
                default -> { }
            }
            return super.visitUnary(tree, unused);
        }

        private void recordMutation(ExpressionTree target, boolean simpleAssignment) {
            if (!enabledRules.contains("java:S9438")) {
                return;
            }
            Element element = trees.getElement(new TreePath(getCurrentPath(), target));
            if (element instanceof VariableElement field && element.getKind() == ElementKind.FIELD) {
                ExecutableElement direct = null;
                if (simpleAssignment) {
                    direct = directConstructor();
                }
                mutations.computeIfAbsent(field, ignored -> new ArrayList<>()).add(direct);
            }
        }

        private ExecutableElement directConstructor() {
            TreePath expression = getCurrentPath().getParentPath();
            TreePath block = expression == null ? null : expression.getParentPath();
            TreePath methodPath = block == null ? null : block.getParentPath();
            if (expression == null || !(expression.getLeaf() instanceof ExpressionStatementTree)
                    || block == null || !(block.getLeaf() instanceof BlockTree)
                    || methodPath == null || !(methodPath.getLeaf() instanceof MethodTree)) {
                return null;
            }
            Element element = trees.getElement(methodPath);
            if (element instanceof ExecutableElement method && element.getKind() == ElementKind.CONSTRUCTOR) {
                return method;
            }
            return null;
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
            if (enabledRules.contains("java:S3398")) {
                addMethodUsage(trees.getElement(getCurrentPath()));
            }
            if (enabledRules.contains("java:S8786")) {
                checkRegularExpression(tree);
            }
            return super.visitMethodInvocation(tree, unused);
        }

        private void checkRegularExpression(MethodInvocationTree tree) {
            if (!(tree.getMethodSelect() instanceof MemberSelectTree select)
                    || tree.getArguments().isEmpty()
                    || !(tree.getArguments().get(0) instanceof LiteralTree literal)
                    || !(literal.getValue() instanceof String pattern)
                    || !isUnanchoredGreedyWhitespacePattern(pattern)) {
                return;
            }
            String method = select.getIdentifier().toString();
            if (!"replaceAll".equals(method) && !"replaceFirst".equals(method)
                    && !"matches".equals(method)) {
                return;
            }
            Element element = trees.getElement(getCurrentPath());
            if (!(element instanceof ExecutableElement executable)
                    || !(executable.getEnclosingElement() instanceof TypeElement owner)
                    || !"java.lang.String".contentEquals(owner.getQualifiedName())) {
                return;
            }
            report(literal, "java:S8786",
                    "Simplify this regular expression to reduce its runtime, "
                            + "as it has super-linear performance due to backtracking.");
        }

        private static boolean isUnanchoredGreedyWhitespacePattern(String pattern) {
            boolean repeatedWhitespace = pattern.startsWith(" *")
                    || pattern.startsWith("\\s*");
            if (!repeatedWhitespace || pattern.startsWith("^")) {
                return false;
            }
            String remainder = pattern.substring(pattern.indexOf('*') + 1);
            return remainder.equals("\r?\n") || remainder.equals("\\r?\\n");
        }

        @Override
        public Void visitMemberReference(MemberReferenceTree tree, Void unused) {
            if (enabledRules.contains("java:S3398")) {
                addMethodUsage(trees.getElement(getCurrentPath()));
            }
            return super.visitMemberReference(tree, unused);
        }

        private void addMethodUsage(Element symbol) {
            if (!(symbol instanceof ExecutableElement method)
                    || symbol.getKind() != ElementKind.METHOD
                    || !symbol.getModifiers().contains(Modifier.PRIVATE)) {
                return;
            }
            MethodUsage usage = usages.computeIfAbsent(method, ignored -> new MethodUsage());
            TypeElement owner = (TypeElement) symbol.getEnclosingElement();
            TypeElement record = containingRecord(owner);
            if (record == null) {
                usage.outsideRecord = true;
            } else {
                usage.records.add(record);
            }
        }

        private TypeElement containingRecord(TypeElement owner) {
            TreePath current = getCurrentPath();
            while (current != null) {
                if (current.getLeaf() instanceof ClassTree declaration
                        && declaration.getKind() == Tree.Kind.RECORD) {
                    Element element = trees.getElement(current);
                    if (element instanceof TypeElement nested
                            && Objects.equals(nested.getEnclosingElement(), owner)) {
                        return nested;
                    }
                }
                current = current.getParentPath();
            }
            return null;
        }

        @Override
        public Void visitCatch(CatchTree tree, Void unused) {
            if (enabledRules.contains("java:S7467") && languageVersion >= 21) {
                TreePath variablePath = new TreePath(getCurrentPath(), tree.getParameter());
                Element variable = trees.getElement(variablePath);
                if (variable != null && !"_".equals(tree.getParameter().getName().toString())) {
                    int[] references = new int[1];
                    new TreePathScanner<Void, Void>() {
                        @Override
                        public Void visitIdentifier(IdentifierTree identifier, Void unusedValue) {
                            if (Objects.equals(trees.getElement(getCurrentPath()), variable)) {
                                references[0]++;
                            }
                            return super.visitIdentifier(identifier, unusedValue);
                        }
                    }.scan(new TreePath(getCurrentPath(), tree.getBlock()), null);
                    if (references[0] == 0) {
                        report(tree.getParameter(), "java:S7467",
                                "Replace this unused exception parameter with an unnamed pattern.");
                    }
                }
            }
            return super.visitCatch(tree, unused);
        }

        List<Finding> finish() {
            if (enabledRules.contains("java:S9438")) {
                checkFields();
            }
            if (enabledRules.contains("java:S3398")) {
                checkRecordMethods();
            }
            return List.copyOf(findings);
        }

        private void checkFields() {
            for (Map.Entry<VariableElement, VariableTree> entry : fields.entrySet()) {
                VariableElement symbol = entry.getKey();
                VariableTree declaration = entry.getValue();
                List<ExecutableElement> writes = mutations.getOrDefault(symbol, List.of());
                if (declaration.getInitializer() != null) {
                    if (writes.isEmpty()) {
                        report(declaration, "java:S9438",
                                "Make this private field \"final\", it is never reassigned.");
                    }
                } else if (initializedOnlyInConstructors(symbol, writes)) {
                    report(declaration, "java:S9438",
                            "Make this private field \"final\", it is never reassigned.");
                }
            }
        }

        private boolean initializedOnlyInConstructors(VariableElement field,
                List<ExecutableElement> writes) {
            if (!(field.getEnclosingElement() instanceof TypeElement owner)) {
                return false;
            }
            Set<ExecutableElement> expected = constructors.getOrDefault(owner, Set.of());
            if (expected.isEmpty() || writes.isEmpty()) {
                return false;
            }
            Map<ExecutableElement, Integer> directWrites = new HashMap<>();
            for (ExecutableElement constructor : writes) {
                if (constructor == null || !expected.contains(constructor)) {
                    return false;
                }
                directWrites.merge(constructor, 1, Integer::sum);
            }
            for (ExecutableElement constructor : expected) {
                if (constructorAssignmentCount(constructor, directWrites, new HashSet<>()) != 1) {
                    return false;
                }
            }
            return true;
        }

        private int constructorAssignmentCount(ExecutableElement constructor,
                Map<ExecutableElement, Integer> directWrites, Set<ExecutableElement> visited) {
            if (!visited.add(constructor)) {
                return -1;
            }
            int assignments = directWrites.getOrDefault(constructor, 0);
            ExecutableElement delegated = constructorDelegations.get(constructor);
            if (delegated != null) {
                int nestedAssignments = constructorAssignmentCount(delegated, directWrites, visited);
                if (nestedAssignments < 0) {
                    return -1;
                }
                assignments += nestedAssignments;
            }
            return assignments;
        }

        private void checkRecordMethods() {
            for (Map.Entry<ExecutableElement, MethodTree> entry : methods.entrySet()) {
                MethodUsage usage = usages.get(entry.getKey());
                if (usage != null && !usage.outsideRecord && usage.records.size() == 1) {
                    TypeElement record = usage.records.iterator().next();
                    report(entry.getValue(), "java:S3398",
                            "Move this method into \"" + record.getSimpleName() + "\".");
                }
            }
        }

        private void report(Tree tree, String rule, String message) {
            long offset = trees.getSourcePositions().getStartPosition(unit, tree);
            if (offset < 0) {
                return;
            }
            Path path = Path.of(unit.getSourceFile().toUri());
            String file = baseDirectory.relativize(path).toString().replace('\\', '/');
            long line = unit.getLineMap().getLineNumber(offset);
            findings.add(new Finding(file, line, rule, message));
        }
    }

    private static final class MethodUsage {
        private final Set<TypeElement> records = new HashSet<>();
        private boolean outsideRecord;
    }
}
