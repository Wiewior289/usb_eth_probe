# USB descriptor-first diagnostics

## Purpose

This build is an evidence-gathering probe, not an Ethernet driver. It records what Android exposes before attempting an interface claim.

## What changed

- The scan logs every configuration returned by `UsbDevice`, including configuration value, interface number, alternate setting, class/subclass/protocol, and endpoint direction/type/address/max packet size.
- The scan separately logs interfaces exposed through `UsbDevice.getInterface()`. Do not assume every descriptor in every configuration is simultaneously active.
- The automated claim path only chooses a Bulk IN/OUT pair on a CDC Data interface (class `0x0A`). Vendor-specific interfaces (class `0xFF`) are logged as candidates but are not auto-claimed.
- A `claimInterface()` result of `false` is reported as a boolean failure. Android's public API does not return the underlying errno here, so the app must not label it `-16 / EBUSY` as a proven fact.
- An FD is the connection handle returned by `UsbDeviceConnection`; it is not an interface number and does not identify the protocol to use.

## Test sequence

1. Connect one adapter at a time, then repeat with the multi-hub topology.
2. Refresh the device list and export logs before running the claim test.
3. Compare `USB-DESC` lines across adapters. Look for multiple configurations, repeated interface IDs under different configurations, alternate settings, and vendor-specific interfaces with Bulk endpoints.
4. Run the automated test. If no CDC Data Bulk pair is exposed to the active Android USB API, the probe should not guess a vendor-specific interface; it will run the control-endpoint descriptor check and stop.
5. Repeat on WCH and Realtek, preserving the complete logs and Android version/build.

## Interpretation limits

- A descriptor showing Bulk IN/OUT proves only that endpoints are described; it does not prove that the selected interface is an Ethernet data plane.
- A failed claim does not by itself prove a kernel driver owns the interface. Keep the observed result separate from hypotheses.
- A successful claim does not prove the correct vendor protocol, framing, initialization sequence, or Ethernet TX/RX behavior.
- This change deliberately does not switch configurations or alternate settings. The first goal is a faithful inventory and a non-random selection policy, not taking ownership of every interface.
