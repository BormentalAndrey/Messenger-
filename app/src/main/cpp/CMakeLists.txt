cmake_minimum_required(VERSION 3.22.1)
project("p2p-ai-engine")

# 1. ЗАГЛУШКИ И ОПРЕДЕЛЕНИЯ АРХИТЕКТУРЫ
add_definitions(-DGGML_VERSION="1")
add_definitions(-DGGML_COMMIT="unknown")
add_definitions(-DGGML_USE_K_QUANTS)

# Критически важно для ARM64 на Android
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    add_definitions(-DGGML_USE_NEON)
    add_definitions(-DGGML_USE_CPU_AARCH64)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8.2-a+fp16+dotprod")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.2-a+fp16+dotprod")
endif()

# Общие флаги оптимизации
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -funroll-loops")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -O3 -funroll-loops")

# 2. ПУТИ К ЗАГОЛОВКАМ
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/common)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src/ggml-cpu)

# 3. СБОР ИСХОДНИКОВ (Исправлено для включения NEON оптимизаций)
file(GLOB_RECURSE LLAMA_SOURCES
    "llama/src/*.cpp"
    "llama/src/*.c"
    "llama/ggml/src/*.c"
    "llama/ggml/src/*.cpp"
)

# Исключаем файлы других архитектур, чтобы не было конфликтов при сборке
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*cuda.*")
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*metal.*")
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*sycl.*")
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*rpc.*")
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*/amx/.*")
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*/avx/.*")
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*/x86/.*")

# 4. СБОРКА БИБЛИОТЕКИ
add_library(llama SHARED native-lib.cpp ${LLAMA_SOURCES})

find_library(log-lib log)
target_link_libraries(llama ${log-lib} android)
