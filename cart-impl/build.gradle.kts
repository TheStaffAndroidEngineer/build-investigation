plugins { id("staff.kotlin-library") }
dependencies {
    api(project(":cart-api"))
    implementation(libs.coroutinesCore)
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
