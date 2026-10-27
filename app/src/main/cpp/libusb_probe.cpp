#include <jni.h>
#include <android/log.h>
#include <libusb.h>

#include <cerrno>
#include <cstdint>
#include <cstring>
#include <sstream>
#include <string>
#include <unistd.h>

#define LOG_TAG "UsbLibusbProbe"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

const char* error_name(int rc) {
    switch (rc) {
        case LIBUSB_SUCCESS: return "SUCCESS";
        case LIBUSB_ERROR_IO: return "IO";
        case LIBUSB_ERROR_INVALID_PARAM: return "INVALID_PARAM";
        case LIBUSB_ERROR_ACCESS: return "ACCESS";
        case LIBUSB_ERROR_NO_DEVICE: return "NO_DEVICE";
        case LIBUSB_ERROR_NOT_FOUND: return "NOT_FOUND";
        case LIBUSB_ERROR_BUSY: return "BUSY";
        case LIBUSB_ERROR_TIMEOUT: return "TIMEOUT";
        case LIBUSB_ERROR_OVERFLOW: return "OVERFLOW";
        case LIBUSB_ERROR_PIPE: return "PIPE";
        case LIBUSB_ERROR_INTERRUPTED: return "INTERRUPTED";
        case LIBUSB_ERROR_NO_MEM: return "NO_MEM";
        case LIBUSB_ERROR_NOT_SUPPORTED: return "NOT_SUPPORTED";
        default: return "OTHER";
    }
}

void append_rc(std::ostringstream& out, const char* label, int rc) {
    out << label << "=" << rc << " (" << error_name(rc);
    if (rc < 0) out << ": " << libusb_error_name(rc);
    out << ")\n";
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_usb_LibUsbNative_probe(JNIEnv* env, jobject /* thiz */,
                                        jint android_fd, jboolean try_detach_and_claim) {
    std::ostringstream out;
    out << "libusb Android probe v1\n";
    out << "input_fd=" << android_fd << "\n";
    out << "mode=" << (try_detach_and_claim ? "WRAP+DETACH+CLAIM+RESTORE" : "WRAP_ONLY") << "\n";

    if (android_fd < 0) {
        out << "FATAL: Android UsbDeviceConnection returned invalid fd\n";
        return env->NewStringUTF(out.str().c_str());
    }

    // libusb owns the descriptor passed to wrap_sys_device on successful wrapping.
    // Duplicate Android's FD so libusb_close() cannot close the framework-owned FD.
    const int owned_fd = dup(android_fd);
    if (owned_fd < 0) {
        out << "dup_failed errno=" << errno << " (" << std::strerror(errno) << ")\n";
        return env->NewStringUTF(out.str().c_str());
    }
    out << "dup_fd=" << owned_fd << "\n";

    // Android's USB Host API already performed device discovery and permission checks.
    // Disable libusb's own discovery and wrap the descriptor supplied by UsbManager.
    const int option_rc = libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);
    append_rc(out, "set_no_device_discovery", option_rc);

    libusb_context* ctx = nullptr;
    const int init_rc = libusb_init(&ctx);
    append_rc(out, "libusb_init", init_rc);
    if (init_rc != LIBUSB_SUCCESS) {
        close(owned_fd);
        return env->NewStringUTF(out.str().c_str());
    }

    libusb_device_handle* handle = nullptr;
    const int wrap_rc = libusb_wrap_sys_device(ctx, static_cast<intptr_t>(owned_fd), &handle);
    append_rc(out, "libusb_wrap_sys_device", wrap_rc);
    if (wrap_rc != LIBUSB_SUCCESS || handle == nullptr) {
        // On failed wrap libusb has not taken ownership of owned_fd.
        close(owned_fd);
        libusb_exit(ctx);
        return env->NewStringUTF(out.str().c_str());
    }

    libusb_device* device = libusb_get_device(handle);
    libusb_device_descriptor desc{};
    const int desc_rc = libusb_get_device_descriptor(device, &desc);
    append_rc(out, "get_device_descriptor", desc_rc);
    if (desc_rc == LIBUSB_SUCCESS) {
        out << "descriptor_vid=0x" << std::hex << desc.idVendor
            << " pid=0x" << desc.idProduct << std::dec
            << " bcdUSB=0x" << std::hex << desc.bcdUSB << std::dec
            << " configurations=" << static_cast<int>(desc.bNumConfigurations) << "\n";
    }

    libusb_config_descriptor* config = nullptr;
    const int config_rc = libusb_get_active_config_descriptor(device, &config);
    append_rc(out, "get_active_config_descriptor", config_rc);
    if (config_rc == LIBUSB_SUCCESS && config != nullptr) {
        out << "active_config_value=" << static_cast<int>(config->bConfigurationValue)
            << " interfaces=" << static_cast<int>(config->bNumInterfaces) << "\n";
        for (uint8_t i = 0; i < config->bNumInterfaces; ++i) {
            const libusb_interface& iface = config->interface[i];
            for (int a = 0; a < iface.num_altsetting; ++a) {
                const libusb_interface_descriptor& alt = iface.altsetting[a];
                out << "iface_number=" << static_cast<int>(alt.bInterfaceNumber)
                    << " alt=" << static_cast<int>(alt.bAlternateSetting)
                    << " class=0x" << std::hex << static_cast<int>(alt.bInterfaceClass)
                    << " subclass=0x" << static_cast<int>(alt.bInterfaceSubClass)
                    << " protocol=0x" << static_cast<int>(alt.bInterfaceProtocol)
                    << std::dec << " endpoints=" << static_cast<int>(alt.bNumEndpoints) << "\n";
                for (uint8_t e = 0; e < alt.bNumEndpoints; ++e) {
                    const libusb_endpoint_descriptor& ep = alt.endpoint[e];
                    out << "  endpoint=0x" << std::hex << static_cast<int>(ep.bEndpointAddress)
                        << std::dec << " type=" << static_cast<int>(ep.bmAttributes & LIBUSB_TRANSFER_TYPE_MASK)
                        << " max_packet=" << ep.wMaxPacketSize << "\n";
                }
            }
        }
        libusb_free_config_descriptor(config);
    }

    if (try_detach_and_claim == JNI_TRUE && desc_rc == LIBUSB_SUCCESS) {
        // Explicitly diagnostic: CDC-ECM control interface 0 is often where cdc_ether
        // is attached; data interface 1 is tested separately. Restore any driver we detach.
        for (int iface = 0; iface < 2; ++iface) {
            out << "---- interface " << iface << " ----\n";
            const int active_rc = libusb_kernel_driver_active(handle, iface);
            append_rc(out, "kernel_driver_active", active_rc);

            bool detached = false;
            if (active_rc == 1) {
                const int detach_rc = libusb_detach_kernel_driver(handle, iface);
                append_rc(out, "detach_kernel_driver", detach_rc);
                detached = (detach_rc == LIBUSB_SUCCESS);
            } else if (active_rc == 0) {
                out << "detach_skipped=no_kernel_driver_reported\n";
            } else {
                out << "detach_skipped=active_query_unsupported_or_failed\n";
            }

            const int claim_rc = libusb_claim_interface(handle, iface);
            append_rc(out, "claim_interface", claim_rc);
            if (claim_rc == LIBUSB_SUCCESS) {
                const int release_rc = libusb_release_interface(handle, iface);
                append_rc(out, "release_interface", release_rc);
            }

            if (detached) {
                const int attach_rc = libusb_attach_kernel_driver(handle, iface);
                append_rc(out, "reattach_kernel_driver", attach_rc);
            }
        }
    } else if (try_detach_and_claim == JNI_TRUE) {
        out << "detach_claim_skipped=no_valid_device_descriptor\n";
    }

    libusb_close(handle);
    libusb_exit(ctx);
    out << "probe_finished=true\n";
    const std::string result = out.str();
    ALOGI("%s", result.c_str());
    return env->NewStringUTF(result.c_str());
}
