#include <jni.h>
#include <android/log.h>
#include <libusb.h>
#include <array>
#include <cerrno>
#include <cstdint>
#include <cstring>
#include <sstream>
#include <string>
#include <unistd.h>

#define LOG_TAG "UsbLibusbProbe"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {
const char* en(int r) {
    switch (r) {
        case 0: return "SUCCESS";
        case LIBUSB_ERROR_IO: return "IO";
        case LIBUSB_ERROR_INVALID_PARAM: return "INVALID_PARAM";
        case LIBUSB_ERROR_ACCESS: return "ACCESS";
        case LIBUSB_ERROR_NO_DEVICE: return "NO_DEVICE";
        case LIBUSB_ERROR_NOT_FOUND: return "NOT_FOUND";
        case LIBUSB_ERROR_BUSY: return "BUSY";
        case LIBUSB_ERROR_TIMEOUT: return "TIMEOUT";
        case LIBUSB_ERROR_PIPE: return "PIPE";
        case LIBUSB_ERROR_INTERRUPTED: return "INTERRUPTED";
        case LIBUSB_ERROR_NO_MEM: return "NO_MEM";
        case LIBUSB_ERROR_NOT_SUPPORTED: return "NOT_SUPPORTED";
        default: return "OTHER";
    }
}

void rc(std::ostringstream& o, const char* n, int r) {
    o << n << "=" << r << " (" << en(r);
    if (r < 0) o << ": " << libusb_error_name(r);
    o << ")\n";
}

const char* sn(int s) {
    switch (s) {
        case 0: return "S0_READ_ONLY";
        case 1: return "S1_CLAIM_NO_DETACH";
        case 2: return "S2_DETACH_CONTROL_CLAIM_RESTORE";
        case 3: return "S3_ALT1_BULK_IN";
        case 4: return "S4_BULK_OUT_TEST_FRAME";
        default: return "INVALID";
    }
}

struct DataEndpoints {
    int in = -1;
    int out = -1;
    int alt = -1;
    int interface_number = 1;
    int config_value = -1;
    bool found = false;
};

// Resolve endpoints from the active configuration instead of assuming that
// every USB Ethernet adapter uses the same endpoint addresses.
DataEndpoints find_data_endpoints(std::ostringstream& o, libusb_device* d) {
    DataEndpoints result{};
    libusb_config_descriptor* c = nullptr;
    const int r = libusb_get_active_config_descriptor(d, &c);
    rc(o, "endpoint_scan_get_active_config", r);
    if (r < 0 || c == nullptr) return result;

    result.config_value = c->bConfigurationValue;
    o << "endpoint_scan_config_value=" << result.config_value
      << " interfaces=" << static_cast<int>(c->bNumInterfaces) << "\n";

    for (uint8_t i = 0; i < c->bNumInterfaces; ++i) {
        const auto& iface = c->interface[i];
        for (int a = 0; a < iface.num_altsetting; ++a) {
            const auto& alt = iface.altsetting[a];
            // CDC data interface, non-default alternate setting, with bulk endpoints.
            if (alt.bInterfaceClass != LIBUSB_CLASS_DATA ||
                alt.bAlternateSetting == 0) continue;

            int bulk_in = -1;
            int bulk_out = -1;
            for (uint8_t ep = 0; ep < alt.bNumEndpoints; ++ep) {
                const auto& endpoint = alt.endpoint[ep];
                if ((endpoint.bmAttributes & LIBUSB_TRANSFER_TYPE_MASK) != LIBUSB_TRANSFER_TYPE_BULK) continue;
                if ((endpoint.bEndpointAddress & LIBUSB_ENDPOINT_DIR_MASK) == LIBUSB_ENDPOINT_IN)
                    bulk_in = endpoint.bEndpointAddress;
                else
                    bulk_out = endpoint.bEndpointAddress;
            }

            o << "endpoint_candidate_iface=" << static_cast<int>(alt.bInterfaceNumber)
              << " alt=" << static_cast<int>(alt.bAlternateSetting)
              << " class=0x" << std::hex << static_cast<int>(alt.bInterfaceClass) << std::dec
              << " bulk_in=";
            if (bulk_in < 0) o << "none"; else o << "0x" << std::hex << bulk_in << std::dec;
            o << " bulk_out=";
            if (bulk_out < 0) o << "none"; else o << "0x" << std::hex << bulk_out << std::dec;
            o << "\n";

            if (bulk_in >= 0 && bulk_out >= 0 && !result.found) {
                result.in = bulk_in;
                result.out = bulk_out;
                result.alt = alt.bAlternateSetting;
                result.interface_number = alt.bInterfaceNumber;
                result.found = true;
            }
        }
    }
    libusb_free_config_descriptor(c);

    if (result.found) {
        o << "selected_data_interface=" << result.interface_number
          << " alt=" << result.alt
          << " bulk_in=0x" << std::hex << result.in
          << " bulk_out=0x" << result.out << std::dec << "\n";
    } else {
        o << "selected_data_endpoints=NOT_FOUND\n";
    }
    return result;
}

void descriptors(std::ostringstream& o, libusb_device* d) {
    libusb_device_descriptor dd{};
    int r = libusb_get_device_descriptor(d, &dd);
    rc(o, "get_device_descriptor", r);
    if (r < 0) return;

    o << "VID=0x" << std::hex << dd.idVendor << " PID=0x" << dd.idProduct
      << std::dec << " configurations=" << static_cast<int>(dd.bNumConfigurations) << "\n";

    // The active configuration descriptor is the authoritative source for the
    // interface/endpoint scan; do not confuse config index with bConfigurationValue.
    libusb_config_descriptor* c = nullptr;
    r = libusb_get_active_config_descriptor(d, &c);
    rc(o, "get_active_config_descriptor", r);
    if (r < 0 || c == nullptr) return;

    o << "active_config_value=" << static_cast<int>(c->bConfigurationValue)
      << " interfaces=" << static_cast<int>(c->bNumInterfaces) << "\n";
    for (uint8_t i = 0; i < c->bNumInterfaces; ++i) {
        for (int a = 0; a < c->interface[i].num_altsetting; ++a) {
            const auto& x = c->interface[i].altsetting[a];
            o << "iface=" << static_cast<int>(x.bInterfaceNumber)
              << " alt=" << static_cast<int>(x.bAlternateSetting)
              << " class=0x" << std::hex << static_cast<int>(x.bInterfaceClass)
              << std::dec << " endpoints=" << static_cast<int>(x.bNumEndpoints) << "\n";
            for (uint8_t e = 0; e < x.bNumEndpoints; ++e) {
                const auto& p = x.endpoint[e];
                o << " endpoint=0x" << std::hex << static_cast<int>(p.bEndpointAddress)
                  << std::dec << " type=" << static_cast<int>(p.bmAttributes & LIBUSB_TRANSFER_TYPE_MASK)
                  << " max_packet=" << p.wMaxPacketSize << "\n";
            }
        }
    }
    libusb_free_config_descriptor(c);
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_usb_LibUsbNative_runStage(JNIEnv* e, jobject, jint fd, jint stage) {
    std::ostringstream o;
    o << "libusb controlled CDC data probe v3 (descriptor-driven endpoints)\n"
      << "stage=" << stage << " name=" << sn(stage) << "\ninput_fd=" << fd << "\n";
    if (fd < 0 || stage < 0 || stage > 4) {
        o << "FATAL invalid FD/stage\n";
        return e->NewStringUTF(o.str().c_str());
    }

    const int own = dup(fd);
    if (own < 0) {
        o << "dup_failed errno=" << errno << " " << strerror(errno) << "\n";
        return e->NewStringUTF(o.str().c_str());
    }

    int z = libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);
    rc(o, "no_device_discovery", z);
    libusb_context* ctx = nullptr;
    z = libusb_init(&ctx);
    rc(o, "libusb_init", z);
    if (z < 0) {
        close(own);
        return e->NewStringUTF(o.str().c_str());
    }

    libusb_device_handle* h = nullptr;
    z = libusb_wrap_sys_device(ctx, static_cast<intptr_t>(own), &h);
    rc(o, "wrap_sys_device", z);
    if (z < 0 || h == nullptr) {
        close(own);
        libusb_exit(ctx);
        return e->NewStringUTF(o.str().c_str());
    }

    libusb_device* d = libusb_get_device(h);
    descriptors(o, d);
    const DataEndpoints endpoints = find_data_endpoints(o, d);

    if (stage == 0) {
        o << "S0 complete: read-only; no claim, detach or transfer\n";
    } else {
        for (int i = 0; i < 2; ++i) {
            o << "observe_interface=" << i << "\n";
            rc(o, "kernel_driver_active", libusb_kernel_driver_active(h, i));
        }

        bool detached = false;
        if (stage >= 2) {
            const int active = libusb_kernel_driver_active(h, 0);
            rc(o, "control_driver_active", active);
            if (active == 1) {
                const int detach = libusb_detach_kernel_driver(h, 0);
                rc(o, "detach_control_0", detach);
                detached = (detach == 0);
            } else {
                o << "detach_control_skipped=driver_not_confirmed_active\n";
            }
        }

        const int iface = endpoints.found ? endpoints.interface_number : 1;
        const int claim = libusb_claim_interface(h, iface);
        rc(o, "claim_data_interface", claim);
        const bool claimed = claim == 0;

        if (claimed && stage >= 3) {
            if (!endpoints.found) {
                o << "transfer_skipped=no_bulk_in_out_endpoints_found\n";
            } else {
                const int set_alt = libusb_set_interface_alt_setting(h, iface, endpoints.alt);
                rc(o, "set_data_alt_setting", set_alt);

                if (set_alt == 0 && stage == 3) {
                    std::array<unsigned char, 2048> b{};
                    int actual = 0;
                    const int transfer = libusb_bulk_transfer(
                        h, static_cast<unsigned char>(endpoints.in), b.data(),
                        static_cast<int>(b.size()), &actual, 250);
                    rc(o, "bulk_in", transfer);
                    o << "bulk_in_endpoint=0x" << std::hex << endpoints.in << std::dec
                      << " requested=" << b.size() << " actual=" << actual << "\n";
                    if (transfer == LIBUSB_ERROR_TIMEOUT)
                        o << "bulk_in_timeout_is_valid_without_incoming_frame=true\n";
                }

                if (set_alt == 0 && stage == 4) {
                    std::array<unsigned char, 60> b{};
                    for (int i = 0; i < 6; ++i) b[i] = 0xff;
                    const unsigned char src[6] = {2, 0, 0, 0, 0, 1};
                    memcpy(b.data() + 6, src, 6);
                    b[12] = 0x88;
                    b[13] = 0xb5;
                    memcpy(b.data() + 14, "USBPROBE", 8);
                    int actual = 0;
                    const int transfer = libusb_bulk_transfer(
                        h, static_cast<unsigned char>(endpoints.out), b.data(),
                        static_cast<int>(b.size()), &actual, 500);
                    rc(o, "bulk_out", transfer);
                    o << "bulk_out_endpoint=0x" << std::hex << endpoints.out << std::dec
                      << " requested=60 actual=" << actual << "\n"
                      << "WARNING: one experimental EtherType 0x88B5 broadcast frame was attempted\n";
                }
            }
        }

        if (claimed) rc(o, "release_data_interface", libusb_release_interface(h, iface));
        if (detached) rc(o, "reattach_control_0", libusb_attach_kernel_driver(h, 0));
        else if (stage >= 2) o << "reattach_skipped=interface_0_not_detached_by_this_run\n";
    }

    libusb_close(h);
    libusb_exit(ctx);
    o << "probe_finished=true\n";
    const auto s = o.str();
    ALOGI("%s", s.c_str());
    return e->NewStringUTF(s.c_str());
}
