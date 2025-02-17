// Copyright (C) 2022 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.compose)
  alias(libs.plugins.agp.application)
  alias(libs.plugins.kotlin.plugin.parcelize)
}

android {
  namespace = "com.slack.circuit.sample.counter.android"
  defaultConfig {
    minSdk = 31 // For the dynamic m3 theme
    targetSdk = 34
  }
}

androidComponents { beforeVariants { variant -> variant.enableAndroidTest = false } }

compose.desktop {
  application { mainClass = "com.slack.circuit.sample.counter.desktop.DesktopCounterCircuitKt" }
}

compose.experimental { web.application {} }

kotlin {
  // region KMP Targets
  androidTarget()
  jvm()
  ios()
  js {
    moduleName = "counterapp"
    browser()
    binaries.executable()
  }
  // endregion

  sourceSets {
    commonMain {
      dependencies {
        api(projects.samples.counter)
        api(libs.kotlinx.immutable)
        api(projects.circuitFoundation)
      }
    }
    maybeCreate("commonTest").apply { dependencies { implementation(libs.kotlin.test) } }
    maybeCreate("jvmMain").apply { dependencies { implementation(compose.desktop.currentOs) } }
    maybeCreate("androidMain").apply {
      dependencies {
        implementation(libs.androidx.appCompat)
        implementation(libs.bundles.compose.ui)
        implementation(libs.androidx.compose.integration.activity)
        implementation(libs.androidx.compose.integration.materialThemeAdapter)
        implementation(libs.androidx.compose.material.icons)
        implementation(libs.androidx.compose.material.iconsExtended)
        implementation(libs.androidx.compose.accompanist.systemUi)
      }
    }
    maybeCreate("jsMain").apply {
      dependencies {
        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        implementation(compose.components.resources)
        implementation(compose.html.core)
      }
    }
  }
}
