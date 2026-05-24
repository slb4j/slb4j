plugins {
    id("java")
    alias(libs.plugins.jmh.plugin)
}

description = "Benchmark for Logback classic"

dependencies {
    implementation(project(":benchmark"))

    // facades
    jmh(platform(libs.log4j.bom))
    jmh(libs.slf4j.api)
    jmh(libs.log4j.api)
    jmh(libs.jcl)

    // backend
    jmh(libs.logback.classic)

    // bridges
    jmh(libs.jcl.to.slf4j)
    jmh(libs.jul.to.slf4j)
    jmh(libs.log4j.to.slf4j)
}

jmh {
    jmhVersion.set("1.37")
    failOnError.set(true)
    forceGC.set(true)

    warmupIterations.set(project.findProperty("jmh.warmupIterations")?.toString()?.toInt())
    iterations.set(project.findProperty("jmh.iterations")?.toString()?.toInt())
    fork.set(project.findProperty("jmh.forks")?.toString()?.toInt())
    warmup.set(project.findProperty("jmh.warmupTime")?.toString())
    timeOnIteration.set(project.findProperty("jmh.timeOnIteration")?.toString())

    project.findProperty("jmh.includes")?.toString()?.let {
        includes.set(listOf(it))
    }

    project.findProperty("jmh.excludes")?.toString()?.let {
        excludes.set(it.split(","))
    }

    project.findProperty("jmh.parameters")?.toString()?.let { params ->
        params.split(";").forEach {
            val parts = it.split("=")
            if (parts.size == 2) {
                benchmarkParameters.put(
                    parts[0],
                    project.objects.listProperty(String::class.java).value(parts[1].split(","))
                )
            }
        }
    }

    project.findProperty("jmh.jvmArgs")?.toString()?.let {
        jvmArgs.add(it)
    }

    project.findProperty("jmh.profilers")?.toString()?.let {
        profilers.set(it.split(","))
    }

    resultFormat.set("JSON")
}

tasks.named("jmh") {
    outputs.upToDateWhen { false }
}
