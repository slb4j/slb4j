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
import org.gradle.internal.impldep.com.google.common.math.LinearTransformation.vertical

plugins {
    id("java")
    alias(libs.plugins.jmh)
}

description = "SLB4J performance benchmarks"

dependencies {
    // JMH
    annotationProcessor(libs.jmh.generator)
    implementation(libs.jmh.core)

    implementation(rootProject)

    // We also need them for JMH compilation
    jmh(platform(libs.log4j.bom))
    jmh(libs.slf4j.api)
    jmh(libs.log4j.api)
    jmh(libs.commons.logging)
    jmh(libs.log4j.core)
    jmh(libs.logback.classic)
    jmh(rootProject)
    // Add bridges to JMH configuration for compilation of all benchmark classes
    jmh(libs.log4j.slf4j2)
    jmh(libs.log4j.jcl)
    jmh(libs.log4j.jul)
    jmh(libs.slf4j.jdk14)
    jmh(libs.slf4j.jcl)
    jmh(libs.slf4j.jul)
    jmh(libs.log4j.to.slf4j)

    // Use separate configurations for each backend to collect their runtime dependencies
    val log4jRuntime by configurations.creating
    val logbackRuntime by configurations.creating
    val julRuntime by configurations.creating
    val slb4jRuntime by configurations.creating

    dependencies {
        log4jRuntime(libs.slf4j.api)
        log4jRuntime(platform(libs.log4j.bom))
        log4jRuntime(libs.log4j.api)
        log4jRuntime(libs.commons.logging)
        log4jRuntime(libs.log4j.core)
        log4jRuntime(libs.log4j.slf4j2) // slf4j to log4j
        log4jRuntime(libs.log4j.jcl)    // jcl to log4j
        log4jRuntime(libs.log4j.jul)    // jul to log4j

        logbackRuntime(libs.slf4j.api)
        logbackRuntime(platform(libs.log4j.bom))
        logbackRuntime(libs.log4j.api)
        logbackRuntime(libs.commons.logging)
        logbackRuntime(libs.logback.classic)
        logbackRuntime(libs.slf4j.jcl)    // jcl to slf4j
        logbackRuntime(libs.slf4j.jul)    // jul to slf4j
        logbackRuntime(libs.log4j.to.slf4j) // log4j to slf4j

        julRuntime(libs.slf4j.api)
        julRuntime(platform(libs.log4j.bom))
        julRuntime(libs.log4j.api)
        julRuntime(libs.commons.logging)
        julRuntime(libs.slf4j.jdk14) // slf4j to jul

        slb4jRuntime(libs.slf4j.api)
        slb4jRuntime(platform(libs.log4j.bom))
        slb4jRuntime(libs.log4j.api)
        slb4jRuntime(libs.commons.logging)
        slb4jRuntime(rootProject)
    }
}

jmh {
    val backendVal = project.findProperty("backend")?.toString() ?: "slb4j"

    // Get the JARs for the selected backend
    val runtimeConfig = when (backendVal) {
        "log4j" -> configurations.getByName("log4jRuntime")
        "logback" -> configurations.getByName("logbackRuntime")
        "jul" -> configurations.getByName("julRuntime")
        "slb4j" -> configurations.getByName("slb4jRuntime")
        else -> configurations.getByName("slb4jRuntime")
    }

    // Filter the JMH configuration to remove unwanted logging backends
    val jmhJars = project.configurations.getByName("jmh").files.filter { file ->
        val name = file.name
        !name.contains("logback-classic") &&
                !name.contains("logback-core") &&
                !name.contains("log4j-core") &&
                !name.contains("slf4j-jdk14") &&
                !name.contains("slf4j-jcl") &&
                !name.contains("slf4j-jul") &&
                !name.contains("log4j-slf4j") &&
                !name.contains("log4j-jcl") &&
                !name.contains("log4j-jul") &&
                !name.contains("jcl-over-slf4j") &&
                !name.contains("jul-to-slf4j") &&
                !name.contains("log4j-to-slf4j") &&
                !name.contains("slb4j")
    }

    // The benchmark classes
    val benchmarkClasses = project.tasks.getByName("jmhJar").outputs.files

    val allJarsList = (jmhJars + runtimeConfig.files + benchmarkClasses)

    // We must ensure JMH uses the correct classpath. 
    // Since the plugin doesn't make it easy, we use a custom task to run JMH.

    jvmArgs.add("-Djmh.ignoreLock=true")
    iterations.set(project.findProperty("iterations")?.toString()?.toInt() ?: 3)
    fork.set(1)
    timeOnIteration = project.findProperty("timeOnIteration")?.toString() ?: "1s"
    resultFormat.set("JSON")
    duplicateClassesStrategy.set(DuplicatesStrategy.EXCLUDE)
    failOnError.set(true)
    forceGC.set(true)

    val includesProp = project.findProperty("jmh.includes")?.toString()
    if (includesProp != null) {
        includes.addAll(includesProp.split(","))
    }

    val outputToFile = project.findProperty("outputToFile")?.toString() ?: "false"
    val messageType = project.findProperty("messageType")?.toString() ?: "CONSTANT,ARGUMENTS,LAMBDA"
    benchmarkParameters.put(
        "outputToFile",
        project.objects.listProperty(String::class.java).value(listOf(outputToFile))
    )
    benchmarkParameters.put("backend", project.objects.listProperty(String::class.java).value(listOf(backendVal)))
    benchmarkParameters.put(
        "messageType",
        project.objects.listProperty(String::class.java).value(messageType.split(","))
    )

    val parametersProp = project.findProperty("jmh.parameters")?.toString()
    if (parametersProp != null) {
        parametersProp.split(";").forEach { pair ->
            val parts = pair.split("=")
            if (parts.size == 2) {
                benchmarkParameters.put(
                    parts[0],
                    project.objects.listProperty(String::class.java).value(parts[1].split(","))
                )
            }
        }
    }
}

tasks.register<JavaExec>("runJmh") {
    dependsOn("jmhJar")
    mainClass.set("org.openjdk.jmh.Main")

    val backendVal = project.findProperty("backend")?.toString() ?: "slb4j"
    val runtimeConfig = when (backendVal) {
        "log4j" -> configurations.getByName("log4jRuntime")
        "logback" -> configurations.getByName("logbackRuntime")
        "jul" -> configurations.getByName("julRuntime")
        "slb4j" -> configurations.getByName("slb4jRuntime")
        else -> configurations.getByName("slb4jRuntime")
    }

    val jmhJars = configurations.getByName("jmh").files.filter { file ->
        val name = file.name
        !name.contains("logback-classic") &&
                !name.contains("logback-core") &&
                !name.contains("log4j-core") &&
                !name.contains("slf4j-jdk14") &&
                !name.contains("slf4j-jcl") &&
                !name.contains("slf4j-jul") &&
                !name.contains("log4j-slf4j") &&
                !name.contains("log4j-jcl") &&
                !name.contains("log4j-jul") &&
                !name.contains("jcl-over-slf4j") &&
                !name.contains("jul-to-slf4j") &&
                !name.contains("log4j-to-slf4j") &&
                !name.contains("log4j-api") &&
                !name.contains("log4j-slf4j2-impl") &&
                !name.contains("slb4j")
    }

    val benchmarkJar = tasks.named<org.gradle.jvm.tasks.Jar>("jmhJar").get().archiveFile
    classpath = files(jmhJars, runtimeConfig, benchmarkJar)

    // Pass arguments to JMH
    val jmhArgs = mutableListOf<String>()

    project.findProperty("jmh.includes")?.toString()?.let {
        jmhArgs.addAll(it.split(","))
    }

    jmhArgs.add("-wi")
    jmhArgs.add(project.findProperty("warmupIterations")?.toString() ?: "2")
    jmhArgs.add("-i")
    jmhArgs.add(project.findProperty("iterations")?.toString() ?: "3")
    jmhArgs.add("-f")
    jmhArgs.add(project.findProperty("forks")?.toString() ?: "1")
    jmhArgs.add("-t")
    jmhArgs.add("1")
    jmhArgs.add("-r")
    jmhArgs.add(project.findProperty("timeOnIteration")?.toString() ?: "1s")

    // Parameters
    val outputToFile = project.findProperty("outputToFile")?.toString() ?: "false"
    val messageType = project.findProperty("messageType")?.toString() ?: "CONSTANT,ARGUMENTS,LAMBDA"
    jmhArgs.add("-p")
    jmhArgs.add("outputToFile=$outputToFile")
    jmhArgs.add("-p")
    jmhArgs.add("backend=$backendVal")
    jmhArgs.add("-p")
    jmhArgs.add("messageType=$messageType")

    project.findProperty("jmh.parameters")?.toString()?.split(";")?.forEach { pair ->
        val parts = pair.split("=")
        if (parts.size == 2) {
            jmhArgs.add("-p")
            jmhArgs.add("${parts[0]}=${parts[1]}")
        }
    }

    jmhArgs.add("-rf")
    jmhArgs.add("JSON")
    jmhArgs.add("-rff")
    jmhArgs.add("jmh-results.json")

    args = jmhArgs
}
