subprojects {
    apply(plugin = "application")

    repositories {
        mavenCentral()
    }

    dependencies {
        "implementation"(rootProject)
    }

    configure<JavaApplication> {
        mainClass.set("org.slb4j.samples." + project.name + ".Main")
    }
}
