#include <cstdint>
#include <jni.h>
#include <mutex>
#include <string>
#include <vector>

#include "onnxruntime_c_api.h"

namespace {

const OrtApi* g_api = nullptr;
OrtEnv* g_env = nullptr;
OrtSession* g_session = nullptr;
OrtMemoryInfo* g_mem = nullptr;
std::string g_path;
std::mutex g_mu;

void dropStatus(OrtStatus* status) {
    if (status != nullptr) {
        g_api->ReleaseStatus(status);
    }
}

bool ensureApi() {
    if (g_api != nullptr) {
        return true;
    }
    const OrtApiBase* base = OrtGetApiBase();
    if (base == nullptr || base->GetApi == nullptr) {
        return false;
    }
    for (uint32_t version = ORT_API_VERSION; version >= 11; --version) {
        g_api = base->GetApi(version);
        if (g_api != nullptr) {
            break;
        }
    }
    return g_api != nullptr;
}

bool ensureSession(const char* path) {
    if (!ensureApi()) {
        return false;
    }
    if (g_session != nullptr && g_path == path) {
        return true;
    }
    if (g_session != nullptr) {
        g_api->ReleaseSession(g_session);
        g_session = nullptr;
    }
    if (g_env == nullptr) {
        OrtStatus* env_status = g_api->CreateEnv(ORT_LOGGING_LEVEL_ERROR, "dougie", &g_env);
        if (env_status != nullptr) {
            g_api->ReleaseStatus(env_status);
            g_env = nullptr;
            return false;
        }
    }
    if (g_mem == nullptr) {
        OrtStatus* mem_status =
            g_api->CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &g_mem);
        if (mem_status != nullptr) {
            g_api->ReleaseStatus(mem_status);
            g_mem = nullptr;
            return false;
        }
    }
    OrtSessionOptions* opts = nullptr;
    OrtStatus* opt_status = g_api->CreateSessionOptions(&opts);
    if (opt_status != nullptr) {
        g_api->ReleaseStatus(opt_status);
        return false;
    }
    OrtStatus* session_status = g_api->CreateSession(g_env, path, opts, &g_session);
    g_api->ReleaseSessionOptions(opts);
    if (session_status != nullptr) {
        g_api->ReleaseStatus(session_status);
        g_session = nullptr;
        return false;
    }
    g_path = path;
    return true;
}

}  // namespace

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_dougie_tool_system_IntentOrtJni_nativeInfer(
    JNIEnv* env,
    jclass,
    jstring model_path,
    jfloatArray features) {
    if (model_path == nullptr || features == nullptr) {
        return nullptr;
    }
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) {
        return nullptr;
    }
    const jsize nfeat = env->GetArrayLength(features);
    if (nfeat <= 0) {
        env->ReleaseStringUTFChars(model_path, path);
        return nullptr;
    }
    std::vector<float> input(static_cast<size_t>(nfeat));
    env->GetFloatArrayRegion(features, 0, nfeat, input.data());

    jfloatArray out_arr = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_mu);
        if (!ensureSession(path)) {
            env->ReleaseStringUTFChars(model_path, path);
            return nullptr;
        }
        int64_t shape[2] = {1, static_cast<int64_t>(nfeat)};
        OrtValue* input_tensor = nullptr;
        OrtStatus* status = g_api->CreateTensorWithDataAsOrtValue(
            g_mem,
            input.data(),
            input.size() * sizeof(float),
            shape,
            2,
            ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT,
            &input_tensor);
        if (status != nullptr) {
            g_api->ReleaseStatus(status);
            env->ReleaseStringUTFChars(model_path, path);
            return nullptr;
        }
        OrtAllocator* allocator = nullptr;
        dropStatus(g_api->GetAllocatorWithDefaultOptions(&allocator));
        char* in_name = nullptr;
        char* out_name = nullptr;
        dropStatus(g_api->SessionGetInputName(g_session, 0, allocator, &in_name));
        dropStatus(g_api->SessionGetOutputName(g_session, 0, allocator, &out_name));
        const char* in_names[] = {in_name};
        const char* out_names[] = {out_name};
        OrtValue* output = nullptr;
        status = g_api->Run(
            g_session,
            nullptr,
            in_names,
            &input_tensor,
            1,
            out_names,
            1,
            &output);
        if (allocator != nullptr) {
            if (in_name != nullptr) {
                allocator->Free(allocator, in_name);
            }
            if (out_name != nullptr) {
                allocator->Free(allocator, out_name);
            }
        }
        g_api->ReleaseValue(input_tensor);
        if (status != nullptr) {
            g_api->ReleaseStatus(status);
            env->ReleaseStringUTFChars(model_path, path);
            return nullptr;
        }
        float* logits = nullptr;
        status = g_api->GetTensorMutableData(output, reinterpret_cast<void**>(&logits));
        if (status != nullptr || logits == nullptr) {
            if (status != nullptr) {
                g_api->ReleaseStatus(status);
            }
            g_api->ReleaseValue(output);
            env->ReleaseStringUTFChars(model_path, path);
            return nullptr;
        }
        OrtTensorTypeAndShapeInfo* info = nullptr;
        dropStatus(g_api->GetTensorTypeAndShape(output, &info));
        if (info == nullptr) {
            g_api->ReleaseValue(output);
            env->ReleaseStringUTFChars(model_path, path);
            return nullptr;
        }
        size_t count = 0;
        dropStatus(g_api->GetTensorShapeElementCount(info, &count));
        g_api->ReleaseTensorTypeAndShapeInfo(info);
        out_arr = env->NewFloatArray(static_cast<jsize>(count));
        if (out_arr != nullptr && count > 0) {
            env->SetFloatArrayRegion(out_arr, 0, static_cast<jsize>(count), logits);
        }
        g_api->ReleaseValue(output);
    }
    env->ReleaseStringUTFChars(model_path, path);
    return out_arr;
}
