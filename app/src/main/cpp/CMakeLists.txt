cmake_minimum_required(VERSION 3.22.1)

project("p2p-ai-engine")

# Настройки оптимизации (КРИТИЧНО для скорости)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -march=native -funroll-loops")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -march=native -funroll-loops")

# 1. Подключаем файлы самого движка llama.cpp
# Предполагается, что исходники llama.cpp лежат в папке 'llama' внутри 'cpp'
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama)

include_directories(${LLAMA_DIR})
include_directories(${LLAMA_DIR}/common)
include_directories(${LLAMA_DIR}/include)

# Собираем исходники llama (это ядро)
# В зависимости от версии llama.cpp список файлов может меняться, это базовый набор
file(GLOB LLAMA_SOURCES
    "${LLAMA_DIR}/ggml/src/ggml.c"
    "${LLAMA_DIR}/ggml/src/ggml-alloc.c"
    "${LLAMA_DIR}/ggml/src/ggml-backend.c"
    "${LLAMA_DIR}/ggml/src/ggml-quants.c"
    "${LLAMA_DIR}/src/llama.cpp"
    "${LLAMA_DIR}/src/unicode.cpp"
    "${LLAMA_DIR}/src/unicode-data.cpp"
    "${LLAMA_DIR}/common/common.cpp"
    "${LLAMA_DIR}/common/sampling.cpp"
    "${LLAMA_DIR}/common/console.cpp"
)

# 2. Создаем нашу библиотеку-мост
add_library(
    llama
    SHARED
    native-lib.cpp
    ${LLAMA_SOURCES}
)

# 3. Подключаем логирование Android
find_library(
    log-lib
    log
)

# 4. Линкуем всё вместе
target_link_libraries(
    llama
    ${log-lib}
)
