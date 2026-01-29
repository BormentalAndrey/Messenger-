cmake_minimum_required(VERSION 3.22.1)
project("p2p-ai-engine")

# 1. СТАНДАРТЫ И БАЗОВЫЕ ФЛАГИ
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# Генерируем build-info.cpp во временной директории сборки
set(BUILD_INFO_FILE "${CMAKE_CURRENT_BINARY_DIR}/build-info.cpp")
file(WRITE ${BUILD_INFO_FILE} "
    #include \"common.h\"
    int LLAMA_BUILD_NUMBER = 1;
    char const * LLAMA_COMMIT = \"unknown\";
    char const * LLAMA_COMPILER = \"clang-android\";
    char const * LLAMA_BUILD_TARGET = \"${ANDROID_ABI}\";
")

# Определения, которые не конфликтуют с типами данных
add_definitions(-DGGML_VERSION="1")
add_definitions(-DGGML_COMMIT="unknown")

set(COMMON_FLAGS "-O3 -funroll-loops -fPIC -DNDEBUG -DGGML_USE_K_QUANTS")

# 2. АРХИТЕКТУРНЫЕ ОПТИМИЗАЦИИ (ARM64)
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    add_definitions(-DGGML_USE_NEON)
    add_definitions(-DGGML_USE_CPU_AARCH64)
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

# 4. СБОР ВСЕХ ИСХОДНИКОВ (Рекурсивно, чтобы не пропустить модели)
file(GLOB_RECURSE CORE_SRC 
    "llama/src/*.cpp" 
    "llama/src/*.c"
)

file(GLOB GGML_SRC 
    "llama/ggml/src/*.c" 
    "llama/ggml/src/*.cpp"
)

file(GLOB_RECURSE GGML_CPU_SRC 
    "llama/ggml/src/ggml-cpu/*.c" 
    "llama/ggml/src/ggml-cpu/*.cpp"
)

# Вспомогательные файлы инфраструктуры
set(COMMON_SRC
    "llama/common/common.cpp"
    "llama/common/sampling.cpp"
    "llama/common/log.cpp"
    ${BUILD_INFO_FILE}
)

# Объединяем все найденные файлы
set(ALL_SOURCES ${CORE_SRC} ${GGML_SRC} ${GGML_CPU_SRC} ${COMMON_SRC})

# ФИЛЬТРАЦИЯ: Удаляем платформо-зависимый мусор и тесты
list(FILTER ALL_SOURCES EXCLUDE REGEX ".*/(amx|avx|x86|loongarch|powerpc|riscv|s390|wasm|kleidiai|spacemit)/.*")
list(FILTER ALL_SOURCES EXCLUDE REGEX ".*/ggml-(blas|cann|cuda|hexagon|kompute|metal|musa|opencl|rpc|sycl|vulkan|virtgpu)/.*")
list(FILTER ALL_SOURCES EXCLUDE REGEX ".*/(test-|tests|examples)/.*")

# 5. СБОРКА БИБЛИОТЕКИ
add_library(llama SHARED 
    native-lib.cpp 
    ${ALL_SOURCES}
)

# 6. ЛИНКОВКА СИСТЕМНЫХ БИБЛИОТЕК
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(llama
    ${log-lib}
    ${android-lib}
    atomic
    m
)

# Предотвращаем конфликты имен при очистке
set_target_properties(llama PROPERTIES CLEAN_DIRECT_OUTPUT 1)
