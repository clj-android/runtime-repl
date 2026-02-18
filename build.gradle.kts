plugins {
    id("com.android.library")
    `maven-publish`
}

group = "org.clojure-android"
version = "0.1.0-SNAPSHOT"

android {
    namespace = "org.clojure_android.runtime.repl"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // Include Clojure source files as resources so they can be loaded at runtime
    sourceSets["main"].resources.srcDirs("src/main/clojure")

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    // Clojure runtime — compileOnly because the app provides it
    compileOnly("org.clojure:clojure:1.12.0")

    // AOSP dx library for JVM bytecode -> DEX translation at runtime
    implementation("com.jakewharton.android.repackaged:dalvik-dx:9.0.0_r3")

    // nREPL server
    // nREPL 1.0.0: last version before Unix domain socket support (nrepl.socket)
    // which requires java.net.UnixDomainSocketAddress (JDK 16+, not available on Android)
    implementation("nrepl:nrepl:1.0.0")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate {
                from(components["release"])
            }
            groupId = "org.clojure-android"
            artifactId = "runtime-repl"
            version = project.version.toString()
        }
    }
}
