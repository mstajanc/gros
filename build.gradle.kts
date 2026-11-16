buildscript {
    dependencies {
        // AGP má Kotlin zabudovaný; týmto sa určuje verzia Kotlinu použitá na kompiláciu.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
