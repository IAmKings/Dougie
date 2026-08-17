#include "llama.h"

#include <jni.h>

#include <cstring>
#include <mutex>
#include <string>
#include <vector>

namespace {

constexpr int kMaxPredict = 192;
constexpr int kCtx = 2048;
constexpr float kTemp = 0.7f;
constexpr float kTopP = 0.8f;
constexpr float kPresence = 1.5f;

std::mutex g_mu;
bool g_backend = false;
llama_model * g_model = nullptr;
std::string g_path;

void silent_log(ggml_log_level, const char *, void *) {}

void ensure_backend() {
    if (!g_backend) {
        llama_log_set(silent_log, nullptr);
        ggml_backend_load_all();
        g_backend = true;
    }
}

bool load_model(const char * path) {
    if (g_model != nullptr && g_path == path) {
        return true;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
        g_path.clear();
    }
    llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(path, params);
    if (g_model == nullptr) {
        return false;
    }
    g_path = path;
    return true;
}

std::string token_piece(const llama_vocab * vocab, llama_token id) {
    std::vector<char> buf(256);
    int n = llama_token_to_piece(vocab, id, buf.data(), static_cast<int32_t>(buf.size()), 0, true);
    if (n < 0) {
        buf.resize(static_cast<size_t>(-n));
        n = llama_token_to_piece(vocab, id, buf.data(), static_cast<int32_t>(buf.size()), 0, true);
    }
    if (n < 0) {
        return {};
    }
    return std::string(buf.data(), static_cast<size_t>(n));
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_dougie_tool_system_LlamaJni_nativeComplete(
    JNIEnv * env,
    jclass,
    jstring model_path,
    jstring prompt
) {
    if (model_path == nullptr || prompt == nullptr) {
        return env->NewStringUTF("");
    }
    const char * path = env->GetStringUTFChars(model_path, nullptr);
    const char * prompt_c = env->GetStringUTFChars(prompt, nullptr);
    if (path == nullptr || prompt_c == nullptr) {
        if (path != nullptr) env->ReleaseStringUTFChars(model_path, path);
        if (prompt_c != nullptr) env->ReleaseStringUTFChars(prompt, prompt_c);
        return env->NewStringUTF("");
    }

    std::string out;
    {
        std::lock_guard<std::mutex> lock(g_mu);
        ensure_backend();
        if (!load_model(path)) {
            env->ReleaseStringUTFChars(model_path, path);
            env->ReleaseStringUTFChars(prompt, prompt_c);
            return env->NewStringUTF("");
        }

        const llama_vocab * vocab = llama_model_get_vocab(g_model);
        const int n_prompt = -llama_tokenize(
            vocab, prompt_c, static_cast<int32_t>(strlen(prompt_c)), nullptr, 0, true, true
        );
        if (n_prompt <= 0 || n_prompt >= kCtx) {
            env->ReleaseStringUTFChars(model_path, path);
            env->ReleaseStringUTFChars(prompt, prompt_c);
            return env->NewStringUTF("");
        }
        std::vector<llama_token> prompt_tokens(static_cast<size_t>(n_prompt));
        if (llama_tokenize(
                vocab,
                prompt_c,
                static_cast<int32_t>(strlen(prompt_c)),
                prompt_tokens.data(),
                n_prompt,
                true,
                true
            ) < 0) {
            env->ReleaseStringUTFChars(model_path, path);
            env->ReleaseStringUTFChars(prompt, prompt_c);
            return env->NewStringUTF("");
        }

        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = kCtx;
        ctx_params.n_batch = n_prompt;
        ctx_params.n_threads = 2;
        llama_context * ctx = llama_init_from_model(g_model, ctx_params);
        if (ctx == nullptr) {
            env->ReleaseStringUTFChars(model_path, path);
            env->ReleaseStringUTFChars(prompt, prompt_c);
            return env->NewStringUTF("");
        }

        auto sparams = llama_sampler_chain_default_params();
        sparams.no_perf = true;
        llama_sampler * smpl = llama_sampler_chain_init(sparams);
        llama_sampler_chain_add(smpl, llama_sampler_init_penalties(64, 1.0f, 0.0f, kPresence));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(kTopP, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(kTemp));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

        llama_batch batch = llama_batch_get_one(prompt_tokens.data(), n_prompt);
        llama_token new_token_id = 0;
        for (int n_pos = 0; n_pos + batch.n_tokens < n_prompt + kMaxPredict; ) {
            if (llama_decode(ctx, batch)) {
                out.clear();
                break;
            }
            n_pos += batch.n_tokens;
            new_token_id = llama_sampler_sample(smpl, ctx, -1);
            if (llama_vocab_is_eog(vocab, new_token_id)) {
                break;
            }
            out += token_piece(vocab, new_token_id);
            batch = llama_batch_get_one(&new_token_id, 1);
        }

        llama_sampler_free(smpl);
        llama_free(ctx);
    }

    env->ReleaseStringUTFChars(model_path, path);
    env->ReleaseStringUTFChars(prompt, prompt_c);
    return env->NewStringUTF(out.c_str());
}
