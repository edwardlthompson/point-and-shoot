cmake_minimum_required(VERSION 3.22)
project(point_and_shoot_native LANGUAGES C CXX)

# C++23 mirrors the planned encoder bodies; the Phase 0 stubs are pure-C JNI
# so they would compile under any standard, but pinning ahead avoids one more
# moving part when libavif / libjxl FetchContent lands.
set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# `pns_native` ships a single shared object. Splitting the AVIF / JXL bodies
# into separate translation units happens when the encoder code lands - for
# Phase 0 a single .cpp keeps the build graph trivial.
add_library(pns_native SHARED
        pns_native.cpp
)

target_compile_definitions(pns_native PRIVATE
        PNS_PHASE0=1
)

# Position-independent code is required for shared libraries on Android.
set_target_properties(pns_native PROPERTIES
        POSITION_INDEPENDENT_CODE ON
)

# Phase 0 only needs JNI headers. When externalNativeBuild lands in
# `app/build.gradle.kts`, AGP injects `${ANDROID_NDK}/sysroot/usr/include`
# automatically; this find_path call keeps standalone CMake (host-side
# `cmake -B build`) from erroring out when ANDROID_NDK is unset.
if(DEFINED ANDROID_NDK)
        # AGP / NDK toolchain handles JNI include resolution.
else()
        find_path(JNI_INCLUDE_DIR jni.h
                PATHS
                "$ENV{JAVA_HOME}/include"
                /usr/lib/jvm/default-java/include
                /usr/lib/jvm/java-17-openjdk/include
                "$ENV{ProgramFiles}/Java/jdk-17/include"
                "$ENV{ProgramFiles}/Java/jdk-21/include"
                "$ENV{ProgramFiles}/Eclipse Adoptium/jdk-17.0.10.7-hotspot/include"
                NO_DEFAULT_PATH)
        if(JNI_INCLUDE_DIR)
                target_include_directories(pns_native PRIVATE
                        ${JNI_INCLUDE_DIR}
                        ${JNI_INCLUDE_DIR}/win32
                        ${JNI_INCLUDE_DIR}/linux
                        ${JNI_INCLUDE_DIR}/darwin
                )
        endif()
endif()

# ----------------------------------------------------------------------------
# Phase 1 (planned): wire libavif and libjxl via FetchContent.
#
# Kept commented so the Phase 0 build stays small and offline. The encoder
# bodies in pns_native.cpp gate on `PNS_USE_LIBAVIF` / `PNS_USE_LIBJXL` so
# the same .cpp compiles whether or not these are wired.
#
# include(FetchContent)
#
# FetchContent_Declare(libavif
#         URL https://github.com/AOMediaCodec/libavif/archive/refs/tags/<pinned>.tar.gz
#         URL_HASH SHA256=<64-char-lowercase-hex>
# )
# FetchContent_MakeAvailable(libavif)
# target_link_libraries(pns_native PRIVATE avif)
# target_compile_definitions(pns_native PRIVATE PNS_USE_LIBAVIF=1)
#
# FetchContent_Declare(libjxl
#         URL https://github.com/libjxl/libjxl/archive/refs/tags/<pinned>.tar.gz
#         URL_HASH SHA256=<64-char-lowercase-hex>
# )
# FetchContent_MakeAvailable(libjxl)
# target_link_libraries(pns_native PRIVATE jxl jxl_threads)
# target_compile_definitions(pns_native PRIVATE PNS_USE_LIBJXL=1)
# ----------------------------------------------------------------------------

# Standard Android system libraries the encoder bodies will need. Resolved by
# AGP when externalNativeBuild is wired; the `find_library` calls are guarded
# so a host-side `cmake -B build` (no NDK on PATH) doesn't fail.
if(ANDROID)
        find_library(LOG_LIB log)
        find_library(JNIGRAPHICS_LIB jnigraphics)
        if(LOG_LIB)
                target_link_libraries(pns_native PRIVATE ${LOG_LIB})
        endif()
        if(JNIGRAPHICS_LIB)
                target_link_libraries(pns_native PRIVATE ${JNIGRAPHICS_LIB})
        endif()
endif()
