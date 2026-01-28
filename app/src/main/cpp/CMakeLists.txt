cmake_minimum_required(VERSION 3.22.1)
project("p2p-ai-engine")

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -funroll-loops -DGGML_USE_K_QUANTS")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -O3 -funroll-loops -DGGML_USE_K_QUANTS")

if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8.2-a+fp16+dotprod")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.2-a+fp16+dotprod")
endif()

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/common)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/include)

# Собираем только ядро, чтобы не натыкаться на зависимости типа JSON в common файлах
file(GLOB LLAMA_SOURCES
    "llama/src/*.cpp"
    "llama/ggml/src/*.c"
    "llama/ggml/src/*.cpp"
    "llama/common/common.cpp"
    "llama/common/sampling.cpp"
)

# Удаляем лишние бэкенды
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*sycl.*")
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*cuda.*")
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*metal.*")

add_library(llama SHARED native-lib.cpp ${LLAMA_SOURCES})
find_library(log-lib log)
target_link_libraries(llama ${log-lib})
