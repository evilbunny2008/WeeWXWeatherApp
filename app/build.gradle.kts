// Several AGP Variant API members used below (outputFileName, artifacts.get,
// onVariants/selector for this variant-configuration style) are still
// marked @Incubating - meaning they work correctly today but the API
// surface could change in a future AGP release, not that anything here is
// broken. This is the standard, conventional way to suppress that specific
// warning category for the whole build script.
@file:Suppress("UnstableApiUsage")

import com.android.build.api.artifact.SingleArtifact

plugins {
	id("com.android.application")
}

android {
	namespace = "com.odiousapps.weewxweather"
	compileSdk = 37

    buildFeatures {
         buildConfig = true
         aidl = true
     }

     // Required for F-Droid's reproducible-build verification -- without
     // this, Android's dependency-metadata block gets embedded slightly
     // differently depending on the exact build environment, which makes
     // F-Droid's independently-rebuilt APK fail to match byte-for-byte
     // even when the actual app source is identical.
     dependenciesInfo {
         includeInApk = false
         includeInBundle = false
     }

	defaultConfig {
		applicationId = "com.odiousapps.weewxweather"
		minSdk = 30
		targetSdk = 37
		versionName = "2.2.15"
		versionCode = 2002015
	}

	// Generates android:localeConfig from the values-*/ folders, so
	// Android 13+ lists this app under Settings -> App languages (on
	// devices whose Settings has that screen). Needs res/resources.properties
	// to say what language the unqualified values/ folder is.
	androidResources {
		generateLocaleConfig = true
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles("proguard-rules.pro")
		}

		debug {
			isMinifyEnabled = false
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_21
		targetCompatibility = JavaVersion.VERSION_21
		isCoreLibraryDesugaringEnabled = true
	}

	packaging {
		resources {
			excludes += setOf(
				"META-INF/INDEX.LIST",
				"META-INF/io.netty.versions.properties"
			)
		}
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.compilerArgs.add("-Xlint:deprecation")
}

// Copies the release .aab from AGP's default build output location
// (app/build/outputs/bundle/release/app-release.aab) to
// app/dist/<appName>-v<versionName>.aab, outside build/ so it survives a
// clean build. Must run after AGP's "produce...BundleIdeListingFile" task,
// which takes the bundle at its default location as an input.
abstract class RenameBundleTask : DefaultTask() {
	@get:InputFile
	abstract val bundleFile: RegularFileProperty

	@get:OutputFile
	abstract val destinationFile: RegularFileProperty

	@TaskAction
	fun rename() {
		val file = bundleFile.get().asFile
		if (!file.exists()) {
			logger.lifecycle("renameBundle: expected bundle file not found at $file - skipping rename")
			return
		}

		val destination = destinationFile.get().asFile
		destination.parentFile.mkdirs()
		file.copyTo(destination, overwrite = true)
		logger.lifecycle("renameBundle: copied to $destination")

		// Leave the original in place: the bundle tasks stay up-to-date between
		// runs and won't recreate it, but produceBundleIdeListingFile and this
		// task still need it as an input on the next build.
	}
}

// Copies the release APK(s) to app/dist/ alongside the bundle.
// SingleArtifact.APK is a directory, so copy every .apk found in it.
abstract class CopyApkTask : DefaultTask() {
	@get:InputFiles
	abstract val apkDirectory: DirectoryProperty

	@get:OutputDirectory
	abstract val destinationDirectory: DirectoryProperty

	@TaskAction
	fun copy() {
		val destDir = destinationDirectory.get().asFile
		destDir.mkdirs()

		apkDirectory.get().asFile.listFiles { f -> f.extension == "apk" }?.forEach { apk ->
			val dest = File(destDir, apk.name)
			apk.copyTo(dest, overwrite = true)
			logger.lifecycle("copyApk: copied to $dest")
		}
	}
}

androidComponents {
	onVariants(selector().withBuildType("release")) { variant ->
		val appName = "WeeWXApp"
		val versionName = variant.outputs.first().versionName
		val variantNameCapitalized = variant.name.replaceFirstChar { it.uppercase() }

		variant.outputs.forEach { output ->
			output.outputFileName.set(versionName.map { "$appName-v$it.apk" })
		}

		val renameBundle = tasks.register<RenameBundleTask>("renameBundle$variantNameCapitalized") {
			group = "build"
			description = "Copies the $variantNameCapitalized .aab to app/dist/"
			mustRunAfter("produce${variantNameCapitalized}BundleIdeListingFile")
			bundleFile.set(variant.artifacts.get(SingleArtifact.BUNDLE))
			destinationFile.set(versionName.map { layout.projectDirectory.file("dist/$appName-v$it.aab") })
		}

		val copyApk = tasks.register<CopyApkTask>("copyApk$variantNameCapitalized") {
			group = "build"
			description = "Copies the $variantNameCapitalized apk(s) to app/dist/"
			apkDirectory.set(variant.artifacts.get(SingleArtifact.APK))
			destinationDirectory.set(layout.projectDirectory.dir("dist"))
		}

		// onVariants already runs after evaluation, so match the tasks
		// lazily rather than nesting another afterEvaluate.
		tasks.matching { it.name == "bundle$variantNameCapitalized" }.configureEach {
			finalizedBy(renameBundle)
		}
		tasks.matching { it.name == "assemble$variantNameCapitalized" }.configureEach {
			finalizedBy(copyApk)
		}
	}
}

dependencies {
	coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

	implementation("com.google.android.material:material:1.14.0")
	implementation("androidx.core:core:1.19.1")
	implementation("androidx.fragment:fragment:1.9.1")
	implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
	implementation("androidx.webkit:webkit:1.18.0")

	implementation("com.google.code.gson:gson:2.15.0")

	implementation("com.caverock:androidsvg:1.4")

	implementation("com.squareup.okhttp3:okhttp:5.5.1")

	implementation("org.jsoup:jsoup:1.23.2")

	implementation("com.hivemq:hivemq-mqtt-client:1.4.1")

	implementation("io.netty:netty-common:4.1.137.Final")
	implementation("io.netty:netty-handler:4.1.137.Final")
	implementation("io.netty:netty-codec:4.1.137.Final")
	implementation("io.netty:netty-codec-http:4.1.137.Final")
	implementation("io.netty:netty-transport:4.1.137.Final")
	implementation("io.netty:netty-buffer:4.1.137.Final")
	implementation("io.netty:netty-resolver:4.1.137.Final")

	implementation("org.slf4j:slf4j-android:1.7.36")

	// The following line suppresses version warnings when using commit IDs for the version...
	//noinspection GradleDependency
	//implementation(files("library-release.aar"))
	implementation("com.github.evilbunny2008:ColourPicker:2.0.24")

	implementation("com.github.evilbunny2008:XmlToJson:2.0.2")
}
