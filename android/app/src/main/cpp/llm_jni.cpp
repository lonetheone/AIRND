// Narrow JNI bridge to llama.cpp (pinned b11982). One Engine = one loaded model
// and one context. Not thread-safe by design: the Kotlin side calls it from a
// single worker thread only (NEMO: on-device-models-for-mobile-games.md).
//
// Generation follows examples/simple/simple.cpp of the same revision
// (llama_batch_ext + llama_process), with the context memory cleared per call.

#include <android/log.h>
#include <jni.h>

#include <chrono>
#include <string>
#include <vector>

#include "llama.h"

#define TAG "airnd_llm"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

namespace {

struct Stats {
    jlong promptTokens = 0, genTokens = 0, promptUs = 0, genUs = 0;
};

struct Engine {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    int nCtx = 0;
    Stats last;
};

int64_t nowUs() {
    return std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

void setTokens(llama_batch_ext * batch, const llama_token * tokens, int32_t n, llama_pos pos0) {
    llama_batch_ext_clear(batch);
    for (int32_t i = 0; i < n; ++i) {
        const int32_t idx = llama_batch_ext_add_token(batch, 0, tokens[i]);
        const llama_pos pos = pos0 + i;
        llama_batch_ext_set_pos(batch, idx, &pos);
    }
    llama_batch_ext_set_output_logits(batch, n - 1, true);
}

void logToLogcat(ggml_log_level level, const char * text, void *) {
    if (level == GGML_LOG_LEVEL_ERROR) __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", text);
    else if (level == GGML_LOG_LEVEL_WARN) __android_log_print(ANDROID_LOG_WARN, TAG, "%s", text);
}

std::string toString(JNIEnv * env, jstring s) {
    if (!s) return {};
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

jlong load(JNIEnv * env, jclass, jstring jpath, jint threads, jint nCtx) {
    const std::string path = toString(env, jpath);
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    llama_model * model = llama_model_load_from_file(path.c_str(), mp);
    if (!model) {
        LOGE("unable to load model %s", path.c_str());
        return 0;
    }
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = nCtx;
    cp.n_batch = nCtx;
    cp.n_threads = threads;
    cp.n_threads_batch = threads;
    cp.no_perf = true;
    llama_context * ctx = llama_init_from_model(model, cp);
    if (!ctx) {
        LOGE("unable to create context");
        llama_model_free(model);
        return 0;
    }
    auto * e = new Engine{model, ctx, llama_model_get_vocab(model), nCtx, {}};
    LOGI("loaded %s, ctx %d, threads %d", path.c_str(), nCtx, threads);
    return reinterpret_cast<jlong>(e);
}

// Returns the generated text, or null on failure. temperature <= 0 means greedy.
jstring generate(JNIEnv * env, jclass, jlong handle, jstring jprompt, jint maxTokens,
                 jfloat temperature, jint seed, jstring jgrammar) {
    auto * e = reinterpret_cast<Engine *>(handle);
    if (!e) return nullptr;
    e->last = {};
    const std::string prompt = toString(env, jprompt);
    const std::string grammar = toString(env, jgrammar);

    const int nPrompt = -llama_tokenize(e->vocab, prompt.c_str(), prompt.size(), nullptr, 0, true, true);
    if (nPrompt <= 0 || nPrompt + maxTokens > e->nCtx) {
        LOGE("prompt of %d tokens does not fit ctx %d", nPrompt, e->nCtx);
        return nullptr;
    }
    std::vector<llama_token> tokens(nPrompt);
    if (llama_tokenize(e->vocab, prompt.c_str(), prompt.size(), tokens.data(), tokens.size(), true, true) < 0) {
        return nullptr;
    }

    llama_memory_clear(llama_get_memory(e->ctx), true);

    llama_sampler * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!grammar.empty()) llama_sampler_chain_add(smpl, llama_sampler_init_grammar(e->vocab, grammar.c_str(), "root"));
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_penalties(llama_vocab_n_tokens(e->vocab), 64, 1.15f, 0.0f, 0.0f));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(static_cast<uint32_t>(seed)));
    }

    llama_batch_ext * batch = llama_batch_ext_init(e->ctx);
    setTokens(batch, tokens.data(), nPrompt, 0);

    std::string out;
    int nPos = 0;
    int nTokens = nPrompt;
    const int64_t t0 = nowUs();
    int64_t tFirst = 0;
    bool ok = true;
    for (int generated = 0; generated < maxTokens; ++generated) {
        if (llama_process(e->ctx, LLAMA_PROCESS_TYPE_DECODE, batch)) {
            LOGE("decode failed");
            ok = false;
            break;
        }
        if (generated == 0) tFirst = nowUs();
        nPos += nTokens;
        const llama_token id = llama_sampler_sample(smpl, e->ctx, -1);
        if (llama_vocab_is_eog(e->vocab, id)) break;
        char buf[128];
        const int n = llama_token_to_piece(e->vocab, id, buf, sizeof(buf), 0, false);
        if (n < 0) {
            ok = false;
            break;
        }
        out.append(buf, n);
        e->last.genTokens++;
        setTokens(batch, &id, 1, nPos);
        nTokens = 1;
    }
    const int64_t t1 = nowUs();
    e->last.promptTokens = nPrompt;
    e->last.promptUs = tFirst ? tFirst - t0 : t1 - t0;
    e->last.genUs = tFirst ? t1 - tFirst : 0;

    llama_batch_ext_free(batch);
    llama_sampler_free(smpl);
    return ok ? env->NewStringUTF(out.c_str()) : nullptr;
}

// [promptTokens, generatedTokens, promptMicros, generateMicros] of the last call.
jlongArray lastStats(JNIEnv * env, jclass, jlong handle) {
    auto * e = reinterpret_cast<Engine *>(handle);
    jlong v[4] = {0, 0, 0, 0};
    if (e) {
        v[0] = e->last.promptTokens;
        v[1] = e->last.genTokens;
        v[2] = e->last.promptUs;
        v[3] = e->last.genUs;
    }
    jlongArray arr = env->NewLongArray(4);
    env->SetLongArrayRegion(arr, 0, 4, v);
    return arr;
}

void release(JNIEnv *, jclass, jlong handle) {
    auto * e = reinterpret_cast<Engine *>(handle);
    if (!e) return;
    llama_free(e->ctx);
    llama_model_free(e->model);
    delete e;
}

}  // namespace

// RegisterNatives: the Java class name is written once, here, and a wrong name
// fails at library load instead of at the first call (NEMO: single-source-identifiers.md).
JNIEXPORT jint JNI_OnLoad(JavaVM * vm, void *) {
    JNIEnv * env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass cls = env->FindClass("com/airnd/dda/llm/NativeLlm");
    if (!cls) return JNI_ERR;
    static const JNINativeMethod methods[] = {
        {"load", "(Ljava/lang/String;II)J", reinterpret_cast<void *>(load)},
        {"generate", "(JLjava/lang/String;IFILjava/lang/String;)Ljava/lang/String;", reinterpret_cast<void *>(generate)},
        {"lastStats", "(J)[J", reinterpret_cast<void *>(lastStats)},
        {"release", "(J)V", reinterpret_cast<void *>(release)},
    };
    if (env->RegisterNatives(cls, methods, sizeof(methods) / sizeof(methods[0])) != JNI_OK) return JNI_ERR;
    llama_log_set(logToLogcat, nullptr);
    llama_backend_init();
    return JNI_VERSION_1_6;
}
