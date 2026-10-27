# Android libusb probe

This branch adds an isolated libusb experiment to USB ETH Probe. It is not a production USB
driver and does not modify Transparent Bridge routing.

## Build

The app uses Android NDK + CMake. During CMake configuration, FetchContent downloads the
pinned CMake-maintained libusb tree from `libusb/libusb-cmake` at tag `v1.0.30-0`.
The first native build therefore requires network access and CMake/NDK installed in the
Android build environment.

Build with:

```bash
./gradlew assembleDebug --no-configuration-cache
```

## Test procedure

1. Install the APK and connect the WCH USB Ethernet adapter.
2. Grant the normal Android USB permission prompt.
3. Run the existing USB descriptor test first and export its log.
4. Tap **LIBUSB: WRAP + DETACH/CLAIM**.
5. Export the complete app log and matching kernel/logcat output.
6. Repeat separately on Android 10, 11, and 15.

## What the probe does

- Duplicates the FD returned by `UsbDeviceConnection`, preserving the framework-owned FD.
- Sets `LIBUSB_OPTION_NO_DEVICE_DISCOVERY`, initializes libusb and wraps the duplicated FD.
- Logs the device descriptor and active configuration/interface/endpoint descriptors.
- Attempts `libusb_kernel_driver_active`, detach, claim, release and reattach for interfaces 0
  and 1, logging each return code.
- Calls release/reattach only when the corresponding operation is applicable.

## Interpretation limits

- Successful `libusb_wrap_sys_device` proves only that libusb accepted the supplied system FD.
- Android's USB Host API permission is still required. libusb cannot bypass it.
- `libusb_kernel_driver_active` and detach operations may be unsupported on Android or by a
  particular vendor kernel. An unsupported result is evidence about that backend, not proof
  that no kernel driver exists.
- Interface 0 is commonly the CDC-ECM control interface; interface 1 is commonly the data
  interface, but always use the descriptors from the current adapter/configuration.
- Detaching a network driver can briefly remove `eth0`. This is an explicit diagnostic action.
  The probe attempts to reattach a driver it successfully detached, but restoration is not
  guaranteed if the device disconnects or the vendor kernel rejects reattachment.
- No device traffic is sent by this test. It only probes descriptor access and interface ownership.

## Dependencies

- Android NDK and CMake installed in the build environment.
- Internet access on the first CMake configure to fetch the pinned libusb CMake port.
- ABI filters are restricted to `arm64-v8a` and `armeabi-v7a`, matching the target Android phones.
