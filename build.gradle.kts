import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.kotlin.dsl.withType

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

object Meta {
    const val VERSION = "0.12.0"
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

allprojects {
    group = Meta.GROUP
    version = Meta.VERSION
}

group = Meta.GROUP
version = Meta.VERSION

// check for development/release version
fun isDevelopmentVersion(versionString: String): Boolean {
    val v = versionString.lowercase()
    val markers = listOf("snapshot", "alpha", "beta")
    return markers.any { marker -> v.contains("-$marker") || v.contains(".$marker") }
}

val isReleaseVersion = !isDevelopmentVersion(project.version.toString())
val isSnapshot = project.version.toString().lowercase().contains("snapshot")

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

val jacocoTestReport by tasks.getting(JacocoReport::class) {
    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")).include("*.exec"))
}

allprojects {
    if (!project.name.contains("benchmark")) {
        apply(plugin = "com.dua3.gradle.jdkprovider")

        jdk {
            version = "21.0.9+"
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

            // Publications for non-BOM projects
            if (!project.name.endsWith("-bom")) {
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
        val publishToStagingDirectory by tasks.registering {
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
                isRequired = shouldSign

                if (shouldSign) {
                    useInMemoryPgpKeys(
                        System.getenv("JRELEASER_GPG_SECRET_KEY"),
                        System.getenv("JRELEASER_GPG_PASSPHRASE")
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

jreleaser {
    project {
        name.set(Meta.ORGANIZATION_NAME)
        version.set(Meta.VERSION)
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
            publicKey.set(System.getenv("JRELEASER_GPG_PUBLIC_KEY"))
            secretKey.set(System.getenv("JRELEASER_GPG_SECRET_KEY"))
            passphrase.set(System.getenv("JRELEASER_GPG_PASSPHRASE"))
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
                        username.set(System.getenv("JRELEASER_SONATYPE_USERNAME"))
                        password.set(System.getenv("JRELEASER_SONATYPE_PASSWORD"))
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
                        username.set(System.getenv("JRELEASER_SONATYPE_USERNAME"))
                        password.set(System.getenv("JRELEASER_SONATYPE_PASSWORD"))
                        connectTimeout.set(300)
                        readTimeout.set(300)
                    }
                }
            }
        }
    }
}