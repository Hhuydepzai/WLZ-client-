#include <jni.h>
#include <android/log.h>
#include <array>
#include <atomic>
#include <cstdarg>
#include <mutex>

namespace {
constexpr char TAG[] = "WLZRuntime";
constexpr int MODULE_COUNT = 9;
constexpr int PARAM_COUNT = 6;

std::atomic<bool> g_initialized{false};
std::array<std::atomic<bool>, MODULE_COUNT> g_modules{};
std::array<std::atomic<int>, PARAM_COUNT> g_params{};
std::once_flag g_init_once;

void initialize_state() {
    for (auto &m : g_modules) m.store(false);
    for (auto &p : g_params) p.store(0);
    g_params[0].store(150);  // Zoom percent
    g_params[1].store(60);   // FPS target preference
    g_params[2].store(100);  // Fullbright percent
    g_params[3].store(1);    // Fix lag profile
    g_params[4].store(0);    // Remove heavy effects
    g_params[5].store(4);    // Texture colors
}

void logi(const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    __android_log_vprint(ANDROID_LOG_INFO, TAG, fmt, args);
    va_end(args);
}
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*) {
    std::call_once(g_init_once, initialize_state);
    logi("WLZ native runtime loaded");
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wlz_client_WlzRuntimeBridge_nativeInitialize(JNIEnv*, jclass) {
    std::call_once(g_init_once, initialize_state);
    g_initialized.store(true);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wlz_client_WlzRuntimeBridge_nativeIsLoaded(JNIEnv*, jclass) {
    return g_initialized.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_wlz_client_WlzRuntimeBridge_nativeSetModule(JNIEnv*, jclass, jint index, jboolean enabled) {
    if (index < 0 || index >= MODULE_COUNT) return;
    g_modules[static_cast<size_t>(index)].store(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wlz_client_WlzRuntimeBridge_nativeGetModule(JNIEnv*, jclass, jint index) {
    if (index < 0 || index >= MODULE_COUNT) return JNI_FALSE;
    return g_modules[static_cast<size_t>(index)].load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_wlz_client_WlzRuntimeBridge_nativeSetParam(JNIEnv*, jclass, jint key, jint value) {
    if (key < 0 || key >= PARAM_COUNT) return;
    g_params[static_cast<size_t>(key)].store(value);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wlz_client_WlzRuntimeBridge_nativeGetParam(JNIEnv*, jclass, jint key) {
    if (key < 0 || key >= PARAM_COUNT) return 0;
    return g_params[static_cast<size_t>(key)].load();
}
