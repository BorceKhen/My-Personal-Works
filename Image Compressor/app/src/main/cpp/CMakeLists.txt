cmake_minimum_required(VERSION 3.22.1)

project("nativecompressor")

# Configure libjpeg-turbo options
set(ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(ENABLE_STATIC ON CACHE BOOL "" FORCE)
set(WITH_SIMD OFF CACHE BOOL "" FORCE)
set(WITH_TURBOJPEG OFF CACHE BOOL "" FORCE)
set(WITH_JAVA OFF CACHE BOOL "" FORCE)
set(ENABLE_PROGRAMS OFF CACHE BOOL "" FORCE)

# Add the vendored libjpeg-turbo directory
add_subdirectory(libjpeg-turbo)

# Add our native compressor shared library
add_library(
    nativecompressor
    SHARED
    native-compressor.cpp
)

# Include directories
target_include_directories(
    nativecompressor
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/libjpeg-turbo
    ${CMAKE_CURRENT_BINARY_DIR}/libjpeg-turbo
)

# Find Android NDK logging library
find_library(
    log-lib
    log
)

# Find Android graphics library
find_library(
    jnigraphics-lib
    jnigraphics
)

# Link nativecompressor with jpeg-static, log, and jnigraphics
target_link_libraries(
    nativecompressor
    ${log-lib}
    ${jnigraphics-lib}
    jpeg-static
)
