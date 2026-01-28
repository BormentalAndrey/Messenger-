cmake_minimum_required(VERSION 3.22.1)

project("p2p-ai-engine")

# --- ИСПРАВЛЕНИЕ ОШИБКИ ЗДЕСЬ ---
# Мы убрали "-march=native".
# Вместо этого мы используем условную логику: если сборка идет под Android ARM64,
# включаем специфичные оптимизации (NEON, FP16), которые нужны для ИИ.

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -funroll-loops -DGGML_USE_K_QUANTS")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -O3 -funroll-loops -DGGML_USE_K_QUANTS")

# Включаем оптимизации специально для ARM64 (большинство современных телефонов)
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    # armv8.2-a+fp16+dotprod дает огромное ускорение для нейросетей на Android
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8.2-a+fp16+dotprod")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.2-a+fp16+dotprod")
endif()

# --- КОНЕЦ ИСПРАВЛЕНИЯ ---

# Указываем пути к заголовочным файлам
# Важно: убедитесь, что структура папок соответствует реальному расположению файлов
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/common)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src)

# Собираем библиотеку.
# Используем GLOB_RECURSE, чтобы CMake сам нашел все .c и .cpp файлы внутри папки llama.
# Это спасет от ошибок "File not found", если структура папок llama.cpp чуть отличается.
file(GLOB_RECURSE LLAMA_SOURCES
    "${CMAKE_CURRENT_SOURCE_DIR}/llama/src/*.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src/*.c"
    "${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src/*.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/llama/common/*.cpp"
)

# Создаем библиотеку-мост
add_library(
    llama
    SHARED
    native-lib.cpp
    ${LLAMA_SOURCES}
)

# Подключаем логирование Android
find_library(
    log-lib
    log
)

# Линкуем всё вместе
target_link_libraries(
    llama
    ${log-lib}
)
