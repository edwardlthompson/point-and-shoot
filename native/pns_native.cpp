// JNI implementation for `dev.pointandshoot.NativeEncoders`. Optional libavif /
// libjxl encode bodies when built with the Android NDK + FetchContent (see
// `native/CMakeLists.txt`).

#include <jni.h>

#if defined(ANDROID) && defined(PNS_USE_LIBAVIF)
#include <avif/avif.h>
#include <cstring>
#endif

#if defined(ANDROID) && defined(PNS_USE_LIBJXL)
#include <jxl/color_encoding.h>
#include <jxl/encode.h>
#include <jxl/types.h>
#include <cstring>
#include <vector>
#endif

#ifndef PNS_NATIVE_VERSION_VALUE
#define PNS_NATIVE_VERSION_VALUE 0
#endif

namespace {

constexpr jint PNS_NATIVE_VERSION = PNS_NATIVE_VERSION_VALUE;

}  // namespace

#if defined(ANDROID) && defined(PNS_USE_LIBAVIF)
namespace {

bool validate_yuv10(int width, int height, int stride_y, int stride_uv, jsize len_y, jsize len_u,
                  jsize len_v) {
    if (width <= 0 || height <= 0) {
        return false;
    }
    if (stride_y < width * 2) {
        return false;
    }
    const int cw = (width + 1) / 2;
    const int ch = (height + 1) / 2;
    if (stride_uv < cw * 2) {
        return false;
    }
    const auto need_y = static_cast<size_t>(stride_y) * static_cast<size_t>(height);
    const auto need_uv = static_cast<size_t>(stride_uv) * static_cast<size_t>(ch);
    return static_cast<size_t>(len_y) >= need_y && static_cast<size_t>(len_u) >= need_uv &&
           static_cast<size_t>(len_v) >= need_uv;
}

jbyteArray encode_avif(JNIEnv* env, const uint8_t* py, const uint8_t* pu, const uint8_t* pv, int width,
                     int height, int stride_y, int stride_uv) {
    avifImage* image = avifImageCreate(static_cast<uint32_t>(width), static_cast<uint32_t>(height), 10,
                                       AVIF_PIXEL_FORMAT_YUV420);
    if (!image) {
        return nullptr;
    }

    image->yuvRange = AVIF_RANGE_FULL;
    image->colorPrimaries = AVIF_COLOR_PRIMARIES_SMPTE432;
    image->transferCharacteristics = AVIF_TRANSFER_CHARACTERISTICS_PQ;
    image->matrixCoefficients = AVIF_MATRIX_COEFFICIENTS_BT2020_NCL;

    if (avifImageAllocatePlanes(image, AVIF_PLANES_YUV) != AVIF_RESULT_OK) {
        avifImageDestroy(image);
        return nullptr;
    }

    const uint32_t chroma_w = (static_cast<uint32_t>(width) + 1u) / 2u;
    const uint32_t chroma_h = (static_cast<uint32_t>(height) + 1u) / 2u;
    const uint32_t row_y = image->yuvRowBytes[AVIF_CHAN_Y];
    const uint32_t row_u = image->yuvRowBytes[AVIF_CHAN_U];
    const uint32_t row_v = image->yuvRowBytes[AVIF_CHAN_V];

    for (int y = 0; y < height; ++y) {
        uint8_t* dst_row = image->yuvPlanes[AVIF_CHAN_Y] + static_cast<size_t>(y) * row_y;
        std::memcpy(dst_row, py + static_cast<size_t>(y) * static_cast<size_t>(stride_y),
                    static_cast<size_t>(width) * 2u);
    }
    for (uint32_t y = 0; y < chroma_h; ++y) {
        uint8_t* dst_u = image->yuvPlanes[AVIF_CHAN_U] + static_cast<size_t>(y) * row_u;
        uint8_t* dst_v = image->yuvPlanes[AVIF_CHAN_V] + static_cast<size_t>(y) * row_v;
        std::memcpy(dst_u, pu + static_cast<size_t>(y) * static_cast<size_t>(stride_uv), chroma_w * 2u);
        std::memcpy(dst_v, pv + static_cast<size_t>(y) * static_cast<size_t>(stride_uv), chroma_w * 2u);
    }

    avifEncoder* encoder = avifEncoderCreate();
    if (!encoder) {
        avifImageDestroy(image);
        return nullptr;
    }
    encoder->speed = 6;

    avifRWData output = AVIF_DATA_EMPTY;
    avifResult enc_result = avifEncoderWrite(encoder, image, &output);
    avifEncoderDestroy(encoder);
    avifImageDestroy(image);

    if (enc_result != AVIF_RESULT_OK || output.size == 0) {
        avifRWDataFree(&output);
        return nullptr;
    }

    jbyteArray out = env->NewByteArray(static_cast<jsize>(output.size));
    if (!out) {
        avifRWDataFree(&output);
        return nullptr;
    }
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(output.size),
                            reinterpret_cast<const jbyte*>(output.data));
    avifRWDataFree(&output);
    return out;
}

}  // namespace
#endif

#if defined(ANDROID) && defined(PNS_USE_LIBJXL)
namespace {

bool validate_rgb12(int width, int height, int stride, jsize len) {
    if (width <= 0 || height <= 0) {
        return false;
    }
    if (stride < width * 6) {
        return false;
    }
    const auto need = static_cast<size_t>(stride) * static_cast<size_t>(height);
    return static_cast<size_t>(len) >= need;
}

jbyteArray encode_jxl(JNIEnv* env, const uint8_t* rgb, int width, int height, int stride) {
    const size_t px = static_cast<size_t>(width) * static_cast<size_t>(height);
    std::vector<float> pixels(px * 3);
    constexpr float k_scale = 1.0f / 4095.0f;
    for (int y = 0; y < height; ++y) {
        const uint8_t* row = rgb + static_cast<size_t>(y) * static_cast<size_t>(stride);
        for (int x = 0; x < width; ++x) {
            const size_t base = static_cast<size_t>(y) * static_cast<size_t>(width) + static_cast<size_t>(x);
            const uint8_t* p = row + static_cast<size_t>(x) * 6u;
            const uint16_t r = static_cast<uint16_t>(p[0]) | (static_cast<uint16_t>(p[1]) << 8);
            const uint16_t g = static_cast<uint16_t>(p[2]) | (static_cast<uint16_t>(p[3]) << 8);
            const uint16_t b = static_cast<uint16_t>(p[4]) | (static_cast<uint16_t>(p[5]) << 8);
            pixels[base * 3 + 0] = static_cast<float>(r) * k_scale;
            pixels[base * 3 + 1] = static_cast<float>(g) * k_scale;
            pixels[base * 3 + 2] = static_cast<float>(b) * k_scale;
        }
    }

    JxlEncoder* enc = JxlEncoderCreate(nullptr);
    if (!enc) {
        return nullptr;
    }

    JxlBasicInfo basic_info;
    JxlEncoderInitBasicInfo(&basic_info);
    basic_info.xsize = static_cast<uint32_t>(width);
    basic_info.ysize = static_cast<uint32_t>(height);
    basic_info.bits_per_sample = 32;
    basic_info.exponent_bits_per_sample = 8;
    basic_info.uses_original_profile = JXL_FALSE;
    if (JxlEncoderSetBasicInfo(enc, &basic_info) != JXL_ENC_SUCCESS) {
        JxlEncoderDestroy(enc);
        return nullptr;
    }

    JxlColorEncoding color_encoding = {};
    color_encoding.color_space = JXL_COLOR_SPACE_RGB;
    color_encoding.white_point = JXL_WHITE_POINT_D65;
    color_encoding.primaries = JXL_PRIMARIES_2100;
    color_encoding.transfer_function = JXL_TRANSFER_FUNCTION_LINEAR;
    color_encoding.rendering_intent = JXL_RENDERING_INTENT_RELATIVE;
    if (JxlEncoderSetColorEncoding(enc, &color_encoding) != JXL_ENC_SUCCESS) {
        JxlEncoderDestroy(enc);
        return nullptr;
    }

    JxlEncoderFrameSettings* frame_settings = JxlEncoderFrameSettingsCreate(enc, nullptr);
    JxlPixelFormat pixel_format = {3, JXL_TYPE_FLOAT, JXL_NATIVE_ENDIAN, 0};
    if (JxlEncoderAddImageFrame(frame_settings, &pixel_format, pixels.data(),
                                sizeof(float) * pixels.size()) != JXL_ENC_SUCCESS) {
        JxlEncoderDestroy(enc);
        return nullptr;
    }
    JxlEncoderCloseInput(enc);

    std::vector<uint8_t> compressed(64);
    uint8_t* next_out = compressed.data();
    size_t avail_out = compressed.size();
    JxlEncoderStatus process_result = JXL_ENC_NEED_MORE_OUTPUT;
    while (process_result == JXL_ENC_NEED_MORE_OUTPUT) {
        process_result = JxlEncoderProcessOutput(enc, &next_out, &avail_out);
        if (process_result == JXL_ENC_NEED_MORE_OUTPUT) {
            const size_t offset = static_cast<size_t>(next_out - compressed.data());
            compressed.resize(compressed.size() * 2);
            next_out = compressed.data() + offset;
            avail_out = compressed.size() - offset;
        }
    }
    JxlEncoderDestroy(enc);

    if (process_result != JXL_ENC_SUCCESS) {
        return nullptr;
    }
    const size_t out_size = static_cast<size_t>(next_out - compressed.data());
    compressed.resize(out_size);

    jbyteArray out = env->NewByteArray(static_cast<jsize>(compressed.size()));
    if (!out) {
        return nullptr;
    }
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(compressed.size()),
                            reinterpret_cast<const jbyte*>(compressed.data()));
    return out;
}

}  // namespace
#endif

extern "C" {

JNIEXPORT jint JNICALL Java_dev_pointandshoot_Native_version(JNIEnv* /*env*/, jclass /*clazz*/) {
    return PNS_NATIVE_VERSION;
}

JNIEXPORT jint JNICALL Java_dev_pointandshoot_NativeEncoders_nativeVersion(JNIEnv* /*env*/, jclass /*clazz*/) {
    return PNS_NATIVE_VERSION;
}

JNIEXPORT jbyteArray JNICALL Java_dev_pointandshoot_NativeEncoders_nativeEncodeAvif10Hdr(
        JNIEnv* env, jclass /*clazz*/, jbyteArray plane_y, jbyteArray plane_u, jbyteArray plane_v, jint width,
        jint height, jint stride_y, jint stride_uv) {
#if defined(ANDROID) && defined(PNS_USE_LIBAVIF)
    if (!plane_y || !plane_u || !plane_v) {
        return nullptr;
    }
    const jsize len_y = env->GetArrayLength(plane_y);
    const jsize len_u = env->GetArrayLength(plane_u);
    const jsize len_v = env->GetArrayLength(plane_v);
    if (!validate_yuv10(width, height, stride_y, stride_uv, len_y, len_u, len_v)) {
        return nullptr;
    }

    jbyte* py = env->GetByteArrayElements(plane_y, nullptr);
    jbyte* pu = env->GetByteArrayElements(plane_u, nullptr);
    jbyte* pv = env->GetByteArrayElements(plane_v, nullptr);
    if (!py || !pu || !pv) {
        if (py) {
            env->ReleaseByteArrayElements(plane_y, py, JNI_ABORT);
        }
        if (pu) {
            env->ReleaseByteArrayElements(plane_u, pu, JNI_ABORT);
        }
        if (pv) {
            env->ReleaseByteArrayElements(plane_v, pv, JNI_ABORT);
        }
        return nullptr;
    }

    jbyteArray out =
            encode_avif(env, reinterpret_cast<const uint8_t*>(py), reinterpret_cast<const uint8_t*>(pu),
                        reinterpret_cast<const uint8_t*>(pv), width, height, stride_y, stride_uv);

    env->ReleaseByteArrayElements(plane_y, py, JNI_ABORT);
    env->ReleaseByteArrayElements(plane_u, pu, JNI_ABORT);
    env->ReleaseByteArrayElements(plane_v, pv, JNI_ABORT);
    return out;
#else
    (void)env;
    (void)plane_y;
    (void)plane_u;
    (void)plane_v;
    (void)width;
    (void)height;
    (void)stride_y;
    (void)stride_uv;
    return nullptr;
#endif
}

JNIEXPORT jbyteArray JNICALL Java_dev_pointandshoot_NativeEncoders_nativeEncodeJxl12Rec2020(
        JNIEnv* env, jclass /*clazz*/, jbyteArray plane_rgb, jint width, jint height, jint stride) {
#if defined(ANDROID) && defined(PNS_USE_LIBJXL)
    if (!plane_rgb) {
        return nullptr;
    }
    const jsize len = env->GetArrayLength(plane_rgb);
    if (!validate_rgb12(width, height, stride, len)) {
        return nullptr;
    }

    jbyte* pr = env->GetByteArrayElements(plane_rgb, nullptr);
    if (!pr) {
        return nullptr;
    }

    jbyteArray out = encode_jxl(env, reinterpret_cast<const uint8_t*>(pr), width, height, stride);

    env->ReleaseByteArrayElements(plane_rgb, pr, JNI_ABORT);
    return out;
#else
    (void)env;
    (void)plane_rgb;
    (void)width;
    (void)height;
    (void)stride;
    return nullptr;
#endif
}

}  // extern "C"
