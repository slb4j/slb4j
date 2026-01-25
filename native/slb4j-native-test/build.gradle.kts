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
    application
    alias(libs.plugins.jdkprovider)
    alias(libs.plugins.graalvm)
}

jdk {
    version = "25.0.1"
    javaFxBundled = true
    nativeImageCapable = true
}

graalvmNative {
    binaries {
        all {
            resources.autodetect()
            this.javaLauncher = jdk.getJavaLauncher(project)
        }
        named("main") {
            imageName.set("native-test")
            mainClass.set("org.slb4j.native_test.Main")
            buildArgs.addAll(
                "-Os",
                "--enable-native-access=ALL-UNNAMED"
            )
        }
    }
}

dependencies {
    implementation(rootProject)
    implementation(platform(libs.log4j.bom))
    implementation(libs.slf4j.api)
    implementation(libs.log4j.api)
    implementation(libs.jcl)
}

application {
    mainClass.set("org.slb4j.native_test.Main")
    applicationDefaultJvmArgs = listOf("-Dlog4j2.debug=true")
}

tasks.withType<JavaExec> {
    jvmArgs("-Dlog4j2.debug=true")
}
