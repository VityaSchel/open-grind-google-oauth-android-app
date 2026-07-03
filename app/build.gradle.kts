import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import com.android.build.api.variant.FilterConfiguration
import java.util.Properties
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
}

abstract class BundleWebExtension : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun bundle() {
        val dest = outputDir.get().dir("grindr-google-oauth").asFile
        fs.delete { delete(outputDir) }
        fs.copy {
            from(source.dir("shared")) { into("shared") }
            from(source.dir("icons")) { into("icons") }
            from(source.file("geckoview/manifest.json"))
            into(dest)
        }
    }
}

fun expandHome(path: String): String =
    if (path.startsWith("~")) System.getProperty("user.home") + path.substring(1) else path

val keystoreProperties = System.getenv("GRINDR_OAUTH_KEYSTORE_PROPERTIES")
    ?.takeIf { it.isNotBlank() }
    ?.let { path ->
        val f = file(expandHome(path))
        if (!f.exists()) {
            throw GradleException(
                "GRINDR_OAUTH_KEYSTORE_PROPERTIES points at '$path' but no file exists there. " +
                    "Create it (see contrib/keystore.properties.example) or unset the variable to build unsigned."
            )
        }
        Properties().apply { f.inputStream().use { load(it) } }
    }

android {
    namespace = "org.opengrind.google_oauth"
    compileSdk {
        version = release(36)
    }
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "org.opengrind.google_oauth"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        keystoreProperties?.let { props ->
            create("release") {
                val store = file(
                    expandHome(
                        props.getProperty("storeFile")
                            ?: throw GradleException("keystore.properties is missing 'storeFile'")
                    )
                )
                if (!store.exists()) {
                    throw GradleException("storeFile '$store' from keystore.properties does not exist.")
                }
                storeFile = store
                storePassword = props.getProperty("password")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("password")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            keystoreProperties?.let { signingConfig = signingConfigs.getByName("release") }
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        val bundleTask = tasks.register<BundleWebExtension>(
            "bundle${variant.name.replaceFirstChar { it.uppercase() }}WebExtension"
        ) {
            source.set(rootProject.layout.projectDirectory.dir("extension"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(
            bundleTask,
            BundleWebExtension::outputDir,
        )

        if (variant.buildType == "debug") {
            variant.outputs.forEach { output ->
                val abi = output.filters
                    .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                    ?.identifier
                output.enabled.set(abi == "arm64-v8a")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.geckoview)
}