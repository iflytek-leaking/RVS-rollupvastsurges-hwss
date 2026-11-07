cmake_minimum_required(VERSION 3.18)
project(mdmdisabler VERSION 1.0 LANGUAGES C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# ── 后端规则表: 独立 so, 只持有加密规则表 ──
add_library(mdmrules SHARED
    src/rules.c
    src/rules_jni.c
)
target_link_libraries(mdmrules log)
target_include_directories(mdmrules PRIVATE src)

# ── 笔按键 native hook: 与规则表解耦 ──
add_library(penhook SHARED
    src/penhook.c
)
find_package(shadowhook REQUIRED CONFIG)
target_link_libraries(penhook shadowhook::shadowhook log)
target_include_directories(penhook PRIVATE src)
