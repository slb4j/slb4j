import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.GradleException
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.signing.Sign
import org.gradle.kotlin.dsl.withType
import org.slb4j.release.PrepareReleaseTask
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Properties

/*
 * Copyright 2026 Axel Howind - axh@dua3.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
plugins {
    id("java-library")
    id("maven-publish")
    id("signing")
    alias(libs.plugins.jdkprovider.plugin)
    alias(libs.plugins.cabe.plugin)
    alias(libs.plugins.spotbugs.plugin)
    alias(libs.plugins.versions.plugin)
    alias(libs.plugins.jreleaser.plugin)
    jacoco
}

java {
    withSourcesJar()
    withJavadocJar()
}

/////////////////////////////////////////////////////////////////////////////
// Meta data object
/////////////////////////////////////////////////////////////////////////////

val projectVersion = rootProject.libs.versions.projectVersion.get()

object Meta {
    const val DESCRIPTION = "Simple Logging Backend for Java"
    const val INCEPTION_YEAR = "2026"
    const val GROUP = "org.slb4j"
    const val SCM = "https://github.com/slb4j/slb4j"
    const val LICENSE_NAME = "The Apache Software License, Version 2.0"
    const val LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0.txt"
    const val DEVELOPER_ID = "axh"
    const val DEVELOPER_NAME = "Axel Howind"
    const val DEVELOPER_EMAIL = "axh@slb4j.org"
    const val ORGANIZATION_NAME = "slb4j.org"
    const val ORGANIZATION_URL = "https://www.slb4j.org"
}

/////////////////////////////////////////////////////////////////////////////
// Selective release model
/////////////////////////////////////////////////////////////////////////////

private val bomName = "slb4j-bom"
private val publishableModuleNames = listOf(
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
)
private val moduleProjectPaths = mapOf(
    "slb4j" to ":",
    "slb4j-ext" to ":slb4j-ext",
    "slb4j-ext-layouts" to ":slb4j-ext:slb4j-ext-layouts",
    "slb4j-ext-swing" to ":slb4j-ext:slb4j-ext-swing",
    "slb4j-ext-fx" to ":slb4j-ext:slb4j-ext-fx",
    "slb4j-config" to ":slb4j-config",
    "slb4j-config-json" to ":slb4j-config:slb4j-config-json",
    "slb4j-config-yaml" to ":slb4j-config:slb4j-config-yaml",
    "slb4j-config-xml" to ":slb4j-config:slb4j-config-xml",
    "slb4j-config-all" to ":slb4j-config:slb4j-config-all"
)

private data class ModuleReleaseState(
    val version: String,
    val publishedRevision: String,
    val paths: List<String>
)

private data class PublishedReleaseState(
    val bomVersion: String,
    val modules: Map<String, ModuleReleaseState>
)

private data class PreparedReleaseModule(
    val version: String,
    val sourceRevision: String,
    val selected: Boolean,
    val reason: String
)

private data class PreparedReleasePlan(
    val releaseType: String,
    val bomVersion: String,
    val sourceRevision: String,
    val modules: Map<String, PreparedReleaseModule>
)

private data class CommandResult(val exitValue: Int, val output: String)
private data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) {
    override fun toString(): String = "$major.$minor.$patch"
}

@Suppress("UNCHECKED_CAST")
private val configuredReleaseModuleVersions = gradle.extra["releaseModuleVersions"] as Map<String, String>
@Suppress("UNCHECKED_CAST")
private val configuredSelectedReleaseModules = gradle.extra["releaseSelectedModules"] as Set<String>
private val effectiveBomVersion = gradle.extra["releaseBomVersion"] as String
private val releasePlanPresent = gradle.extra["releasePlanPresent"] as Boolean
private val releaseStateFile = gradle.extra["releaseStateFile"] as File
private val preparedReleasePlanFile = gradle.extra["preparedReleasePlanFile"] as File

private fun parseToml(file: File): Map<String, Map<String, String>> {
    require(file.isFile) { "release file does not exist: ${file.path}" }
    var section = ""
    val values = mutableMapOf<String, MutableMap<String, String>>()
    val tablePattern = Regex("""^\[([A-Za-z0-9_.-]+)]$""")
    val valuePattern = Regex("""^([A-Za-z][A-Za-z0-9_-]*)\s*=\s*(.+)$""")
    file.forEachLine { rawLine ->
        val line = rawLine.substringBefore('#').trim()
        if (line.isEmpty()) return@forEachLine
        tablePattern.matchEntire(line)?.let {
            section = it.groupValues[1]
            values.getOrPut(section) { mutableMapOf() }
            return@forEachLine
        }
        valuePattern.matchEntire(line)?.let {
            check(section.isNotEmpty()) { "value outside a TOML table in ${file.path}" }
            values.getOrPut(section) { mutableMapOf() }[it.groupValues[1]] =
                it.groupValues[2].trim().removeSurrounding("\"")
            return@forEachLine
        }
        throw GradleException("unsupported release TOML syntax in ${file.path}: $line")
    }
    return values
}

private fun parseTomlStringArray(value: String): List<String> =
    Regex(""""((?:\\.|[^"\\])*)"""").findAll(value).map { match ->
        match.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
    }.toList()

private fun readPublishedReleaseState(file: File): PublishedReleaseState {
    val values = parseToml(file)
    val release = values["release"] ?: throw GradleException("[release] table missing from ${file.path}")
    val modules = publishableModuleNames.associateWith { moduleName ->
        val module = values["modules.$moduleName"]
            ?: throw GradleException("[modules.$moduleName] table missing from ${file.path}")
        ModuleReleaseState(
            module["version"] ?: throw GradleException("modules.$moduleName.version missing from ${file.path}"),
            module["publishedRevision"]
                ?: throw GradleException("modules.$moduleName.publishedRevision missing from ${file.path}"),
            parseTomlStringArray(
                module["paths"] ?: throw GradleException("modules.$moduleName.paths missing from ${file.path}")
            )
        )
    }
    return PublishedReleaseState(
        release["bomVersion"] ?: throw GradleException("release.bomVersion missing from ${file.path}"),
        modules
    )
}

private fun readPreparedReleasePlan(file: File): PreparedReleasePlan {
    val values = parseToml(file)
    val release = values["release"] ?: throw GradleException("[release] table missing from ${file.path}")
    val modules = publishableModuleNames.associateWith { moduleName ->
        val module = values["modules.$moduleName"]
            ?: throw GradleException("[modules.$moduleName] table missing from ${file.path}")
        PreparedReleaseModule(
            module["version"] ?: throw GradleException("modules.$moduleName.version missing from ${file.path}"),
            module["sourceRevision"]
                ?: throw GradleException("modules.$moduleName.sourceRevision missing from ${file.path}"),
            module["selected"]?.toBooleanStrictOrNull()
                ?: throw GradleException("modules.$moduleName.selected missing or invalid in ${file.path}"),
            module["reason"] ?: throw GradleException("modules.$moduleName.reason missing from ${file.path}")
        )
    }
    return PreparedReleasePlan(
        release["releaseType"] ?: throw GradleException("release.releaseType missing from ${file.path}"),
        release["bomVersion"] ?: throw GradleException("release.bomVersion missing from ${file.path}"),
        release["sourceRevision"] ?: throw GradleException("release.sourceRevision missing from ${file.path}"),
        modules
    )
}

private fun parseSemanticVersion(value: String): SemanticVersion {
    val match = Regex("""^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$""").matchEntire(value)
        ?: throw GradleException("release version must be a stable major.minor.patch value: $value")
    return SemanticVersion(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
}

private fun runGit(vararg arguments: String): CommandResult {
    val output = ByteArrayOutputStream()
    val process = ProcessBuilder(listOf("git") + arguments)
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    process.inputStream.copyTo(output)
    val exitValue = process.waitFor()
    return CommandResult(exitValue, output.toString(StandardCharsets.UTF_8).trim())
}

private fun requireGitSuccess(description: String, vararg arguments: String): String {
    val result = runGit(*arguments)
    check(result.exitValue == 0) {
        "$description failed (${result.output.ifBlank { "no output" }})"
    }
    return result.output
}

private fun isMavenCentralCoordinatePublished(artifactId: String, version: String): Boolean {
    val path = "${Meta.GROUP.replace('.', '/')}/$artifactId/$version/$artifactId-$version.pom"
    val connection = (URI("https://repo1.maven.org/maven2/$path").toURL().openConnection() as HttpURLConnection).apply {
        requestMethod = "HEAD"
        connectTimeout = 10_000
        readTimeout = 10_000
    }
    return try {
        when (val status = connection.responseCode) {
            HttpURLConnection.HTTP_NOT_FOUND -> false
            in 200..399 -> true
            else -> throw GradleException(
                "could not determine whether $artifactId:$version exists on Maven Central (HTTP $status)"
            )
        }
    } finally {
        connection.disconnect()
    }
}

private fun tomlString(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

allprojects {
    group = Meta.GROUP

    if (!name.equals("benchmark")) {
        dependencyLocking {
            lockMode.set(LockMode.LENIENT)
            lockAllConfigurations()
        }
    }
}

allprojects {
    val projectPath = path

    tasks.register("resolveAndLockAll") {
        group = "build setup"
        description = "Resolves all resolvable configurations in this project for dependency locking."

        notCompatibleWithConfigurationCache("Resolves all configurations to update dependency lock files.")

        doFirst {
            require(gradle.startParameter.isWriteDependencyLocks) {
                "Run this task with --write-locks to update lock files."
            }
        }

        doLast {
            configurations
                .filter {
                    it.isCanBeResolved &&
                        // Graal plugin wires an incomplete nativeImageTestClasspath configuration.
                        it.name != "nativeImageTestClasspath"
                }
                .forEach { configuration ->
                    runCatching { configuration.resolve() }
                        .onFailure { error ->
                            logger.warn(
                                "Skipping dependency lock update for $projectPath:${configuration.name}: ${error.message}"
                            )
                        }
                }
        }
    }
}

tasks.named("resolveAndLockAll") {
    dependsOn(subprojects.map { it.tasks.named("resolveAndLockAll") })
}

group = Meta.GROUP

// check for development/release version
fun isDevelopmentVersion(versionString: String): Boolean {
    val v = versionString.lowercase()
    val markers = listOf("snapshot", "alpha", "beta")
    return markers.any { marker -> v.contains("-$marker") || v.contains(".$marker") }
}

val isReleaseVersion = !isDevelopmentVersion(effectiveBomVersion)
val isSnapshot = effectiveBomVersion.lowercase().contains("snapshot")
val ciReleaseBundleMode = providers.gradleProperty("ciReleaseBundle").map(String::toBoolean).orElse(false).get()
val prebuiltReleaseBundleMode = providers.gradleProperty("prebuiltReleaseBundle").map(String::toBoolean).orElse(false).get()

dependencies {
    implementation(libs.jspecify)

    compileOnly(platform(libs.log4j.bom))
    compileOnly(libs.log4j.api)
    compileOnly(libs.slf4j.api)
    compileOnly(libs.jcl)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(platform(libs.log4j.bom))
    testImplementation(libs.log4j.api)
    testImplementation(libs.log4j.core)
    testImplementation(libs.slf4j.api)
    testImplementation(libs.jcl)

    testRuntimeOnly(libs.junit.platform.launcher)
}

cabe {
    if (isReleaseVersion) {
        config.set(com.dua3.cabe.processor.Configuration.parse("publicApi=THROW_NPE:privateApi=ASSERT"))
    } else {
        config.set(com.dua3.cabe.processor.Configuration.DEVELOPMENT.withStrict(true))
    }
}

tasks.test {
    useJUnitPlatform()
    outputs.upToDateWhen { false }
    dependsOn(
        ":samples:all:classes",
        ":samples:jul:classes",
        ":samples:jcl:classes",
        ":samples:log4j:classes",
        ":samples:slf4j:classes"
    )
}

val jacocoTestReport = tasks.getByName<JacocoReport>("jacocoTestReport") {
    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")).include("*.exec"))
}

allprojects {
    if (!project.name.contains("benchmark") && !project.name.endsWith("-bom")) {
        apply(plugin = "com.dua3.gradle.jdkprovider")

        jdk {
            version = rootProject.libs.versions.jdk.get()
            javaFxBundled = true
        }
    }

    repositories {
        mavenLocal()
        mavenCentral()
    }

    // --- PUBLISHING ---

    pluginManager.withPlugin("maven-publish") {
        configure<PublishingExtension> {
            // Repositories for publishing
            repositories {
                // Sonatype snapshots for snapshot versions
                if (isSnapshot) {
                    maven {
                        name = "sonatypeSnapshots"
                        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
                        credentials {
                            username = System.getenv("SONATYPE_USERNAME")
                            password = System.getenv("SONATYPE_PASSWORD")
                        }
                    }
                }

                // Always add root-level staging directory for JReleaser
                maven {
                    name = "stagingDirectory"
                    url = rootProject.layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
                }
            }

            if (project.name.endsWith("-bom")) {
                publications {
                    create<MavenPublication>("bomPublication") {
                        from(components["javaPlatform"])

                        groupId = Meta.GROUP
                        artifactId = project.name
                        version = project.version.toString()

                        pom {
                            name.set(project.name)
                            description.set(project.description ?: "SLB4J bill of materials")
                            url.set(Meta.SCM)
                            licenses {
                                license {
                                    name.set(Meta.LICENSE_NAME)
                                    url.set(Meta.LICENSE_URL)
                                }
                            }
                            developers {
                                developer {
                                    id.set(Meta.DEVELOPER_ID)
                                    name.set(Meta.DEVELOPER_NAME)
                                    email.set(Meta.DEVELOPER_EMAIL)
                                    organization.set(Meta.ORGANIZATION_NAME)
                                    organizationUrl.set(Meta.ORGANIZATION_URL)
                                }
                            }
                            scm {
                                connection.set("scm:git:${Meta.SCM}")
                                developerConnection.set("scm:git:${Meta.SCM}")
                                url.set(Meta.SCM)
                            }
                        }
                    }
                }
            } else {
                publications {
                    create<MavenPublication>("mavenJava") {
                        from(components["java"])

                        groupId = Meta.GROUP
                        artifactId = project.name
                        version = project.version.toString()

                        pom {
                            name.set(project.name)
                            description.set(Meta.DESCRIPTION)
                            url.set(Meta.SCM)

                            licenses {
                                license {
                                    name.set(Meta.LICENSE_NAME)
                                    url.set(Meta.LICENSE_URL)
                                }
                            }

                            developers {
                                developer {
                                    id.set(Meta.DEVELOPER_ID)
                                    name.set(Meta.DEVELOPER_NAME)
                                    email.set(Meta.DEVELOPER_EMAIL)
                                    organization.set(Meta.ORGANIZATION_NAME)
                                    organizationUrl.set(Meta.ORGANIZATION_URL)
                                }
                            }

                            scm {
                                connection.set("scm:git:${Meta.SCM}")
                                developerConnection.set("scm:git:${Meta.SCM}")
                                url.set(Meta.SCM)
                            }

                            withXml {
                                val root = asNode()
                                root.appendNode("inceptionYear", "2019")
                            }
                        }
                    }
                }
            }
        }

        // Task to publish to staging directory per subproject
        val publishToStagingDirectory = tasks.register("publishToStagingDirectory") {
            group = "publishing"
            description = "Publish artifacts to root staging directory for JReleaser"

            dependsOn(tasks.withType<PublishToMavenRepository>().matching {
                it.repository.name == "stagingDirectory"
            })
        }
    }

    // Signing configuration deferred until after evaluation
    afterEvaluate {
        if (pluginManager.hasPlugin("signing")) {
            configure<SigningExtension> {
                val isSnapshot = project.version.toString().lowercase().contains("snapshot")
                val isPublishing = gradle.taskGraph.hasTask("publish") || 
                                 gradle.taskGraph.hasTask("publishToMavenLocal") ||
                                 gradle.taskGraph.hasTask("publishToStagingDirectory")
                val shouldSign = !isSnapshot && isPublishing
                isRequired = shouldSign && !ciReleaseBundleMode

                val signingSecretKey = System.getenv("SIGNING_SECRET_KEY")
                if (shouldSign && !signingSecretKey.isNullOrBlank() && !ciReleaseBundleMode) {
                    useInMemoryPgpKeys(
                        signingSecretKey,
                        System.getenv("SIGNING_PASSWORD")
                    )
                }

                val publishing = project.extensions.findByType<PublishingExtension>() ?: return@configure

                if (project.name.endsWith("-bom")) {
                    if (publishing.publications.names.contains("bomPublication")) {
                        sign(publishing.publications["bomPublication"])
                    }
                } else {
                    if (publishing.publications.names.contains("mavenJava")) {
                        sign(publishing.publications["mavenJava"])
                    }
                }
            }
        }
    }

    if (releasePlanPresent && project.name != bomName && project.name !in configuredSelectedReleaseModules) {
        tasks.withType<PublishToMavenRepository>().configureEach {
            onlyIf("module is not selected by the prepared release plan") { false }
        }
    }

    if (ciReleaseBundleMode) {
        tasks.withType<Sign>().configureEach {
            onlyIf("signing is deferred to the protected release workflow") { false }
        }
    }

    // set the project description after evaluation because it is not yet visible when the POM is first created
    afterEvaluate {
        if (pluginManager.hasPlugin("maven-publish")) {
            project.extensions.configure<PublishingExtension> {
                publications.withType<MavenPublication> {
                    pom {
                        if (description.orNull.isNullOrBlank()) {
                            description.set(project.description ?: "No description provided")
                        }
                    }
                }
            }
        }
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-Xlint:unchecked")
        options.compilerArgs.add("-Xlint:deprecation")
    }

    // SpotBugs for non-BOM projects
    if (!project.name.endsWith("-bom") && pluginManager.hasPlugin("com.github.spotbugs")) {

        // === SPOTBUGS ===
        configure<com.github.spotbugs.snom.SpotBugsExtension> {
            excludeFilter.set(project.file("spotbugs-exclude.xml"))
        }

        tasks.named<com.github.spotbugs.snom.SpotBugsTask>("spotbugsMain") {
            reports.create("html") {
                required.set(true)
                outputLocation.set(layout.buildDirectory.file("reports/spotbugs/main.html"))
                setStylesheet("fancy-hist.xsl")
            }
        }

        tasks.named<com.github.spotbugs.snom.SpotBugsTask>("spotbugsTest") {
            reports.create("html") {
                required.set(true)
                outputLocation.set(layout.buildDirectory.file("reports/spotbugs/test.html"))
                setStylesheet("fancy-hist.xsl")
            }
        }
    }

    // configure the versions plugin
    fun isStable(version: String): Boolean {
        val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
        val regex = "[0-9,.v-]+-(rc|ea|alpha|beta|b|M|SNAPSHOT)([+-]?[0-9]*)?".toRegex(RegexOption.IGNORE_CASE)
        return stableKeyword || !regex.matches(version)
    }

    tasks.withType<DependencyUpdatesTask> {
        // refuse non-stable versions
        rejectVersionIf {
            !isStable(candidate.version)
        }

        // dependencyUpdates fails in parallel mode with Gradle 9+ (https://github.com/ben-manes/gradle-versions-plugin/issues/968)
        doFirst {
            gradle.startParameter.isParallelProjectExecutionEnabled = false
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// Release planning, staging, and finalization
/////////////////////////////////////////////////////////////////////////////

private fun validatePreparedReleasePlan(plan: PreparedReleasePlan) {
    check(plan.releaseType in setOf("patch", "minor", "major")) {
        "unsupported prepared release type: ${plan.releaseType}"
    }
    parseSemanticVersion(plan.bomVersion)
    check(runGit("merge-base", "--is-ancestor", plan.sourceRevision, "HEAD").exitValue == 0) {
        "prepared release source revision is not an ancestor of HEAD: ${plan.sourceRevision}"
    }
    val selectedModules = plan.modules.filterValues { it.selected }.keys
    check(selectedModules.isNotEmpty()) { "prepared release plan does not select any library module" }
    check(selectedModules == configuredSelectedReleaseModules) {
        "prepared release plan does not match the modules selected during Gradle configuration"
    }
    check(effectiveBomVersion == plan.bomVersion) {
        "configured BOM version $effectiveBomVersion does not match prepared plan ${plan.bomVersion}"
    }
    val publishedState = readPublishedReleaseState(releaseStateFile)
    publishableModuleNames.forEach { moduleName ->
        val planned = plan.modules.getValue(moduleName)
        check(configuredReleaseModuleVersions[moduleName] == planned.version) {
            "configured version for $moduleName does not match the prepared release plan"
        }
        if (planned.selected) {
            check(planned.sourceRevision == plan.sourceRevision) {
                "selected module $moduleName does not use the plan source revision"
            }
        } else {
            val published = publishedState.modules.getValue(moduleName)
            check(planned.version == published.version && planned.sourceRevision == published.publishedRevision) {
                "retained module $moduleName does not match published release state"
            }
        }
    }
}

private fun renderReleasePlan(plan: PreparedReleasePlan) = buildString {
    appendLine("Selective release plan")
    appendLine("  type: ${plan.releaseType}")
    appendLine("  source revision: ${plan.sourceRevision}")
    appendLine("  BOM: $bomName:${plan.bomVersion}")
    appendLine("  modules to publish:")
    plan.modules.filterValues { it.selected }.forEach { (moduleName, module) ->
        appendLine("    $moduleName:${module.version} (${module.reason})")
    }
    appendLine("  retained modules:")
    plan.modules.filterValues { !it.selected }.forEach { (moduleName, module) ->
        appendLine("    $moduleName:${module.version}")
    }
}

tasks.register<PrepareReleaseTask>("prepareRelease") {
    group = "release"
    description = "Plans a selective release; add -PconfirmRelease=true to write gradle/prepared-release.toml."
    repositoryDirectory.set(layout.projectDirectory)
    releaseStateFile.set(layout.projectDirectory.file("gradle/release-state.toml"))
    preparedReleasePlanPath.set(layout.projectDirectory.file("gradle/prepared-release.toml").asFile.absolutePath)
    releaseType.convention(providers.gradleProperty("releaseType").orElse(""))
    requestedReleaseVersion.convention(providers.gradleProperty("releaseVersion").orElse(""))
    additionalReleaseModules.convention(providers.gradleProperty("additionalReleaseModules").orElse(""))
    confirmRelease.convention(providers.gradleProperty("confirmRelease").map { it == "true" }.orElse(false))
}

tasks.register("verifyPreparedRelease") {
    group = "release"
    description = "Validates the persisted prepared release plan and configured module versions."
    notCompatibleWithConfigurationCache("Release-plan validation reads Git state at task execution time.")
    doLast {
        check(releasePlanPresent) {
            "no prepared release plan exists at ${preparedReleasePlanFile.path}; run prepareRelease first"
        }
        validatePreparedReleasePlan(readPreparedReleasePlan(preparedReleasePlanFile))
        logger.lifecycle("Prepared release plan is valid.")
    }
}

tasks.register("printPreparedReleasePlan") {
    group = "release"
    description = "Prints the persisted prepared release plan."
    notCompatibleWithConfigurationCache("The task reads the persisted release plan at task execution time.")
    doLast {
        check(releasePlanPresent) { "no prepared release plan exists at ${preparedReleasePlanFile.path}" }
        logger.lifecycle(renderReleasePlan(readPreparedReleasePlan(preparedReleasePlanFile)))
    }
}

private val japicmpTool = configurations.create("japicmpTool") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    add(japicmpTool.name, "com.github.siom79.japicmp:japicmp:0.26.1:jar-with-dependencies") {
        isTransitive = false
    }
}

private fun downloadPublishedModuleJar(moduleName: String, version: String): File {
    val target = layout.buildDirectory.file("release-compatibility/$moduleName-$version.jar").get().asFile
    if (target.isFile) return target
    target.parentFile.mkdirs()
    val path = "${Meta.GROUP.replace('.', '/')}/$moduleName/$version/$moduleName-$version.jar"
    URI("https://repo1.maven.org/maven2/$path").toURL().openStream().use { input ->
        Files.copy(input, target.toPath())
    }
    return target
}

tasks.register("checkReleaseCompatibility") {
    group = "verification"
    description = "Checks selected patch-release modules against their last published binary API."
    notCompatibleWithConfigurationCache(
        "Compatibility verification resolves published artifacts and invokes the external japicmp process."
    )
    if (releasePlanPresent && !prebuiltReleaseBundleMode) {
        dependsOn(configuredSelectedReleaseModules.map { moduleName ->
            "${moduleProjectPaths.getValue(moduleName)}:jar".replace("::", ":")
        })
    }
    doLast {
        check(releasePlanPresent) {
            "no prepared release plan exists at ${preparedReleasePlanFile.path}; run prepareRelease first"
        }
        val plan = readPreparedReleasePlan(preparedReleasePlanFile)
        if (plan.releaseType != "patch") {
            logger.lifecycle("Skipping binary compatibility enforcement for ${plan.releaseType} release.")
            return@doLast
        }
        val state = readPublishedReleaseState(releaseStateFile)
        val tool = japicmpTool.singleFile
        plan.modules.filterValues { it.selected }.forEach { (moduleName, module) ->
            val oldVersion = state.modules.getValue(moduleName).version
            val oldJar = downloadPublishedModuleJar(moduleName, oldVersion)
            val newJar = if (prebuiltReleaseBundleMode) {
                stagedReleaseFile(moduleName, module.version, "$moduleName-${module.version}.jar")
            } else {
                project(moduleProjectPaths.getValue(moduleName)).layout.buildDirectory
                    .file("libs/$moduleName-${module.version}.jar").get().asFile
            }
            check(newJar.isFile) { "candidate artifact was not built: ${newJar.path}" }
            logger.lifecycle("Checking binary compatibility: $moduleName $oldVersion -> ${module.version}")
            val javaExecutable = File(System.getProperty("java.home"), "bin/java").absolutePath
            val process = ProcessBuilder(
                javaExecutable, "-jar", tool.absolutePath,
                "--old", oldJar.absolutePath,
                "--new", newJar.absolutePath,
                "--only-modified",
                "--error-on-binary-incompatibility",
                "--error-on-source-incompatibility",
                "--ignore-missing-classes"
            ).inheritIO().start()
            check(process.waitFor() == 0) { "binary compatibility check failed for $moduleName" }
        }
    }
}

private fun writePublishedReleaseState(plan: PreparedReleasePlan, previous: PublishedReleaseState) {
    val content = buildString {
        appendLine("[release]")
        appendLine("schemaVersion = 1")
        appendLine("bomVersion = \"${tomlString(plan.bomVersion)}\"")
        publishableModuleNames.forEach { moduleName ->
            val old = previous.modules.getValue(moduleName)
            val planned = plan.modules.getValue(moduleName)
            val version = if (planned.selected) planned.version else old.version
            val revision = if (planned.selected) planned.sourceRevision else old.publishedRevision
            appendLine()
            appendLine("[modules.$moduleName]")
            appendLine("version = \"${tomlString(version)}\"")
            appendLine("publishedRevision = \"${tomlString(revision)}\"")
            appendLine("paths = [${old.paths.joinToString(", ") { "\"${tomlString(it)}\"" }}]")
        }
    }
    Files.writeString(releaseStateFile.toPath(), content, StandardCharsets.UTF_8)
}

private fun nextDevelopmentVersion(releaseVersion: String): String {
    val parsed = parseSemanticVersion(releaseVersion)
    return SemanticVersion(parsed.major, parsed.minor, parsed.patch + 1).toString() + "-SNAPSHOT"
}

private fun writeDevelopmentVersion(version: String) {
    val catalog = rootProject.file("gradle/libs.versions.toml")
    val previous = Files.readString(catalog.toPath(), StandardCharsets.UTF_8)
    val pattern = Regex("""(?m)^(\s*projectVersion\s*=\s*")[^"]+("\s*(?:#.*)?)$""")
    check(pattern.containsMatchIn(previous)) { "projectVersion declaration not found in ${catalog.path}" }
    Files.writeString(catalog.toPath(), previous.replace(pattern, "$1$version$2"), StandardCharsets.UTF_8)
}

val cleanPreparedReleaseStaging = tasks.register<Delete>("cleanPreparedReleaseStaging") {
    group = "release"
    description = "Removes stale staging artifacts before a prepared release is staged."
    delete(layout.buildDirectory.dir("staging-deploy"))
}

val aggregateStaging = tasks.named("publishToStagingDirectory") {
    group = "publishing"
    description = "Publishes the BOM and eligible library modules to the root staging directory."
}

gradle.projectsEvaluated {
    val eligibleModules = if (releasePlanPresent) configuredSelectedReleaseModules else publishableModuleNames.toSet()
    aggregateStaging.configure {
        mustRunAfter(cleanPreparedReleaseStaging)
        dependsOn(
            (eligibleModules + bomName)
                .filter { it != rootProject.name }
                .map { moduleName ->
                    project(if (moduleName == bomName) ":$bomName" else moduleProjectPaths.getValue(moduleName))
                        .tasks.named("publishToStagingDirectory")
                }
        )
    }
    allprojects.forEach { releaseProject ->
        releaseProject.tasks.withType<PublishToMavenRepository>().configureEach {
            mustRunAfter(cleanPreparedReleaseStaging)
        }
    }
}

private val releaseBundleStagingDirectory = layout.buildDirectory.dir("staging-deploy").get().asFile
private val releaseBundleDirectory = layout.buildDirectory.dir("release-bundle").get().asFile
private val releaseBundleManifest = releaseBundleDirectory.resolve("manifest.sha256")
private val releaseBundleMetadata = releaseBundleDirectory.resolve("metadata.properties")
private val releaseBundleGroupPath = Meta.GROUP.replace('.', '/')

private fun releaseBundleRelativePath(root: File, file: File): String =
    root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')

private fun releaseBundleFiles(root: File): List<File> =
    if (root.isDirectory) root.walkTopDown().filter(File::isFile)
        .sortedBy { releaseBundleRelativePath(root, it) }.toList() else emptyList()

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun stagedReleaseFile(moduleName: String, version: String, filename: String): File =
    releaseBundleStagingDirectory.resolve("$releaseBundleGroupPath/$moduleName/$version/$filename")

private fun writeReleaseBundleMetadata(plan: PreparedReleasePlan) {
    releaseBundleDirectory.mkdirs()
    Files.writeString(
        releaseBundleMetadata.toPath(),
        buildString {
            appendLine("commit=${requireGitSuccess("resolving release bundle commit", "rev-parse", "HEAD")}")
            appendLine("planSourceRevision=${plan.sourceRevision}")
            appendLine("bomVersion=${plan.bomVersion}")
            appendLine("selectedModules=${configuredSelectedReleaseModules.sorted().joinToString(",")}")
        },
        StandardCharsets.UTF_8
    )
}

private fun writeReleaseBundleManifest() {
    Files.writeString(
        releaseBundleManifest.toPath(),
        buildString {
            releaseBundleFiles(releaseBundleStagingDirectory).forEach { file ->
                append(sha256(file))
                append("  ")
                appendLine("staging-deploy/${releaseBundleRelativePath(releaseBundleStagingDirectory, file)}")
            }
        },
        StandardCharsets.UTF_8
    )
}

private fun validateReleaseBundleContents(plan: PreparedReleasePlan) {
    check(releaseBundleStagingDirectory.isDirectory) {
        "release bundle staging directory is missing: ${releaseBundleStagingDirectory.path}"
    }
    check(releaseBundleMetadata.isFile) { "release bundle metadata is missing: ${releaseBundleMetadata.path}" }
    val metadata = Properties().apply {
        releaseBundleMetadata.inputStream().use { load(it) }
    }
    check(metadata.getProperty("commit") == requireGitSuccess("resolving release bundle commit", "rev-parse", "HEAD")) {
        "release bundle was built from a different Git revision"
    }
    check(metadata.getProperty("planSourceRevision") == plan.sourceRevision)
    check(metadata.getProperty("bomVersion") == plan.bomVersion)
    check(metadata.getProperty("selectedModules") == configuredSelectedReleaseModules.sorted().joinToString(","))

    val files = releaseBundleFiles(releaseBundleStagingDirectory)
    check(files.none { it.name.endsWith(".asc") }) {
        "CI release bundles must be unsigned; signatures are added only by the protected release workflow"
    }
    val manifest = releaseBundleManifest.readLines(StandardCharsets.UTF_8)
        .filter { it.isNotBlank() }
        .associate { line ->
            val separator = line.indexOf("  ")
            check(separator == 64) { "invalid release bundle manifest line: $line" }
            line.substring(separator + 2).removePrefix("staging-deploy/") to line.substring(0, separator)
        }
    val actualPaths = files.map { releaseBundleRelativePath(releaseBundleStagingDirectory, it) }.toSet()
    check(manifest.keys == actualPaths) { "release bundle manifest does not match staging directory contents" }
    manifest.forEach { (path, digest) ->
        check(sha256(releaseBundleStagingDirectory.resolve(path)) == digest) {
            "release bundle checksum mismatch: $path"
        }
    }

    fun requireFile(moduleName: String, version: String, filename: String) {
        check(stagedReleaseFile(moduleName, version, filename).isFile) {
            "release bundle is missing $moduleName:$version/$filename"
        }
    }
    requireFile(bomName, plan.bomVersion, "$bomName-${plan.bomVersion}.pom")
    requireFile(bomName, plan.bomVersion, "$bomName-${plan.bomVersion}.module")
    plan.modules.filterValues { it.selected }.forEach { (moduleName, module) ->
        val artifact = "$moduleName-${module.version}"
        listOf("$artifact.jar", "$artifact-sources.jar", "$artifact-javadoc.jar", "$artifact.pom", "$artifact.module")
            .forEach { requireFile(moduleName, module.version, it) }
    }
}

val prepareCiReleaseBundle = tasks.register("prepareCiReleaseBundle") {
    group = "release"
    description = "Creates an unsigned, checksummed Maven bundle from CI build outputs."
    notCompatibleWithConfigurationCache("The CI bundle records Git state and writes a manifest.")
    dependsOn("verifyPreparedRelease", cleanPreparedReleaseStaging, aggregateStaging)
    doLast {
        check(ciReleaseBundleMode) { "prepareCiReleaseBundle requires -PciReleaseBundle=true" }
        check(releasePlanPresent) { "prepareCiReleaseBundle requires gradle/prepared-release.toml" }
        releaseBundleDirectory.deleteRecursively()
        val plan = readPreparedReleasePlan(preparedReleasePlanFile)
        writeReleaseBundleMetadata(plan)
        writeReleaseBundleManifest()
        validateReleaseBundleContents(plan)
        logger.lifecycle("Prepared unsigned CI release bundle at ${releaseBundleStagingDirectory.path}")
    }
}

val verifyCiReleaseBundle = tasks.register("verifyCiReleaseBundle") {
    group = "release"
    description = "Verifies the checksummed Maven bundle produced by a successful CI run."
    notCompatibleWithConfigurationCache("The CI bundle and Git revision are external promotion inputs.")
    doLast {
        check(prebuiltReleaseBundleMode) { "verifyCiReleaseBundle requires -PprebuiltReleaseBundle=true" }
        check(releasePlanPresent) { "verifyCiReleaseBundle requires gradle/prepared-release.toml" }
        validateReleaseBundleContents(readPreparedReleasePlan(preparedReleasePlanFile))
        logger.lifecycle("Verified CI release bundle.")
    }
}

tasks.named("checkReleaseCompatibility") {
    mustRunAfter(verifyCiReleaseBundle)
}

tasks.register("publishSnapshotsToMavenLocal") {
    group = "publishing"
    description = "Publishes every library module and the BOM to the local Maven repository."
    onlyIf { isSnapshot }
    dependsOn(publishableModuleNames.map { "${moduleProjectPaths.getValue(it)}:publishToMavenLocal".replace("::", ":") } +
        ":$bomName:publishToMavenLocal")
}

val stagePreparedRelease = tasks.register("stagePreparedRelease") {
    group = "release"
    description = "Builds, verifies, and stages only artifacts selected by the prepared release plan."
    dependsOn("verifyPreparedRelease", "checkReleaseCompatibility", cleanPreparedReleaseStaging)
    if (releasePlanPresent) {
        dependsOn(publishableModuleNames.map { "${moduleProjectPaths.getValue(it)}:check".replace("::", ":") })
        dependsOn(configuredSelectedReleaseModules.map {
            "${moduleProjectPaths.getValue(it)}:publishToStagingDirectory".replace("::", ":")
        })
        dependsOn(":$bomName:publishToStagingDirectory")
    }
}

val jreleaserDeploy = tasks.named("jreleaserDeploy")

tasks.register("publishPreparedRelease") {
    group = "release"
    description = "Deploys the verified prepared release to Maven Central."
    dependsOn(stagePreparedRelease, jreleaserDeploy)
}

tasks.register("publishPreparedReleaseFromCi") {
    group = "release"
    description = "Signs and deploys the exact publication bundle verified by CI."
    dependsOn("verifyPreparedRelease", verifyCiReleaseBundle, "checkReleaseCompatibility", jreleaserDeploy)
}

jreleaserDeploy.configure {
    mustRunAfter(stagePreparedRelease, verifyCiReleaseBundle, "verifyPreparedRelease", "checkReleaseCompatibility")
}

tasks.register("finalizeRelease") {
    group = "release"
    description = "Records a successfully deployed prepared release and creates its Git tag."
    notCompatibleWithConfigurationCache("Finalization verifies Maven Central and modifies Git state.")
    doLast {
        check(providers.gradleProperty("confirmFinalize").orNull == "true") {
            "re-run with -PconfirmFinalize=true after verifying Maven Central deployment"
        }
        check(preparedReleasePlanFile.isFile) {
            "no prepared release plan exists at ${preparedReleasePlanFile.path}"
        }
        check(runGit("status", "--porcelain").output.isBlank()) {
            "the Git working tree must be clean before finalizing a release"
        }
        check(runGit("ls-files", "--error-unmatch", preparedReleasePlanFile.relativeTo(rootDir).path).exitValue == 0) {
            "the prepared release plan must be committed before finalization"
        }

        val plan = readPreparedReleasePlan(preparedReleasePlanFile)
        validatePreparedReleasePlan(plan)
        val expectedCoordinates = buildList {
            add(bomName to plan.bomVersion)
            plan.modules.filterValues { it.selected }
                .forEach { (moduleName, module) -> add(moduleName to module.version) }
        }
        expectedCoordinates.forEach { (artifactId, version) ->
            check(isMavenCentralCoordinatePublished(artifactId, version)) {
                "Maven Central does not yet expose expected artifact $artifactId:$version"
            }
        }

        val tagName = "v${plan.bomVersion}"
        check(runGit("rev-parse", "-q", "--verify", "refs/tags/$tagName").exitValue != 0) {
            "final release tag already exists: $tagName"
        }
        writePublishedReleaseState(plan, readPublishedReleaseState(releaseStateFile))
        writeDevelopmentVersion(nextDevelopmentVersion(plan.bomVersion))
        Files.delete(preparedReleasePlanFile.toPath())
        requireGitSuccess(
            "staging finalized release state",
            "add",
            releaseStateFile.relativeTo(rootDir).path,
            "gradle/libs.versions.toml",
            preparedReleasePlanFile.relativeTo(rootDir).path
        )
        requireGitSuccess("committing finalized release state", "commit", "-m", "Release ${plan.bomVersion}")
        requireGitSuccess("creating final release tag", "tag", "-a", tagName, "-m", "Release ${plan.bomVersion}")

        if (providers.gradleProperty("pushReleaseTag").orNull == "true") {
            val branch = providers.gradleProperty("releaseBranch").orNull
                ?: runGit("branch", "--show-current").output
            check(branch.isNotBlank()) { "supply -PreleaseBranch=<protected branch> when detached" }
            requireGitSuccess("pushing finalized release commit", "push", "origin", "HEAD:refs/heads/$branch")
            requireGitSuccess("pushing final release tag", "push", "origin", tagName)
        }
        logger.lifecycle("Finalized release ${plan.bomVersion} with tag $tagName.")
    }
}

jreleaser {
    project {
        name.set(Meta.ORGANIZATION_NAME)
        version.set(effectiveBomVersion)
        group = Meta.GROUP
        authors.set(listOf(Meta.DEVELOPER_NAME))
        license.set(Meta.LICENSE_NAME)
        links {
            homepage.set(Meta.ORGANIZATION_URL)
        }
        inceptionYear.set(Meta.INCEPTION_YEAR)
        gitRootSearch.set(true)
    }

    signing {
        active.set(org.jreleaser.model.Active.ALWAYS)
        pgp {
            armored.set(true)
            secretKey.set(System.getenv("SIGNING_SECRET_KEY"))
            passphrase.set(System.getenv("SIGNING_PASSWORD"))
        }
    }

    deploy {
        maven {
            if (!isSnapshot) {
                mavenCentral {
                    create("release-deploy") {
                        active.set(org.jreleaser.model.Active.RELEASE)
                        url.set("https://central.sonatype.com/api/v1/publisher")
                        stagingRepositories.add("build/staging-deploy")
                        username.set(System.getenv("SONATYPE_USERNAME"))
                        password.set(System.getenv("SONATYPE_PASSWORD"))
                        connectTimeout.set(300)
                        readTimeout.set(300)
                        // skipExisting.set(true)
                    }
                }
            } else {
                nexus2 {
                    create("snapshot-deploy") {
                        active.set(org.jreleaser.model.Active.SNAPSHOT)
                        snapshotUrl.set("https://central.sonatype.com/repository/maven-snapshots/")
                        applyMavenCentralRules.set(true)
                        snapshotSupported.set(true)
                        closeRepository.set(true)
                        releaseRepository.set(true)
                        stagingRepositories.add("build/staging-deploy")
                        username.set(System.getenv("SONATYPE_USERNAME"))
                        password.set(System.getenv("SONATYPE_PASSWORD"))
                        connectTimeout.set(300)
                        readTimeout.set(300)
                    }
                }
            }
        }
    }
}
