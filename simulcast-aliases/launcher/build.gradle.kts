plugins {
    id("com.android.application")
}

android {
    namespace = "dev.denza.simulcast.alias"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    flavorDimensions += "slot"
    productFlavors {
        create("vkQqliveAudio") {
            dimension = "slot"
            applicationId = "com.tencent.qqlive.audiobox"
            resValue("string", "app_name", "VK Video")
            buildConfigField("String", "TARGET_PACKAGE", "\"com.vk.vkvideo\"")
            buildConfigField("String", "FALLBACK_URI", "\"vkvideo://\"")
        }
        create("rutubeMgtv") {
            dimension = "slot"
            applicationId = "com.mgtv.auto"
            resValue("string", "app_name", "Rutube")
            buildConfigField("String", "TARGET_PACKAGE", "\"ru.rutube.app\"")
            buildConfigField("String", "FALLBACK_URI", "\"\"")
        }
        create("vkCmvideo") {
            dimension = "slot"
            applicationId = "cn.cmvideo.car.play"
            resValue("string", "app_name", "Yandex Navi")
            buildConfigField("String", "TARGET_PACKAGE", "\"ru.yandex.yandexnavi\"")
            buildConfigField("String", "FALLBACK_URI", "\"\"")
        }
        create("rutubeYouku") {
            dimension = "slot"
            applicationId = "com.youku.car"
            resValue("string", "app_name", "Yandex Music")
            buildConfigField("String", "TARGET_PACKAGE", "\"ru.yandex.music\"")
            buildConfigField("String", "FALLBACK_URI", "\"\"")
        }
        create("vkQqliveLegacy") {
            dimension = "slot"
            applicationId = "com.tencent.qqlive"
            resValue("string", "app_name", "VK Video")
            buildConfigField("String", "TARGET_PACKAGE", "\"com.vk.vkvideo\"")
            buildConfigField("String", "FALLBACK_URI", "\"vkvideo://\"")
        }
        create("rutubeIqiyiLegacy") {
            dimension = "slot"
            applicationId = "com.qiyi.video.pad"
            resValue("string", "app_name", "Rutube")
            buildConfigField("String", "TARGET_PACKAGE", "\"ru.rutube.app\"")
            buildConfigField("String", "FALLBACK_URI", "\"\"")
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("simulcast-alias-${variant.flavorName}.apk")
        }
    }
}

dependencies {
    implementation(project(":dishare-bridge"))
}
