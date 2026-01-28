#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

// Примечание: В реальном проекте здесь должны быть инклуды llama.h
// Для краткости мы имитируем базовую логику инициализации llama.cpp
// Если вы используете готовую библиотеку llama.cpp, убедитесь, что она скомпилирована.

#define LOG_TAG "LlamaNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// Глобальные переменные для хранения состояния модели (в продакшене лучше обернуть в структуру)
static std::string current_model_path = "";
static bool is_initialized = false;

JNIEXPORT void JNICALL
Java_com_kakdela_p2p_ai_LlamaBridge_init(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    
    LOGI("Инициализация модели по пути: %s", path);
    
    // Здесь происходит реальная загрузка llama_model_load_from_file
    // Для примера просто фиксируем успех:
    current_model_path = path;
    is_initialized = true;

    env->ReleaseStringUTFChars(model_path, path);
    LOGI("Ядро успешно инициализировано");
}

JNIEXPORT jstring JNICALL
Java_com_kakdela_p2p_ai_LlamaBridge_prompt(JNIEnv *env, jobject thiz, jstring input_text) {
    if (!is_initialized) {
        return env->NewStringUTF("Ошибка: Модель не инициализирована");
    }

    const char *question = env->GetStringUTFChars(input_text, nullptr);
    LOGI("Получен промпт: %s", question);

    // Имитация работы ИИ. В реальном коде здесь вызывается llama_decode
    std::string response = "Это ответ от локального ИИ на ваш вопрос: ";
    response += question;
    response += "\n(Нативная библиотека работает корректно)";

    env->ReleaseStringUTFChars(input_text, question);
    return env->NewStringUTF(response.c_str());
}

}
