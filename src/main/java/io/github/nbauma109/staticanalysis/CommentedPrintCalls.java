package io.github.nbauma109.staticanalysis;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;

final class CommentedPrintCalls {

    private static final String RULE = "java:S125";
    private static final String MESSAGE = "This block of commented-out lines of code should be removed.";
    private static final Pattern PRINT_CALL = Pattern.compile(
            "(?:^|[^\\p{javaJavaIdentifierPart}.])System\\.(?:out|err)\\.(?:print|println|printf)\\s*\\(");

    private CommentedPrintCalls() {
    }

    static List<SupplementalAnalyzer.Finding> analyze(Path baseDirectory, CompilationUnitTree unit,
            Trees trees, int release) throws IOException {
        String source = unit.getSourceFile().getCharContent(true).toString();
        String level = release == 8 ? JavaCore.VERSION_1_8 : Integer.toString(release);
        IScanner scanner = ToolFactory.createScanner(true, false, false, level, level);
        scanner.setSource(source.toCharArray());
        List<SupplementalAnalyzer.Finding> findings = new ArrayList<>();
        String file = baseDirectory.relativize(Path.of(unit.getSourceFile().toUri()))
                .toString().replace('\\', '/');
        long firstCode = firstCodeOffset(unit, trees);
        CommentBlock block = new CommentBlock();
        try {
            int token;
            while ((token = scanner.getNextToken()) != ITerminalSymbols.TokenNameEOF) {
                int offset = scanner.getCurrentTokenStartPosition();
                long line = unit.getLineMap().getLineNumber(offset);
                if (isStandaloneComment(token, source, offset, firstCode)) {
                    if (block.end + 1 != line) {
                        block.flush(file, findings);
                    }
                    block.add(line, new String(scanner.getCurrentTokenSource()));
                } else {
                    block.flush(file, findings);
                }
            }
            block.flush(file, findings);
        } catch (InvalidInputException exception) {
            throw new IOException("Could not tokenize Java comments in " + file, exception);
        }
        return List.copyOf(findings);
    }

    private static long firstCodeOffset(CompilationUnitTree unit, Trees trees) {
        if (unit.getPackage() != null) {
            return trees.getSourcePositions().getStartPosition(unit, unit.getPackage());
        }
        if (!unit.getImports().isEmpty()) {
            return trees.getSourcePositions().getStartPosition(unit, unit.getImports().get(0));
        }
        if (!unit.getTypeDecls().isEmpty()) {
            return trees.getSourcePositions().getStartPosition(unit, unit.getTypeDecls().get(0));
        }
        return Long.MAX_VALUE;
    }

    private static boolean isStandaloneComment(int token, String source, int offset, long firstCode) {
        if (token != ITerminalSymbols.TokenNameCOMMENT_LINE || offset < firstCode
                || source.startsWith("///", offset)) {
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

    private static final class CommentBlock {
        private long start = -1;
        private long end = -1;
        private long finding = -1;

        void add(long line, String comment) {
            if (start < 0) {
                start = line;
            }
            end = line;
            if (finding < 0 && !comment.contains("NOSONAR")
                    && PRINT_CALL.matcher(comment).find()) {
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
