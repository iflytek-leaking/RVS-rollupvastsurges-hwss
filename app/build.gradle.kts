import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.zip.CRC32

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ── Git 分支检测 & 过期日期计算 ──
fun detectGitBranch(): String {
    return try {
        val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().readText().trim()
            .ifEmpty { "unknown" }
    } catch (_: Exception) {
        "unknown"
    }
}

fun calcExpiryDate(branch: String): Long {
    val cal = Calendar.getInstance()
    when {
        branch.contains("test", ignoreCase = true) -> cal.add(Calendar.DAY_OF_YEAR, 30)
        branch.contains("dev", ignoreCase = true) -> cal.add(Calendar.DAY_OF_YEAR, 14)
        else -> return 99991231L  // main/其他 — 永不过期
    }
    return SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time).toLong()
}

val gitBranch = detectGitBranch()
val expiryDate = calcExpiryDate(gitBranch)

android {
    namespace = "com.leaking.rollupvastsurges"
    compileSdk = 37
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.leaking.rollupvastsurges"
        minSdk = 28
        targetSdk = 36
        versionCode = 4
        versionName = "4.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }

        // ── 注入时间炸弹参数 ──
        buildConfigField("long", "EXPIRY_DATE", "${expiryDate}L")
        buildConfigField("String", "GIT_BRANCH", "\"$gitBranch\"")
        // ── 防二次打包: 注入构建时签名 SHA-256（运行时校验用） ──
        val sigHash = try {
            val pb = ProcessBuilder("keytool", "-list", "-keystore", "release.jks",
                "-storepass", "android", "-keypass", "android")
                .directory(rootProject.projectDir).redirectErrorStream(true).start()
            val out = pb.inputStream.bufferedReader().readText()
            val sha = Regex("SHA-256: ([0-9A-Fa-f:]+)").find(out)?.groupValues?.get(1)?.replace(":", "")?.lowercase()
            sha ?: ""
        } catch (_: Exception) { "" }
        buildConfigField("String", "RELEASE_SIG_SHA256", "\"$sigHash\"")
        // ── 防重打包: 注入 DEX CRC32 (构建时难直接算, 运行时读 base.apk 计算) ──
        // 注: 完整 CRC 需打包后算 classes.dex, 此处留空由 IntegrityCheck 降级处理
        buildConfigField("String", "RELEASE_DEX_CRC32", "\"\"")
        println("── TimeBomb: branch=$gitBranch, expiry=$expiryDate, sig=$sigHash ──")
    }
    // 沙箱/CI: ~/.android 只读，debug 签名用项目内 keystore
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 加固: 资源混淆 + 移除调试信息
            proguardFile(file("proguard-hardening.pro"))
        }
        debug {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            proguardFile(file("proguard-hardening.pro"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        prefab = true
        buildConfig = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    packaging {
        jniLibs.pickFirsts.add("**/libshadowhook.so")
        jniLibs.pickFirsts.add("**/libc++_shared.so")
    }
}

// ── 防重打包: post-build 计算 base.apk 整体 CRC32 ──
// 口径与 penhook.c 的 crc32_dex 一致: 对整个 base.apk 文件做标准 CRC32。
// 注意(见 penhook.c 注释): 自引用校验, 期望值在 BuildConfig 里, 防不住"改 DEX 顺手改常量"。
// 仅作低级重打包兜底, 默认留空跳过。用法: ./gradlew calcDexCrc, 输出填到 RELEASE_DEX_CRC32
tasks.register("calcDexCrc") {
    description = "计算 release APK 整体 CRC32 (与 native crc32_dex 口径一致)"
    group = "verification"
    dependsOn("assembleRelease")
    doLast {
        val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        if (!apk.exists()) {
            println("[calcDexCrc] APK 不存在: ${apk.absolutePath}, 跳过")
            return@doLast
        }
        try {
            // 与 penhook.c crc32_dex 同口径: 整个文件, 标准 CRC32
            val crc = CRC32()
            apk.inputStream().use { input ->
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } > 0) {
                    crc.update(buf, 0, n)
                }
            }
            val crcHex = "0x" + crc.value.toString(16)
            println("[calcDexCrc] base.apk 整体 CRC32 = $crcHex")
            println("[calcDexCrc] 填入 build.gradle.kts 的 RELEASE_DEX_CRC32 (默认留空=跳过校验)")
        } catch (e: Exception) {
            println("[calcDexCrc] 计算失败: ${e.message}")
        }
    }
}

dependencies {
    // libxposed API 102 (modern Xposed) — 取代 legacy de.robv.android.xposed:api:82
    compileOnly("io.github.libxposed:api:102.0.0")
    compileOnly("io.github.libxposed:annotation:1.0.0")

    // DexKit — native DEX 扫描（后端规则表驱动的目标发现）
    implementation("org.luckypray:dexkit:2.3.0")

    // Gson — JSON 解析
    implementation("com.google.code.gson:gson:2.10.1")

    // ShadowHook — native inline hook
    implementation("com.bytedance.android:shadowhook:1.1.1")

    // Compose Material3 (保持作为底层依赖)
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")

    // Miuix — MIUI 风格 Compose UI (实验)
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.0")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.0")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.0")
    // NavigationEvent — Miuix 内部依赖未透传，需显式添加
    implementation("org.jetbrains.androidx.navigationevent:navigationevent-compose:1.1.0")
    implementation("androidx.navigationevent:navigationevent-android:1.1.1")
}
