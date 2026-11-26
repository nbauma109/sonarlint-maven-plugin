package io.github.nbauma109.staticanalysis;

import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
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

    record Finding(String file, long line, String rule, String message,
            long blockStart, long blockEnd) {

        Finding(String file, long line, String rule, String message) {
            this(file, line, rule, message, line, line);
        }
    }

    static List<Finding> analyze(Path baseDirectory, List<Path> sources, List<String> classpath,
            String sourceVersion, Set<String> enabledRules) throws IOException {
        int release = RuleJavaVersion.parse(sourceVersion);
        Set<String> eligibleRules = RuleJavaVersion.eligibleRules(enabledRules, release);
        if (sources.isEmpty() || eligibleRules.isEmpty()) {
            return List.of();
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("A Java Development Kit is required for supplemental source checks.");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<String> options = compilerOptions(classpath, Integer.toString(release));
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
                Scanner scanner = new Scanner(trees, unit, baseDirectory, eligibleRules);
                scanner.scan(unit, null);
                findings.addAll(scanner.finish());
                if (eligibleRules.contains("java:S125")) {
                    findings.addAll(CommentedPrintCalls.analyze(baseDirectory, unit, trees, release));
                }
            }
            return List.copyOf(findings);
        } catch (RuntimeException exception) {
            throw new IOException("Supplemental Java analysis failed: " + exception.getMessage(), exception);
        }
    }

    static List<Finding> analyzeByRelease(Path baseDirectory, Map<Integer, List<Path>> sources,
            List<String> classpath, Set<String> enabledRules) throws IOException {
        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<Integer, List<Path>> entry : sources.entrySet()) {
            findings.addAll(analyze(baseDirectory, entry.getValue(), classpath,
                    Integer.toString(entry.getKey()), enabledRules));
        }
        return List.copyOf(findings);
    }

    static List<Finding> withoutNativeDuplicates(List<Finding> supplemental, List<String> nativeLocations) {
        Map<String, Integer> remaining = new HashMap<>();
        for (String location : nativeLocations) {
            remaining.merge(location, 1, Integer::sum);
        }
        List<Finding> distinct = new ArrayList<>();
        for (Finding finding : supplemental) {
            String location = finding.file() + ":" + finding.line() + ":" + finding.rule();
            int count = remaining.getOrDefault(location, 0);
            if (count == 0) {
                if (!coveredCommentBlock(finding, nativeLocations)) {
                    distinct.add(finding);
                }
            } else {
                remaining.put(location, count - 1);
            }
        }
        return List.copyOf(distinct);
    }

    private static boolean coveredCommentBlock(Finding finding, List<String> nativeLocations) {
        if (!"java:S125".equals(finding.rule())) {
            return false;
        }
        for (long line = finding.blockStart(); line <= finding.blockEnd(); line++) {
            if (nativeLocations.contains(finding.file() + ":" + line + ":" + finding.rule())) {
                return true;
            }
        }
        return false;
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
        private final List<Finding> findings = new ArrayList<>();
        private final Map<VariableElement, VariableTree> fields = new HashMap<>();
        private final Map<VariableElement, List<ExecutableElement>> mutations = new HashMap<>();
        private final Map<TypeElement, Set<ExecutableElement>> constructors = new HashMap<>();
        private final Map<ExecutableElement, ExecutableElement> constructorDelegations = new HashMap<>();
        private final Map<ExecutableElement, MethodTree> methods = new HashMap<>();
        private final Map<ExecutableElement, MethodUsage> usages = new HashMap<>();
        private final Map<ExecutableElement, Set<VariableElement>> nullTolerantParameters =
                new HashMap<>();
        private final List<CallSite> methodCalls = new ArrayList<>();
        private final List<DereferenceSite> dereferences = new ArrayList<>();
        private final List<ValidationSite> validatedParameters = new ArrayList<>();

        Scanner(Trees trees, CompilationUnitTree unit, Path baseDirectory,
                Set<String> enabledRules) {
            this.trees = trees;
            this.unit = unit;
            this.baseDirectory = baseDirectory;
            this.enabledRules = enabledRules;
        }

        @Override
        public Void visitClass(ClassTree tree, Void unused) {
            if (enabledRules.contains("java:S9398") && tree.getKind() == Tree.Kind.CLASS) {
                TreePath parent = getCurrentPath().getParentPath();
                if (parent != null && parent.getLeaf() instanceof ClassTree owner
                        && owner.getKind() == Tree.Kind.INTERFACE
                        && !tree.getModifiers().getFlags().contains(Modifier.ABSTRACT)
                        && !tree.getSimpleName().toString().endsWith("Builder")
                        && !referencedInInterfaceSignature(owner, parent,
                                trees.getElement(getCurrentPath()))) {
                    report(tree, "java:S9398", "Move this class out of this interface.");
                }
            }
            return super.visitClass(tree, unused);
        }

        private boolean referencedInInterfaceSignature(ClassTree owner, TreePath ownerPath,
                Element nestedType) {
            for (Tree member : owner.getMembers()) {
                if (!(member instanceof MethodTree method)) {
                    continue;
                }
                TreePath methodPath = new TreePath(ownerPath, method);
                if (referencesType(methodPath, method.getReturnType(), nestedType)) {
                    return true;
                }
                for (VariableTree parameter : method.getParameters()) {
                    if (referencesType(new TreePath(methodPath, parameter),
                            parameter.getType(), nestedType)) {
                        return true;
                    }
                }
                for (ExpressionTree exceptionType : method.getThrows()) {
                    if (referencesType(methodPath, exceptionType, nestedType)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean referencesType(TreePath parent, Tree type, Element target) {
            if (type == null || target == null) {
                return false;
            }
            TypeReferenceVisitor visitor = new TypeReferenceVisitor(trees, target);
            visitor.scan(new TreePath(parent, type), null);
            return visitor.found;
        }

        private static final class TypeReferenceVisitor extends TreePathScanner<Void, Void> {
            private final Trees trees;
            private final Element target;
            private boolean found;

            private TypeReferenceVisitor(Trees trees, Element target) {
                this.trees = trees;
                this.target = target;
            }

            @Override
            public Void visitIdentifier(IdentifierTree identifier, Void unused) {
                if (target.equals(trees.getElement(getCurrentPath()))) {
                    found = true;
                }
                return super.visitIdentifier(identifier, unused);
            }

            @Override
            public Void visitMemberSelect(MemberSelectTree select, Void unused) {
                if (target.equals(trees.getElement(getCurrentPath()))) {
                    found = true;
                }
                return super.visitMemberSelect(select, unused);
            }
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
            if (enabledRules.contains("java:S9412")) {
                checkCollectionsSort(tree);
            }
            if (enabledRules.contains("java:S2259")) {
                inspectNullSafety(tree);
            }
            return super.visitMethodInvocation(tree, unused);
        }

        private void inspectNullSafety(MethodInvocationTree tree) {
            ExecutableElement caller = enclosingExecutable();
            Element target = trees.getElement(getCurrentPath());
            if (caller == null || !(target instanceof ExecutableElement callee)
                    || !(callee.getEnclosingElement() instanceof TypeElement owner)) {
                return;
            }
            String methodName = callee.getSimpleName().toString();
            String ownerName = owner.getQualifiedName().toString();
            if (!tree.getArguments().isEmpty()
                    && tree.getArguments().get(0) instanceof IdentifierTree argument) {
                Element variable = trees.getElement(new TreePath(getCurrentPath(), argument));
                if (variable instanceof VariableElement parameter
                        && variable.getKind() == ElementKind.PARAMETER) {
                    if (tree.getArguments().size() == 1
                            && isNullTolerantMethod(ownerName, methodName)) {
                        nullTolerantParameters.computeIfAbsent(caller,
                                ignored -> new HashSet<>()).add(parameter);
                    }
                    if ("java.util.Objects".equals(ownerName)
                            && "requireNonNull".equals(methodName)) {
                        validatedParameters.add(new ValidationSite(caller, parameter,
                                sourceOffset(tree)));
                    }
                }
            }
            List<VariableElement> actualArguments = new ArrayList<>();
            for (ExpressionTree argument : tree.getArguments()) {
                VariableElement parameter = null;
                if (argument instanceof IdentifierTree identifier) {
                    Element actual = trees.getElement(new TreePath(getCurrentPath(), argument));
                    if (actual instanceof VariableElement variable
                            && actual.getKind() == ElementKind.PARAMETER) {
                        parameter = variable;
                    }
                }
                actualArguments.add(parameter);
            }
            methodCalls.add(new CallSite(caller, callee, actualArguments, sourceOffset(tree)));
            if (tree.getMethodSelect() instanceof MemberSelectTree select
                    && select.getExpression() instanceof IdentifierTree) {
                TreePath selected = new TreePath(getCurrentPath(), select);
                Element receiver = trees.getElement(new TreePath(selected, select.getExpression()));
                if (receiver instanceof VariableElement parameter
                        && receiver.getKind() == ElementKind.PARAMETER) {
                    dereferences.add(new DereferenceSite(caller, parameter,
                            select.getExpression(), sourceOffset(tree)));
                }
            }
        }

        private static boolean isNullTolerantMethod(String owner, String method) {
            if ("java.util.Objects".equals(owner)) {
                return "isNull".equals(method) || "nonNull".equals(method);
            }
            return "org.apache.commons.lang3.StringUtils".equals(owner)
                    && ("isEmpty".equals(method) || "isNotEmpty".equals(method)
                    || "isBlank".equals(method) || "isNotBlank".equals(method));
        }

        private ExecutableElement enclosingExecutable() {
            TreePath path = getCurrentPath();
            while (path != null) {
                if (path.getLeaf() instanceof MethodTree) {
                    Element element = trees.getElement(path);
                    if (element instanceof ExecutableElement executable) {
                        return executable;
                    }
                }
                path = path.getParentPath();
            }
            return null;
        }

        private long sourceOffset(Tree tree) {
            return trees.getSourcePositions().getStartPosition(unit, tree);
        }

        private void checkPotentialNullDereferences() {
            Map<ExecutableElement, Map<VariableElement, Long>> nullableAt = new HashMap<>();
            for (CallSite call : methodCalls) {
                Set<VariableElement> nullable = nullTolerantParameters.get(call.target());
                if (nullable == null) {
                    continue;
                }
                List<? extends VariableElement> formals = call.target().getParameters();
                int size = Math.min(formals.size(), call.arguments().size());
                for (int index = 0; index < size; index++) {
                    VariableElement argument = call.arguments().get(index);
                    if (argument != null && nullable.contains(formals.get(index))
                            && acceptsNullWithoutDereference(call.target(), formals.get(index))) {
                        nullableAt.computeIfAbsent(call.caller(), ignored -> new HashMap<>())
                                .merge(argument, call.offset(), Math::min);
                    }
                }
            }
            Set<Long> reportedOffsets = new HashSet<>();
            for (DereferenceSite dereference : dereferences) {
                long firstNullable = nullableAt.getOrDefault(
                        dereference.caller(), Map.of()).getOrDefault(
                                dereference.parameter(), Long.MAX_VALUE);
                if (firstNullable > dereference.offset()
                        || isValidated(dereference)
                        || !reportedOffsets.add(dereference.offset())) {
                    continue;
                }
                report(dereference.receiver(), "java:S2259",
                        "Fix this access that can throw a NullPointerException when executed.");
            }
        }

        private boolean acceptsNullWithoutDereference(ExecutableElement method,
                VariableElement parameter) {
            for (DereferenceSite dereference : dereferences) {
                if (dereference.caller().equals(method)
                        && dereference.parameter().equals(parameter)) {
                    return false;
                }
            }
            for (ValidationSite validation : validatedParameters) {
                if (validation.caller().equals(method)
                        && validation.parameter().equals(parameter)) {
                    return false;
                }
            }
            return true;
        }

        private boolean isValidated(DereferenceSite dereference) {
            for (ValidationSite validation : validatedParameters) {
                if (validation.caller().equals(dereference.caller())
                        && validation.parameter().equals(dereference.parameter())
                        && validation.offset() < dereference.offset()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Void visitArrayAccess(ArrayAccessTree tree, Void unused) {
            if (enabledRules.contains("java:S6466") && ArrayBounds.isOutOfBounds(trees, getCurrentPath(), tree)) {
                report(tree, "java:S6466",
                        "Fix this array access that will throw an ArrayIndexOutOfBoundsException when executed.");
            }
            return super.visitArrayAccess(tree, unused);
        }

        @Override
        public Void visitBinary(BinaryTree tree, Void unused) {
            if (enabledRules.contains("java:S7158")) {
                checkBlankString(tree);
            }
            return super.visitBinary(tree, unused);
        }

        private void checkBlankString(BinaryTree tree) {
            if (tree.getKind() != Tree.Kind.GREATER_THAN
                    || !(tree.getRightOperand() instanceof LiteralTree literal)
                    || !Integer.valueOf(0).equals(literal.getValue())) {
                return;
            }
            TreePath lengthPath = new TreePath(getCurrentPath(), tree.getLeftOperand());
            if (!isStringMethod(lengthPath, "length")
                    || !(lengthPath.getLeaf() instanceof MethodInvocationTree length)
                    || !(length.getMethodSelect() instanceof MemberSelectTree selection)) {
                return;
            }
            TreePath trimPath = new TreePath(new TreePath(lengthPath, selection),
                    selection.getExpression());
            if (isStringMethod(trimPath, "trim")) {
                report(tree, "java:S7158",
                        "Use \"isBlank()\" to check whether a \"String\" is blank or not. "
                        + "Use this if Unicode whitespace should be treated as blank and control characters as non-blank.");
            }
        }

        private boolean isStringMethod(TreePath path, String name) {
            return path.getLeaf() instanceof MethodInvocationTree invocation
                    && invocation.getArguments().isEmpty()
                    && trees.getElement(path) instanceof ExecutableElement method
                    && method.getEnclosingElement() instanceof TypeElement owner
                    && "java.lang.String".contentEquals(owner.getQualifiedName())
                    && name.contentEquals(method.getSimpleName());
        }

        private void checkCollectionsSort(MethodInvocationTree tree) {
            Element element = trees.getElement(getCurrentPath());
            if (element instanceof ExecutableElement method
                    && method.getEnclosingElement() instanceof TypeElement owner
                    && "java.util.Collections".contentEquals(owner.getQualifiedName())
                    && "sort".contentEquals(method.getSimpleName())) {
                report(tree, "java:S9412", "Replace this \"Collections.sort()\" with \"List.sort()\".");
            }
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
            if (!"replaceAll".equals(method) && !"replaceFirst".equals(method)) {
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
            String normalized = pattern.replace("\\t", "\t")
                    .replace("\\h", " ").replace("\\s", " ")
                    .replace("\\r", "\r").replace("\\n", "\n");
            int quantifier = leadingWhitespaceEnd(normalized);
            if (quantifier == 0 || quantifier >= normalized.length()
                    || "*+".indexOf(normalized.charAt(quantifier)) < 0) {
                return false;
            }
            String remainder = normalized.substring(quantifier + 1);
            return remainder.equals("\r?\n") || remainder.equals("\n")
                    || remainder.equals("\r\n") || isRequiredEscapedDelimiter(remainder);
        }

        private static boolean isRequiredEscapedDelimiter(String remainder) {
            if (remainder.length() < 2 || remainder.charAt(0) != '\\'
                    || "{}[]().+*?|^$".indexOf(remainder.charAt(1)) < 0) {
                return false;
            }
            String suffix = remainder.substring(2);
            if (suffix.isEmpty()) {
                return true;
            }
            int end = leadingWhitespaceEnd(suffix);
            return end > 0 && end + 1 == suffix.length()
                    && "*+".indexOf(suffix.charAt(end)) >= 0;
        }

        private static int leadingWhitespaceEnd(String pattern) {
            if (pattern.isEmpty()) {
                return 0;
            }
            if (isHorizontalWhitespace(pattern.charAt(0))) {
                return 1;
            }
            if (pattern.charAt(0) != '[') {
                return 0;
            }
            int end = pattern.indexOf(']');
            if (end <= 1) {
                return 0;
            }
            for (int index = 1; index < end; index++) {
                if (!isHorizontalWhitespace(pattern.charAt(index))) {
                    return 0;
                }
            }
            return end + 1;
        }

        private static boolean isHorizontalWhitespace(char value) {
            return value == ' ' || value == '\t';
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
            if (enabledRules.contains("java:S7467")) {
                TreePath variablePath = new TreePath(getCurrentPath(), tree.getParameter());
                Element variable = trees.getElement(variablePath);
                String name = tree.getParameter().getName().toString();
                if (variable != null && !name.isEmpty() && !"_".equals(name)) {
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
            if (enabledRules.contains("java:S2259")) {
                checkPotentialNullDereferences();
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

    private record CallSite(ExecutableElement caller, ExecutableElement target,
            List<VariableElement> arguments, long offset) {
    }

    private record DereferenceSite(ExecutableElement caller, VariableElement parameter,
            Tree receiver, long offset) {
    }

    private record ValidationSite(ExecutableElement caller, VariableElement parameter, long offset) {
    }

    private static final class MethodUsage {
        private final Set<TypeElement> records = new HashSet<>();
        private boolean outsideRecord;
    }
}
