plugins {
    id("java-platform")
    id("maven-publish")
    id("signing")
}

description = "Bill of materials for SLB4J"

@Suppress("UNCHECKED_CAST")
val releaseModuleVersions = gradle.extra["releaseModuleVersions"] as Map<String, String>

dependencies {
    constraints {
        releaseModuleVersions.forEach { (moduleName, moduleVersion) ->
            api("${project.group}:$moduleName:$moduleVersion")
        }
    }
}
