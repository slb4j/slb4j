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

    // Frontends
    implementation(platform(libs.log4j.bom))
    implementation(libs.slf4j.api)
    implementation(libs.log4j.api)
    implementation(libs.commons.logging)

    // Core dependencies needed for all benchmarks compilation
    implementation(libs.log4j.core)
    implementation(libs.logback.classic)

    jmh(platform(libs.log4j.bom))
    jmh(libs.log4j.core)
    jmh(libs.logback.classic)
    jmh(rootProject)

    // Backends - runtime bridges
    val backend = project.findProperty("backend")?.toString() ?: "slb4j"

    when (backend) {
        "log4j" -> {
            jmh(libs.log4j.slf4j2) // slf4j to log4j
            jmh(libs.log4j.jcl) // jcl to log4j
            jmh(libs.log4j.jul) // jul to log4j
        }

        "logback" -> {
            jmh(libs.slf4j.jcl) // jcl to slf4j
            jmh(libs.slf4j.jul) // jul to slf4j
            jmh(libs.log4j.to.slf4j) // log4j to slf4j
        }

        "jul" -> {
            jmh(libs.slf4j.jdk14) // slf4j to jul
            // JCL to JUL is handled by JCL's default behavior or system properties
        }

        "slb4j" -> {
            // SLB4J handles all four frontends directly, no bridges needed
        }
    }
}

jmh {
    val backendVal = project.findProperty("backend")?.toString() ?: "slb4j"
    warmupIterations.set(project.findProperty("warmupIterations")?.toString()?.toInt() ?: 2)
    iterations.set(project.findProperty("iterations")?.toString()?.toInt() ?: 3)
    fork.set(1)
    timeOnIteration = project.findProperty("timeOnIteration")?.toString() ?: "1s"
    resultFormat.set("JSON")
    duplicateClassesStrategy.set(DuplicatesStrategy.EXCLUDE)
    failOnError.set(true)
    jvmArgs.add("-Djmh.ignoreLock=true")
    forceGC.set(true)

    val includesProp = project.findProperty("jmh.includes")?.toString()
    if (includesProp != null) {
        includes.addAll(includesProp.split(","))
    }

    val outputToFile = project.findProperty("outputToFile")?.toString() ?: "false"
    benchmarkParameters.put("outputToFile", project.objects.listProperty(String::class.java).value(listOf(outputToFile)))
    benchmarkParameters.put("backend", project.objects.listProperty(String::class.java).value(listOf(backendVal)))

    val parametersProp = project.findProperty("jmh.parameters")?.toString()
    if (parametersProp != null) {
        parametersProp.split(";").forEach { pair ->
            val parts = pair.split("=")
            if (parts.size == 2) {
                benchmarkParameters.put(parts[0], project.objects.listProperty(String::class.java).value(parts[1].split(",")))
            }
        }
    }
}
