#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <thread>
#include <algorithm>
#include "llama.h"
#include "common.h"

#define LOG_TAG "LlamaNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model *model = nullptr;
static llama_context *ctx = nullptr;
static llama_sampler *sampler = nullptr;

extern "C" {

/**
 * Инициализация модели: загрузка из файла и создание контекста
 */
JNIEXPORT void JNICALL
Java_com_kakdela_p2p_ai_LlamaBridge_init(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);

    // Очистка ресурсов перед повторной инициализацией
    if (sampler) { llama_sampler_free(sampler); sampler = nullptr; }
    if (ctx)     { llama_free(ctx); ctx = nullptr; }
    if (model)   { llama_model_free(model); model = nullptr; }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // На Android используем CPU

    LOGI("Loading model from: %s", path);
    model = llama_model_load_from_file(path, model_params);
    if (!model) {
        LOGE("Failed to load model from path: %s", path);
        env->ReleaseStringUTFChars(model_path, path);
        return;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx     = 2048; // Размер окна контекста
    // Оставляем одно ядро свободным для системных нужд
    ctx_params.n_threads = std::max(1u, std::thread::hardware_concurrency() - 1);
    ctx_params.n_threads_batch = ctx_params.n_threads;

    ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create llama context");
        env->ReleaseStringUTFChars(model_path, path);
        return;
    }

    // Инициализация цепочки сэмплеров (Sampling Chain)
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    sampler = llama_sampler_chain_init(sparams);
    
    // В новых версиях llama.cpp функции top_p и min_p требуют параметр min_keep
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(-1)); // Random seed

    LOGI("Llama native initialized successfully. Threads: %d", ctx_params.n_threads);
    env->ReleaseStringUTFChars(model_path, path);
}

/**
 * Проверка, загружена ли модель в память
 */
JNIEXPORT jboolean JNICALL
Java_com_kakdela_p2p_ai_LlamaBridge_isReady(JNIEnv *env, jobject thiz) {
    return (model != nullptr && ctx != nullptr && sampler != nullptr);
}

/**
 * Основной цикл генерации текста
 */
JNIEXPORT jstring JNICALL
Java_com_kakdela_p2p_ai_LlamaBridge_prompt(JNIEnv *env, jobject thiz, jstring input_text) {
    if (!model || !ctx || !sampler) {
        LOGE("Prompt called but AI not ready");
        return env->NewStringUTF("Error: AI bridge not initialized");
    }

    const char *text = env->GetStringUTFChars(input_text, nullptr);
    std::string prompt(text);
    env->ReleaseStringUTFChars(input_text, text);

    const struct llama_vocab *vocab = llama_model_get_vocab(model);

    // 1. Токенизация входного текста
    std::vector<llama_token> tokens(prompt.length() + 32);
    int n_tokens = llama_tokenize(vocab, prompt.c_str(), (int)prompt.length(),
                                  tokens.data(), (int)tokens.size(), true, true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, prompt.c_str(), (int)prompt.length(),
                                  tokens.data(), (int)tokens.size(), true, true);
    }
    tokens.resize(n_tokens);

    // 2. Подготовка и обработка (Decode) промпта
    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    for (int i = 0; i < n_tokens; i++) {
        // Добавляем токены промпта в батч. Логиты считаем только для последнего токена.
        common_batch_add(batch, tokens[i], i, {0}, (i == n_tokens - 1));
    }

    if (llama_decode(ctx, batch) != 0) {
        LOGE("Failed to decode prompt tokens");
        llama_batch_free(batch);
        return env->NewStringUTF("Error: Local decode failed");
    }

    // 3. Цикл авторегрессионной генерации
    std::string response = "";
    llama_token curr_token;
    int n_cur = n_tokens;

    // Генерируем до 256 новых токенов
    for (int i = 0; i < 256; i++) {
        // Выбираем следующий токен с помощью сэмплера
        curr_token = llama_sampler_sample(sampler, ctx, -1);
        
        // Проверка на токен конца генерации
        if (llama_vocab_is_eog(vocab, curr_token)) {
            break;
        }

        // Преобразование токена в строку
        char piece[128];
        int n_piece = llama_token_to_piece(vocab, curr_token, piece, sizeof(piece), 0, true);
        if (n_piece > 0) {
            response.append(piece, n_piece);
        }

        // Очищаем батч для следующей итерации (обработка по 1 токену)
        llama_batch_clear(batch);
        common_batch_add(batch, curr_token, n_cur, {0}, true);

        if (llama_decode(ctx, batch) != 0) {
            LOGE("Failed to decode during generation at %d", n_cur);
            break;
        }
        n_cur++;
    }

    llama_batch_free(batch);
    
    if (response.empty()) {
        return env->NewStringUTF("[Модель не смогла сгенерировать ответ]");
    }

    return env->NewStringUTF(response.c_str());
}

} // extern "C"
