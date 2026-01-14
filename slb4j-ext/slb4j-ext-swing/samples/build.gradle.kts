plugins {
    application
}

dependencies {
    implementation(platform(libs.log4j.bom))
    implementation(project(":slb4j-ext:slb4j-ext-swing"))
    implementation(libs.slf4j.api)
    implementation(libs.log4j.api)
    implementation(libs.commons.logging)
}

application {
    mainClass.set("org.slb4j.ext.swing.samples.SwingLogWindowSample")
}
