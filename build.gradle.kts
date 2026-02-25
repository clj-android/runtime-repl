plugins {
    id("com.android.library")
    `maven-publish`
}

group = "com.goodanser.clj-android"
version = "0.1.0-SNAPSHOT"

android {
    namespace = "com.goodanser.clj_android.runtime.repl"
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

// When consumed via includeBuild(), raw project configurations are exposed
// instead of published module metadata.  AGP's published metadata includes
// these attributes automatically, but the raw configurations do not, so we
// add them here for composite-build compatibility.
afterEvaluate {
    val categoryAttr = Attribute.of("org.gradle.category", Named::class.java)
    val jvmEnvAttr = Attribute.of("org.gradle.jvm.environment", Named::class.java)
    val kotlinPlatformAttr = Attribute.of("org.jetbrains.kotlin.platform.type", Named::class.java)

    configurations.configureEach {
        if (isCanBeConsumed && !isCanBeResolved) {
            attributes {
                attribute(categoryAttr, objects.named("library"))
                attribute(jvmEnvAttr, objects.named("android"))
                attribute(kotlinPlatformAttr, objects.named("androidJvm"))
            }
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate {
                from(components["release"])
            }
            groupId = "com.goodanser.clj-android"
            artifactId = "runtime-repl"
            version = project.version.toString()
        }
    }
}
