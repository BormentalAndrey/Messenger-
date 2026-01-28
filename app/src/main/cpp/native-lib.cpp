#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <thread>
#include "llama.h"   // Требует наличия llama.cpp/include/llama.h
#include "common.h"  // Требует наличия llama.cpp/common/common.h

#define LOG_TAG "LlamaNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Глобальное состояние (в продакшене лучше использовать класс-обертку)
static llama_model *model = nullptr;
static llama_context *ctx = nullptr;
static llama_sampler *sampler = nullptr;

extern "C" {

JNIEXPORT void JNICALL
Java_com_kakdela_p2p_ai_LlamaBridge_init(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Загрузка модели из: %s", path);

    // Очистка предыдущего состояния
    if (ctx) llama_free(ctx);
    if (model) llama_free_model(model);
    if (sampler) llama_sampler_free(sampler);

    // Настройка параметров модели
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // На CPU (для Android GPU нужен OpenCL/Vulkan backend)

    model = llama_load_model_from_file(path, model_params);
    
    if (model == nullptr) {
        LOGE("Ошибка: Не удалось загрузить модель");
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Failed to load model");
        env->ReleaseStringUTFChars(model_path, path);
        return;
    }

    // Настройка контекста
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048; // Длина контекста (2k токенов)
    ctx_params.n_threads = std::thread::hardware_concurrency(); // Использовать все ядра
    ctx_params.n_threads_batch = ctx_params.n_threads;

    ctx = llama_new_context_with_model(model, ctx_params);
    
    // Инициализация сэмплера (для разнообразия ответов)
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.6f)); // Температура 0.6
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));

    if (ctx == nullptr) {
        LOGE("Ошибка: Не удалось создать контекст");
        llama_free_model(model);
        model = nullptr;
    } else {
        LOGI("Модель успешно загружена. Потоков: %d", ctx_params.n_threads);
    }

    env->ReleaseStringUTFChars(model_path, path);
}

JNIEXPORT jstring JNICALL
Java_com_kakdela_p2p_ai_LlamaBridge_prompt(JNIEnv *env, jobject thiz, jstring input_text) {
    if (!model || !ctx) {
        return env->NewStringUTF("Error: Model not initialized");
    }

    const char *text = env->GetStringUTFChars(input_text, nullptr);
    std::string prompt(text);
    env->ReleaseStringUTFChars(input_text, text);

    // Токенизация промпта
    std::vector<llama_token> tokens_list;
    tokens_list = ::llama_tokenize(model, prompt, true, true);
    
    const int n_ctx = llama_n_ctx(ctx);
    const int n_kv_req = tokens_list.size() + 32; // Запас на ответ

    if (n_kv_req > n_ctx) {
        return env->NewStringUTF("Error: Prompt too long for context window");
    }

    // Очистка KV-кэша для нового запроса (для упрощения, в сложном чате нужно сдвигать окно)
    llama_kv_cache_clear(ctx);

    // Обработка промпта (Batch decoding)
    llama_batch batch = llama_batch_init(512, 0, 1);
    
    for (size_t i = 0; i < tokens_list.size(); i++) {
        llama_batch_add(batch, tokens_list[i], i, { 0 }, false);
    }
    // Последний токен должен запустить генерацию
    batch.logits[batch.n_tokens - 1] = true;

    if (llama_decode(ctx, batch) != 0) {
        return env->NewStringUTF("Error: llama_decode failed");
    }

    std::string result_str = "";
    int n_cur = batch.n_tokens;
    int n_decode = 0;
    const int max_tokens = 256; // Ограничение длины ответа

    // Цикл генерации
    while (n_decode < max_tokens) {
        // Сэмплирование следующего токена
        llama_token new_token_id = llama_sampler_sample(sampler, ctx, batch.n_tokens - 1);

        // Проверка на конец генерации (EOS)
        if (llama_token_is_eog(model, new_token_id) || new_token_id == llama_token_eos(model)) {
            break;
        }

        // Конвертация токена в строку
        char buf[256];
        int n = llama_token_to_piece(model, new_token_id, buf, sizeof(buf), 0, true);
        if (n < 0) {
            // Ошибка конвертации
        } else {
            std::string piece(buf, n);
            result_str += piece;
        }

        // Подготовка следующего шага
        llama_batch_clear(batch);
        llama_batch_add(batch, new_token_id, n_cur, { 0 }, true);
        
        n_decode++;
        n_cur++;

        if (llama_decode(ctx, batch) != 0) {
            break;
        }
    }

    llama_batch_free(batch);
    LOGI("Ответ сгенерирован: %s", result_str.c_str());

    return env->NewStringUTF(result_str.c_str());
}

}
