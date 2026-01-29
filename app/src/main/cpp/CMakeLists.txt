cmake_minimum_required(VERSION 3.22.1)
project("p2p-ai-engine" LANGUAGES C CXX)

# ------------------------------------------------------------------------------
# 1. СТАНДАРТЫ
# ------------------------------------------------------------------------------
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ------------------------------------------------------------------------------
# 2. BUILD INFO (Обязателен для llama.cpp)
# ------------------------------------------------------------------------------
set(BUILD_INFO_FILE "${CMAKE_CURRENT_BINARY_DIR}/build-info.cpp")
file(WRITE "${BUILD_INFO_FILE}" "
    int LLAMA_BUILD_NUMBER = 1;
    char const * LLAMA_COMMIT = \"unknown\";
    char const * LLAMA_COMPILER = \"clang-android\";
    char const * LLAMA_BUILD_TARGET = \"${ANDROID_ABI}\";
")

# ------------------------------------------------------------------------------
# 3. ГЛОБАЛЬНЫЕ МАКРОСЫ
# ------------------------------------------------------------------------------
add_compile_definitions(
    GGML_VERSION=\"1\"
    GGML_COMMIT=\"unknown\"
)

# -g0 критичен для CI: без него линковщик падает по OOM (Out of Memory)
set(COMMON_FLAGS "-O3 -fPIC -DNDEBUG -g0 -DGGML_USE_K_QUANTS")

# ------------------------------------------------------------------------------
# 4. АРХИТЕКТУРНЫЕ ОПТИМИЗАЦИИ
# ------------------------------------------------------------------------------
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_compile_definitions(
        GGML_USE_NEON
        GGML_USE_CPU_AARCH64
    )
    set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS} ${COMMON_FLAGS} -march=armv8.2-a+fp16+dotprod")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${COMMON_FLAGS} -march=armv8.2-a+fp16+dotprod")

elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    # ARMv7: Глубокая блокировка FP16. 
    # Новые версии ggml-cpu требуют этих флагов, чтобы не лезть в vec.h с f16 интринсиками.
    add_compile_definitions(
        GGML_USE_NEON
        GGML_FP16_INSTALLED=0
        GGML_SOFT_FP16
        # КРИТИЧЕСКИЕ ФЛАГИ ДЛЯ ПОДАВЛЕНИЯ ОШИБОК vmulq_f16 / vfmaq_f16
        GGML_NO_F16_VEC
        GGML_NO_ACCELERATE
        __ARM_FEATURE_FP16_VECTOR_ARITHMETIC=0
        __ARM_FEATURE_FP16_SCALAR_ARITHMETIC=0
    )

    set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS} ${COMMON_FLAGS} -march=armv7-a -mfpu=neon -mfloat-abi=softfp")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${COMMON_FLAGS} -march=armv7-a -mfpu=neon -mfloat-abi=softfp")

else()
    set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS} ${COMMON_FLAGS}")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${COMMON_FLAGS}")
endif()

# ------------------------------------------------------------------------------
# 5. ПУТИ К ЗАГОЛОВКАМ
# ------------------------------------------------------------------------------
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/llama
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/common
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/ggml/src/ggml-cpu
)

# ------------------------------------------------------------------------------
# 6. СБОР ИСХОДНИКОВ
# ------------------------------------------------------------------------------
file(GLOB_RECURSE LLAMA_SRC "llama/src/*.cpp" "llama/src/*.c")
file(GLOB GGML_SRC "llama/ggml/src/*.cpp" "llama/ggml/src/*.c")
file(GLOB_RECURSE GGML_CPU_SRC "llama/ggml/src/ggml-cpu/*.cpp" "llama/ggml/src/ggml-cpu/*.c")

set(COMMON_SRC
    "llama/common/common.cpp"
    "llama/common/sampling.cpp"
    "llama/common/log.cpp"
    ${BUILD_INFO_FILE}
)

set(ALL_SOURCES ${LLAMA_SRC} ${GGML_SRC} ${GGML_CPU_SRC} ${COMMON_SRC})

# ------------------------------------------------------------------------------
# 7. ФИЛЬТРАЦИЯ
# ------------------------------------------------------------------------------
list(FILTER ALL_SOURCES EXCLUDE REGEX ".*/(amx|avx|x86|loongarch|powerpc|riscv|s390|wasm|kleidiai|spacemit)/.*")
list(FILTER ALL_SOURCES EXCLUDE REGEX ".*/ggml-(blas|cann|cuda|hexagon|kompute|metal|musa|opencl|rpc|sycl|vulkan|virtgpu)/.*")
list(FILTER ALL_SOURCES EXCLUDE REGEX ".*/(test-|tests|examples)/.*")

# ------------------------------------------------------------------------------
# 8. КРИТИЧЕСКИЙ ФИКС: Удаляем llamafile (sgemm) для armeabi-v7a
# ------------------------------------------------------------------------------
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    list(FILTER ALL_SOURCES EXCLUDE REGEX ".*/llamafile/.*")
endif()

# ------------------------------------------------------------------------------
# 9. СБОРКА БИБЛИОТЕКИ
# ------------------------------------------------------------------------------
add_library(llama SHARED native-lib.cpp ${ALL_SOURCES})

# ------------------------------------------------------------------------------
# 10. ЛИНКОВКА
# ------------------------------------------------------------------------------
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(llama
    ${log-lib}
    ${android-lib}
    atomic
    m
)

set_target_properties(llama PROPERTIES CLEAN_DIRECT_OUTPUT 1)
