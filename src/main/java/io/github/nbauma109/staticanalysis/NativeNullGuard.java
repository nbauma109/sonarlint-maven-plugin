package io.github.nbauma109.staticanalysis;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;

/**
 * Verifies a narrow, source-proven null guard before retaining a native null
 * dereference diagnostic. Unsupported or unresolved cases retain the diagnostic.
 */
final class NativeNullGuard {

    private static final String RULE = "java:S2259";

    private NativeNullGuard() {
    }

    static List<Issue> retainUnproven(AnalysisConfiguration configuration, List<Issue> issues,
            LogOutput logger) {
        Set<Location> candidates = new HashSet<>();
        for (Issue issue : issues) {
            location(issue).ifPresent(candidates::add);
        }
        if (candidates.isEmpty()) {
            return issues;
        }
        Set<Location> proven;
        try {
            proven = prove(configuration, candidates);
        } catch (IOException | RuntimeException exception) {
            logger.log("Null guard verification unavailable: " + exception.getMessage(),
                    LogOutput.Level.DEBUG, null);
            return issues;
        }
        List<Issue> retained = new ArrayList<>();
        for (Issue issue : issues) {
            Optional<Location> location = location(issue);
            if (location.isPresent() && proven.contains(location.get())) {
                logger.log("Verified non-null receiver at " + issue.getInputFile().relativePath()
                        + ":" + issue.getTextRange().getStartLine() + " [" + RULE + "]",
                        LogOutput.Level.INFO, null);
            } else {
                retained.add(issue);
            }
        }
        return List.copyOf(retained);
    }

    private static Optional<Location> location(Issue issue) {
        ClientInputFile file = issue.getInputFile();
        TextRange range = issue.getTextRange();
        if (!RULE.equals(issue.getRuleKey()) || file == null || range == null) {
            return Optional.empty();
        }
        String message = issue.getMessage();
        if (message == null || !message.startsWith("A \"NullPointerException\" could be thrown; \"")
                || !message.endsWith("\" is nullable here.")) {
            return Optional.empty();
        }
        String receiver = message.substring("A \"NullPointerException\" could be thrown; \"".length(),
                message.length() - "\" is nullable here.".length());
        return Optional.of(new Location(Path.of(file.uri()), range, receiver));
    }

    private static Set<Location> prove(AnalysisConfiguration configuration, Set<Location> candidates)
            throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null || configuration.inputFiles().isEmpty()) {
            return Set.of();
        }
        List<Path> paths = new ArrayList<>();
        Set<Charset> encodings = new HashSet<>();
        for (ClientInputFile file : configuration.inputFiles()) {
            paths.add(Path.of(file.uri()));
            encodings.add(file.getCharset());
        }
        if (encodings.size() != 1) {
            return Set.of();
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(
                diagnostics, null, encodings.iterator().next())) {
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
                    compilerOptions(configuration.extraProperties()), null,
                    files.getJavaFileObjectsFromPaths(paths));
            List<CompilationUnitTree> units = new ArrayList<>();
            for (CompilationUnitTree unit : task.parse()) {
                units.add(unit);
            }
            task.analyze();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    return Set.of();
                }
            }
            Set<Location> proven = new HashSet<>();
            Trees trees = Trees.instance(task);
            for (CompilationUnitTree unit : units) {
                Path path = Path.of(unit.getSourceFile().toUri());
                if (containsFile(candidates, path)) {
                    new GuardScanner(trees, unit, path, candidates, proven).scan(unit, null);
                }
            }
            return proven;
        }
    }

    private static boolean containsFile(Set<Location> locations, Path path) {
        for (Location location : locations) {
            if (location.file().equals(path)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> compilerOptions(Map<String, String> properties) {
        List<String> options = new ArrayList<>(List.of("-proc:none", "-implicit:none"));
        Set<String> classpath = new LinkedHashSet<>();
        for (String property : List.of("sonar.java.binaries", "sonar.java.test.binaries",
                "sonar.java.libraries", "sonar.java.test.libraries")) {
            for (String entry : properties.getOrDefault(property, "").split(",")) {
                if (!entry.isBlank()) {
                    classpath.add(entry);
                }
            }
        }
        if (!classpath.isEmpty()) {
            options.add("-classpath");
            options.add(String.join(File.pathSeparator, classpath));
        }
        Optional.ofNullable(properties.get("sonar.java.source")).filter(value -> !value.isBlank())
                .ifPresent(value -> {
                    options.add("--release");
                    options.add(Integer.toString(RuleJavaVersion.parse(value)));
                });
        return options;
    }

    private record Location(Path file, TextRange range, String receiver) {
    }

    private static final class GuardScanner extends TreePathScanner<Void, Void> {

        private final Trees trees;
        private final CompilationUnitTree unit;
        private final Path file;
        private final Set<Location> candidates;
        private final Set<Location> proven;

        GuardScanner(Trees trees, CompilationUnitTree unit, Path file,
                Set<Location> candidates, Set<Location> proven) {
            this.trees = trees;
            this.unit = unit;
            this.file = file;
            this.candidates = candidates;
            this.proven = proven;
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree tree, Void unused) {
            if (tree.getExpression() instanceof IdentifierTree receiver) {
                String name = receiver.getName().toString();
                Location selection = location(tree, name);
                Location identifier = location(receiver, name);
                if ((candidates.contains(selection) || candidates.contains(identifier))
                        && trees.getElement(new TreePath(getCurrentPath(), receiver)) instanceof VariableElement variable
                        && isLocal(variable) && guarded(variable)) {
                    proven.add(selection);
                    proven.add(identifier);
                }
            }
            return super.visitMemberSelect(tree, unused);
        }

        private Location location(Tree tree, String receiver) {
            long start = trees.getSourcePositions().getStartPosition(unit, tree);
            long end = trees.getSourcePositions().getEndPosition(unit, tree);
            if (start < 0 || end < start) {
                return new Location(file, new TextRange(0, 0, 0, 0), receiver);
            }
            return new Location(file, new TextRange(
                    (int) unit.getLineMap().getLineNumber(start),
                    (int) unit.getLineMap().getColumnNumber(start) - 1,
                    (int) unit.getLineMap().getLineNumber(end),
                    (int) unit.getLineMap().getColumnNumber(end) - 1), receiver);
        }

        private static boolean isLocal(VariableElement variable) {
            return variable.getKind() == ElementKind.LOCAL_VARIABLE
                    || variable.getKind() == ElementKind.PARAMETER;
        }

        private boolean guarded(VariableElement variable) {
            TreePath child = getCurrentPath();
            for (TreePath parent = child.getParentPath(); parent != null; parent = parent.getParentPath()) {
                Tree tree = parent.getLeaf();
                if (tree instanceof LambdaExpressionTree || tree instanceof ClassTree || tree instanceof MethodTree) {
                    return false;
                }
                if (tree instanceof IfTree condition && guardsBranch(parent, child, condition, variable)) {
                    return true;
                }
                child = parent;
            }
            return false;
        }

        private boolean guardsBranch(TreePath path, TreePath branch, IfTree condition,
                VariableElement variable) {
            boolean thenBranch = branch.getLeaf() == condition.getThenStatement();
            if (!thenBranch && branch.getLeaf() != condition.getElseStatement()) {
                return false;
            }
            TreePath predicate = unwrap(new TreePath(path, condition.getCondition()));
            boolean requiredResult = thenBranch;
            if (predicate.getLeaf() instanceof UnaryTree negation
                    && negation.getKind() == Tree.Kind.LOGICAL_COMPLEMENT) {
                predicate = unwrap(new TreePath(predicate, negation.getExpression()));
                requiredResult = !requiredResult;
            }
            Optional<Boolean> nullResult = nullResult(predicate, variable);
            return nullResult.isPresent() && nullResult.get() != requiredResult
                    && !new MutationScanner(trees, variable).changed(branch);
        }

        private Optional<Boolean> nullResult(TreePath predicate, VariableElement variable) {
            if (!(predicate.getLeaf() instanceof MethodInvocationTree invocation)
                    || invocation.getArguments().size() != 1
                    || !(invocation.getArguments().get(0) instanceof IdentifierTree argument)
                    || !variable.equals(trees.getElement(new TreePath(predicate, argument)))
                    || !(trees.getElement(predicate) instanceof ExecutableElement method)
                    || !method.getModifiers().contains(Modifier.STATIC)
                    || method.getParameters().size() != 1) {
                return Optional.empty();
            }
            TreePath declaration = trees.getPath(method);
            if (declaration == null || !(declaration.getLeaf() instanceof MethodTree source)
                    || source.getBody() == null || source.getBody().getStatements().size() != 1
                    || !(source.getBody().getStatements().get(0) instanceof ReturnTree returned)
                    || returned.getExpression() == null) {
                return Optional.empty();
            }
            TreePath body = new TreePath(declaration, source.getBody());
            TreePath result = unwrap(new TreePath(new TreePath(body, returned), returned.getExpression()));
            return nullResultOfExpression(result, method.getParameters().get(0));
        }

        private Optional<Boolean> nullResultOfExpression(TreePath expression, VariableElement parameter) {
            if (!(expression.getLeaf() instanceof BinaryTree binary)) {
                return Optional.empty();
            }
            return switch (binary.getKind()) {
                case CONDITIONAL_OR -> nullComparison(
                        unwrap(new TreePath(expression, binary.getLeftOperand())), parameter)
                        .filter(Boolean.TRUE::equals);
                case CONDITIONAL_AND -> nullComparison(
                        unwrap(new TreePath(expression, binary.getLeftOperand())), parameter)
                        .filter(Boolean.FALSE::equals);
                default -> nullComparison(expression, parameter);
            };
        }

        private Optional<Boolean> nullComparison(TreePath expression, VariableElement parameter) {
            if (!(expression.getLeaf() instanceof BinaryTree binary)
                    || (binary.getKind() != Tree.Kind.EQUAL_TO && binary.getKind() != Tree.Kind.NOT_EQUAL_TO)) {
                return Optional.empty();
            }
            TreePath left = unwrap(new TreePath(expression, binary.getLeftOperand()));
            TreePath right = unwrap(new TreePath(expression, binary.getRightOperand()));
            if (isParameterAndNull(left, right, parameter) || isParameterAndNull(right, left, parameter)) {
                return Optional.of(binary.getKind() == Tree.Kind.EQUAL_TO);
            }
            return Optional.empty();
        }

        private boolean isParameterAndNull(TreePath possibleParameter, TreePath possibleNull,
                VariableElement parameter) {
            return possibleParameter.getLeaf() instanceof IdentifierTree
                    && parameter.equals(trees.getElement(possibleParameter))
                    && possibleNull.getLeaf().getKind() == Tree.Kind.NULL_LITERAL;
        }

        private static TreePath unwrap(TreePath path) {
            TreePath result = path;
            while (result.getLeaf() instanceof ParenthesizedTree parenthesized) {
                result = new TreePath(result, parenthesized.getExpression());
            }
            return result;
        }
    }

    private static final class MutationScanner extends TreePathScanner<Void, Void> {

        private final Trees trees;
        private final VariableElement variable;
        private boolean changed;

        MutationScanner(Trees trees, VariableElement variable) {
            this.trees = trees;
            this.variable = variable;
        }

        boolean changed(TreePath branch) {
            scan(branch, null);
            return changed;
        }

        @Override
        public Void visitAssignment(AssignmentTree tree, Void unused) {
            inspect(tree.getVariable());
            return super.visitAssignment(tree, unused);
        }

        @Override
        public Void visitCompoundAssignment(CompoundAssignmentTree tree, Void unused) {
            inspect(tree.getVariable());
            return super.visitCompoundAssignment(tree, unused);
        }

        private void inspect(ExpressionTree target) {
            Element element = trees.getElement(new TreePath(getCurrentPath(), target));
            if (variable.equals(element)) {
                changed = true;
            }
        }
    }
}
