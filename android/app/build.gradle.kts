plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dvdiso.sunbi"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dvdiso.sunbi"
        minSdk = 29
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        // tag v1.2.3 → versão 1.2.3; fora de tag, 0.2.<número do build>
        versionName = listOf(System.getenv("SUNBI_VERSION"), System.getenv("GITHUB_REF_NAME"))
            .firstOrNull { it != null && it.matches(Regex("v\\d+\\.\\d+\\.\\d+")) }?.drop(1)
            ?: "0.2.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }

    // Assinatura:
    // - Releases oficiais: chave privada vinda do ambiente (secrets do GitHub Actions):
    //   SUNBI_KEYSTORE (caminho do .jks), SUNBI_KEYSTORE_PASSWORD, SUNBI_KEY_ALIAS, SUNBI_KEY_PASSWORD.
    // - Sem essas variáveis: chave de desenvolvimento pública (dev-debug.keystore). Serve para
    //   testar, mas como ela está no repositório, APKs assinados com ela não devem ser distribuídos.
    val releaseKeystore = System.getenv("SUNBI_KEYSTORE")?.takeIf { it.isNotBlank() && file(it).exists() }
    signingConfigs {
        create("dev") {
            storeFile = file("dev-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("SUNBI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SUNBI_KEY_ALIAS")
                keyPassword = System.getenv("SUNBI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("dev")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "dev")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Material 3 Expressive (formas, movimento com mola, grupos de botões, barra flutuante)
    implementation("androidx.compose.material3:material3:1.5.0-rc01")
    implementation("androidx.compose.ui:ui:1.12.0")
    implementation("androidx.compose.foundation:foundation:1.12.0")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    // resumo semanal da SunAI em segundo plano (precisa de rede e pode demorar)
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    // sono vindo do relógio (Health Connect)
    implementation("androidx.health.connect:connect-client:1.1.0")

    // Testes de tela no JVM (Robolectric com gráficos nativos): geram PNGs para revisar o visual
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.18")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.12.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.12.0")
}
