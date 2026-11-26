package io.github.nbauma109.staticanalysis;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class RuleJavaVersion {

    private static final Map<String, Integer> MINIMUM_RELEASES = Map.of(
            "java:S7467", 22,
            "java:S9412", 8,
            "java:S7158", 11);

    private RuleJavaVersion() {
    }

    static int parse(String version) {
        if (version == null || version.isBlank()) {
            return Runtime.version().feature();
        }
        String normalized = version.trim();
        if (normalized.startsWith("1.")) {
            normalized = normalized.substring(2);
        }
        int release = Integer.parseInt(normalized);
        if (release < 1) {
            throw new IllegalArgumentException("Invalid Java source release: " + version);
        }
        return release;
    }

    static Set<String> eligibleRules(Set<String> selected, int release) {
        Set<String> eligible = new TreeSet<>();
        for (String rule : selected) {
            if (release >= MINIMUM_RELEASES.getOrDefault(rule, 1)) {
                eligible.add(rule);
            }
        }
        return Set.copyOf(eligible);
    }

    static Map<String, Integer> minimumReleases() {
        return MINIMUM_RELEASES;
    }
}
