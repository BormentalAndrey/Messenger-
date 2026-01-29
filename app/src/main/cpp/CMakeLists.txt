cmake_minimum_required(VERSION 3.22.1)
project("p2p-ai-engine" LANGUAGES C CXX)

# ============================================================================
# 1. СТАНДАРТЫ
# ============================================================================
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ============================================================================
# 2. BUILD INFO (обязательно для llama.cpp)
# ============================================================================
set(BUILD_INFO_FILE "${CMAKE_CURRENT_BINARY_DIR}/build-info.cpp")
file(WRITE "${BUILD_INFO_FILE}" "
int LLAMA_BUILD_NUMBER = 1;
char const * LLAMA_COMMIT = \"unknown\";
char const * LLAMA_COMPILER = \"clang-android\";
char const * LLAMA_BUILD_TARGET = \"${ANDROID_ABI}\";
")

# ============================================================================
# 3. МАКРОСЫ И ГЛОБАЛЬНЫЕ ФЛАГИ
# ============================================================================
add_compile_definitions(
    GGML_VERSION=\"1\"
    GGML_COMMIT=\"unknown\"
    LLAMA_NO_JSON            # не тянем nlohmann/json
)

# -g0 критично для CI (OOM linker)
set(COMMON_FLAGS "-O3 -fPIC -DNDEBUG -g0 -DGGML_USE_K_QUANTS")

# ============================================================================
# 4. АРХИТЕКТУРНЫЕ ФЛАГИ
# ============================================================================
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_compile_definitions(GGML_USE_NEON GGML_USE_CPU_AARCH64)
    set(ARCH_FLAGS "-march=armv8.2-a+fp16+dotprod")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    add_compile_definitions(
        GGML_USE_NEON
        GGML_FP16_INSTALLED=0
        GGML_SOFT_FP16
        GGML_NO_F16_VEC
    )
    set(ARCH_FLAGS "-march=armv7-a -mfpu=neon -mfloat-abi=softfp")
endif()

set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS} ${COMMON_FLAGS} ${ARCH_FLAGS}")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${COMMON_FLAGS} ${ARCH_FLAGS}")

# ============================================================================
# 5. INCLUDE PATHS
# ============================================================================
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/llama
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/common
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src/ggml-cpu
)

# ============================================================================
# 6. СБОР ИСХОДНИКОВ (WHITE-LIST, CPU ONLY)
# ============================================================================

# GGML core
file(GLOB GGML_CORE_SRC
    "llama/ggml/src/*.c"
    "llama/ggml/src/*.cpp"
)

# GGML CPU backend
file(GLOB GGML_CPU_SRC
    "llama/ggml/src/ggml-cpu/*.c"
    "llama/ggml/src/ggml-cpu/*.cpp"
    "llama/ggml/src/ggml-cpu/cpp/*.cpp"
)

# llama core
file(GLOB LLAMA_CORE_SRC
    "llama/src/*.cpp"
)

# 🔥 КРИТИЧНО: билдеры моделей (иначе undefined symbol)
file(GLOB LLAMA_MODEL_BUILDERS_SRC
    "llama/src/models/*.cpp"
)

# minimal common (без cli / json)
set(LLAMA_COMMON_SRC
    "llama/common/common.cpp"
    "llama/common/sampling.cpp"
    "llama/common/log.cpp"
    "llama/common/console.cpp"
    ${BUILD_INFO_FILE}
)

set(ALL_SOURCES
    ${GGML_CORE_SRC}
    ${GGML_CPU_SRC}
    ${LLAMA_CORE_SRC}
    ${LLAMA_MODEL_BUILDERS_SRC}
    ${LLAMA_COMMON_SRC}
)

# ============================================================================
# 7. ФИЛЬТРАЦИЯ ЛИШНЕГО
# ============================================================================
list(FILTER ALL_SOURCES EXCLUDE REGEX
    ".*/(chat|arg|json|speculative|ngram-cache|server|main-|main\\.|test-|tests|examples|poc)/.*"
)

# repack.cpp конфликтует — исключаем
if(ANDROID_ABI MATCHES "arm")
    list(FILTER ALL_SOURCES EXCLUDE REGEX "ggml-cpu/repack\\.cpp$")
endif()

# llamafile не нужен на 32-bit
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    list(FILTER ALL_SOURCES EXCLUDE REGEX ".*/llamafile/.*")
endif()

# ============================================================================
# 8. СБОРКА SHARED LIB
# ============================================================================
add_library(llama SHARED
    native-lib.cpp
    ${ALL_SOURCES}
)

# ============================================================================
# 9. ЛИНКОВКА
# ============================================================================
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(llama
    ${log-lib}
    ${android-lib}
    atomic
    m
)

# ============================================================================
# 10. PROPERTIES (оптимизация размера и символов)
# ============================================================================
set_target_properties(llama PROPERTIES
    POSITION_INDEPENDENT_CODE ON
    C_VISIBILITY_PRESET hidden
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)
