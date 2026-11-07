/*
 * libpenhook — iFLYTEK 学习机笔按键重映射 Native Hook
 *
 * Hook TouchButtonAccumulator::process() 在 native 层改 toolType：
 *   笔侧键按下 → 切换橡皮擦模式 → field_B=1 → getToolType 返回 ERASER(3)
 *
 * 技术栈：ShadowHook (inline hook)
 * 三版适配：a9 libinputflinger / a12 a13 libinputreader
 */

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include <unistd.h>
#include <time.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <android/log.h>
#include <sys/system_properties.h>
#include "shadowhook.h"

#define LOG_TAG "penhook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ── 模块完整性校验 (防二次打包) ──
// 时间炸弹日期: 2026-12-31 → 20261231 (long)
// 拆为多个异或片段防静态拼接分析
static const int64_t EXP_P1 = 20260000LL ^ 0x5A5A5A5A5A5A5A5ALL;
static const int64_t EXP_P2 = 1231LL       ^ 0x5A5A5A5A5A5A5A5ALL;

// JNI 导出: 返回 1=模块过期应停用
JNIEXPORT jint JNICALL
Java_com_leaking_rollupvastsurges_hook_NativeStatusKt_isExpiredNative(JNIEnv *env, jclass clazz) {
    int64_t d = (EXP_P1 ^ 0x5A5A5A5A5A5A5A5ALL) + (EXP_P2 ^ 0x5A5A5A5A5A5A5A5ALL);
    struct tm tm = {0};
    char buf[16];
    snprintf(buf, sizeof(buf), "%lld", (long long)d);
    strptime(buf, "%Y%m%d", &tm);
    time_t expiry = mktime(&tm);
    return (time(NULL) > expiry) ? 1 : 0;
}

// ── 防二次打包 native 校验: 校验 APK 文件的 DEX 完整性 ──
// 计算 /data/app/<pkg>/base.apk 的 classes.dex CRC32, 与 gradle 注入值比对。
// 被重打包后 classes.dex 内容变化 → CRC32 不一致 → 模块静默失效。
// 注意: 此方案防"解包改代码重打包", 但防不住 inline patch so(需更复杂自校验)。
// 防二次打包 native 校验。限制(读我):
//  1. 口径: 对整个 base.apk 做标准 CRC32(表驱动, 与 java.util.zip.CRC32 一致),
//     与 gradle calcDexCrc 任务口径一致, 两端可对上。
//  2. 自引用死结: 期望值在 BuildConfig(被保护的 DEX 里), 防不住"改 DEX+改常量"。
//     真正有效需把期望值挪进 .so 且 .so 自校验(未实现)。
//  3. RELEASE_DEX_CRC32 默认留空 → 校验跳过(返回 0), 不误杀正版。
static uint32_t crc32_dex(const char *apk_path) {
    // 标准 CRC32 (IEEE 802.3) 表驱动实现
    static uint32_t table[256];
    static int table_ready = 0;
    if (!table_ready) {
        for (uint32_t i = 0; i < 256; i++) {
            uint32_t c = i;
            for (int k = 0; k < 8; k++)
                c = (c & 1) ? (0xEDB88320 ^ (c >> 1)) : (c >> 1);
            table[i] = c;
        }
        table_ready = 1;
    }
    FILE *f = fopen(apk_path, "rb");
    if (!f) return 0;
    uint32_t crc = 0xFFFFFFFF;
    unsigned char buf[8192];
    size_t n;
    while ((n = fread(buf, 1, sizeof(buf), f)) > 0) {
        for (size_t i = 0; i < n; i++)
            crc = table[(crc ^ buf[i]) & 0xFF] ^ (crc >> 8);
    }
    fclose(f);
    return crc ^ 0xFFFFFFFF;
}

JNIEXPORT jint JNICALL
Java_com_leaking_rollupvastsurges_hook_NativeStatusKt_checkIntegrityNative(JNIEnv *env, jclass clazz, jstring expectedCrc) {
    // 从 /proc/self/maps 找模块 APK 路径
    FILE *maps = fopen("/proc/self/maps", "r");
    if (!maps) return -1;
    char line[512];
    char apk_path[256] = {0};
    while (fgets(line, sizeof(line), maps)) {
        // 找包含 com.leaking.rollupvastsurges 的 base.apk
        if (strstr(line, "com.leaking.rollupvastsurges") && strstr(line, "base.apk")) {
            // 提取路径 (maps 行格式: addr-addr perm off dev inode path)
            char *p = strchr(line, '/');
            if (p) {
                strncpy(apk_path, p, sizeof(apk_path)-1);
                char *nl = strchr(apk_path, '\n');
                if (nl) *nl = '\0';
            }
            break;
        }
    }
    fclose(maps);
    if (apk_path[0] == 0) return -1;

    uint32_t crc = crc32_dex(apk_path);
    const char *exp = (*env)->GetStringUTFChars(env, expectedCrc, NULL);
    uint32_t exp_crc = exp ? (uint32_t)strtoul(exp, NULL, 16) : 0;
    if (exp) (*env)->ReleaseStringUTFChars(env, expectedCrc, exp);
    return (exp_crc == 0 || crc == exp_crc) ? 0 : 1;
}

#define FIELD_B_OFFSET  11
#define BTN_STYLUS      0x14C
#define ERASER_TIMEOUT_MS 30000

#define PREF_ERASER        "/data/system/penhook/eraser"
#define STATUS_FILE        "/data/system/penhook/status"
#define ERASER_ACTIVE_FILE "/data/system/penhook/eraser_mode_active"

static volatile int g_eraser_mode = 0;
static struct timespec g_last_pen_time;
static int g_last_pen_valid = 0;
static int g_check_counter = 0;
static int api_level = 0;

typedef void *(*process_t)(void *self, int *ev);
static process_t g_original = NULL;

// PreferStylusOverTouchBlocker
typedef void (*prefer_stylus_t)(void *self, void *event);
static prefer_stylus_t g_prefer_original = NULL;

static int file_read_int(const char *path, int def) {
    FILE *f = fopen(path, "r");
    if (!f) return def;
    int v = def; fscanf(f, "%d", &v); fclose(f);
    return v;
}

static void file_write_int(const char *path, int v) {
    FILE *f = fopen(path, "w");
    if (f) { fprintf(f, "%d", v); fclose(f); }
}

static int64_t now_ms() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

// ── 代理函数: TouchButtonAccumulator::process() ──
static void *proxied_process(void *self, int *ev) {
    void *result = NULL;
    if (g_original) result = g_original(self, ev);

    int type  = ev[5];
    int code  = ev[6];
    int value = ev[7];
    if (type != 1) return result; // not EV_KEY

    // 记录笔触时间
    if (code >= 0x140 && code <= 0x14F) {
        clock_gettime(CLOCK_MONOTONIC, &g_last_pen_time);
        g_last_pen_valid = 1;
    }

    // 超时自动关（每256次事件检查一次）
    if (g_eraser_mode && g_last_pen_valid) {
        int cnt = __atomic_add_fetch(&g_check_counter, 1, __ATOMIC_RELAXED);
        if ((cnt & 255) == 0) {
            if (now_ms() - ((int64_t)g_last_pen_time.tv_sec * 1000
                          + g_last_pen_time.tv_nsec / 1000000) > ERASER_TIMEOUT_MS) {
                __atomic_store_n(&g_eraser_mode, 0, __ATOMIC_SEQ_CST);
                file_write_int(PREF_ERASER, 0);
                file_write_int(ERASER_ACTIVE_FILE, 0);
            }
        }
    }

    // ① 笔侧键按下 → 切换橡皮擦
    if (code == BTN_STYLUS && value == 1) {
        int old = __atomic_exchange_n(&g_eraser_mode, !g_eraser_mode, __ATOMIC_SEQ_CST);
        int new_mode = !old;
        file_write_int(PREF_ERASER, new_mode);
        file_write_int(ERASER_ACTIVE_FILE, new_mode);
        LOGI("eraser toggle: %d -> %d", old, new_mode);
        return result;
    }

    // ② 橡皮擦模式：笔触事件设 field_B → getToolType 返回 ERASER(3)
    if (g_eraser_mode && code >= 0x140 && code <= 0x147) {
        ((unsigned char *)self)[FIELD_B_OFFSET] = 1;
    }
    return result;
}

// ── A13: PreferStylusOverTouchBlocker::processMotion() ──
static void proxied_prefer_stylus(void *self, void *event) {
    // 用全局状态 g_eraser_mode 判断，不读错误的偏移（不同类结构不同）
    if (!g_eraser_mode) {
        if (g_prefer_original) g_prefer_original(self, event);
        return;
    }
    // 橡皮擦模式：直接放行原函数
    if (g_prefer_original) g_prefer_original(self, event);
}

// ── 硬编码降级 ──
static int detect_api_level() {
    char prop[PROP_VALUE_MAX];
    if (__system_property_get("ro.build.version.sdk", prop) > 0)
        api_level = atoi(prop);
    if (!api_level) api_level = 28;
    LOGI("api_level=%d", api_level);
    return api_level;
}

static uintptr_t find_lib_base(const char *lib_name) {
    FILE *maps = fopen("/proc/self/maps", "r");
    if (!maps) return 0;
    char line[512];
    uintptr_t base = 0;
    while (fgets(line, sizeof(line), maps)) {
        if (strstr(line, "/system/") && strstr(line, lib_name) && strstr(line, "r-xp")) {
            char *dash = strchr(line, '-');
            if (dash) { *dash = '\0'; base = strtoul(line, NULL, 16); }
            break;
        }
    }
    fclose(maps);
    return base;
}

static uintptr_t find_sym_offset(const char *so_path, const char *sym) {
    FILE *f = fopen(so_path, "r");
    if (!f) return 0;
    // 快速双阶段：先找 .dynsym section，再找符号
    // 这里简化为走 /proc/self/maps + dlopen/dlsym 方式
    fclose(f);

    void *handle = dlopen(so_path, RTLD_NOLOAD | RTLD_NOW);
    if (!handle) return 0;
    void *addr = dlsym(handle, sym);
    if (!addr) { dlclose(handle); return 0; }

    uintptr_t base = find_lib_base(strrchr(so_path, '/') + 1);
    dlclose(handle);
    if (base == 0) return 0;
    return (uintptr_t)addr - base;
}

// ── 入口 ──
__attribute__((constructor))
static void penhook_init() {
    detect_api_level();
    file_write_int(STATUS_FILE, 1);

    int ret = shadowhook_init(SHADOWHOOK_MODE_SHARED, false);
    if (ret != 0) {
        LOGE("shadowhook_init failed: %d", ret);
        file_write_int(STATUS_FILE, 0);
        return;
    }

    // ── Hook 1: TouchButtonAccumulator::process() ──
    const char *so = (api_level >= 31) ? "/system/lib64/libinputreader.so"
                                       : "/system/lib64/libinputflinger.so";
    const char *sym = "_ZN7android22TouchButtonAccumulator7processEPKNS_8RawEventE";
    uintptr_t off = find_sym_offset(so, sym);
    if (off == 0) {
        // 硬编码回退
        if (api_level >= 33)      off = 0x67300;
        else if (api_level >= 31) off = 0x63158;
        else                      off = 0x45fe8;
    }
    uintptr_t base = find_lib_base(strrchr(so, '/') + 1);
    if (base > 0 && off > 0) {
        void *addr = (void *)(base + off);
        void *stub = shadowhook_hook_func_addr(addr, (void *)proxied_process, (void **)&g_original);
        if (stub) {
            LOGI("process hook OK: %s+0x%lx", so, (unsigned long)off);
        } else {
            LOGE("process hook FAILED: err=%d", shadowhook_get_errno());
        }
    }

    // ── Hook 2: PreferStylusOverTouchBlocker::processMotion() (A13+) ──
    if (api_level >= 33) {
        const char *so2 = "/system/lib64/libinputflinger.so";
        const char *sym2 = "_ZN7android28PreferStylusOverTouchBlocker13processMotionERKNS_16NotifyMotionArgsE";
        uintptr_t off2 = find_sym_offset(so2, sym2);
        if (off2 == 0) off2 = 0x68940;
        uintptr_t base2 = find_lib_base("libinputflinger.so");
        if (base2 > 0 && off2 > 0) {
            void *addr2 = (void *)(base2 + off2);
            void *stub2 = shadowhook_hook_func_addr(addr2, (void *)proxied_prefer_stylus, (void **)&g_prefer_original);
            if (stub2) {
                LOGI("prefer_stylus hook OK: %s+0x%lx", so2, (unsigned long)off2);
            } else {
                LOGW("prefer_stylus hook FAILED: err=%d", shadowhook_get_errno());
            }
        }
    }

    file_write_int(STATUS_FILE, 2);
    LOGI("penhook init done");
}
