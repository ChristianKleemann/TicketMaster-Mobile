import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

// --- Configuración de GREEN-API (WhatsApp) -----------------------------------------------------
// Se lee de local.properties (no versionado) o de variables de entorno (CI). Ver
// local.properties.example. Si faltan los datos de la instancia o el destino, la integración
// queda desactivada y la app funciona igual.
val localProps = Properties().apply {
    val txt = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText
    if (txt.isPresent) load(StringReader(txt.get()))
}

fun prop(key: String): String {
    val env = providers.environmentVariable(key.uppercase().replace('.', '_')).orNull
    return (localProps.getProperty(key) ?: env ?: "").trim()
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.example.ticketmaster_mobile"
    compileSdk {
        version = release(37)
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        // Espresso / UI Automator necesitan las animaciones apagadas para no ser inestables
        animationsDisabled = true
    }

    defaultConfig {
        applicationId = "com.example.ticketmaster_mobile"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GREEN_API_URL", prop("green.apiUrl").ifEmpty { "https://api.green-api.com" }.asBuildConfigString())
        buildConfigField("String", "GREEN_ID_INSTANCE", prop("green.idInstance").asBuildConfigString())
        buildConfigField("String", "GREEN_API_TOKEN", prop("green.apiToken").asBuildConfigString())
        buildConfigField("String", "GREEN_CHAT_DESTINO", prop("green.chatDestino").asBuildConfigString())

        // Exporta el esquema de Room como JSON (útil para migraciones futuras)
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

configurations.configureEach {
    // Firestore usa protobuf-javalite; algunas librerías de pruebas traen protobuf-lite y chocan
    exclude(group = "com.google.protobuf", module = "protobuf-lite")
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.fragment:fragment:1.6.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    // --- Room (Android Jetpack) ---
    implementation("androidx.room:room-runtime:2.7.2")
    annotationProcessor("androidx.room:room-compiler:2.7.2")

    // --- WorkManager: envío en segundo plano, con red, que sobrevive a cierres y reinicios ---
    implementation("androidx.work:work-runtime:2.9.1")

    // LiveData para observar los resultados de las consultas de Room
    implementation("androidx.lifecycle:lifecycle-livedata:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime:2.7.0")

    testImplementation(libs.junit)
    testImplementation("androidx.room:room-testing:2.7.2")
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    // --- Pruebas de integración (androidTest) ---
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    // Espresso: RecyclerViewActions (listas de establecimientos, funciones y zonas)
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.5.1")
    // UI Automator: pruebas de caja negra desde el launcher / entre procesos
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")

    // --- Firebase ---
    implementation(platform("com.google.firebase:firebase-bom:35.2.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}
