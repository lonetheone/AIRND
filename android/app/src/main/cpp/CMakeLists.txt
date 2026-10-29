# On-device LLM for the match-3 test APK: the pinned llama.cpp submodule plus a
# narrow JNI bridge. Options follow llama.cpp docs/android.md for b11982; the
# ARM arch matches both test phones (dotprod + fp16, no i8mm), checked in
# /proc/cpuinfo on 2026-10-29.
cmake_minimum_required(VERSION 3.22.1)
project(airnd_llm C CXX)

set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../third_party/llama.cpp)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
foreach(opt LLAMA_BUILD_COMMON LLAMA_BUILD_TESTS LLAMA_BUILD_TOOLS LLAMA_BUILD_EXAMPLES
            LLAMA_BUILD_SERVER LLAMA_BUILD_APP LLAMA_OPENSSL
            GGML_NATIVE GGML_OPENMP GGML_LLAMAFILE)
    set(${opt} OFF CACHE BOOL "" FORCE)
endforeach()
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)

add_subdirectory(${LLAMA_DIR} llama)

add_library(airnd_llm SHARED llm_jni.cpp)
target_compile_features(airnd_llm PRIVATE cxx_std_17)
target_link_libraries(airnd_llm PRIVATE llama android log)
