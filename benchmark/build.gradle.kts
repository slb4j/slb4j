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
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(1)
    resultFormat.set("JSON")
    jvmArgs.set(listOf("-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager"))
}
