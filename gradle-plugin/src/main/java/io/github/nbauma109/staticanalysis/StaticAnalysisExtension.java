package io.github.nbauma109.staticanalysis;

import java.io.File;
import java.util.Map;

/**
 * Configuration shared by the Gradle analysis task and its build script.
 */
public class StaticAnalysisExtension {

    private String profile = "priority";
    private String enabledRules = "";
    private String disabledRules = "";
    private int cognitiveComplexityThreshold = 15;
    private boolean failOnViolation;
    private boolean skip;
    private boolean eclipseEnabled;
    private File eclipseExecutable;
    private File eclipseCleanupProfile;
    private String eclipseDuplicatePolicy = "prefer-sonar";
    private long eclipseTimeoutSeconds = 600;
    private Map<String, Map<String, String>> ruleParameters = Map.of();

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getEnabledRules() {
        return enabledRules;
    }

    public void setEnabledRules(String enabledRules) {
        this.enabledRules = enabledRules;
    }

    public String getDisabledRules() {
        return disabledRules;
    }

    public void setDisabledRules(String disabledRules) {
        this.disabledRules = disabledRules;
    }

    public int getCognitiveComplexityThreshold() {
        return cognitiveComplexityThreshold;
    }

    public void setCognitiveComplexityThreshold(int cognitiveComplexityThreshold) {
        this.cognitiveComplexityThreshold = cognitiveComplexityThreshold;
    }

    public boolean isFailOnViolation() {
        return failOnViolation;
    }

    public void setFailOnViolation(boolean failOnViolation) {
        this.failOnViolation = failOnViolation;
    }

    public boolean isSkip() {
        return skip;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    public boolean isEclipseEnabled() {
        return eclipseEnabled;
    }

    public void setEclipseEnabled(boolean eclipseEnabled) {
        this.eclipseEnabled = eclipseEnabled;
    }

    public File getEclipseExecutable() {
        return eclipseExecutable;
    }

    public void setEclipseExecutable(File eclipseExecutable) {
        this.eclipseExecutable = eclipseExecutable;
    }

    public File getEclipseCleanupProfile() {
        return eclipseCleanupProfile;
    }

    public void setEclipseCleanupProfile(File eclipseCleanupProfile) {
        this.eclipseCleanupProfile = eclipseCleanupProfile;
    }

    public String getEclipseDuplicatePolicy() {
        return eclipseDuplicatePolicy;
    }

    public void setEclipseDuplicatePolicy(String eclipseDuplicatePolicy) {
        this.eclipseDuplicatePolicy = eclipseDuplicatePolicy;
    }

    public long getEclipseTimeoutSeconds() {
        return eclipseTimeoutSeconds;
    }

    public void setEclipseTimeoutSeconds(long eclipseTimeoutSeconds) {
        this.eclipseTimeoutSeconds = eclipseTimeoutSeconds;
    }

    public Map<String, Map<String, String>> getRuleParameters() {
        return ruleParameters;
    }

    public void setRuleParameters(Map<String, Map<String, String>> ruleParameters) {
        this.ruleParameters = Map.copyOf(ruleParameters);
    }
}
