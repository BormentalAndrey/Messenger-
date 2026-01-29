cmake_minimum_required(VERSION 3.22.1)
project("p2p-ai-engine" LANGUAGES C CXX)

# 1. СТАНДАРТЫ
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 2. BUILD INFO
set(BUILD_INFO_FILE "${CMAKE_CURRENT_BINARY_DIR}/build-info.cpp")
file(WRITE "${BUILD_INFO_FILE}" "
int LLAMA_BUILD_NUMBER = 1;
char const * LLAMA_COMMIT = \"unknown\";
char const * LLAMA_COMPILER = \"clang-android\";
char const * LLAMA_BUILD_TARGET = \"${ANDROID_ABI}\";
")

# 3. ГЛОБАЛЬНЫЕ МАКРОСЫ
add_compile_definitions(GGML_VERSION=\"1\" GGML_COMMIT=\"unknown\")
# Полная блокировка JSON на уровне кода llama.cpp
add_compile_definitions(LLAMA_NO_JSON)

# -g0 критичен для Github Actions, чтобы не упасть по памяти (OOM) при линковке
set(COMMON_FLAGS "-O3 -fPIC -DNDEBUG -g0 -DGGML_USE_K_QUANTS")

# 4. АРХИТЕКТУРНЫЕ ОПТИМИЗАЦИИ
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_compile_definitions(GGML_USE_NEON GGML_USE_CPU_AARCH64)
    set(ARCH_FLAGS "-march=armv8.2-a+fp16+dotprod")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    add_compile_definitions(
        GGML_USE_NEON GGML_FP16_INSTALLED=0 GGML_SOFT_FP16
        GGML_NO_F16_VEC GGML_NO_ACCELERATE
        __ARM_FEATURE_FP16_VECTOR_ARITHMETIC=0
        __ARM_FEATURE_FP16_SCALAR_ARITHMETIC=0
    )
    set(ARCH_FLAGS "-march=armv7-a -mfpu=neon -mfloat-abi=softfp")
else()
    set(ARCH_FLAGS "")
endif()

set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS} ${COMMON_FLAGS} ${ARCH_FLAGS}")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${COMMON_FLAGS} ${ARCH_FLAGS}")

# 5. INCLUDE PATHS
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/llama
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/common
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src/ggml-cpu
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src/ggml-cpu/cpp
)

# 6. СБОР ИСХОДНИКОВ
# Ядро LLAMA и модели
file(GLOB LLAMA_CORE_SRC "llama/src/*.cpp")
file(GLOB LLAMA_MODELS_SRC "llama/src/models/*.cpp")

# Ядро GGML и CPU бэкенд
file(GLOB GGML_BASE_SRC "llama/ggml/src/*.c" "llama/ggml/src/*.cpp")
file(GLOB_RECURSE GGML_CPU_SRC 
    "llama/ggml/src/ggml-cpu/*.c" 
    "llama/ggml/src/ggml-cpu/*.cpp"
)

# COMMON: Только то, что НЕ зависит от JSON и парсеров аргументов
set(COMMON_SRC
    "llama/common/common.cpp"
    "llama/common/sampling.cpp"
    "llama/common/log.cpp"
    "llama/common/console.cpp"
    ${BUILD_INFO_FILE}
)

set(ALL_SOURCES 
    ${LLAMA_CORE_SRC} 
    ${LLAMA_MODELS_SRC} 
    ${GGML_BASE_SRC} 
    ${GGML_CPU_SRC} 
    ${COMMON_SRC}
)

# 7. ФИЛЬТРАЦИЯ (Критический этап для Android)
# 7.1. Удаляем все файлы, связанные с JSON, чатом и парсингом аргументов (arg.cpp)
list(FILTER ALL_SOURCES EXCLUDE REGEX ".*/(chat|arg|json-schema-to-grammar|speculative|ngram-cache|json)\\..*")

# 7.2. Удаляем другие платформы
list(FILTER ALL_SOURCES EXCLUDE REGEX ".*/(amx|avx|x86|loongarch|powerpc|riscv|s390|wasm|kleidiai|spacemit)/.*")
list(FILTER ALL_SOURCES EXCLUDE REGEX ".*/ggml-(blas|cann|cuda|hexagon|kompute|metal|musa|opencl|rpc|sycl|vulkan|virtgpu)/.*")
list(FILTER ALL_SOURCES EXCLUDE REGEX ".*/(test-|tests|examples|poc|main|server)/.*")

# 7.3. Конфликт repack.cpp
if(ANDROID_ABI MATCHES "arm")
    list(FILTER ALL_SOURCES EXCLUDE REGEX "ggml-cpu/repack\\.cpp$")
else()
    list(FILTER ALL_SOURCES EXCLUDE REGEX "ggml-cpu/arch/arm/.*")
endif()

# 7.4. Фикс для 32-bit ARM
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    list(FILTER ALL_SOURCES EXCLUDE REGEX ".*/llamafile/.*")
endif()

# 8. СБОРКА БИБЛИОТЕКИ
# native-lib.cpp идет последним для корректной линковки символов
add_library(llama SHARED ${ALL_SOURCES} native-lib.cpp)

# 9. ЛИНКОВКА
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(llama
    ${log-lib}
    ${android-lib}
    atomic
    m
)

# Свойства видимости для минимизации размера библиотеки
set_target_properties(llama PROPERTIES 
    CLEAN_DIRECT_OUTPUT 1
    POSITION_INDEPENDENT_CODE ON
    VISIBILITY_INLINES_HIDDEN ON
    C_VISIBILITY_PRESET hidden
    CXX_VISIBILITY_PRESET hidden
)
