package com.example.usb

/**
 * JNI bridge to the test-only native libusb probe.
 *
 * The descriptor is borrowed from UsbDeviceConnection. Native code duplicates it before
 * calling libusb_wrap_sys_device(), so libusb_close() cannot close Android's original FD.
 */
class LibUsbNative {
    external fun runStage(fileDescriptor: Int, stage: Int): String

    companion object {
        init {
            System.loadLibrary("usbprobe")
        }
    }
}
