package com.example.usb

/**
 * JNI bridge to the test-only native libusb probe.
 *
 * The descriptor is borrowed from UsbDeviceConnection. Native code duplicates it before
 * calling libusb_wrap_sys_device(), so libusb_close() cannot close Android's original FD.
 */
class LibUsbNative {
    external fun probe(fileDescriptor: Int, tryDetachAndClaim: Boolean): String

    companion object {
        init {
            System.loadLibrary("usbprobe")
        }
    }
}
