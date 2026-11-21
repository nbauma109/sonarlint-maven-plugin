package io.github.nbauma109.staticanalysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.commons.api.TextRange;

/**
 * Suppresses Eclipse diagnostics only for equivalent Sonar findings whose
 * source locations overlap an actual cleanup edit.
 */
final class EclipseDuplicateFilter {

    private static final Map<String, String> EQUIVALENTS = Map.of(
            "cleanup.remove_unused_imports", "java:S1128",
            "cleanup.remove_unnecessary_casts", "java:S1905",
            "cleanup.remove_unused_local_variables", "java:S1481");

    private EclipseDuplicateFilter() {
    }

    static boolean isDuplicate(EclipseCleanupRunner.Diagnostic finding, List<Issue> sonarIssues,
            Path project) throws IOException {
        String ruleKey = EQUIVALENTS.get(finding.cleanup());
        if (ruleKey == null || finding.endOffset() <= finding.startOffset()) {
            return false;
        }
        Path sourceFile = project.resolve(finding.file());
        String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
        if (finding.endOffset() > source.length()) {
            return false;
        }
        if ("java:S1128".equals(ruleKey) && !isImportEdit(source, finding)) {
            return false;
        }
        for (Issue issue : sonarIssues) {
            if (matchesIssue(issue, finding, ruleKey, source)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isImportEdit(String source, EclipseCleanupRunner.Diagnostic finding) {
        List<Integer> starts = lineStarts(source);
        if (finding.line() > starts.size()) {
            return false;
        }
        int start = starts.get(finding.line() - 1);
        int end = finding.line() < starts.size() ? starts.get(finding.line()) : source.length();
        String line = source.substring(start, end).strip();
        return line.startsWith("import ") && !line.startsWith("import module ");
    }

    private static boolean matchesIssue(Issue issue, EclipseCleanupRunner.Diagnostic finding,
            String expectedRule, String source) {
        if (!expectedRule.equals(issue.getRuleKey())) {
            return false;
        }
        String file = Optional.ofNullable(issue.getInputFile())
                .map(ClientInputFile::relativePath).orElse("");
        TextRange range = issue.getTextRange();
        if (!finding.file().equals(file) || range == null) {
            return false;
        }
        List<Integer> starts = lineStarts(source);
        int start = absoluteOffset(starts, range.getStartLine(), range.getStartLineOffset(), source.length());
        int end = absoluteOffset(starts, range.getEndLine(), range.getEndLineOffset(), source.length());
        return start >= 0 && end > start
                && finding.startOffset() < end && finding.endOffset() > start;
    }

    private static int absoluteOffset(List<Integer> starts, int line, int column, int length) {
        if (line < 1 || line > starts.size() || column < 0) {
            return -1;
        }
        int offset = starts.get(line - 1) + column;
        if (offset > length) {
            return -1;
        }
        if (line < starts.size() && offset > starts.get(line)) {
            return -1;
        }
        return offset;
    }

    private static List<Integer> lineStarts(String source) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '\r') {
                if (index + 1 < source.length() && source.charAt(index + 1) == '\n') {
                    index++;
                }
                starts.add(index + 1);
            } else if (character == '\n') {
                starts.add(index + 1);
            }
        }
        return starts;
    }
}
