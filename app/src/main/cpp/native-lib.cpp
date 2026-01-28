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

// Глобальное состояние
static llama_model *model = nullptr;
static llama_context *ctx = nullptr;
static llama_sampler *sampler = nullptr;

extern "C" {

JNIEXPORT void JNICALL
Java_com_kakdela_p2p_ai_LlamaBridge_init(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Инициализация модели: %s", path);

    // 1. Безопасная очистка старой памяти
    if (sampler) {
        llama_sampler_free(sampler);
        sampler = nullptr;
    }
    if (ctx) {
        llama_free(ctx);
        ctx = nullptr;
    }
    if (model) {
        llama_model_free(model);
        model = nullptr;
    }

    // 2. Загрузка модели
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // Android CPU

    model = llama_model_load_from_file(path, model_params);
    
    if (model == nullptr) {
        LOGE("Не удалось загрузить модель по пути: %s", path);
        env->ReleaseStringUTFChars(model_path, path);
        return;
    }

    // 3. Создание контекста
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048; 
    ctx_params.n_threads = std::thread::hardware_concurrency();
    ctx_params.n_threads_batch = ctx_params.n_threads;

    ctx = llama_init_from_model(model, ctx_params);
    
    if (ctx == nullptr) {
        LOGE("Не удалось создать контекст llama");
        llama_model_free(model);
        model = nullptr;
        env->ReleaseStringUTFChars(model_path, path);
        return;
    }

    // 4. Инициализация цепочки сэмплера
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.6f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    LOGI("LlamaBridge инициализирован успешно");
    env->ReleaseStringUTFChars(model_path, path);
}

JNIEXPORT jstring JNICALL
Java_com_kakdela_p2p_ai_LlamaBridge_prompt(JNIEnv *env, jobject thiz, jstring input_text) {
    if (!model || !ctx || !sampler) {
        return env->NewStringUTF("Error: Model not initialized");
    }

    const char *text = env->GetStringUTFChars(input_text, nullptr);
    std::string prompt(text);
    env->ReleaseStringUTFChars(input_text, text);

    const struct llama_vocab * vocab = llama_model_get_vocab(model);

    // 1. Токенизация входного текста
    // В новом API llama_tokenize требует vocab, текст, длину, буфер токенов и флаги
    int n_tokens_max = prompt.length() + 32;
    std::vector<llama_token> tokens_list(n_tokens_max);
    int n_tokens = llama_tokenize(vocab, prompt.c_str(), prompt.length(), tokens_list.data(), tokens_list.size(), true, false);
    
    if (n_tokens < 0) { // Если буфер мал, выделяем больше
        tokens_list.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, prompt.c_str(), prompt.length(), tokens_list.data(), tokens_list.size(), true, false);
    }
    tokens_list.resize(n_tokens);

    // 2. Очистка KV-кэша перед новым запросом
    // Использование llama_kv_cache_clear наиболее надежно для сброса состояния
    llama_kv_cache_clear(ctx);

    // 3. Подготовка батча для обработки промпта
    llama_batch batch = llama_batch_init(tokens_list.size(), 0, 1);
    for (size_t i = 0; i < tokens_list.size(); i++) {
        common_batch_add(batch, tokens_list[i], i, { 0 }, false);
    }
    // Нам нужны логиты только для последнего токена, чтобы начать генерацию
    batch.logits[batch.n_tokens - 1] = true;

    if (llama_decode(ctx, batch) != 0) {
        llama_batch_free(batch);
        LOGE("llama_decode failed during prompt processing");
        return env->NewStringUTF("Error: Decode failed");
    }

    // 4. Цикл генерации ответа
    std::string result_str = "";
    int n_cur = batch.n_tokens;
    int n_decode = 0;
    const int max_tokens = 256;

    while (n_decode < max_tokens) {
        // Выбор следующего токена
        llama_token new_token_id = llama_sampler_sample(sampler, ctx, batch.n_tokens - 1);

        // Проверка на токен конца (EOG - End Of Generation)
        if (llama_vocab_is_eog(vocab, new_token_id)) {
            break;
        }

        // Преобразование токена в строку
        char buf[256];
        int n = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result_str += std::string(buf, n);
        }

        // Подготовка батча для следующего шага (всего один токен)
        batch.n_tokens = 0; 
        common_batch_add(batch, new_token_id, n_cur, { 0 }, true);
        
        n_decode++;
        n_cur++;

        if (llama_decode(ctx, batch) != 0) {
            LOGE("llama_decode failed during generation");
            break;
        }
    }

    llama_batch_free(batch);
    LOGI("Генерация завершена. Длина: %zu", result_str.length());

    return env->NewStringUTF(result_str.c_str());
}

}
