cmake_minimum_required(VERSION 3.22.1)
project("p2p-ai-engine")

# 1. СТАНДАРТЫ
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# Генерируем build-info.cpp (без него llama.cpp не соберется из-за extern переменных)
set(BUILD_INFO_FILE "${CMAKE_CURRENT_BINARY_DIR}/build-info.cpp")
file(WRITE ${BUILD_INFO_FILE} "
    int LLAMA_BUILD_NUMBER = 1;
    char const * LLAMA_COMMIT = \"unknown\";
    char const * LLAMA_COMPILER = \"clang-android\";
    char const * LLAMA_BUILD_TARGET = \"${ANDROID_ABI}\";
")

# Глобальные макросы
add_definitions(-DGGML_VERSION="1")
add_definitions(-DGGML_COMMIT="unknown")

# Базовые флаги: -g0 критически важен для GitHub Actions (экономит RAM)
set(COMMON_FLAGS "-O3 -fPIC -DNDEBUG -g0 -DGGML_USE_K_QUANTS")

# 2. АРХИТЕКТУРНЫЕ ОПТИМИЗАЦИИ
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    add_definitions(-DGGML_USE_NEON)
    add_definitions(-DGGML_USE_CPU_AARCH64)
    # Включаем современные инструкции для 64-бит
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${COMMON_FLAGS} -march=armv8.2-a+fp16+dotprod")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${COMMON_FLAGS} -march=armv8.2-a+fp16+dotprod")
elseif(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    add_definitions(-DGGML_USE_NEON)
    # Фикс ошибки vld1q_f16: отключаем аппаратный float16 для 32-бит
    add_definitions(-DGGML_FP16_INSTALLED=0)
    add_definitions(-DGGML_SOFT_FP16)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${COMMON_FLAGS} -march=armv7-a -mfpu=neon -mfloat-abi=softfp")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${COMMON_FLAGS} -march=armv7-a -mfpu=neon -mfloat-abi=softfp")
else()
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${COMMON_FLAGS}")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${COMMON_FLAGS}")
endif()

# 3. ПУТИ К ЗАГОЛОВКАМ
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/llama
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/common
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src/ggml-cpu
)

# 4. СБОР ИСХОДНИКОВ
# Важно: GLOB_RECURSE по src/models решает ошибку undefined symbol starcoder2
file(GLOB_RECURSE CORE_SRC 
    "llama/src/*.cpp" 
    "llama/src/*.c"
)
file(GLOB GGML_SRC "llama/ggml/src/*.c" "llama/ggml/src/*.cpp")
file(GLOB_RECURSE GGML_CPU_SRC "llama/ggml/src/ggml-cpu/*.c" "llama/ggml/src/ggml-cpu/*.cpp")

set(COMMON_SRC
    "llama/common/common.cpp"
    "llama/common/sampling.cpp"
    "llama/common/log.cpp"
    ${BUILD_INFO_FILE}
)

set(ALL_SOURCES ${CORE_SRC} ${GGML_SRC} ${GGML_CPU_SRC} ${COMMON_SRC})

# ФИЛЬТРАЦИЯ (Удаляем лишнее для Android)
list(FILTER ALL_SOURCES EXCLUDE REGEX ".*/(amx|avx|x86|loongarch|powerpc|riscv|s390|wasm|kleidiai|spacemit)/.*")
list(FILTER ALL_SOURCES EXCLUDE REGEX ".*/ggml-(blas|cann|cuda|hexagon|kompute|metal|musa|opencl|rpc|sycl|vulkan|virtgpu)/.*")
list(FILTER ALL_SOURCES EXCLUDE REGEX ".*/(test-|tests|examples)/.*")

# 5. СБОРКА БИБЛИОТЕКИ
add_library(llama SHARED 
    native-lib.cpp 
    ${ALL_SOURCES}
)

# 6. ЛИНКОВКА
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(llama
    ${log-lib}
    ${android-lib}
    atomic
    m
)

set_target_properties(llama PROPERTIES CLEAN_DIRECT_OUTPUT 1)
