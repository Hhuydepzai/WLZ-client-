cmake_minimum_required(VERSION 3.22.1)
project(wlzruntime LANGUAGES CXX)

add_library(wlzruntime SHARED wlzclient.cpp)

target_compile_features(wlzruntime PRIVATE cxx_std_17)
target_compile_options(wlzruntime PRIVATE -O2 -ffunction-sections -fdata-sections)

target_link_options(wlzruntime PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(wlzruntime ${log-lib} ${android-lib})
