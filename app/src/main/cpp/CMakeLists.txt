cmake_minimum_required(VERSION 3.22.1)
project(usbprobe LANGUAGES C CXX)

include(FetchContent)

# Reproducible CMake port of upstream libusb. This is fetched at configure time;
# no libusb binary is checked into the repository.
set(LIBUSB_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LIBUSB_INSTALL_TARGETS OFF CACHE BOOL "" FORCE)
set(LIBUSB_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LIBUSB_BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(LIBUSB_ENABLE_LOGGING ON CACHE BOOL "" FORCE)
set(LIBUSB_ENABLE_UDEV OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    libusb_cmake
    GIT_REPOSITORY https://github.com/libusb/libusb-cmake.git
    GIT_TAG v1.0.30-0
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(libusb_cmake)

add_library(usbprobe SHARED libusb_probe.cpp)
target_compile_features(usbprobe PRIVATE cxx_std_17)
target_compile_options(usbprobe PRIVATE -Wall -Wextra -Werror=return-type)
target_link_libraries(usbprobe PRIVATE libusb::usb-1.0 log)
