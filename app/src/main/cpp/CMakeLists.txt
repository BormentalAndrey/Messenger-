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
add_compile_definitions(LLAMA_NO_JSON)

# -g0 экономит память (OOM) на CI/GitHub Actions при линковке
set(COMMON_FLAGS "-O3 -fPIC -DNDEBUG -g0 -DGGML_USE_K_QUANTS")

# 4. АРХИТЕКТУРНЫЕ ОПТИМИЗАЦИИ
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_compile_definitions(GGML_USE_NEON GGML_USE_CPU_AARCH64)
    set(ARCH_FLAGS "-march=armv8.2-a+fp16+dotprod")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    add_compile_definitions(
        GGML_USE_NEON GGML_FP16_INSTALLED=0 GGML_SOFT_FP16
        GGML_NO_F16_VEC GGML_NO_ACCELERATE
    )
    set(ARCH_FLAGS "-march=armv7-a -mfpu=neon -mfloat-abi=softfp")
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
)

# 6. СБОР ИСХОДНИКОВ
file(GLOB_RECURSE ALL_FILES 
    "llama/src/*.cpp"
    "llama/src/*.c"
    "llama/ggml/src/*.c"
    "llama/ggml/src/*.cpp"
)

# Выбираем стабильный минимум из common
set(COMMON_MINIMAL_SRC
    "llama/common/common.cpp"
    "llama/common/sampling.cpp"
    "llama/common/log.cpp"
    "llama/common/console.cpp"
    ${BUILD_INFO_FILE}
)

set(ALL_SOURCES ${ALL_FILES} ${COMMON_MINIMAL_SRC})

# 7. СТРОГАЯ ФИЛЬТРАЦИЯ (Production-ready)
# 7.1. Исключаем JSON, CLI-парсеры и серверную логику
list(FILTER ALL_SOURCES EXCLUDE REGEX ".*/(chat|arg|json|speculative|ngram-cache|main-|main\\.|server|test-|tests|examples|poc)/.*")

# 7.2. Исключаем GPU/Акселераторы (включая проблемный webgpu)
list(FILTER ALL_SOURCES EXCLUDE REGEX ".*/ggml-(blas|cann|cuda|hexagon|kompute|metal|musa|opencl|rpc|sycl|vulkan|virtgpu|webgpu|amx)/.*")

# 7.3. Исключаем другие платформы
list(FILTER ALL_SOURCES EXCLUDE REGEX ".*/(avx|x86|loongarch|powerpc|riscv|s390|wasm|kleidiai|spacemit)/.*")

# 7.4. Решаем конфликт repack.cpp (оставляем только версию из arch/arm/)
if(ANDROID_ABI MATCHES "arm")
    list(FILTER ALL_SOURCES EXCLUDE REGEX "ggml-cpu/repack\\.cpp$")
else()
    list(FILTER ALL_SOURCES EXCLUDE REGEX "ggml-cpu/arch/arm/.*")
endif()

# 7.5. Фикс для 32-bit ARM (llamafile не поддерживает старые NDK)
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    list(FILTER ALL_SOURCES EXCLUDE REGEX ".*/llamafile/.*")
endif()

# 8. СБОРКА БИБЛИОТЕКИ
add_library(llama SHARED native-lib.cpp ${ALL_SOURCES})

# 9. ЛИНКОВКА
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(llama
    ${log-lib}
    ${android-lib}
    atomic
    m
)

# 10. PROPERTIES
set_target_properties(llama PROPERTIES 
    CLEAN_DIRECT_OUTPUT 1
    POSITION_INDEPENDENT_CODE ON
    # Скрываем символы для уменьшения размера .so
    C_VISIBILITY_PRESET hidden
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)
