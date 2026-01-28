cmake_minimum_required(VERSION 3.22.1)
project("p2p-ai-engine")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# 1. ФЛАГИ И МАКРОСЫ (Устраняем ошибки undefined symbol для версий и логов)
set(COMMON_FLAGS "-O3 -funroll-loops -fPIC -DNDEBUG -DGGML_USE_K_QUANTS")
add_definitions(-DGGML_VERSION="1")
add_definitions(-DGGML_COMMIT="unknown")
add_definitions(-DLLAMA_BUILD_NUMBER=1)
add_definitions(-DLLAMA_COMMIT="unknown")
add_definitions(-DLLAMA_COMPILER="clang-${CMAKE_CXX_COMPILER_VERSION}")
add_definitions(-DLLAMA_BUILD_TARGET="${ANDROID_ABI}")

# 2. АРХИТЕКТУРНЫЕ ОПТИМИЗАЦИИ
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    add_definitions(-DGGML_USE_NEON)
    add_definitions(-DGGML_USE_CPU_AARCH64)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${COMMON_FLAGS} -march=armv8.2-a+fp16+dotprod")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${COMMON_FLAGS} -march=armv8.2-a+fp16+dotprod")
else()
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${COMMON_FLAGS}")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${COMMON_FLAGS}")
endif()

# 3. ПУТИ
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/llama
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/common
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src/ggml-cpu
)

# 4. ИСХОДНИКИ (Добавляем недостающий log.cpp)

# Все модели и ядро
file(GLOB_RECURSE LLAMA_CORE_SOURCES
    "llama/src/*.cpp"
    "llama/src/*.c"
)

# Файлы GGML
file(GLOB GGML_SOURCES
    "llama/ggml/src/*.c"
    "llama/ggml/src/*.cpp"
)

# CPU бэкенд
file(GLOB_RECURSE GGML_CPU_SOURCES
    "llama/ggml/src/ggml-cpu/*.c"
    "llama/ggml/src/ggml-cpu/*.cpp"
)

# Вспомогательные файлы (ДОБАВЛЕН log.cpp)
set(COMMON_SOURCES
    "llama/common/common.cpp"
    "llama/common/sampling.cpp"
    "llama/common/log.cpp"
    "llama/common/build-info.cpp"
)

# Объединяем
set(LLAMA_SOURCES 
    ${LLAMA_CORE_SOURCES} 
    ${GGML_SOURCES} 
    ${GGML_CPU_SOURCES} 
    ${COMMON_SOURCES}
)

# Фильтрация
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*/(amx|avx|x86|loongarch|powerpc|riscv|s390|wasm|kleidiai|spacemit)/.*")
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*/ggml-(blas|cann|cuda|hexagon|kompute|metal|musa|opencl|rpc|sycl|vulkan|virtgpu)/.*")
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*/(test-|tests|examples)/.*")

# 5. СБОРКА
# Собираем модели отдельно (статикой), чтобы не перегружать линковщик
add_library(llama_objs OBJECT ${LLAMA_SOURCES})

add_library(llama SHARED 
    native-lib.cpp 
    $<TARGET_OBJECTS:llama_objs>
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(llama
    ${log-lib}
    ${android-lib}
    atomic
    m
)

set_target_properties(llama PROPERTIES CLEAN_DIRECT_OUTPUT 1)
