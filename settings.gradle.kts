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
@file:Suppress("UnstableApiUsage")

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

private data class ReleaseVersions(
    val bomVersion: String,
    val moduleVersions: Map<String, String>,
    val selectedModules: Set<String>
)

private fun readReleaseVersions(file: File, requireSelection: Boolean): ReleaseVersions {
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
            require(section.isNotEmpty()) { "value outside a TOML table in ${file.path}: $line" }
            values.getOrPut(section) { mutableMapOf() }[it.groupValues[1]] =
                it.groupValues[2].trim().removeSurrounding("\"")
            return@forEachLine
        }
        throw GradleException("unsupported release TOML syntax in ${file.path}: $line")
    }

    val release = values["release"] ?: throw GradleException("[release] table missing from ${file.path}")
    val bomVersion = release["bomVersion"] ?: throw GradleException("release.bomVersion missing from ${file.path}")
    val moduleVersions = publishableModuleNames.associateWith { moduleName ->
        values["modules.$moduleName"]?.get("version")
            ?: throw GradleException("modules.$moduleName.version missing from ${file.path}")
    }
    val selectedModules = if (requireSelection) {
        publishableModuleNames.filter { moduleName ->
            values["modules.$moduleName"]?.get("selected")?.toBooleanStrictOrNull()
                ?: throw GradleException("modules.$moduleName.selected missing or invalid in ${file.path}")
        }.toSet()
    } else {
        emptySet()
    }

    return ReleaseVersions(bomVersion, moduleVersions, selectedModules)
}

private fun versionCatalogVersion(alias: String): String {
    val catalog = file("gradle/libs.versions.toml")
    val versions = catalog.readLines()
        .dropWhile { it.trim() != "[versions]" }
        .drop(1)
        .takeWhile { !it.trim().startsWith("[") }
    val declaration = Regex("""^\s*${Regex.escape(alias)}\s*=\s*"([^"]+)"""")
    return versions.firstNotNullOfOrNull { declaration.matchEntire(it)?.groupValues?.get(1) }
        ?: throw GradleException("version '$alias' not found in ${catalog.path}")
}

rootProject.name = "slb4j"

private val developmentVersion = versionCatalogVersion("projectVersion")
private val releaseStateFile = file("gradle/release-state.toml")
private val publishedRelease = readReleaseVersions(releaseStateFile, requireSelection = false)
private val preparedReleasePlanFile = file("gradle/prepared-release.toml")
private val preparedRelease = preparedReleasePlanFile.takeIf(File::isFile)?.let {
    readReleaseVersions(it, requireSelection = true)
}
private val effectiveBomVersion = preparedRelease?.bomVersion ?: developmentVersion
private val effectiveModuleVersions = preparedRelease?.moduleVersions
    ?: publishableModuleNames.associateWith { developmentVersion }

gradle.extra["releaseStateFile"] = releaseStateFile
gradle.extra["publishedReleaseBomVersion"] = publishedRelease.bomVersion
gradle.extra["publishedReleaseModuleVersions"] = publishedRelease.moduleVersions
gradle.extra["preparedReleasePlanFile"] = preparedReleasePlanFile
gradle.extra["releaseBomVersion"] = effectiveBomVersion
gradle.extra["releaseModuleVersions"] = effectiveModuleVersions
gradle.extra["releasePlanPresent"] = preparedRelease != null
gradle.extra["releaseSelectedModules"] = preparedRelease?.selectedModules ?: emptySet<String>()

include("benchmark")
include("samples:jul")
include("samples:jcl")
include("samples:log4j")
include("samples:slf4j")
include("samples:all")
include("slb4j-ext")
include("slb4j-ext:slb4j-ext-fx")
include("slb4j-ext:slb4j-ext-fx:samples")
include("slb4j-ext:slb4j-ext-swing")
include("slb4j-ext:slb4j-ext-swing:samples")

include("native:slb4j-native-test")
include("native:slb4j-fx-native-test")

include("benchmark:benchmark-jul")
include("benchmark:benchmark-logback")
include("benchmark:benchmark-log4j")
include("benchmark:benchmark-slb4j")
include("slb4j-ext:slb4j-ext-layouts")

include("slb4j-config")
include("slb4j-config:slb4j-config-xml")
include("slb4j-config:slb4j-config-json")
include("slb4j-config:slb4j-config-yaml")
include("slb4j-config:slb4j-config-all")
include("slb4j-bom")

gradle.projectsLoaded {
    rootProject.allprojects {
        version = when {
            name == "slb4j-bom" -> effectiveBomVersion
            name in publishableModuleNames -> effectiveModuleVersions.getValue(name)
            else -> developmentVersion
        }
    }
}
