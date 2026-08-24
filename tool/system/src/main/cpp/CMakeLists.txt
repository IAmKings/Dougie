cmake_minimum_required(VERSION 3.22.1)
project(dougie_intent_jni)

if(NOT DEFINED DOUGIE_ONNXRUNTIME_SO OR NOT EXISTS "${DOUGIE_ONNXRUNTIME_SO}")
    message(FATAL_ERROR "DOUGIE_ONNXRUNTIME_SO is missing; fetchSherpaJni must copy libonnxruntime.so")
endif()

add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES IMPORTED_LOCATION "${DOUGIE_ONNXRUNTIME_SO}")

add_library(dougie_intent SHARED intent_ort_jni.cpp)
target_include_directories(dougie_intent PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_link_libraries(dougie_intent android log onnxruntime)
