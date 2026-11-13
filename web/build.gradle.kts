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
