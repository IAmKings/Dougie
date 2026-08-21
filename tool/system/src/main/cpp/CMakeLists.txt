cmake_minimum_required(VERSION 3.22.1)
project(dougie_intent_jni)

add_library(dougie_intent SHARED intent_ort_jni.cpp)
target_include_directories(dougie_intent PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_link_libraries(dougie_intent android log dl)
