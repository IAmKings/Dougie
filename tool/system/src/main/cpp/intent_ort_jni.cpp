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

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_dougie_tool_system_IntentOrtJni_nativeInferTokens(
    JNIEnv* env,
    jclass,
    jstring model_path,
    jlongArray ids_arr,
    jlongArray mask_arr) {
    if (model_path == nullptr || ids_arr == nullptr || mask_arr == nullptr) {
        return nullptr;
    }
    const jsize seq = env->GetArrayLength(ids_arr);
    if (seq <= 0 || env->GetArrayLength(mask_arr) != seq) {
        return nullptr;
    }
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) {
        return nullptr;
    }
    std::vector<int64_t> ids(static_cast<size_t>(seq));
    std::vector<int64_t> mask(static_cast<size_t>(seq));
    env->GetLongArrayRegion(ids_arr, 0, seq, reinterpret_cast<jlong*>(ids.data()));
    env->GetLongArrayRegion(mask_arr, 0, seq, reinterpret_cast<jlong*>(mask.data()));
    std::vector<int64_t> types(static_cast<size_t>(seq), 0);

    jfloatArray out_arr = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_mu);
        if (!ensureSession(path)) {
            env->ReleaseStringUTFChars(model_path, path);
            return nullptr;
        }
        size_t n_in = 0;
        dropStatus(g_api->SessionGetInputCount(g_session, &n_in));
        if (n_in < 2) {
            env->ReleaseStringUTFChars(model_path, path);
            return nullptr;
        }
        int64_t shape[2] = {1, static_cast<int64_t>(seq)};
        OrtValue* t_ids = nullptr;
        OrtValue* t_mask = nullptr;
        OrtValue* t_types = nullptr;
        OrtStatus* status = g_api->CreateTensorWithDataAsOrtValue(
            g_mem,
            ids.data(),
            ids.size() * sizeof(int64_t),
            shape,
            2,
            ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64,
            &t_ids);
        if (status != nullptr) {
            g_api->ReleaseStatus(status);
            env->ReleaseStringUTFChars(model_path, path);
            return nullptr;
        }
        status = g_api->CreateTensorWithDataAsOrtValue(
            g_mem,
            mask.data(),
            mask.size() * sizeof(int64_t),
            shape,
            2,
            ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64,
            &t_mask);
        if (status != nullptr) {
            g_api->ReleaseStatus(status);
            g_api->ReleaseValue(t_ids);
            env->ReleaseStringUTFChars(model_path, path);
            return nullptr;
        }
        if (n_in >= 3) {
            status = g_api->CreateTensorWithDataAsOrtValue(
                g_mem,
                types.data(),
                types.size() * sizeof(int64_t),
                shape,
                2,
                ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64,
                &t_types);
            if (status != nullptr) {
                g_api->ReleaseStatus(status);
                g_api->ReleaseValue(t_ids);
                g_api->ReleaseValue(t_mask);
                env->ReleaseStringUTFChars(model_path, path);
                return nullptr;
            }
        }
        OrtAllocator* allocator = nullptr;
        dropStatus(g_api->GetAllocatorWithDefaultOptions(&allocator));
        char* n0 = nullptr;
        char* n1 = nullptr;
        char* n2 = nullptr;
        char* out_name = nullptr;
        dropStatus(g_api->SessionGetInputName(g_session, 0, allocator, &n0));
        dropStatus(g_api->SessionGetInputName(g_session, 1, allocator, &n1));
        if (n_in >= 3) {
            dropStatus(g_api->SessionGetInputName(g_session, 2, allocator, &n2));
        }
        dropStatus(g_api->SessionGetOutputName(g_session, 0, allocator, &out_name));
        const char* in_names_2[] = {n0, n1};
        const char* in_names_3[] = {n0, n1, n2};
        OrtValue* inputs_2[] = {t_ids, t_mask};
        OrtValue* inputs_3[] = {t_ids, t_mask, t_types};
        const char** in_names = n_in >= 3 ? in_names_3 : in_names_2;
        OrtValue** inputs = n_in >= 3 ? inputs_3 : inputs_2;
        const char* out_names[] = {out_name};
        OrtValue* output = nullptr;
        status = g_api->Run(
            g_session,
            nullptr,
            in_names,
            inputs,
            n_in >= 3 ? 3 : 2,
            out_names,
            1,
            &output);
        if (allocator != nullptr) {
            if (n0 != nullptr) allocator->Free(allocator, n0);
            if (n1 != nullptr) allocator->Free(allocator, n1);
            if (n2 != nullptr) allocator->Free(allocator, n2);
            if (out_name != nullptr) allocator->Free(allocator, out_name);
        }
        g_api->ReleaseValue(t_ids);
        g_api->ReleaseValue(t_mask);
        if (t_types != nullptr) {
            g_api->ReleaseValue(t_types);
        }
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
