package io.github.nbauma109.sonarlint;

import java.util.Map;

/**
 * Maven configuration for exact, case-sensitive analyzer rule properties.
 */
public final class RuleParameters {

    private String key;
    private Map<String, String> properties = Map.of();

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = Map.copyOf(properties);
    }
}
