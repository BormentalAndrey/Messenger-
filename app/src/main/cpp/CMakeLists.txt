cmake_minimum_required(VERSION 3.22.1)

project("p2p-ai-engine")

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -funroll-loops -DGGML_USE_K_QUANTS")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -O3 -funroll-loops -DGGML_USE_K_QUANTS")

# Включаем оптимизации специально для ARM64
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8.2-a+fp16+dotprod")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.2-a+fp16+dotprod")
endif()

# Пути к заголовкам
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/common)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src)

# Собираем исходники
# Использование GLOB_RECURSE с фильтром, чтобы исключить GPU-специфичные файлы (SYCL, CUDA, Metal),
# которые ломают сборку на Android.
file(GLOB_RECURSE LLAMA_SOURCES
    "${CMAKE_CURRENT_SOURCE_DIR}/llama/src/*.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src/*.c"
    "${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src/*.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/llama/common/*.cpp"
)

# ИСКЛЮЧАЕМ проблемные файлы (SYCL для Intel, CUDA для Nvidia, и т.д.)
# Это исправляет ошибку "fatal error: 'sycl/sycl.hpp' file not found"
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*sycl.*")
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*cuda.*")
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*metal.*")
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*vulkan.*")
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*kompute.*")
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*rpc.*")
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*amx.*")
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*blas.*")

# Создаем библиотеку
add_library(
    llama
    SHARED
    native-lib.cpp
    ${LLAMA_SOURCES}
)

find_library(
    log-lib
    log
)

target_link_libraries(
    llama
    ${log-lib}
)
