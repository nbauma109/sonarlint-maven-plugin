package io.github.nbauma109.staticanalysis;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.sonarsource.sonarlint.core.analysis.api.ActiveRule;

final class RuleCatalog {

    private static final String RESOURCE_PATH = "org/sonar/l10n/java/rules/java/";
    private static final String SYMBOLIC_RESOURCE_PATH = "org/sonar/l10n/java/rules/javase/";
    private static final String PREFIX = "java:";
    private static final String SONAR_WAY_SNAPSHOT = "/io/github/nbauma109/staticanalysis/sonar-way-java-8.6.0.37351.rules";
    private static final int EFFECTIVE_SONAR_WAY_RULE_COUNT = 392;
    private static final String COMPLEXITY = "java:S3776";
    private static final Set<String> REVIEWED_SYMBOLIC = Set.of("java:S2095",
            "java:S2259", "java:S3655");
    private static final Set<String> PRIORITY = Set.of(COMPLEXITY, "java:S100", "java:S107",
            "java:S1192", "java:S1066", "java:S1128", "java:S1481", "java:S112");
    private static final Set<String> TEMPLATES = Set.of("java:S124", "java:S2253",
            "java:S3688", "java:S4011");
    private static final Set<String> SUPPLEMENTAL_ONLY = Set.of("java:S9438", "java:S9398", "java:S7467");
    private static final String RECORD_METHOD_PLACEMENT = "java:S3398";
    private static final Gson GSON = new Gson();

    private final Map<String, RuleMetadata> metadata;
    private final Set<String> sonarWay;

    private RuleCatalog(Map<String, RuleMetadata> metadata, Set<String> sonarWay) {
        this.metadata = Map.copyOf(metadata);
        this.sonarWay = Set.copyOf(sonarWay);
    }

    static RuleCatalog load(Path analyzer) throws IOException {
        try (ZipFile archive = new ZipFile(analyzer.toFile())) {
            Map<String, RuleMetadata> definitions = new TreeMap<>();
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (isRuleEntry(entry.getName())) {
                    String key = PREFIX + entry.getName().substring(
                            RESOURCE_PATH.length(), entry.getName().length() - 5);
                    JsonObject definition = readJson(archive, entry);
                    definitions.put(key, new RuleMetadata(
                            definition.get("scope").getAsString(),
                            definition.get("status").getAsString(),
                            definition.get("type").getAsString()));
                }
            }
            ZipEntry profile = archive.getEntry(RESOURCE_PATH + "Sonar_way_profile.json");
            if (profile == null || definitions.isEmpty()) {
                throw new IOException("The analyzer does not contain Java rule metadata and Sonar Way profile.");
            }
            Set<String> embedded = new TreeSet<>();
            for (var key : readJson(archive, profile).getAsJsonArray("ruleKeys")) {
                embedded.add(PREFIX + key.getAsString());
            }
            Set<String> snapshot = readSonarWaySnapshot();
            if (!embedded.equals(snapshot)) {
                Set<String> missingFromAnalyzer = new TreeSet<>(snapshot);
                missingFromAnalyzer.removeAll(embedded);
                Set<String> missingFromSnapshot = new TreeSet<>(embedded);
                missingFromSnapshot.removeAll(snapshot);
                throw new IOException("Pinned Sonar Way rules differ from the analyzer archive. "
                        + "Missing from analyzer: " + missingFromAnalyzer
                        + "; missing from snapshot: " + missingFromSnapshot);
            }
            return new RuleCatalog(definitions, snapshot);
        }
    }

    static RuleCatalog load(Path analyzer, Path symbolicAnalyzer) throws IOException {
        RuleCatalog standard = load(analyzer);
        Map<String, RuleMetadata> selected = new TreeMap<>(standard.metadata);
        try (ZipFile archive = new ZipFile(symbolicAnalyzer.toFile())) {
            for (String key : REVIEWED_SYMBOLIC) {
                ZipEntry definition = archive.getEntry(SYMBOLIC_RESOURCE_PATH
                        + key.substring(PREFIX.length()) + ".json");
                if (definition == null) {
                    throw new IOException("Missing symbolic analyzer metadata for " + key);
                }
                JsonObject metadata = readJson(archive, definition);
                selected.put(key, new RuleMetadata(
                        metadata.get("scope").getAsString(),
                        metadata.get("status").getAsString(),
                        metadata.get("type").getAsString()));
            }
        }
        return new RuleCatalog(selected, standard.sonarWay);
    }

    private static Set<String> readSonarWaySnapshot() throws IOException {
        InputStream stream = RuleCatalog.class.getResourceAsStream(SONAR_WAY_SNAPSHOT);
        if (stream == null) {
            throw new IOException("The pinned Sonar Way snapshot is missing: " + SONAR_WAY_SNAPSHOT);
        }
        Set<String> keys = new TreeSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String key;
            while ((key = reader.readLine()) != null) {
                if (!key.matches("java:S[0-9]+") || !keys.add(key)) {
                    throw new IOException("Invalid or duplicate Sonar Way rule: " + key);
                }
            }
        }
        if (keys.isEmpty()) {
            throw new IOException("The pinned Sonar Way snapshot contains no rules.");
        }
        return keys;
    }

    private static boolean isRuleEntry(String name) {
        if (!name.startsWith(RESOURCE_PATH)) {
            return false;
        }
        return name.substring(RESOURCE_PATH.length()).matches("(S[0-9]+|NoSonar)\\.json");
    }

    private static JsonObject readJson(ZipFile archive, ZipEntry entry) throws IOException {
        try (Reader reader = new InputStreamReader(archive.getInputStream(entry), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, JsonObject.class);
        }
    }

    List<ActiveRule> select(String profile, String enabledRules, String disabledRules,
            int threshold, List<RuleParameters> configuration) {
        Set<String> selected = new TreeSet<>(profileRules(profile));
        Set<String> enabled = parseKeys(enabledRules);
        Set<String> disabled = parseKeys(disabledRules);
        for (String key : enabled) {
            if (!disabled.contains(key)) {
                if (!SUPPLEMENTAL_ONLY.contains(key)) {
                    requireEligible(key);
                    selected.add(key);
                }
            }
        }
        for (String key : disabled) {
            if (!metadata.containsKey(key) && !SUPPLEMENTAL_ONLY.contains(key)) {
                throw new IllegalArgumentException("Unknown local Java rule: " + key);
            }
        }
        selected.removeAll(disabled);
        Map<String, Map<String, String>> overrides = parameterOverrides(configuration, selected);
        List<ActiveRule> result = new ArrayList<>();
        for (String key : selected) {
            requireEligible(key);
            result.add(activeRule(key, threshold, overrides.getOrDefault(key, Map.of())));
        }
        return result;
    }

    Set<String> supplementalRules(String profile, String enabledRules, String disabledRules) {
        Set<String> selected = new TreeSet<>();
        if ("sonar-way".equals(profile) || "sonar-way-standard".equals(profile)
                || "sonar-way-reliability".equals(profile)) {
            selected.addAll(SUPPLEMENTAL_ONLY);
            selected.add(RECORD_METHOD_PLACEMENT);
        }
        for (String key : parseKeys(enabledRules)) {
            if (SUPPLEMENTAL_ONLY.contains(key) || RECORD_METHOD_PLACEMENT.equals(key)) {
                selected.add(key);
            }
        }
        selected.removeAll(parseKeys(disabledRules));
        return Set.copyOf(selected);
    }

    private Set<String> profileRules(String profile) {
        return switch (profile) {
            case "priority" -> PRIORITY;
            case "complexity" -> Set.of(COMPLEXITY);
            case "none" -> Set.of();
            case "sonar-way-standard" -> standardSonarWay();
            case "sonar-way", "sonar-way-reliability" -> sonarWayWithReliability();
            case "reliability" -> REVIEWED_SYMBOLIC;
            default -> throw new IllegalArgumentException("Unknown rule profile: " + profile);
        };
    }

    private Set<String> sonarWayWithReliability() {
        Set<String> rules = new TreeSet<>(standardSonarWay());
        rules.addAll(REVIEWED_SYMBOLIC);
        return rules;
    }

    private Set<String> standardSonarWay() {
        Set<String> selected = new TreeSet<>();
        for (String key : sonarWay) {
            RuleMetadata rule = metadata.get(key);
            if (eligible(key, rule) && !"SECURITY_HOTSPOT".equals(rule.type())
                    && !"VULNERABILITY".equals(rule.type())) {
                selected.add(key);
            }
        }
        if (selected.size() != EFFECTIVE_SONAR_WAY_RULE_COUNT) {
            throw new IllegalStateException("The effective pinned Sonar Way rule set changed: "
                    + selected.size() + " instead of " + EFFECTIVE_SONAR_WAY_RULE_COUNT
                    + ". Review analyzer metadata and update the versioned profile deliberately.");
        }
        return selected;
    }

    private void requireEligible(String key) {
        RuleMetadata rule = metadata.get(key);
        if (!eligible(key, rule)) {
            throw new IllegalArgumentException("Rule " + key
                    + " is not an eligible standard production Java rule in the pinned analyzer. "
                    + "Check the rule catalog for symbolic, test, template or server requirements.");
        }
        if ("SECURITY_HOTSPOT".equals(rule.type()) || "VULNERABILITY".equals(rule.type())) {
            throw new IllegalArgumentException("Security review reporting is not implemented for " + key);
        }
    }

    private static boolean eligible(String key, RuleMetadata rule) {
        return rule != null && !TEMPLATES.contains(key)
                && "ready".equals(rule.status()) && !"Tests".equals(rule.scope());
    }

    private static Set<String> parseKeys(String value) {
        Set<String> keys = new TreeSet<>();
        if (value != null && !value.isBlank()) {
            for (String part : value.split(",", -1)) {
                String key = part.trim();
                if (key.isEmpty()) {
                    throw new IllegalArgumentException("Rule lists must not contain blank entries.");
                }
                keys.add(key);
            }
        }
        return keys;
    }

    private static Map<String, Map<String, String>> parameterOverrides(
            List<RuleParameters> configuration, Set<String> selected) {
        Map<String, Map<String, String>> overrides = new LinkedHashMap<>();
        for (RuleParameters rule : configuration) {
            String key = rule.getKey();
            if (!selected.contains(key)) {
                throw new IllegalArgumentException("Parameters configured for inactive rule: " + key);
            }
            if (overrides.putIfAbsent(key, rule.getProperties()) != null) {
                throw new IllegalArgumentException("Duplicate parameter configuration for " + key);
            }
        }
        return overrides;
    }

    private static ActiveRule activeRule(String key, int threshold, Map<String, String> overrides) {
        ActiveRule rule = new ActiveRule(key, "java");
        Map<String, String> parameters = new TreeMap<>();
        if (COMPLEXITY.equals(key)) {
            parameters.put("Threshold", Integer.toString(threshold));
        }
        parameters.putAll(overrides);
        rule.setParams(parameters);
        return rule;
    }

    private record RuleMetadata(String scope, String status, String type) {
    }
}
