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

    // All these should be compileOnly to avoid being on the default JMH runtime classpath
    // We will add them back selectively for each forked process.
    compileOnly(platform(libs.log4j.bom))
    compileOnly(libs.slf4j.api)
    compileOnly(libs.log4j.api)
    compileOnly(libs.commons.logging)
    compileOnly(libs.log4j.core)
    compileOnly(libs.logback.classic)

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

    // Define configurations for each backend to collect their runtime dependencies
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
        // JCL to JUL is handled by JCL's default behavior or system properties

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

    // Add them to jvmArgs as a classpath addition
    // We want to replace the default classpath with our isolated one.
    // me.champeau.jmh plugin puts jmh.core and the benchmark classes on the classpath.
    // If we use -cp in jvmArgs, it should override the default one if we are lucky,
    // or we might need to include everything needed.
    
    val jmhJars = project.configurations.getByName("jmh").files.filter { file ->
        val name = file.name
        // Exclude other backends and bridges that might be in the 'jmh' configuration
        // We want ONLY the selected backend's JARs (which are added via runtimeConfigJars)
        // and general JMH / non-logging JARs from the 'jmh' configuration.
        !name.contains("logback-classic") &&
        !name.contains("log4j-core") &&
        !name.contains("slf4j-jdk14") &&
        !name.contains("log4j-slf4j") &&
        !name.contains("log4j-jcl") &&
        !name.contains("log4j-jul") &&
        !name.contains("jcl-over-slf4j") &&
        !name.contains("jul-to-slf4j") &&
        !name.contains("log4j-to-slf4j") &&
        !name.contains("slb4j")
    }
    val jmhWorkerJars = project.configurations.findByName("jmhWorker")?.files ?: emptySet<File>()
    val runtimeConfigJars = runtimeConfig.files
    
    // Also need the benchmark classes themselves
    val benchmarkClasses = project.tasks.getByName("jmhJar").outputs.files
    
    val allJars = (jmhJars + jmhWorkerJars + runtimeConfigJars + benchmarkClasses).joinToString(File.pathSeparator)
    
    if (allJars.isNotEmpty()) {
        jvmArgs.add("-cp")
        jvmArgs.add(allJars)
    }
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
    val messageType = project.findProperty("messageType")?.toString() ?: "CONSTANT,ARGUMENTS,LAMBDA"
    benchmarkParameters.put("outputToFile", project.objects.listProperty(String::class.java).value(listOf(outputToFile)))
    benchmarkParameters.put("backend", project.objects.listProperty(String::class.java).value(listOf(backendVal)))
    benchmarkParameters.put("messageType", project.objects.listProperty(String::class.java).value(messageType.split(",")))

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
