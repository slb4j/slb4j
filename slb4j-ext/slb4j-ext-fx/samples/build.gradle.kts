plugins {
    application
}

dependencies {
    implementation(platform(libs.log4j.bom))
    implementation(project(":slb4j-ext"))
    implementation(project(":slb4j-ext:slb4j-ext-fx"))
    implementation(libs.slf4j.api)
    implementation(libs.log4j.api)
    implementation(libs.commons.logging)
}

application {
    mainClass.set("org.slb4j.ext.fx.samples.FxLogPaneSample")
}
