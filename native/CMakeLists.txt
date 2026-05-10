cmake_minimum_required(VERSION 3.22)
project(point_and_shoot_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

add_library(pns_native SHARED
        pns_native.cpp
)

set_target_properties(pns_native PROPERTIES
        POSITION_INDEPENDENT_CODE ON
)

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

# -----------------------------------------------------------------------------
# Android NDK: libavif (SVT-AV1) + libjxl via FetchContent — real encoder bodies.
# -----------------------------------------------------------------------------
if(ANDROID)
        include(FetchContent)
        if(POLICY CMP0135)
                cmake_policy(SET CMP0135 NEW)
        endif()

        # SVT-AV1 merges static libs with bare `llvm-ar`/`llvm-ranlib` in Ninja rules. Pin archive tools and
        # prepend the NDK LLVM bin dir to PATH so nested CMake (libavif → SVT) subprocesses resolve them on Windows.
        get_filename_component(_pns_llvm_bin "${CMAKE_C_COMPILER}" DIRECTORY)
        set(CMAKE_AR "${_pns_llvm_bin}/llvm-ar${CMAKE_EXECUTABLE_SUFFIX}" CACHE FILEPATH "" FORCE)
        set(CMAKE_RANLIB "${_pns_llvm_bin}/llvm-ranlib${CMAKE_EXECUTABLE_SUFFIX}" CACHE FILEPATH "" FORCE)
        if(CMAKE_HOST_WIN32)
                set(ENV{PATH} "${_pns_llvm_bin};$ENV{PATH}")
        else()
                set(ENV{PATH} "${_pns_llvm_bin}:$ENV{PATH}")
        endif()

        # SVT-AV1 (via libavif): libaom builds require Perl on the host for RTCD generation,
        # which is brittle on Windows CI/dev boxes. SVT-AV1 encodes AV1 without that dependency.
        # Android ARM uses the NDK Clang assembler (not host `as`).
        set(CMAKE_ASM_COMPILER "${CMAKE_C_COMPILER}" CACHE FILEPATH "Assembler for SVT-AV1 / deps" FORCE)

        # Static upstream libs linked into libpns_native.so (single JNI module).
        set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

        # --- libavif + SVT-AV1 (LOCAL encoder; FetchContent clones GitLab + builds statically) ---
        set(AVIF_CODEC_AOM "OFF" CACHE STRING "" FORCE)
        set(AVIF_CODEC_SVT "LOCAL" CACHE STRING "" FORCE)
        set(AVIF_BUILD_TESTS OFF CACHE BOOL "" FORCE)
        set(AVIF_BUILD_APPS OFF CACHE BOOL "" FORCE)
        set(AVIF_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
        set(AVIF_LIBYUV "OFF" CACHE STRING "" FORCE)
        set(AVIF_ZLIBPNG "OFF" CACHE STRING "" FORCE)
        set(AVIF_JPEG "OFF" CACHE STRING "" FORCE)

        FetchContent_Declare(libavif
                URL https://github.com/AOMediaCodec/libavif/archive/refs/tags/v1.2.1.tar.gz
                URL_HASH SHA256=9c859c7c12ccb0f407511bfe303e6a7247f5f6738f54852662c6df8048daddf4
        )
        FetchContent_MakeAvailable(libavif)

        # --- libjxl (GitHub tarball has empty third_party; pin submodules per upstream deps.sh) ---
        set(JPEGXL_ENABLE_TOOLS OFF CACHE BOOL "" FORCE)
        set(JPEGXL_ENABLE_BENCHMARK OFF CACHE BOOL "" FORCE)
        set(JPEGXL_ENABLE_EXAMPLES OFF CACHE BOOL "" FORCE)
        set(JPEGXL_ENABLE_DOXYGEN OFF CACHE BOOL "" FORCE)
        set(JPEGXL_ENABLE_MANPAGES OFF CACHE BOOL "" FORCE)
        set(JPEGXL_ENABLE_JNI OFF CACHE BOOL "" FORCE)
        set(JPEGXL_ENABLE_OPENEXR OFF CACHE BOOL "" FORCE)
        set(JPEGXL_ENABLE_PLUGINS OFF CACHE BOOL "" FORCE)
        set(JPEGXL_ENABLE_DEVTOOLS OFF CACHE BOOL "" FORCE)
        set(JPEGXL_ENABLE_JPEGLI OFF CACHE BOOL "" FORCE)
        set(JPEGXL_ENABLE_SJPEG OFF CACHE BOOL "" FORCE)
        set(JPEGXL_WARNINGS_AS_ERRORS OFF CACHE BOOL "" FORCE)
        set(BUILD_TESTING OFF CACHE BOOL "" FORCE)

        # Pin full-tree tarballs (deps.sh SHAs). Git shallow clones can fail on Windows when a
        # commit is not advertised as a branch tip.
        FetchContent_Declare(highway_vendor
                URL https://github.com/google/highway/archive/457c891775a7397bdb0376bb1031e6e027af1c48.tar.gz
                URL_HASH SHA256=5124b0501c98d9930dbb065bfa1a5bbbd59ce0f12facb7e1e33aaef01a5f1f1a
        )
        FetchContent_Declare(brotli_vendor
                URL https://github.com/google/brotli/archive/36533a866ed1ca4b75cf049f4521e4ec5fe24727.tar.gz
                URL_HASH SHA256=9dbeae5b67739ad00f8e355a20f9af5507ed578abace0a4939a54c6c2b597005
        )
        # skcms: googlesource archive tarballs are not byte-stable; shallow git clones may fail on
        # Windows for commit-only SHAs — use a full fetch (repo is tiny).
        FetchContent_Declare(skcms_vendor
                GIT_REPOSITORY https://skia.googlesource.com/skcms
                GIT_TAG 42030a771244ba67f86b1c1c76a6493f873c5f91
        )
        FetchContent_Declare(libjxl
                URL https://github.com/libjxl/libjxl/archive/refs/tags/v0.11.1.tar.gz
                URL_HASH SHA256=1492dfef8dd6c3036446ac3b340005d92ab92f7d48ee3271b5dac1d36945d3d9
        )

        FetchContent_Populate(libjxl)
        FetchContent_Populate(highway_vendor)
        FetchContent_Populate(brotli_vendor)
        FetchContent_Populate(skcms_vendor)

        set(_pns_libjxl_tp "${libjxl_SOURCE_DIR}/third_party")
        file(MAKE_DIRECTORY "${_pns_libjxl_tp}")
        file(REMOVE_RECURSE "${_pns_libjxl_tp}/highway")
        file(COPY "${highway_vendor_SOURCE_DIR}/" DESTINATION "${_pns_libjxl_tp}/highway")
        file(REMOVE_RECURSE "${_pns_libjxl_tp}/brotli")
        file(COPY "${brotli_vendor_SOURCE_DIR}/" DESTINATION "${_pns_libjxl_tp}/brotli")
        file(REMOVE_RECURSE "${_pns_libjxl_tp}/skcms")
        file(COPY "${skcms_vendor_SOURCE_DIR}/" DESTINATION "${_pns_libjxl_tp}/skcms")

        add_subdirectory("${libjxl_SOURCE_DIR}" "${CMAKE_BINARY_DIR}/_deps/libjxl-build")

        target_link_libraries(pns_native PRIVATE avif jxl jxl_threads)
        target_compile_definitions(pns_native PRIVATE
                PNS_USE_LIBAVIF=1
                PNS_USE_LIBJXL=1
                PNS_NATIVE_VERSION_VALUE=1
        )
else()
        target_compile_definitions(pns_native PRIVATE
                PNS_NATIVE_VERSION_VALUE=0
        )
endif()

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
