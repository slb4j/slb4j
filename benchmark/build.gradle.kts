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
}

description = "SLB4J performance benchmarks"

sourceSets {
    main {
        java {
            srcDir("src/jmh/java")
        }
    }
}

dependencies {
    implementation(libs.jmh.core)
    implementation(platform(libs.log4j.bom))
    implementation(libs.slf4j.api)
    implementation(libs.log4j.api)
    implementation(libs.jcl)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

subprojects {
    plugins.withId("me.champeau.jmh") {
        val jmhSources = extensions.getByType<SourceSetContainer>().named("jmh")
        val benchmarkClass = providers.gradleProperty("sampleBenchmark")
        val directory = providers.gradleProperty("sampleDirectory")
        val formats = providers.gradleProperty("sampleFormats")
        val frontends = providers.gradleProperty("sampleFrontends")
        val usesLog4jJulBridge = name == "benchmark-log4j"

        fun registerLauncher(taskName: String, sampleFormats: Provider<String>) = tasks.register<JavaExec>(taskName) {
            classpath = files(jmhSources.map { it.runtimeClasspath })
            mainClass.set("org.slb4j.benchmark.SampleOutput")
            argumentProviders.add { listOf(benchmarkClass.get(), directory.get(), sampleFormats.get(), frontends.get()) }
            if (usesLog4jJulBridge) {
                systemProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager")
            }
        }

        val sampleOutput = if (name == "benchmark-rainbowgum") {
            // Rainbow Gum's bridges can retain loggers from the first configuration.
            val launchers = listOf("Compact", "Default", "Detailed").associate { suffix ->
                val format = suffix.uppercase()
                format to registerLauncher("sampleOutput$suffix", providers.provider { format })
            }
            tasks.register("sampleOutput") {
                dependsOn(formats.map { selected -> selected.split(",").map { launchers.getValue(it) } })
            }
        } else {
            registerLauncher("sampleOutput", formats)
        }
        sampleOutput.configure {
            description = "Generate example console output without running JMH."
        }
    }
}
