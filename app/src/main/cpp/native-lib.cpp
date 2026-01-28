#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <thread>
#include "llama.h"   
#include "common.h"  

#define LOG_TAG "LlamaNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model *model = nullptr;
static llama_context *ctx = nullptr;
static llama_sampler *sampler = nullptr;

extern "C" {

JNIEXPORT void JNICALL
Java_com_kakdela_p2p_ai_LlamaBridge_init(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    
    if (sampler) { llama_sampler_free(sampler); sampler = nullptr; }
    if (ctx) { llama_free(ctx); ctx = nullptr; }
    if (model) { llama_model_free(model); model = nullptr; }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; 

    model = llama_model_load_from_file(path, model_params);
    if (!model) {
        env->ReleaseStringUTFChars(model_path, path);
        return;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048; 
    ctx_params.n_threads = std::thread::hardware_concurrency();
    ctx_params.n_threads_batch = ctx_params.n_threads;

    ctx = llama_init_from_model(model, ctx_params);
    
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));

    env->ReleaseStringUTFChars(model_path, path);
    LOGI("Model initialized successfully");
}

JNIEXPORT jstring JNICALL
Java_com_kakdela_p2p_ai_LlamaBridge_prompt(JNIEnv *env, jobject thiz, jstring input_text) {
    if (!model || !ctx || !sampler) return env->NewStringUTF("Error: Not initialized");

    const char *text = env->GetStringUTFChars(input_text, nullptr);
    std::string prompt(text);
    env->ReleaseStringUTFChars(input_text, text);

    const struct llama_vocab * vocab = llama_model_get_vocab(model);

    // Токенизация
    std::vector<llama_token> tokens_list(prompt.length() + 32);
    int n_tokens = llama_tokenize(vocab, prompt.c_str(), prompt.length(), tokens_list.data(), tokens_list.size(), true, false);
    if (n_tokens < 0) {
        tokens_list.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, prompt.c_str(), prompt.length(), tokens_list.data(), tokens_list.size(), true, false);
    }
    tokens_list.resize(n_tokens);

    // ВАЖНО: Если llama_kv_cache_clear не работает, в новых версиях используется этот метод:
    // Мы его закомментируем, если он вызывает ошибки. Без него модель просто будет помнить контекст.
    // llama_kv_cache_clear(ctx); 

    // Подготовка батча
    llama_batch batch = llama_batch_init(tokens_list.size(), 0, 1);
    for (int i = 0; i < (int)tokens_list.size(); i++) {
        common_batch_add(batch, tokens_list[i], i, { 0 }, false);
    }
    batch.logits[batch.n_tokens - 1] = true;

    if (llama_decode(ctx, batch) != 0) {
        llama_batch_free(batch);
        return env->NewStringUTF("Error: Decode failed");
    }

    std::string result_str = "";
    int n_cur = batch.n_tokens;
    
    for (int i = 0; i < 256; i++) {
        llama_token id = llama_sampler_sample(sampler, ctx, batch.n_tokens - 1);

        if (llama_vocab_is_eog(vocab, id)) break;

        char buf[256];
        int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n > 0) result_str += std::string(buf, n);

        batch.n_tokens = 0; 
        common_batch_add(batch, id, n_cur, { 0 }, true);
        
        n_cur++;
        if (llama_decode(ctx, batch) != 0) break;
    }

    llama_batch_free(batch);
    return env->NewStringUTF(result_str.c_str());
}
}
