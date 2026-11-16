import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Podpisovanie: jeden stály keystore mimo repozitára. Cesta a heslá sú v súbore
// ~/.gros/keystore.properties (alebo v súbore, na ktorý ukazuje GROS_KEYSTORE_PROPERTIES).
// Bez neho vznikne nepodpísaný release (napr. pre F-Droid) a debug podpíše predvolený debug kľúč.
val keystorePropertiesFile: File = file(
    providers.environmentVariable("GROS_KEYSTORE_PROPERTIES").orNull
        ?: "${System.getProperty("user.home")}/.gros/keystore.properties",
)
val keystoreProperties = Properties().apply {
    // Číta sa ako UTF-8 (tak ho ukladajú bežné editory), prípadný BOM sa zahodí.
    if (keystorePropertiesFile.isFile) {
        val byteOrderMark = Char(0xFEFF).toString()
        load(keystorePropertiesFile.readText(Charsets.UTF_8).removePrefix(byteOrderMark).reader())
    }
}
val keystoreFile: File? = keystoreProperties.getProperty("storeFile")?.trim()?.takeIf { it.isNotEmpty() }?.let(::file)
val hasReleaseKeystore = keystoreFile?.isFile == true
if (!hasReleaseKeystore) {
    val reason = when {
        !keystorePropertiesFile.isFile -> "chýba súbor $keystorePropertiesFile"
        keystoreFile == null -> "v súbore $keystorePropertiesFile chýba storeFile"
        else -> "keystore $keystoreFile neexistuje"
    }
    logger.warn("Groš: build nebude podpísaný stálym kľúčom ($reason).")
}

android {
    namespace = "io.github.mstajanc.gros"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.mstajanc.gros"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("gros") {
                // Hodnoty sa orezávajú: medzera na konci riadku v súbore by inak bola súčasťou hesla.
                storeFile = keystoreFile
                storePassword = keystoreProperties.getProperty("storePassword")?.trim()
                keyAlias = keystoreProperties.getProperty("keyAlias")?.trim()
                keyPassword = keystoreProperties.getProperty("keyPassword")?.trim()
            }
        }
    }

    buildTypes {
        debug {
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("gros")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("gros")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Žiadny šifrovaný blok so zoznamom závislostí v APK (požiadavka F-Droid).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Počty sa skloňujú v `domain` (SlovakText), lebo systémové `plurals` sa riadia jazykom
        // zariadenia a na neslovenskom telefóne by slovenské tvary vybrali nesprávne.
        disable += "PluralsCandidate"
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        // Spodné panely, výber dátumu a potiahnutie na zmazanie sú v Material 3 stále označené ako experimentálne.
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

/**
 * Akceptačné kritérium: zlúčený manifest release buildu nesmie obsahovať sieťové povolenia.
 * Úloha je súčasťou `check`.
 */
abstract class VerifyNoNetworkPermissions : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifest = mergedManifest.get().asFile.readText()
        val forbidden = listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE")
        val found = forbidden.filter { manifest.contains("\"$it\"") }
        if (found.isNotEmpty()) {
            throw GradleException("Zlúčený manifest obsahuje zakázané povolenia: ${found.joinToString()}")
        }
        val declared = Regex("""<uses-permission[^>]*android:name="([^"]+)"""")
            .findAll(manifest).map { it.groupValues[1] }.toList()
        report.get().asFile.writeText(
            "OK – žiadne sieťové povolenia.\nDeklarované povolenia: ${declared.ifEmpty { listOf("žiadne") }.joinToString()}\n",
        )
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val variantName = variant.name.replaceFirstChar(Char::uppercase)
        val verify = tasks.register<VerifyNoNetworkPermissions>("verify${variantName}NoNetworkPermissions") {
            group = "verification"
            description = "Overí, že zlúčený manifest neobsahuje INTERNET ani ACCESS_NETWORK_STATE."
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            report.set(layout.buildDirectory.file("reports/permissions/${variant.name}.txt"))
        }
        tasks.named("check") { dependsOn(verify) }
    }
}
