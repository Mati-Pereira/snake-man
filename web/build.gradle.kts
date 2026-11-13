plugins {
    kotlin("multiplatform")
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "snake-man.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            // Reaproveita a lógica pura do app: motor, placar e sintetizador.
            // A UI Android (ui/**, MainActivity) e as classes que tocam em
            // APIs do Android (Prefs, Feedback, MusicPlayer) ficam de fora.
            kotlin.srcDir("../app/src/main/java")
            kotlin.exclude(
                "**/MainActivity.kt",
                "**/data/Prefs.kt",
                "**/audio/Feedback.kt",
                "**/audio/MusicPlayer.kt",
                "**/ui/**"
            )
        }
    }
}

/**
 * Monta o demo jogável em `dist/web/snake-man/` (index.html + snake-man.js).
 * Abra com `python3 -m http.server -d dist/web/snake-man 8080`.
 */
val webDemo by tasks.registering(Copy::class) {
    group = "distribution"
    description = "Monta o demo web em dist/web/snake-man."
    dependsOn("jsBrowserProductionWebpack")

    from("src/jsMain/resources")
    from(layout.buildDirectory.dir("kotlin-webpack/js/productionExecutable")) {
        include("snake-man.js")
    }
    into(rootProject.layout.projectDirectory.dir("dist/web/snake-man"))
}
