cmake_minimum_required(VERSION 3.22.1)
project("p2p-ai-engine")

# 1. ЗАГЛУШКИ ДЛЯ ВЕРСИИ (Исправляет ошибку GGML_VERSION и GGML_COMMIT)
add_definitions(-DGGML_VERSION="1")
add_definitions(-DGGML_COMMIT="unknown")

# Флаги оптимизации
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -funroll-loops -DGGML_USE_K_QUANTS")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -O3 -funroll-loops -DGGML_USE_K_QUANTS")

if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8.2-a+fp16+dotprod")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.2-a+fp16+dotprod")
endif()

# ПУТИ К ЗАГОЛОВКАМ
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/common)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src/ggml-cpu)

# ИСХОДНЫЕ ФАЙЛЫ (с учетом новой структуры папок)
file(GLOB LLAMA_SOURCES
    "llama/src/*.cpp"
    "llama/ggml/src/*.c"
    "llama/ggml/src/*.cpp"
    "llama/ggml/src/ggml-cpu/*.c"
    "llama/ggml/src/ggml-cpu/*.cpp"
    "llama/common/common.cpp"
    "llama/common/sampling.cpp"
)

# Исключаем лишнее
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*cuda.*")
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*metal.*")
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*sycl.*")

add_library(llama SHARED native-lib.cpp ${LLAMA_SOURCES})

find_library(log-lib log)
target_link_libraries(llama ${log-lib})
