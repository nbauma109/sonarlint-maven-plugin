package io.github.nbauma109.staticanalysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.analysis.api.Issue;
import org.sonarsource.sonarlint.core.commons.api.TextRange;

class EclipseDuplicateFilterTest {

    private static final String RELATIVE_FILE = "src/main/java/example/OverlapSample.java";
    private static final String SOURCE = """
            package example;

            import java.util.List;

            final class OverlapSample {
                String value() {
                    int unused = 1;
                    return (String) "cast";
                }
            }
            """;

    @TempDir
    Path project;

    @Test
    void suppressesExactUnusedImports() throws IOException {
        writeSource(SOURCE);
        EclipseCleanupRunner.Diagnostic edit = finding("cleanup.remove_unused_imports",
                "import java.util.List;", SOURCE);
        Issue sonar = sonarIssue("java:S1128", "java.util.List", SOURCE);
        assertTrue(EclipseDuplicateFilter.isDuplicate(edit, List.of(sonar), project));
        assertFalse(EclipseDuplicateFilter.isDuplicate(edit, List.of(), project));
        assertFalse(EclipseDuplicateFilter.isDuplicate(
                finding("UnnecessaryCodeCleanUp", "import java.util.List;", SOURCE),
                List.of(sonar), project));
    }

    @Test
    void suppressesAnImportEditThatIncludesThePrecedingBlankLine() throws IOException {
        writeSource(SOURCE);
        Issue issue = sonarIssue("java:S1128", "java.util.List", SOURCE);
        int importStart = SOURCE.indexOf("import java.util.List;");
        int editStart = importStart - 2;
        int editEnd = importStart + "import java.util.List;".length();
        EclipseCleanupRunner.Diagnostic finding = new EclipseCleanupRunner.Diagnostic(
                RELATIVE_FILE, 1, 17, "cleanup.remove_unused_imports",
                "Remove unused imports", editStart, editEnd);
        assertTrue(EclipseDuplicateFilter.isDuplicate(finding, List.of(issue), project));
    }

    @Test
    void suppressesExactRedundantCastsButNotUnrelatedEditsOnTheSameLine() throws IOException {
        writeSource(SOURCE);
        Issue sonar = sonarIssue("java:S1905", "(String)", SOURCE);
        assertTrue(EclipseDuplicateFilter.isDuplicate(
                finding("cleanup.remove_unnecessary_casts", "(String)", SOURCE),
                List.of(sonar), project));
        assertFalse(EclipseDuplicateFilter.isDuplicate(
                finding("cleanup.remove_unnecessary_casts", "return", SOURCE),
                List.of(sonar), project));
        assertFalse(EclipseDuplicateFilter.isDuplicate(
                finding("cleanup.remove_unused_local_variables", "(String)", SOURCE),
                List.of(sonar), project));
    }

    @Test
    void suppressesUnusedLocalVariablesWhenSourceRangesOverlap() throws IOException {
        writeSource(SOURCE);
        Issue sonar = sonarIssue("java:S1481", "unused", SOURCE);
        assertTrue(EclipseDuplicateFilter.isDuplicate(
                finding("cleanup.remove_unused_local_variables", "int unused = 1;", SOURCE),
                List.of(sonar), project));
        assertFalse(EclipseDuplicateFilter.isDuplicate(
                finding("cleanup.remove_unused_local_variables", "(String)", SOURCE),
                List.of(sonar), project));
    }

    @Test
    void handlesWindowsLineEndingsAndRetainsUnmatchedImports() throws IOException {
        String windows = SOURCE.replace("\n", "\r\n");
        writeSource(windows);
        Issue sonar = sonarIssue("java:S1905", "(String)", windows);
        assertTrue(EclipseDuplicateFilter.isDuplicate(
                finding("cleanup.remove_unnecessary_casts", "(String)", windows),
                List.of(sonar), project));
        assertFalse(EclipseDuplicateFilter.isDuplicate(
                finding("cleanup.remove_unused_imports", "(String)", windows),
                List.of(sonar), project));
    }


    @Test
    void suppressesOnlyOverrideInsertionsAtTheTargetMethod() throws IOException {
        String code = """
                package example;
                class Parent {
                    String label() { return "parent"; }
                }
                class Child extends Parent {
                    String label() { return "child"; }
                    String second() { return "other"; }
                }
                """;
        writeSource(code);
        Issue issue = sonarIssue("java:S1161", "label", code, true);
        int method = code.indexOf("    String label()", code.indexOf("class Child"));
        EclipseCleanupRunner.Diagnostic correct = new EclipseCleanupRunner.Diagnostic(
                RELATIVE_FILE, 6, 1, "cleanup.add_missing_override_annotations",
                "Add missing Override annotation", method, method);
        assertTrue(EclipseDuplicateFilter.isDuplicate(correct, List.of(issue), project));
        EclipseCleanupRunner.Diagnostic unrelated = new EclipseCleanupRunner.Diagnostic(
                RELATIVE_FILE, 7, 1, "cleanup.add_missing_override_annotations",
                "Unrelated annotation", code.indexOf("    String second()"),
                code.indexOf("    String second()"));
        assertFalse(EclipseDuplicateFilter.isDuplicate(unrelated, List.of(issue), project));
        assertFalse(EclipseDuplicateFilter.isDuplicate(
                new EclipseCleanupRunner.Diagnostic(RELATIVE_FILE, 6, 1,
                        "SomeCleanupModule", "Unknown cleanup", method, method),
                List.of(issue), project));
    }

    @Test
    void suppressesOnlyRewritesOfTheReportedTryStatement() throws IOException {
        String code = """
                package example;
                final class Sample {
                    void action() {
                        try {
                            System.out.println("one");
                        } finally {
                            System.out.println("done");
                        }
                        try {
                            System.out.println("two");
                        } finally {
                            System.out.println("done");
                        }
                    }
                }
                """;
        writeSource(code);
        Issue issue = sonarIssue("java:S2093", "try", code);
        assertTrue(EclipseDuplicateFilter.isDuplicate(
                finding("cleanup.try_with_resource", "try {", code), List.of(issue), project));
        int secondTry = code.indexOf("try", code.indexOf("try") + 3);
        EclipseCleanupRunner.Diagnostic unrelated = new EclipseCleanupRunner.Diagnostic(
                RELATIVE_FILE, 9, 1, "cleanup.try_with_resource",
                "Rewrite another try", secondTry, secondTry + 3);
        assertFalse(EclipseDuplicateFilter.isDuplicate(unrelated, List.of(issue), project));
    }

    @Test
    void suppressesOnlyTheFourNewExactSourceOverlaps() throws IOException {
        String code = """
                package example;
                final class NewOverlaps {
                    String value() { return new String("literal"); }
                    void action(String... words) {
                        accept(new String[] {"one", "two"});
                        return;
                    }
                    void accept(String... words) { }
                    java.util.List<String> items() {
                        java.util.List<String> result = new java.util.ArrayList<String>();
                        return result;
                    }
                }
                """;
        writeSource(code);
        String[][] cases = {
            {"cleanup.no_string_creation", "java:S2129", "new String(\"literal\")"},
            {"cleanup.remove_redundant_type_arguments", "java:S2293", "<String>();"},
            {"cleanup.remove_unnecessary_array_creation", "java:S3878", "new String[]"},
            {"cleanup.useless_return", "java:S3626", "return;"}
        };
        for (String[] item : cases) {
            EclipseCleanupRunner.Diagnostic edit = finding(item[0], item[2], code);
            Issue sameIssue = sonarIssue(item[1], item[2], code);
            Issue differentIssue = sonarIssue("java:S1128", item[2], code);
            assertTrue(EclipseDuplicateFilter.isDuplicate(edit, List.of(sameIssue), project),
                    item[0]);
            assertFalse(EclipseDuplicateFilter.isDuplicate(edit, List.of(differentIssue), project),
                    item[0]);
            assertFalse(EclipseDuplicateFilter.isDuplicate(edit, List.of(), project),
                    item[0]);
            assertFalse(EclipseDuplicateFilter.isDuplicate(
                    finding("cleanup.add_all", item[2], code),
                    List.of(sameIssue), project), item[0]);
        }
    }

    @Test
    void preservesComplementaryCleanupsEvenWhenSonarReportsTheSameText() throws IOException {
        writeSource(SOURCE);
        Issue sonar = sonarIssue("java:S1128", "java.util.List", SOURCE);
        List<String> complementaryOptions = List.of(
                "cleanup.pull_up_assignment",
                "cleanup.extract_increment",
                "cleanup.remove_unnecessary_suppress_warnings",
                "cleanup.system_property_javaversion",
                "cleanup.system_property_javaspecversion",
                "cleanup.no_string_creation",
                "cleanup.stringbuffer_to_stringbuilder",
                "cleanup.use_string_is_blank",
                "cleanup.remove_redundant_modifiers",
                "cleanup.static_inner_class",
                "cleanup.make_private_fields_final",
                "cleanup.array_with_curly",
                "cleanup.remove_unnecessary_array_creation",
                "cleanup.add_all",
                "cleanup.precompile_regex",
                "cleanup.remove_redundant_type_arguments",
                "cleanup.useless_return",
                "cleanup.stringbuilder_for_local_vars",
                "cleanup.pull_out_if_from_if_else",
                "cleanup.reduce_indentation",
                "cleanup.ternary_operator",
                "cleanup.remove_unnecessary_nls_tags",
                "cleanup.module_imports");
        for (String option : complementaryOptions) {
            assertFalse(EclipseDuplicateFilter.isDuplicate(
                    finding(option, "java.util.List", SOURCE),
                    List.of(sonar), project), option);
        }
    }

    private void writeSource(String source) throws IOException {
        Path file = project.resolve(RELATIVE_FILE);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    private EclipseCleanupRunner.Diagnostic finding(String cleanup, String token, String source) {
        int start = source.indexOf(token);
        int line = 1;
        int column = 1;
        for (int index = 0; index < start; index++) {
            if (source.charAt(index) == '\n') {
                line++;
                column = 1;
            } else if (source.charAt(index) != '\r') {
                column++;
            }
        }
        return new EclipseCleanupRunner.Diagnostic(RELATIVE_FILE, line, column,
                cleanup, "Eclipse cleanup", start, start + token.length());
    }

    private Issue sonarIssue(String rule, String token, String source, boolean overrideMethod) {
        int start = overrideMethod ? source.indexOf("class Child") : 0;
        return sonarIssue(rule, token, source, start);
    }

    private Issue sonarIssue(String rule, String token, String source) {
        return sonarIssue(rule, token, source, 0);
    }

    private Issue sonarIssue(String rule, String token, String source, int searchFrom) {
        int offset = source.indexOf(token, searchFrom);
        int line = 1;
        int startOfLine = 0;
        for (int index = 0; index < offset; index++) {
            if (source.charAt(index) == '\n') {
                line++;
                startOfLine = index + 1;
            }
        }
        TextRange range = new TextRange(line, offset - startOfLine,
                line, offset - startOfLine + token.length());
        JavaSourceFile file = new JavaSourceFile(project, project.resolve(RELATIVE_FILE),
                StandardCharsets.UTF_8);
        return new Issue(rule, "Sonar issue", Map.of(), range, file, List.of(), List.of(),
                Optional.empty());
    }
}
