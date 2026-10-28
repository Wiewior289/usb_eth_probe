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
const char* en(int r){switch(r){case 0:return "SUCCESS";case LIBUSB_ERROR_IO:return "IO";case LIBUSB_ERROR_INVALID_PARAM:return "INVALID_PARAM";case LIBUSB_ERROR_ACCESS:return "ACCESS";case LIBUSB_ERROR_NO_DEVICE:return "NO_DEVICE";case LIBUSB_ERROR_NOT_FOUND:return "NOT_FOUND";case LIBUSB_ERROR_BUSY:return "BUSY";case LIBUSB_ERROR_TIMEOUT:return "TIMEOUT";case LIBUSB_ERROR_PIPE:return "PIPE";case LIBUSB_ERROR_INTERRUPTED:return "INTERRUPTED";case LIBUSB_ERROR_NO_MEM:return "NO_MEM";case LIBUSB_ERROR_NOT_SUPPORTED:return "NOT_SUPPORTED";default:return "OTHER";}}
void rc(std::ostringstream&o,const char*n,int r){o<<n<<"="<<r<<" ("<<en(r);if(r<0)o<<": "<<libusb_error_name(r);o<<")\n";}
const char* sn(int s){switch(s){case 0:return "S0_READ_ONLY";case 1:return "S1_CLAIM_NO_DETACH";case 2:return "S2_DETACH_CONTROL_CLAIM_RESTORE";case 3:return "S3_ALT1_BULK_IN";case 4:return "S4_BULK_OUT_TEST_FRAME";default:return "INVALID";}}
void descriptors(std::ostringstream&o,libusb_device*d){
 libusb_device_descriptor dd{};int r=libusb_get_device_descriptor(d,&dd);rc(o,"get_device_descriptor",r);if(r)return;
 o<<"VID=0x"<<std::hex<<dd.idVendor<<" PID=0x"<<dd.idProduct<<std::dec<<" configurations="<<(int)dd.bNumConfigurations<<"\n";
 libusb_config_descriptor*c=nullptr;r=libusb_get_active_config_descriptor(d,&c);rc(o,"get_active_config_descriptor",r);if(r||!c)return;
 o<<"active_config="<<(int)c->bConfigurationValue<<" interfaces="<<(int)c->bNumInterfaces<<"\n";
 for(uint8_t i=0;i<c->bNumInterfaces;i++)for(int a=0;a<c->interface[i].num_altsetting;a++){auto&x=c->interface[i].altsetting[a];o<<"iface="<<(int)x.bInterfaceNumber<<" alt="<<(int)x.bAlternateSetting<<" class=0x"<<std::hex<<(int)x.bInterfaceClass<<std::dec<<" endpoints="<<(int)x.bNumEndpoints<<"\n";for(uint8_t e=0;e<x.bNumEndpoints;e++){auto&p=x.endpoint[e];o<<" endpoint=0x"<<std::hex<<(int)p.bEndpointAddress<<std::dec<<" type="<<(int)(p.bmAttributes&LIBUSB_TRANSFER_TYPE_MASK)<<" max_packet="<<p.wMaxPacketSize<<"\n";}}
 libusb_free_config_descriptor(c);
}
}
extern "C" JNIEXPORT jstring JNICALL Java_com_example_usb_LibUsbNative_runStage(JNIEnv*e,jobject,jint fd,jint stage){
 std::ostringstream o;o<<"libusb controlled CDC-ECM v2\nstage="<<stage<<" name="<<sn(stage)<<"\ninput_fd="<<fd<<"\n";
 if(fd<0||stage<0||stage>4){o<<"FATAL invalid FD/stage\n";return e->NewStringUTF(o.str().c_str());}
 int own=dup(fd);if(own<0){o<<"dup_failed errno="<<errno<<" "<<strerror(errno)<<"\n";return e->NewStringUTF(o.str().c_str());}
 int z=libusb_set_option(nullptr,LIBUSB_OPTION_NO_DEVICE_DISCOVERY);rc(o,"no_device_discovery",z);
 libusb_context*ctx=nullptr;z=libusb_init(&ctx);rc(o,"libusb_init",z);if(z){close(own);return e->NewStringUTF(o.str().c_str());}
 libusb_device_handle*h=nullptr;z=libusb_wrap_sys_device(ctx,(intptr_t)own,&h);rc(o,"wrap_sys_device",z);if(z||!h){close(own);libusb_exit(ctx);return e->NewStringUTF(o.str().c_str());}
 auto*d=libusb_get_device(h);descriptors(o,d);
 if(stage==0)o<<"S0 complete: read-only; no claim, detach or transfer\n";
 else {
  for(int i=0;i<2;i++){o<<"observe_interface="<<i<<"\n";rc(o,"kernel_driver_active",libusb_kernel_driver_active(h,i));}
  bool detached=false;
  if(stage>=2){int a=libusb_kernel_driver_active(h,0);rc(o,"control_driver_active",a);if(a==1){int q=libusb_detach_kernel_driver(h,0);rc(o,"detach_control_0",q);detached=q==0;}else o<<"detach_control_skipped=driver_not_confirmed_active\n";}
  int q=libusb_claim_interface(h,1);rc(o,"claim_data_1",q);bool claimed=q==0;
  if(claimed&&stage>=3){int a=libusb_set_interface_alt_setting(h,1,1);rc(o,"set_alt_1",a);
   if(a==0&&stage==3){std::array<unsigned char,2048>b{};int n=0;int t=libusb_bulk_transfer(h,0x82,b.data(),(int)b.size(),&n,250);rc(o,"bulk_in_0x82",t);o<<"bulk_in_actual="<<n<<"\n";if(t==LIBUSB_ERROR_TIMEOUT)o<<"bulk_in_timeout_is_valid_without_incoming_frame=true\n";}
   if(a==0&&stage==4){std::array<unsigned char,60>b{};for(int i=0;i<6;i++)b[i]=0xff;const unsigned char src[6]={2,0,0,0,0,1};memcpy(b.data()+6,src,6);b[12]=0x88;b[13]=0xb5;memcpy(b.data()+14,"USBPROBE",8);int n=0;int t=libusb_bulk_transfer(h,0x03,b.data(),(int)b.size(),&n,500);rc(o,"bulk_out_0x03",t);o<<"bulk_out_requested=60 actual="<<n<<"\nWARNING: one experimental EtherType 0x88B5 broadcast frame was attempted\n";}
  }
  if(claimed)rc(o,"release_data_1",libusb_release_interface(h,1));
  if(detached)rc(o,"reattach_control_0",libusb_attach_kernel_driver(h,0));else if(stage>=2)o<<"reattach_skipped=interface_0_not_detached_by_this_run\n";
 }
 libusb_close(h);libusb_exit(ctx);o<<"probe_finished=true\n";auto s=o.str();ALOGI("%s",s.c_str());return e->NewStringUTF(s.c_str());
}