pluginManagement {
    repositories {
        maven { setUrl("https://maven.aliyun.com/repository/public") }
        maven { setUrl("https://maven.aliyun.com/repository/google") }
        maven { setUrl("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { setUrl("https://artifact.bytedance.com/repository/byteX") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { setUrl("https://maven.aliyun.com/repository/public") }
        maven { setUrl("https://maven.aliyun.com/repository/google") }
        maven { setUrl("https://artifact.bytedance.com/repository/byteX") }
        google()
        mavenCentral()
        maven { setUrl("https://api.xposed.info") }
        maven { setUrl("https://jitpack.io") }
    }
}
rootProject.name = "MDMDisabler"
include(":app")
