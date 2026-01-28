cmake_minimum_required(VERSION 3.22.1)

project("p2p-ai-engine")

# Создаем библиотеку llama из нашего файла
add_library(
    llama
    SHARED
    native-lib.cpp
)

# Поиск системных библиотек Android для логирования
find_library(
    log-lib
    log
)

# Линковка библиотек
target_link_libraries(
    llama
    ${log-lib}
)
