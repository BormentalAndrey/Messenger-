cmake_minimum_required(VERSION 3.22.1)
project("p2p-ai-engine")

# 1. КОНФИГУРАЦИЯ СТАНДАРТОВ И ФЛАГОВ
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# Оптимизации для релиза
set(COMMON_FLAGS "-O3 -funroll-loops -fPIC -DNDEBUG -DGGML_USE_K_QUANTS")

# Версионность (Production approach: используем определения, если они не переданы извне)
if(NOT DEFINED GGML_VERSION)
    add_definitions(-DGGML_VERSION="1")
endif()
if(NOT DEFINED GGML_COMMIT)
    add_definitions(-DGGML_COMMIT="unknown")
endif()

# 2. АРХИТЕКТУРНЫЕ ОПТИМИЗАЦИИ (ARM64 NEON / DotProd / FP16)
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    add_definitions(-DGGML_USE_NEON)
    add_definitions(-DGGML_USE_CPU_AARCH64)
    # Включаем аппаратное ускорение матриц (dotprod) и работу с половинной точностью (fp16)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${COMMON_FLAGS} -march=armv8.2-a+fp16+dotprod")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${COMMON_FLAGS} -march=armv8.2-a+fp16+dotprod")
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

# 4. СБОР ИСХОДНЫХ ФАЙЛОВ
# Собираем только то, что необходимо для работы на CPU
file(GLOB_RECURSE LLAMA_SOURCES
    "llama/src/*.cpp"
    "llama/src/*.c"
    "llama/ggml/src/*.c"
    "llama/ggml/src/*.cpp"
    "llama/common/common.cpp"
    "llama/common/sampling.cpp"
    "llama/common/json.hpp"
)

# --- ПРОДАКШН ФИЛЬТРАЦИЯ (Удаляем всё, что требует внешних зависимостей) ---

# Удаляем бэкенды сторонних вендоров (BLAS, CUDA, Vulkan и т.д.)
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*/ggml-(blas|cann|cuda|kompute|metal|musa|opencl|rpc|sycl|vulkan)/.*")

# Удаляем специфичные для x86/Intel файлы (они не скомпилируются под Android ARM)
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*/(amx|avx|x86)/.*")

# Удаляем тестовые и вспомогательные файлы внутри библиотек
list(FILTER LLAMA_SOURCES EXCLUDE REGEX ".*/test-.*")

# 5. СБОРКА И ЛИНКОВКА
add_library(llama SHARED 
    native-lib.cpp 
    ${LLAMA_SOURCES}
)

# Системные библиотеки Android
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(llama
    ${log-lib}
    ${android-lib}
    atomic
    m # Математическая библиотека
)

# Установка свойств (необязательно, но полезно для предотвращения конфликтов имен)
set_target_properties(llama PROPERTIES CLEAN_DIRECT_OUTPUT 1)
