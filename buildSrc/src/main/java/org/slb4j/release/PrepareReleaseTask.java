package org.slb4j.release;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates the persisted, reproducible release plan used by the publication tasks.
 *
 * <p>The task deliberately receives all runtime data through Gradle properties so it
 * remains safe to use from a configuration-cache-disabled release invocation.</p>
 */
public abstract class PrepareReleaseTask extends DefaultTask {
    private static final String GROUP = "org.slb4j";
    private static final String BOM = "slb4j-bom";
    private static final List<String> MODULES = List.of(
            "slb4j",
            "slb4j-ext",
            "slb4j-ext-layouts",
            "slb4j-ext-swing",
            "slb4j-ext-fx",
            "slb4j-config",
            "slb4j-config-json",
            "slb4j-config-yaml",
            "slb4j-config-xml",
            "slb4j-config-all"
    );
    private static final List<String> SHARED_INPUT_PATHS = List.of(
            "build.gradle.kts",
            "settings.gradle.kts",
            "gradle.properties",
            "gradle/libs.versions.toml",
            "gradle/wrapper",
            "buildSrc",
            "spotbugs-exclude.xml",
            "gradle.lockfile",
            "settings-gradle.lockfile",
            ":(glob)**/gradle.lockfile"
    );
    private static final Pattern TABLE = Pattern.compile("^\\[([A-Za-z0-9_.-]+)]$");
    private static final Pattern VALUE = Pattern.compile("^([A-Za-z][A-Za-z0-9_-]*)\\s*=\\s*(.+)$");
    private static final Pattern QUOTED_STRING = Pattern.compile("\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");
    private static final Pattern VERSION = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");

    @InputDirectory
    public abstract DirectoryProperty getRepositoryDirectory();

    @InputFile
    public abstract RegularFileProperty getReleaseStateFile();

    @Input
    public abstract Property<String> getPreparedReleasePlanPath();

    @Input
    public abstract Property<String> getReleaseType();

    @Input
    public abstract Property<String> getRequestedReleaseVersion();

    @Input
    public abstract Property<String> getAdditionalReleaseModules();

    @Input
    public abstract Property<Boolean> getConfirmRelease();

    @TaskAction
    public void prepareRelease() {
        File repository = getRepositoryDirectory().get().getAsFile();
        File stateFile = getReleaseStateFile().get().getAsFile();
        File planFile = new File(getPreparedReleasePlanPath().get());

        if (planFile.exists()) {
            throw new GradleException("a prepared release plan already exists at " + planFile.getPath());
        }
        if (!runGit(repository, "status", "--porcelain").output().isBlank()) {
            throw new GradleException("the Git working tree must be clean before preparing a release");
        }

        String releaseType = getReleaseType().get().trim().toLowerCase();
        if (!Set.of("patch", "minor", "major").contains(releaseType)) {
            throw new GradleException("supply -PreleaseType=patch, -PreleaseType=minor, or -PreleaseType=major");
        }

        ReleaseState state = readReleaseState(stateFile);
        SemanticVersion previousBom = SemanticVersion.parse(state.bomVersion());
        String requestedVersion = getRequestedReleaseVersion().get().trim();
        SemanticVersion targetVersion = requestedVersion.isEmpty()
                ? nextVersion(releaseType, previousBom)
                : SemanticVersion.parse(requestedVersion);
        validateTargetVersion(releaseType, previousBom, targetVersion);

        String releaseRevision = requireGitSuccess(repository, "resolving release revision", "rev-parse", "HEAD");
        for (Map.Entry<String, ModuleState> entry : state.modules().entrySet()) {
            String publishedRevision = entry.getValue().publishedRevision();
            requireGitSuccess(repository, "checking published revision for " + entry.getKey(),
                    "cat-file", "-e", publishedRevision + "^{commit}");
            if (runGit(repository, "merge-base", "--is-ancestor", publishedRevision, releaseRevision).exitValue() != 0) {
                throw new GradleException("published revision for " + entry.getKey()
                        + " is not an ancestor of " + releaseRevision);
            }
        }

        Set<String> additionalModules = parseModuleList(getAdditionalReleaseModules().get());
        if (!MODULES.containsAll(additionalModules)) {
            throw new GradleException("additionalReleaseModules contains an unknown publishable module");
        }

        Map<String, String> reasons = new LinkedHashMap<>();
        if ("patch".equals(releaseType)) {
            for (Map.Entry<String, ModuleState> entry : state.modules().entrySet()) {
                ModuleState module = entry.getValue();
                boolean sourceChanged = gitHasChanges(
                        repository, module.publishedRevision(), releaseRevision, module.paths());
                boolean sharedChanged = gitHasChanges(
                        repository, module.publishedRevision(), releaseRevision, SHARED_INPUT_PATHS);
                if (sourceChanged && sharedChanged) {
                    reasons.put(entry.getKey(), "direct source and shared build input change");
                } else if (sourceChanged) {
                    reasons.put(entry.getKey(), "direct source change");
                } else if (sharedChanged) {
                    reasons.put(entry.getKey(), "shared build or dependency input change");
                }
            }
            for (String module : additionalModules) {
                reasons.putIfAbsent(module, "explicit minimum internal dependency update");
            }
            if (reasons.isEmpty()) {
                throw new GradleException("no publishable module changed; a BOM-only patch release is not allowed");
            }
        } else {
            for (String module : MODULES) {
                reasons.put(module, releaseType + " release");
            }
        }

        String target = targetVersion.toString();
        Map<String, PlannedModule> modules = new LinkedHashMap<>();
        for (String module : MODULES) {
            ModuleState old = state.modules().get(module);
            boolean selected = reasons.containsKey(module);
            modules.put(module, new PlannedModule(
                    selected ? target : old.version(),
                    selected ? releaseRevision : old.publishedRevision(),
                    selected,
                    reasons.getOrDefault(module, "retained published module")
            ));
        }
        ReleasePlan plan = new ReleasePlan(releaseType, target, releaseRevision, modules);

        List<Coordinate> coordinates = new ArrayList<>();
        coordinates.add(new Coordinate(BOM, plan.bomVersion()));
        for (Map.Entry<String, PlannedModule> entry : modules.entrySet()) {
            if (entry.getValue().selected()) {
                coordinates.add(new Coordinate(entry.getKey(), entry.getValue().version()));
            }
        }
        for (Coordinate coordinate : coordinates) {
            if (isMavenCentralCoordinatePublished(coordinate.artifactId(), coordinate.version())) {
                throw new GradleException("Maven Central already contains " + coordinate.artifactId() + ":"
                        + coordinate.version() + "; release coordinates are immutable");
            }
        }

        getLogger().lifecycle(renderPlan(plan));
        if (getConfirmRelease().get()) {
            writePlan(planFile, plan);
            getLogger().lifecycle("Wrote " + planFile.getPath() + ". Commit it before publication.");
        } else {
            getLogger().lifecycle("Dry run only. Re-run with -PconfirmRelease=true to write the plan.");
        }
    }

    private static Set<String> parseModuleList(String value) {
        if (value.isBlank()) {
            return Set.of();
        }
        return Set.copyOf(List.of(value.split(",")).stream()
                .map(String::trim).filter(item -> !item.isEmpty()).toList());
    }

    private static SemanticVersion nextVersion(String type, SemanticVersion previous) {
        return switch (type) {
            case "patch" -> new SemanticVersion(previous.major(), previous.minor(), previous.patch() + 1);
            case "minor" -> new SemanticVersion(previous.major(), previous.minor() + 1, 0);
            case "major" -> new SemanticVersion(previous.major() + 1, 0, 0);
            default -> throw new IllegalStateException("validated before computing the version");
        };
    }

    private static void validateTargetVersion(String type, SemanticVersion previous, SemanticVersion target) {
        boolean valid = switch (type) {
            case "patch" -> target.major() == previous.major() && target.minor() == previous.minor()
                    && target.patch() > previous.patch();
            case "minor" -> target.major() == previous.major() && target.minor() > previous.minor()
                    && target.patch() == 0;
            case "major" -> target.major() > previous.major() && target.minor() == 0 && target.patch() == 0;
            default -> false;
        };
        if (!valid) {
            throw new GradleException("invalid " + type + " release version " + target + " after " + previous);
        }
    }

    private static ReleaseState readReleaseState(File file) {
        Map<String, Map<String, String>> values = parseToml(file);
        Map<String, String> release = requireTable(values, "release", file);
        String bomVersion = requireValue(release, "bomVersion", "release", file);
        Map<String, ModuleState> modules = new LinkedHashMap<>();
        for (String module : MODULES) {
            Map<String, String> valuesForModule = requireTable(values, "modules." + module, file);
            modules.put(module, new ModuleState(
                    requireValue(valuesForModule, "version", "modules." + module, file),
                    requireValue(valuesForModule, "publishedRevision", "modules." + module, file),
                    parseTomlArray(requireValue(valuesForModule, "paths", "modules." + module, file))
            ));
        }
        return new ReleaseState(bomVersion, modules);
    }

    private static Map<String, Map<String, String>> parseToml(File file) {
        if (!file.isFile()) {
            throw new GradleException("release file does not exist: " + file.getPath());
        }
        Map<String, Map<String, String>> values = new LinkedHashMap<>();
        String section = "";
        try {
            for (String rawLine : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String line = rawLine.split("#", 2)[0].trim();
                if (line.isEmpty()) continue;
                Matcher table = TABLE.matcher(line);
                if (table.matches()) {
                    section = table.group(1);
                    values.computeIfAbsent(section, ignored -> new LinkedHashMap<>());
                    continue;
                }
                Matcher value = VALUE.matcher(line);
                if (value.matches()) {
                    if (section.isEmpty()) {
                        throw new GradleException("value outside a TOML table in " + file.getPath());
                    }
                    String parsed = value.group(2).trim();
                    if (parsed.length() >= 2 && parsed.startsWith("\"") && parsed.endsWith("\"")) {
                        parsed = parsed.substring(1, parsed.length() - 1);
                    }
                    values.computeIfAbsent(section, ignored -> new LinkedHashMap<>()).put(value.group(1), parsed);
                    continue;
                }
                throw new GradleException("unsupported release TOML syntax in " + file.getPath() + ": " + line);
            }
        } catch (IOException exception) {
            throw new GradleException("could not read " + file.getPath(), exception);
        }
        return values;
    }

    private static Map<String, String> requireTable(
            Map<String, Map<String, String>> values, String table, File file) {
        Map<String, String> result = values.get(table);
        if (result == null) throw new GradleException("[" + table + "] table missing from " + file.getPath());
        return result;
    }

    private static String requireValue(Map<String, String> values, String key, String table, File file) {
        String result = values.get(key);
        if (result == null) throw new GradleException(table + "." + key + " missing from " + file.getPath());
        return result;
    }

    private static List<String> parseTomlArray(String value) {
        List<String> result = new ArrayList<>();
        Matcher matcher = QUOTED_STRING.matcher(value);
        while (matcher.find()) {
            result.add(matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        return result;
    }

    private static CommandResult runGit(File directory, String... arguments) {
        try {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(List.of(arguments));
            Process process = new ProcessBuilder(command).directory(directory).redirectErrorStream(true).start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            process.getInputStream().transferTo(output);
            return new CommandResult(process.waitFor(), output.toString(StandardCharsets.UTF_8).trim());
        } catch (IOException exception) {
            throw new GradleException("could not execute git", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GradleException("interrupted while executing git", exception);
        }
    }

    private static String requireGitSuccess(File directory, String description, String... arguments) {
        CommandResult result = runGit(directory, arguments);
        if (result.exitValue() != 0) {
            throw new GradleException(description + " failed ("
                    + (result.output().isBlank() ? "no output" : result.output()) + ")");
        }
        return result.output();
    }

    private static boolean gitHasChanges(
            File directory, String fromRevision, String toRevision, List<String> pathspecs) {
        List<String> arguments = new ArrayList<>(List.of("diff", "--quiet", fromRevision, toRevision, "--"));
        arguments.addAll(pathspecs);
        CommandResult result = runGit(directory, arguments.toArray(String[]::new));
        return switch (result.exitValue()) {
            case 0 -> false;
            case 1 -> true;
            default -> throw new GradleException("could not compare Git revisions: " + result.output());
        };
    }

    private static boolean isMavenCentralCoordinatePublished(String artifactId, String version) {
        String path = GROUP.replace('.', '/') + "/" + artifactId + "/" + version + "/"
                + artifactId + "-" + version + ".pom";
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create("https://repo1.maven.org/maven2/" + path)
                    .toURL().openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) return false;
            if (status >= 200 && status <= 399) return true;
            throw new GradleException("could not determine whether " + artifactId + ":" + version
                    + " exists on Maven Central (HTTP " + status + ")");
        } catch (IOException exception) {
            throw new GradleException("could not check Maven Central for " + artifactId + ":" + version, exception);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void writePlan(File file, ReleasePlan plan) {
        StringBuilder content = new StringBuilder();
        content.append("[release]\n")
                .append("schemaVersion = 1\n")
                .append("releaseType = \"").append(tomlString(plan.releaseType())).append("\"\n")
                .append("bomVersion = \"").append(tomlString(plan.bomVersion())).append("\"\n")
                .append("sourceRevision = \"").append(tomlString(plan.sourceRevision())).append("\"\n");
        for (String module : MODULES) {
            PlannedModule planned = plan.modules().get(module);
            content.append("\n[modules.").append(module).append("]\n")
                    .append("version = \"").append(tomlString(planned.version())).append("\"\n")
                    .append("sourceRevision = \"").append(tomlString(planned.sourceRevision())).append("\"\n")
                    .append("selected = ").append(planned.selected()).append("\n")
                    .append("reason = \"").append(tomlString(planned.reason())).append("\"\n");
        }
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new GradleException("could not write " + file.getPath(), exception);
        }
    }

    private static String renderPlan(ReleasePlan plan) {
        StringBuilder text = new StringBuilder("Selective release plan\n")
                .append("  type: ").append(plan.releaseType()).append("\n")
                .append("  source revision: ").append(plan.sourceRevision()).append("\n")
                .append("  BOM: ").append(BOM).append(":").append(plan.bomVersion()).append("\n")
                .append("  modules to publish:\n");
        for (Map.Entry<String, PlannedModule> entry : plan.modules().entrySet()) {
            if (entry.getValue().selected()) {
                text.append("    ").append(entry.getKey()).append(":").append(entry.getValue().version())
                        .append(" (").append(entry.getValue().reason()).append(")\n");
            }
        }
        text.append("  retained modules:\n");
        for (Map.Entry<String, PlannedModule> entry : plan.modules().entrySet()) {
            if (!entry.getValue().selected()) {
                text.append("    ").append(entry.getKey()).append(":").append(entry.getValue().version()).append("\n");
            }
        }
        return text.toString();
    }

    private static String tomlString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record ModuleState(String version, String publishedRevision, List<String> paths) {}
    private record ReleaseState(String bomVersion, Map<String, ModuleState> modules) {}
    private record PlannedModule(String version, String sourceRevision, boolean selected, String reason) {}
    private record ReleasePlan(String releaseType, String bomVersion, String sourceRevision,
                               Map<String, PlannedModule> modules) {}
    private record Coordinate(String artifactId, String version) {}
    private record CommandResult(int exitValue, String output) {}

    private record SemanticVersion(int major, int minor, int patch) {
        private static SemanticVersion parse(String value) {
            Matcher matcher = VERSION.matcher(value);
            if (!matcher.matches()) {
                throw new GradleException("release version must be a stable major.minor.patch value: " + value);
            }
            return new SemanticVersion(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            );
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }
}
