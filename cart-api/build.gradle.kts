plugins { id("staff.kotlin-library") }
dependencies {
    api(libs.coroutinesCore)
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
