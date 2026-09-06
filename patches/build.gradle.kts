group = "app.brave"

patches {
    about {
        name = "Brave and universal patches"
        description = "Brave Browser and universal Android patches"
        author = "dh6k"
        website = "https://github.com/dh6k/morphe-patches"
        source = "git@github.com:dh6k/morphe-patches"
        contact = ""
        license = "GPLv3"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

// Keep Gson available to generatePatchesList without bundling its runtime graph
// into the patches DEX.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }
    // Local-only task: patch a real APK with the CLI to validate a patch.
    register<JavaExec>("patchLocalApk") {
        description = "Patch a local APK with MorpheLauncherKt (local validation only)"
        val mpp = providers.gradleProperty("mpp").orElse("build/libs/patches-1.6.0-dev.4.mpp")
        val patchName = providers.gradleProperty("patch").orElse("Force highest refresh rate")
        val apk = providers.gradleProperty("apk")
        val outDir = providers.gradleProperty("out").orElse("build/local-apk-test")
        classpath = files(rootDir.resolve("morphe-desktop-1.15.0-all.jar"))
        mainClass.set("app.morphe.MorpheLauncherKt")
        // ponytail: keep every provider lazy so plain :tasks/configure never
        // resolves -Papk on CI; it is read only when this task executes.
        val cliArgs: Provider<List<String>> = apk.flatMap { a ->
            mpp.flatMap { m ->
                patchName.flatMap { p ->
                    outDir.map { o ->
                        listOf("patch", "-p", m, "-e", p, "--exclusive", "--unsigned",
                            "-o", "$o/out.apk", "-r", "$o/result.json", "-t", "$o/scratch", a)
                    }
                }
            }
        }
        doFirst {
            require(apk.isPresent) { "Pass -Papk=<input apk path>" }
            mkdir(outDir.get())
            args(cliArgs.get())
        }
    }
    publish {
        dependsOn("generatePatchesList")
    }
}
