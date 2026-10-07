#include <jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <elf.h>
#include <sys/mman.h>
#include <unistd.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <cstdarg>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <thread>
#include <vector>

namespace {
constexpr const char* TAG = "WLZRuntime";
constexpr const char* MC_MODULE = "libminecraftpe.so";
constexpr int MODULE_COUNT = 9;
constexpr int PARAM_COUNT = 6;
constexpr size_t INLINE_HEAD = 16;
constexpr size_t FULLBRIGHT_HEAD = 12;

std::atomic<bool> g_initialized{false};
std::atomic<bool> g_mcReady{false};
std::atomic<uint32_t> g_caps{0};
std::array<std::atomic<bool>, MODULE_COUNT> g_modules{};
std::array<std::atomic<int>, PARAM_COUNT> g_params{};
std::once_flag g_initOnce;
std::mutex g_patchMutex;

struct ModuleRange { uintptr_t base=0, start=0, end=0; };
struct Patch { void* addr=nullptr; std::vector<uint8_t> original; bool active=false; };

Patch g_fullbright;
void* g_zoomTrampoline = nullptr;
void* g_turnTrampoline = nullptr;

using ZoomFn = float (*)(void*);
using SwapFn = EGLBoolean (*)(EGLDisplay, EGLSurface);

ZoomFn g_zoomOrig = nullptr;
SwapFn g_swapOrig = nullptr;
void** g_swapGot = nullptr;

bool g_zoomHooked=false, g_freeLookVectorHooked=false;
bool g_swapHooked=false, g_fullbrightResolved=false;
std::atomic<bool> g_capsReady{false};

constexpr uintptr_t MC_ZOOM_OFFSET = 0xAAC9CE4ULL;
// Exact Apollon FreeLook target in the same libminecraftpe.so build.
constexpr uintptr_t MC_FREELOOK_VECTOR_OFFSET = 0xF578E60ULL;

struct Vec2 { float x; float y; };

void logi(const char* fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    __android_log_vprint(ANDROID_LOG_INFO, TAG, fmt, ap);
    va_end(ap);
}

std::vector<int> parsePattern(const char* sig) {
    std::vector<int> out;
    const char* p=sig;
    while (*p) {
        while (*p==' ' || *p=='\t') ++p;
        if (!*p) break;
        if (*p=='?') { ++p; if (*p=='?') ++p; out.push_back(-1); continue; }
        if (!p[1]) return {};
        auto hex=[](char c)->int {
            if (c>='0'&&c<='9') return c-'0';
            if (c>='a'&&c<='f') return c-'a'+10;
            if (c>='A'&&c<='F') return c-'A'+10;
            return -1;
        };
        const int hi=hex(*p++), lo=hex(*p++);
        if (hi<0 || lo<0) return {};
        out.push_back((hi<<4)|lo);
    }
    return out;
}

std::vector<ModuleRange> moduleRanges() {
    std::vector<ModuleRange> out;
    FILE* f=std::fopen("/proc/self/maps","r");
    if (!f) return out;

    uintptr_t base=UINTPTR_MAX;
    char line[1024];
    while (std::fgets(line,sizeof(line),f)) {
        unsigned long s=0,e=0,off=0;
        char perm[8]={}, path[512]={};
        int n=std::sscanf(line,"%lx-%lx %7s %lx %*s %*s %511[^\n]",&s,&e,perm,&off,path);
        if (n<5 || !std::strstr(path,MC_MODULE)) continue;
        if (off==0 && s<base) base=(uintptr_t)s;
    }
    std::rewind(f);
    while (std::fgets(line,sizeof(line),f)) {
        unsigned long s=0,e=0,off=0;
        char perm[8]={}, path[512]={};
        int n=std::sscanf(line,"%lx-%lx %7s %lx %*s %*s %511[^\n]",&s,&e,perm,&off,path);
        if (n<5 || !std::strstr(path,MC_MODULE) || base==UINTPTR_MAX) continue;
        if (perm[0]=='r' && perm[2]=='x')
            out.push_back({base,(uintptr_t)s,(uintptr_t)e});
    }
    std::fclose(f);
    return out;
}

uintptr_t resolveSignature(const char* sig) {
    const auto p=parsePattern(sig);
    if (p.empty()) return 0;
    uintptr_t found=0;
    int hits=0;
    for (const auto& r: moduleRanges()) {
        const size_t len=r.end-r.start;
        if (len<p.size()) continue;
        const uint8_t* b=reinterpret_cast<const uint8_t*>(r.start);
        for (size_t i=0;i+p.size()<=len;++i) {
            bool ok=true;
            for (size_t j=0;j<p.size();++j) {
                if (p[j]>=0 && b[i+j]!=static_cast<uint8_t>(p[j])) { ok=false; break; }
            }
            if (!ok) continue;
            found=r.start+i;
            if (++hits>1) return 0;
        }
    }
    return hits==1 ? found : 0;
}

bool setExecWrite(void* addr,size_t len) {
    const long ps=sysconf(_SC_PAGESIZE);
    if (ps<=0 || !addr || !len) return false;
    uintptr_t start=reinterpret_cast<uintptr_t>(addr)&~(static_cast<uintptr_t>(ps)-1);
    uintptr_t end=(reinterpret_cast<uintptr_t>(addr)+len+ps-1)&~(static_cast<uintptr_t>(ps)-1);
    return mprotect(reinterpret_cast<void*>(start),end-start,PROT_READ|PROT_WRITE|PROT_EXEC)==0;
}

bool writeCode(void* addr,const void* data,size_t len) {
    if (!setExecWrite(addr,len)) return false;
    std::memcpy(addr,data,len);
    __builtin___clear_cache(reinterpret_cast<char*>(addr),reinterpret_cast<char*>(addr)+len);
    const long ps=sysconf(_SC_PAGESIZE);
    if (ps>0) {
        uintptr_t start=reinterpret_cast<uintptr_t>(addr)&~(static_cast<uintptr_t>(ps)-1);
        uintptr_t end=(reinterpret_cast<uintptr_t>(addr)+len+ps-1)&~(static_cast<uintptr_t>(ps)-1);
        mprotect(reinterpret_cast<void*>(start),end-start,PROT_READ|PROT_EXEC);
    }
    return true;
}

void absJump16(uint8_t out[16],uintptr_t target) {
    const uint32_t ldrX17=0x58000051U; // ldr x17, #8
    const uint32_t brX17 =0xD61F0220U; // br x17
    std::memcpy(out+0,&ldrX17,4);
    std::memcpy(out+4,&brX17,4);
    std::memcpy(out+8,&target,8);
}

void* installInlineHook(void* target,void* detour,void** originalOut) {
    if (!target || !detour || !originalOut) return nullptr;
    constexpr size_t trampSize=64;
    void* tramp=mmap(nullptr,trampSize,PROT_READ|PROT_WRITE|PROT_EXEC,MAP_PRIVATE|MAP_ANONYMOUS,-1,0);
    if (tramp==MAP_FAILED) return nullptr;

    std::memcpy(tramp,target,INLINE_HEAD);
    uint8_t back[16]{};
    absJump16(back,reinterpret_cast<uintptr_t>(target)+INLINE_HEAD);
    std::memcpy(reinterpret_cast<uint8_t*>(tramp)+INLINE_HEAD,back,16);
    __builtin___clear_cache(reinterpret_cast<char*>(tramp),reinterpret_cast<char*>(tramp)+32);

    uint8_t jump[16]{};
    absJump16(jump,reinterpret_cast<uintptr_t>(detour));
    if (!writeCode(target,jump,sizeof(jump))) {
        munmap(tramp,trampSize);
        return nullptr;
    }
    *originalOut=tramp;
    return tramp;
}

float zoomHook(void* self) {
    // Apollon's Zoom callback replaces the camera/FOV result with 0.1f
    // while Zoom is enabled. Reimplemented here without copying its binary.
    if (g_modules[0].load()) return 0.1f;
    return g_zoomOrig ? g_zoomOrig(self) : 0.1f;
}

struct Vec3 {
    float x;
    float y;
    float z;
};

void freeLookVectorHook(void* self, void* vecPtr) {
    if (!self || !vecPtr) return;

    // This reproduces Apollon's exact target behavior:
    // state = *(self + 0x208), then copy vec.{x,y,z} into
    // state.{x,y,z}. In FreeLook mode 2, x/z are replaced with the
    // current state values so camera movement no longer rotates the player.
    uintptr_t state = 0;
    std::memcpy(&state, reinterpret_cast<uint8_t*>(self) + 0x208, sizeof(state));
    if (state < 0x1000ULL) return;

    Vec3 incoming{};
    std::memcpy(&incoming, vecPtr, sizeof(incoming));

    if (g_modules[1].load()) {
        float keepX = 0.0f;
        float keepZ = 0.0f;
        std::memcpy(&keepX, reinterpret_cast<uint8_t*>(state) + 0x18, sizeof(keepX));
        std::memcpy(&keepZ, reinterpret_cast<uint8_t*>(state) + 0x20, sizeof(keepZ));

        // Apollon mode == 2.
        incoming.x = keepX;
        incoming.z = keepZ;
    }

    std::memcpy(reinterpret_cast<uint8_t*>(state) + 0x18, &incoming.x, sizeof(float));
    std::memcpy(reinterpret_cast<uint8_t*>(state) + 0x1c, &incoming.y, sizeof(float));
    std::memcpy(reinterpret_cast<uint8_t*>(state) + 0x20, &incoming.z, sizeof(float));
}

uintptr_t moduleAddress(uintptr_t offset) {
    for (const auto& r : moduleRanges()) {
        const uintptr_t span = r.end - r.base;
        if (offset < span && offset + INLINE_HEAD <= span)
            return r.base + offset;
    }
    return 0;
}

bool fixedPrologMatches(uintptr_t offset, const uint8_t expected[INLINE_HEAD]) {
    const uintptr_t addr = moduleAddress(offset);
    if (!addr) return false;
    uint8_t got[INLINE_HEAD]{};
    std::memcpy(got, reinterpret_cast<const void*>(addr), sizeof(got));
    return std::memcmp(got, expected, sizeof(got)) == 0;
}

bool installFixedHook(
    uintptr_t offset,
    const uint8_t expected[INLINE_HEAD],
    void* detour,
    void** originalOut) {
    const uintptr_t addr = moduleAddress(offset);
    if (!addr || !fixedPrologMatches(offset, expected)) return false;
    return installInlineHook(
        reinterpret_cast<void*>(addr), detour, originalOut) != nullptr;
}

EGLBoolean swapHook(EGLDisplay display,EGLSurface surface) {
    if (!g_swapOrig) return EGL_FALSE;
    if (display!=EGL_NO_DISPLAY) eglSwapInterval(display,g_modules[3].load()?0:1);
    return g_swapOrig(display,surface);
}

void installZoomHook() {
    if (g_zoomHooked) return;
    static const uint8_t PROLOG[INLINE_HEAD] = {
        0xEA,0x0F,0x1D,0xFC, 0xE9,0xA3,0x00,0x6D,
        0xFD,0xFB,0x01,0xA9, 0xF3,0x17,0x00,0xF9
    };
    void* tramp=nullptr;
    if (installFixedHook(
            MC_ZOOM_OFFSET, PROLOG,
            reinterpret_cast<void*>(&zoomHook),
            &tramp)) {
        g_zoomOrig=reinterpret_cast<ZoomFn>(tramp);
        g_zoomTrampoline=tramp;
        g_zoomHooked=true;
        logi("Apollon Zoom target hooked @ +0x%llX",
             static_cast<unsigned long long>(MC_ZOOM_OFFSET));
    }
}

void installFreeLookHook() {
    if (g_freeLookVectorHooked) return;

    static const uint8_t PROLOG[INLINE_HEAD] = {
        0x08,0x04,0x41,0xF9, 0x29,0x08,0x40,0xB9,
        0x2A,0x00,0x40,0xF9, 0x09,0x20,0x00,0xB9
    };

    const uintptr_t addr = moduleAddress(MC_FREELOOK_VECTOR_OFFSET);
    if (!addr || !fixedPrologMatches(MC_FREELOOK_VECTOR_OFFSET, PROLOG)) return;

    uint8_t jump[INLINE_HEAD]{};
    absJump16(jump, reinterpret_cast<uintptr_t>(&freeLookVectorHook));
    if (writeCode(reinterpret_cast<void*>(addr), jump, sizeof(jump))) {
        g_freeLookVectorHooked = true;
        logi("Apollon FreeLook vector hook installed @ +0x%llX",
             static_cast<unsigned long long>(MC_FREELOOK_VECTOR_OFFSET));
    }
}

bool resolveFullbright() {
    if (g_fullbrightResolved) return true;
    uintptr_t a=resolveSignature("? ? ? A9 FD 03 00 91 ? ? ? F9 ? ? ? 52 ? ? ? F9 00 01 3F D6 ? ? ? A8 ? ? ? 14 ? ? ? A9 ? ? ? F9 FD 03 00 91 ? ? ? F9 F3 03 01 2A ? ? ? 52 ? ? ? F9 00 01 3F D6 E1 03 13 2A ? ? ? F9 ? ? ? A8 ? ? ? 14 ? ? ? A9 ? ? ? A9");
    if (!a) return false;
    g_fullbright.addr=reinterpret_cast<void*>(a);
    g_fullbrightResolved=true;
    return true;
}

bool resolveSwapImport() {
    if (g_swapHooked) return true;
    const auto ranges=moduleRanges();
    for (const auto& r:ranges) {
        const auto* eh=reinterpret_cast<const Elf64_Ehdr*>(r.base);
        if (eh->e_ident[EI_MAG0]!=ELFMAG0 || eh->e_ident[EI_MAG1]!=ELFMAG1 ||
            eh->e_ident[EI_MAG2]!=ELFMAG2 || eh->e_ident[EI_MAG3]!=ELFMAG3 ||
            eh->e_ident[EI_CLASS]!=ELFCLASS64) continue;

        const auto* ph=reinterpret_cast<const Elf64_Phdr*>(r.base+eh->e_phoff);
        const Elf64_Phdr* dynPh=nullptr;
        for (int i=0;i<eh->e_phnum;++i) if (ph[i].p_type==PT_DYNAMIC) { dynPh=&ph[i]; break; }
        if (!dynPh) continue;

        const auto* dyn=reinterpret_cast<const Elf64_Dyn*>(r.base+dynPh->p_vaddr);
        const char* strtab=nullptr;
        const Elf64_Sym* symtab=nullptr;
        const Elf64_Rela* rela=nullptr;
        size_t relaCount=0;
        for (;dyn->d_tag!=DT_NULL;++dyn) {
            if (dyn->d_tag==DT_STRTAB) strtab=reinterpret_cast<const char*>(r.base+dyn->d_un.d_ptr);
            else if (dyn->d_tag==DT_SYMTAB) symtab=reinterpret_cast<const Elf64_Sym*>(r.base+dyn->d_un.d_ptr);
            else if (dyn->d_tag==DT_JMPREL) rela=reinterpret_cast<const Elf64_Rela*>(r.base+dyn->d_un.d_ptr);
            else if (dyn->d_tag==DT_PLTRELSZ) relaCount=dyn->d_un.d_val/sizeof(Elf64_Rela);
        }
        if (!strtab || !symtab || !rela || !relaCount) continue;

        for (size_t i=0;i<relaCount;++i) {
            const auto& rr=rela[i];
            if (ELF64_R_TYPE(rr.r_info)!=R_AARCH64_JUMP_SLOT) continue;
            const auto idx=ELF64_R_SYM(rr.r_info);
            const char* name=strtab+symtab[idx].st_name;
            if (std::strcmp(name,"eglSwapBuffers")!=0) continue;
            auto* got=reinterpret_cast<void**>(r.base+rr.r_offset);
            if (!got || !*got) continue;
            g_swapGot=got;
            g_swapOrig=reinterpret_cast<SwapFn>(*got);
            void* repl=reinterpret_cast<void*>(&swapHook);
            if (writeCode(got,&repl,sizeof(repl))) {
                g_swapHooked=true;
                return true;
            }
        }
    }
    return false;
}

void applyFullbright() {
    static const uint8_t patch[FULLBRIGHT_HEAD]={
        0x40,0x8F,0xA8,0x52, 0x00,0x00,0x27,0x1E, 0xC0,0x03,0x5F,0xD6
    };
    if (g_fullbrightResolved && g_modules[4].load()) {
        std::lock_guard<std::mutex> lock(g_patchMutex);
        if (!g_fullbright.active) {
            g_fullbright.original.resize(sizeof(patch));
            std::memcpy(g_fullbright.original.data(),g_fullbright.addr,sizeof(patch));
            if (writeCode(g_fullbright.addr,patch,sizeof(patch))) g_fullbright.active=true;
        }
    } else {
        std::lock_guard<std::mutex> lock(g_patchMutex);
        if (g_fullbright.active) {
            writeCode(g_fullbright.addr,g_fullbright.original.data(),g_fullbright.original.size());
            g_fullbright.active=false;
        }
    }
}

void refreshCapabilities() {
    // Startup must be non-invasive. Only validate the exact runtime targets;
    // hooks are installed later, on explicit user action.
    uint32_t caps=0;

    static const uint8_t ZOOM_PROLOG[INLINE_HEAD] = {
        0xEA,0x0F,0x1D,0xFC, 0xE9,0xA3,0x00,0x6D,
        0xFD,0xFB,0x01,0xA9, 0xF3,0x17,0x00,0xF9
    };
    static const uint8_t ALLOW_PROLOG[INLINE_HEAD] = {
        0xFF,0xC3,0x01,0xD1, 0xFD,0x7B,0x04,0xA9,
        0xF5,0x2B,0x00,0xF9, 0xF4,0x4F,0x06,0xA9
    };
    if (fixedPrologMatches(MC_ZOOM_OFFSET, ZOOM_PROLOG)) caps|=(1u<<0);
    static const uint8_t FREELOOK_PROLOG[INLINE_HEAD] = {
        0x08,0x04,0x41,0xF9, 0x29,0x08,0x40,0xB9,
        0x2A,0x00,0x40,0xF9, 0x09,0x20,0x00,0xB9
    };
    if (fixedPrologMatches(MC_FREELOOK_VECTOR_OFFSET, FREELOOK_PROLOG)) {
        caps|=(1u<<1); // FreeLook.
    }
    if (resolveFullbright()) caps|=(1u<<4);

    // Unlock FPS uses an EGL GOT patch and is intentionally not probed/applied
    // during automatic startup. It remains disabled until explicitly enabled.
    g_caps.store(caps);
    g_capsReady.store(true);
    logi("MC ready. WLZ safe capabilities=0x%X",caps);
}

void runtimeThread() {
    for (int i=0;i<500 && !g_mcReady.load();++i) {
        if (!moduleRanges().empty()) { g_mcReady.store(true); break; }
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
    if (!g_mcReady.load()) return;
    refreshCapabilities();
    applyFullbright();
}

void initializeState() {
    for (auto& m:g_modules) m.store(false);
    for (auto& p:g_params) p.store(0);
    g_params[0].store(150);
    g_params[1].store(60);
    g_params[2].store(100);
    g_params[3].store(1);
    g_params[4].store(0);
    g_params[5].store(4);
}

bool supported(int index) {
    if (index==8) return true;
    if (index<0 || index>=MODULE_COUNT) return false;
    return (g_caps.load()&(1u<<index))!=0;
}
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*,void*) {
    std::call_once(g_initOnce,initializeState);
    if (!g_initialized.exchange(true)) std::thread(runtimeThread).detach();
    logi("WLZ native runtime loaded");
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wlz_client_WlzRuntimeBridge_nativeInitialize(JNIEnv*,jclass) {
    std::call_once(g_initOnce,initializeState);
    if (!g_initialized.exchange(true)) std::thread(runtimeThread).detach();
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wlz_client_WlzRuntimeBridge_nativeIsLoaded(JNIEnv*,jclass) {
    return g_initialized.load()?JNI_TRUE:JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wlz_client_WlzRuntimeBridge_nativeIsMinecraftReady(JNIEnv*,jclass) {
    return g_mcReady.load()?JNI_TRUE:JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wlz_client_WlzRuntimeBridge_nativeIsModuleSupported(JNIEnv*,jclass,jint index) {
    if (!g_capsReady.load() && !moduleRanges().empty()) {
        g_mcReady.store(true);
        refreshCapabilities();
    }
    return supported(index)?JNI_TRUE:JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wlz_client_WlzRuntimeBridge_nativeGetCapabilities(JNIEnv*,jclass) {
    return static_cast<jint>(g_caps.load());
}

extern "C" JNIEXPORT void JNICALL
Java_com_wlz_client_WlzRuntimeBridge_nativeSetModule(JNIEnv*,jclass,jint index,jboolean enabled) {
    if (index<0 || index>=MODULE_COUNT) return;
    const bool on=enabled==JNI_TRUE;

    // Invasive native patches are installed only on explicit user action,
    // never during application startup.
    if (!g_capsReady.load() && !moduleRanges().empty()) {
        g_mcReady.store(true);
        refreshCapabilities();
    }

    if (on && index==0 && !g_zoomHooked) {
        installZoomHook();
        if (!g_zoomHooked) {
            g_modules[index].store(false);
            return;
        }
    }
    if (on && index==1 && !g_freeLookVectorHooked) {
        installFreeLookHook();
        if (!g_freeLookVectorHooked) {
            g_modules[index].store(false);
            return;
        }
    }
    if (on && index==3 && !g_swapHooked) {
        if (!resolveSwapImport()) {
            g_modules[index].store(false);
            return;
        }
    }

    g_modules[index].store(on);

    if (index==4) applyFullbright();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wlz_client_WlzRuntimeBridge_nativeGetModule(JNIEnv*,jclass,jint index) {
    if (index<0 || index>=MODULE_COUNT) return JNI_FALSE;
    return g_modules[index].load()?JNI_TRUE:JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_wlz_client_WlzRuntimeBridge_nativeSetParam(JNIEnv*,jclass,jint key,jint value) {
    if (key<0 || key>=PARAM_COUNT) return;
    g_params[key].store(value);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wlz_client_WlzRuntimeBridge_nativeGetParam(JNIEnv*,jclass,jint key) {
    if (key<0 || key>=PARAM_COUNT) return 0;
    return g_params[key].load();
}
