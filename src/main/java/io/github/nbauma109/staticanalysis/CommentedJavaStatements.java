package io.github.nbauma109.staticanalysis;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.lang.model.element.Element;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EmptyStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;

final class CommentedJavaStatements {

    private static final String RULE = "java:S125";
    private static final String MESSAGE = "This block of commented-out lines of code should be removed.";
    private static final Pattern LABEL = Pattern.compile("^[\\p{L}][\\p{L}\\p{N} _-]{0,60}:\\s+");
    private static final int MAX_FRAGMENT_LENGTH = 1000;

    private final String level;
    private final Map<String, String> compilerOptions = new HashMap<>();
    private final Map<String, Boolean> recognized = new HashMap<>();

    CommentedJavaStatements(int release) {
        level = release == 8 ? JavaCore.VERSION_1_8 : Integer.toString(release);
        JavaCore.setComplianceOptions(level, compilerOptions);
    }

    List<SupplementalAnalyzer.Finding> analyze(Path baseDirectory, CompilationUnitTree unit,
            Trees trees) throws IOException {
        String source = unit.getSourceFile().getCharContent(true).toString();
        IScanner scanner = ToolFactory.createScanner(true, false, false, level, level);
        scanner.setSource(source.toCharArray());
        String file = baseDirectory.relativize(Path.of(unit.getSourceFile().toUri()))
                .toString().replace('\\', '/');
        List<SourceRange> suppressions = suppressedRanges(unit, trees);
        List<SupplementalAnalyzer.Finding> findings = new ArrayList<>();
        CommentBlock block = new CommentBlock();
        boolean seenCode = false;
        try {
            int token;
            while ((token = scanner.getNextToken()) != ITerminalSymbols.TokenNameEOF) {
                int offset = scanner.getCurrentTokenStartPosition();
                if (isStandaloneLineComment(token, source, offset) && seenCode
                        && !isSuppressed(offset, suppressions)) {
                    long line = unit.getLineMap().getLineNumber(offset);
                    if (block.end + 1 != line) {
                        block.flush(file, findings);
                    }
                    String comment = new String(scanner.getCurrentTokenSource()).strip();
                    block.add(line, recognizes(comment));
                } else {
                    block.flush(file, findings);
                    seenCode |= !isComment(token);
                }
            }
            block.flush(file, findings);
        } catch (InvalidInputException exception) {
            throw new IOException("Could not tokenize Java comments in " + file, exception);
        }
        return List.copyOf(findings);
    }

    private boolean recognizes(String comment) {
        if (comment.contains("NOSONAR") || comment.length() > MAX_FRAGMENT_LENGTH) {
            return false;
        }
        String fragment = LABEL.matcher(comment.substring(2).strip()).replaceFirst("");
        if (!looksLikeStatement(fragment)) {
            return false;
        }
        return recognized.computeIfAbsent(fragment, this::isJavaStatement);
    }

    private static boolean looksLikeStatement(String fragment) {
        return fragment.endsWith(";") || fragment.indexOf('(') >= 0
                || fragment.indexOf('=') >= 0 || fragment.contains("++") || fragment.contains("--");
    }

    private boolean isJavaStatement(String fragment) {
        boolean terminated = fragment.endsWith(";");
        String statement = terminated ? fragment : fragment + ";";
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setCompilerOptions(compilerOptions);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(("class Fragment { void check() {\n" + statement + "\n} }").toCharArray());
        CompilationUnit unit = (CompilationUnit) parser.createAST(null);
        for (IProblem problem : unit.getProblems()) {
            if (problem.isError()) {
                return false;
            }
        }
        if (unit.types().size() != 1 || !(unit.types().get(0) instanceof TypeDeclaration type)
                || type.getMethods().length != 1) {
            return false;
        }
        MethodDeclaration method = type.getMethods()[0];
        if (method.getBody() == null || method.getBody().statements().size() != 1) {
            return false;
        }
        Object parsed = method.getBody().statements().get(0);
        if (!(parsed instanceof Statement javaStatement)) {
            return false;
        }
        if (javaStatement instanceof ExpressionStatement expressionStatement) {
            return isCodeExpression(expressionStatement.getExpression(), terminated);
        }
        return terminated && !(javaStatement instanceof EmptyStatement);
    }

    private static boolean isCodeExpression(Expression expression, boolean terminated) {
        if (terminated) {
            return true;
        }
        if (expression instanceof MethodInvocation invocation) {
            return invocation.getExpression() != null;
        }
        return expression instanceof Assignment || expression instanceof PostfixExpression
                || expression instanceof PrefixExpression;
    }

    private static boolean isStandaloneLineComment(int token, String source, int offset) {
        if (token != ITerminalSymbols.TokenNameCOMMENT_LINE || source.startsWith("///", offset)) {
            return false;
        }
        for (int index = offset - 1; index >= 0; index--) {
            char character = source.charAt(index);
            if (character == '\n' || character == '\r') {
                break;
            }
            if (!Character.isWhitespace(character)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isComment(int token) {
        return token == ITerminalSymbols.TokenNameCOMMENT_LINE
                || token == ITerminalSymbols.TokenNameCOMMENT_BLOCK
                || token == ITerminalSymbols.TokenNameCOMMENT_JAVADOC;
    }

    private static boolean isSuppressed(int offset, List<SourceRange> ranges) {
        for (SourceRange range : ranges) {
            if (offset >= range.start() && offset < range.end()) {
                return true;
            }
        }
        return false;
    }

    private static List<SourceRange> suppressedRanges(CompilationUnitTree unit, Trees trees) {
        List<SourceRange> ranges = new ArrayList<>();
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitClass(ClassTree tree, Void unused) {
                record(tree);
                return super.visitClass(tree, unused);
            }

            @Override
            public Void visitMethod(MethodTree tree, Void unused) {
                record(tree);
                return super.visitMethod(tree, unused);
            }

            @Override
            public Void visitVariable(VariableTree tree, Void unused) {
                record(tree);
                return super.visitVariable(tree, unused);
            }

            private void record(Tree tree) {
                Element element = trees.getElement(getCurrentPath());
                if (element == null) {
                    return;
                }
                SuppressWarnings suppression = element.getAnnotation(SuppressWarnings.class);
                if (suppression != null && suppressesRule(suppression.value())) {
                    ranges.add(new SourceRange(trees.getSourcePositions().getStartPosition(unit, tree),
                            trees.getSourcePositions().getEndPosition(unit, tree)));
                }
            }
        }.scan(unit, null);
        return ranges;
    }

    private static boolean suppressesRule(String[] values) {
        for (String value : values) {
            if ("all".equals(value) || RULE.equals(value) || "squid:S125".equals(value)) {
                return true;
            }
        }
        return false;
    }

    private record SourceRange(long start, long end) {
    }

    private static final class CommentBlock {
        private long start = -1;
        private long end = -1;
        private long finding = -1;

        void add(long line, boolean code) {
            if (start < 0) {
                start = line;
            }
            end = line;
            if (finding < 0 && code) {
                finding = line;
            }
        }

        void flush(String file, List<SupplementalAnalyzer.Finding> findings) {
            if (finding >= 0) {
                findings.add(new SupplementalAnalyzer.Finding(file, finding, RULE, MESSAGE, start, end));
            }
            start = -1;
            end = -1;
            finding = -1;
        }
    }
}
