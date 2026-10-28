# Blueprint: controlled libusb CDC-ECM access

## Objective

Continue the USB ETH Probe experiment using libusb only for native USB operations, while Android UsbManager remains the source of device permission and the file descriptor. The test target observed so far is WCH VID:PID 1a86:5394, active configuration value 2, CDC-ECM control interface 0 and data interface 1. Data interface 1 exposes bulk IN 0x82 and bulk OUT 0x03 on alternate setting 1. Re-check descriptors on every device; do not assume these numbers are universal.

## Experimental rules

- One button = one stage and one clearly logged operation sequence.
- Never run Android UsbDeviceConnection.claimInterface() before a libusb stage.
- Stop the app's sniffer and close its previous Android connection before opening a fresh connection for a stage.
- Do not automatically run stages in sequence.
- Record Android version, device model, adapter VID:PID, active config, eth0 state before/after, and complete app + logcat output.
- A stage failure must not silently proceed to the next stage.
- Restoration is best-effort. If reattach fails, stop and physically replug the adapter before further tests.

## Stage sequence

### S0 - READ ONLY
Wrap the Android-owned USB FD (duplicate first), read device and active configuration descriptors, enumerate every interface alternate setting and endpoint. No claim, detach, set-alt or data transfer. Baseline: eth0 should remain unchanged.

### S1 - CLAIM DATA WITHOUT DETACH
Call libusb_claim_interface(handle, 1), then release only if claim succeeded. Do not detach any kernel driver. This isolates claim behavior from detach behavior. Record kernel_driver_active(0) and kernel_driver_active(1) as observations only; a negative result means unsupported/error, not proof that no driver exists.

### S2 - DETACH CONTROL, CLAIM DATA, RESTORE
If and only if kernel_driver_active(0) returns 1, detach interface 0. Attempt claim/release on interface 1. Reattach interface 0 only if this test detached it successfully. Never detach interface 1 automatically. Log every return code independently and inspect eth0 afterwards.

### S3 - SET ALT 1 + BULK IN
Repeat the controlled interface-0 detach / interface-1 claim, explicitly set interface 1 alternate setting 1, then perform a bounded bulk IN read from endpoint 0x82 with a 250 ms timeout. A timeout is a valid observation: an Ethernet NIC may have no incoming frame. Do not interpret timeout as a broken endpoint. Release and restore in all reachable paths.

### S4 - BULK OUT TEST FRAME
Only run after S0-S3 are understood and the test network is isolated. Send one 60-byte Ethernet frame to broadcast destination with locally administered source MAC 02:00:00:00:00:01 and experimental EtherType 0x88B5. This is explicit test traffic, not a production packet. Log requested length, actual length and result. Do not repeat automatically. The switch/network may learn the test source MAC temporarily.

## Interpretation

- S0 fails: FD wrapping, permissions, device state, or backend problem. Do not continue.
- S1 fails while eth0 is active: claim/driver ownership conflict is likely; do not add data transfers.
- S2 differs from S1: detach/reattach behavior is implicated. Preserve the full log and stop if restoration fails.
- S3 claim succeeds but set-alt fails: isolate alternate-setting control before interpreting endpoint behavior.
- S3 bulk IN timeout: not proof of endpoint failure; test with known inbound Ethernet traffic later.
- S4 OUT success means the USB bulk transfer accepted the frame, not that the frame reached the network or was processed by a peer.
- Any unexpected eth0 disappearance, adapter disconnect, or failed reattach: stop testing, capture logs, physically replug before the next stage.

## Required report per run

1. Stage ID and timestamp.
2. Device / Android build / VID:PID.
3. eth0 before and after.
4. Native result for every operation, including negative libusb error names.
5. Kernel log lines around detach, claim, disconnect/register and reattach.
6. Whether the test was repeated after a physical replug.

## Current status

The previous experiment established that libusb initialization, wrapping, descriptor reading and some claim/release calls can succeed. Other logs show active-driver detection, detach success and reattach failure (NOT_FOUND). It has not yet established reliable CDC-ECM data transfer. This blueprint deliberately separates ownership, alternate-setting and transfer tests.
