package io.github.nbauma109.staticanalysis;

import java.util.Optional;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;

final class GradleSourceVersion {

    private GradleSourceVersion() {
    }

    static String release(Project project, SourceSet sourceSet) {
        JavaCompile compiler = (JavaCompile) project.getTasks()
                .getByName(sourceSet.getCompileJavaTaskName());
        String version = Optional.ofNullable(compiler.getOptions().getRelease().getOrNull())
                .map(Object::toString).orElse(compiler.getSourceCompatibility());
        return Integer.toString(RuleJavaVersion.parse(version));
    }
}
