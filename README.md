# Hubitat Elevation Teslamate Link

***System to monitor Tesla vehicle status in Hubitat.***

Each of the prior MQTT Bridge releases set out to fill a gap in SmartThings and
Hubitat as each platform lacked a native MQTT client for which to interface with
an MQTT broker. Both releases relied upon a separate, self-hosted nodejs
_bridge_ app that ran outside of the platform and provided both a client to
receive MQTT messages and a client to translate those MQTT messages to REST
calls which were both platforms offered as integration points.

Since that time the Hubitat platform has introduced an MQTT client capable of interfacing with an MQTT broker without the need for external bridges.

The MQTT Link project builds upon the methods established in the prior works by refactoring the Driver code to utilize the built-in Hubitat MQTT client and to make improvements to the App code.

A big thanks to stjohnjohnson, jeubanks and those to blazed the trails to make this project possible.

#### Device Capabilities

Each of the devices chosen on the prior page are listed on this page and include a dropdown containing the capabilities associated with that device. This page also lists the normalized topic for the device.

* Click to set - Expand and select the associated device capabilities that the app should monitor for.

# Release Notes

### Release 0.1.0
* Initial release

