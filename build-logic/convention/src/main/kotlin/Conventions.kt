import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Valores comunes de todos los módulos: un solo lugar para cambiarlos. */
object Build {
    const val COMPILE_SDK = 37
    const val MIN_SDK = 26
    const val JVM = 21
    val JAVA = JavaVersion.VERSION_21
}

private val Project.libs get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** `:feature:auth:data` → `com.barberia.feature.auth.data` */
private val Project.defaultNamespace get() = "com.barberia" + path.replace(':', '.').replace("-", "")

private fun Project.addUnitTestLibraries() = dependencies {
    add("testImplementation", libs.findLibrary("junit4").get())
    add("testImplementation", libs.findLibrary("coroutines-test").get())
}

/** Módulo Android library: SDKs, Java 21, namespace por ruta y librerías de test. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            namespace = defaultNamespace
            compileSdk = Build.COMPILE_SDK
            defaultConfig.minSdk = Build.MIN_SDK
            // Q1: tests instrumentados (androidTest) contra Firebase Emulator Suite.
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            compileOptions {
                sourceCompatibility = Build.JAVA
                targetCompatibility = Build.JAVA
            }
        }
        extensions.configure<KotlinProjectExtension> { jvmToolchain(Build.JVM) }
        addUnitTestLibraries()
        dependencies {
            add("androidTestImplementation", libs.findLibrary("androidx-test-runner").get())
            add("androidTestImplementation", libs.findLibrary("androidx-test-junit").get())
            add("androidTestImplementation", libs.findLibrary("junit4").get())
            add("androidTestImplementation", libs.findLibrary("coroutines-test").get())
        }
    }
}

/** Agrega Compose (compilador + BOM + tooling de preview) a un módulo Android. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("barberia.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure<LibraryExtension> { buildFeatures.compose = true }
        dependencies {
            val bom = libs.findLibrary("compose-bom").get()
            add("implementation", platform(bom))
            add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
            add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
        }
    }
}

/** Módulo Kotlin puro (dominio): sin Android. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<KotlinJvmProjectExtension> { jvmToolchain(Build.JVM) }
        addUnitTestLibraries()
    }
}
