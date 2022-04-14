# Hubitat Elevation MQTT Link

***System to share and control Hubitat Elevation device states in MQTT.***

MQTT Link is a derivative of  [MQTT Bridge](https://github.com/jeubanks/hubitat-mqtt-bridge) for Hubitat released by jeubanks who derived it from [MQTT Bridge](https://github.com/stjohnjohnson/smartthings-mqtt-bridge) for SmartThings by stjohnjohnson.

Each of the prior MQTT Bridge releases set out to fill a gap in SmartThings and Hubitat as each platform lacked a native MQTT client for which to interface with an MQTT broker. Both releases relied upon a separate, self-hosted nodejs _bridge_ app that ran outside of

the platform and provided both a client to receive MQTT messages and a client to translate those MQTT messages to REST calls which were both platforms offered as integration points.

Since that time the Hubitat platform has introduced an MQTT client capable of interfacing with an MQTT broker without the need for external bridges.

The MQTT Link project builds upon the methods established in the prior works by refactoring the Driver code to utilize the built-in Hubitat MQTT client and to make improvements to the App code.

A big thanks to stjohnjohnson, jeubanks and those to blazed the trails to make this project possible.

## MQTT

The MQTT Link apps provide for transit of Hubitat device-specific messages to and from the configured MQTT broker. To remain versatile and lean, no assumptions or impositions were made about the consumers of the published events however, contracts were needed to ensure proper integration with those consumers.

Following are details about the topic format and messages used to communicate to and from the hub devices.

### Topics

The MQTT topics apply the following pattern.
* prefix - Hardcoded to `hubitat`
* hub name & id - Combines the hub location name with the hub id
* normalized device id - Combines the device name and id
* normalized capability - Provides

Example: `hubitat/home-000d/hue-color-lamp-1-738/switch`

### Messages

Each device has a set of capabilities, attributes and commands that it supports but not every device has support for all three areas. Triggered hub events are converted to a standardized message matching the event that occurred. For example, when light is turned on or off or a door is opened or closed, an MQTT message will be sent to the broker with details about the device and event so that consumers can take the appropriate action.

See the Supported Capabilities section for details on message details for each capability.


#### Outbound

Messages resulting from hub events mostly report state changes in order to inform subscribers to those events.

* Light was turned on

Topic: `hubitat/home-000d/hue-color-lamp-1-738/switch`
Message: `on`

* Light was turned off

Topic: `hubitat/home-000d/hue-color-lamp-1-738/switch`
Message: `off`

#### Inbound 

For those devices that support commands, MQTT messages can be authored be downstream consumers so that  those commands are executed on the target device.

* Message to lock the front door

Topic: `hubitat/home-000d/august-pro-z-wave-lock-324/lock`
Message: `lock`

* Message to unlock the front door

Topic: `hubitat/home-000d/august-pro-z-wave-lock-324/lock`
Message: `unlock`
  

### Last Will

When the client establishes a connection to the broker it sets an default `LWT` topic to `offline` and then pushes `online` shortly thereafter.

In addition to `LWT`, the client also sends `UPTIME`, `FW` and `IP` containing uptime, current firmware version and IP address of the hub.

```
hubitat:
    home-000d:
        LWT: online
        FW: 2.2.0.126
        IP: 192.168.1.100
        UPTIME: 82815
```
## Installation & Configuration

MQTT Link consists of both a driver and app. Both must be installed and configured prior to their use.

### Driver

The driver app must be installed first because the App depends upon it. The driver connects to the configured MQTT broker and sends out messages when new hub events occur and receives messages from external client events such has those from Home Assistant.

The driver provides a number of commands that are useful for troubleshooting but they are not needed for normal operation of the driver code.

* Connect - Connects to the configured broker
* Disconnect - Disconnects from the configured broker
* Device Notification - For internal use by the app

The following commands allow for subscribing and publishing to MQTT topics. The driver automatically prefixes all topics with the following prefix within the code to ensure unique topics for each hub.

`/hubitat/{hub-name}-{hub-id}/` 
e.g. 
`/hubitat/home-893/`

* Subscribe - Subscribes to the provided topic. 
	* e.g. `device` becomes `/hubitat/home-893/device`
* Unsubscribe - Unsubscribe from the provided topic. 
	* e.g `#` becomes `/hubitat/home-893/#`
* Publish - Publish message to provided topic.
	*  e.g. `switch` msg: `on` becomes `/hubitat/home-893/switch` msg: `on`

Follow the procedure for installing user driver code on Hubitat and enter the following details.

* MQTT Broker IP Address - Provide the IP address of the target MQTT broker
* MQTT Broker Port - Provide the port for the broker. This is typically 1883
* MQTT Broker Username - Provide username
* MQTT Broker Password - Provide password
* Type - MQTT Link Driver

_optional_

* Send full payload messages on device events - When ON the driver will send a detailed payload of the fired event
* Enable debug logging - When ON the driver will log debug statements for troubleshooting

### App 

The app is responsible for listening to subscribed hub events that it relays to the driver to publish to the MQTT broker. It also listens for inbound messages from the driver that it then translates to a hub event.

Follow the procedure for installing apps code on Hubitat and specify the following details.

#### Select Devices and Driver

* Select devices - Expand and select the devices that the app should monitor for. Note that the capabilities for each of the selected devices are selected on the next page.
* Notify this driver - Example and select the MQTT Link Driver device that was installed previously.

_optional_

* Enable debug logging - When ON the driver will log debug statements for troubleshooting

#### Device Capabilities

Each of the devices chosen on the prior page are listed on this page and include a dropdown containing the capabilities associated with that device. This page also lists the normalized topic for the device.

* Click to set - Expand and select the associated device capabilities that the app should monitor for.

## Supported Capabilities
Following is an inclusive list of device capabilities, attributes and commands recognized by MQTT Link. 

Limited access to devices within each of these categories made it impossible to test each combination list. Please report any missing or erroneous details so that they can be corrected within the code. 

[Hubitat Capabilities List](https://docs.hubitat.com/index.php?title=Driver_Capability_List) | [SmartThings Capabilities List](https://docs.smartthings.com/en/latest/capabilities-reference.html)
* Acceleration Sensor - accelerationSensor
	* acceleration
* Alarm - alarm
	* alarm
		* siren
		* strobe
		* both
		* off
* Audio Notification - audioNotification
	* -
		* playText
		* playTextAndRestore
		* playTextAndResume
		* playTrack
		* playTrackAndResume
		* playTrackAndRestore
* Audio Volume - audioVolume
	* mute
		* mute
		* unmute
	* volume
		* setVolume
		* volumeUp
		* volumeDown
* Battery - battery
	* battery
* Carbon Dioxide Measurement - carbonDioxideMeasurement
	* carbonDioxide
* Carbon Monoxide Detector - carbonMonoxideDetector
	* carbonMonoxide
* Change Level - changeLevel
	* -
		* startLevelChange
		* stopLevelChange
* Chime - chime
	* soundEffects
		* playSound
		* stop
	* soundName
	* status
* Color Control - colorControl
	* color
		* setColor
	* hue
		* setHue
	* saturation
		* setSaturation
* Color Mode - colorMode
  * colorMode
* Color Temperature - colorTemperature
  * colorTemperature
* Configuration - configuration
  * -
* Consumable - consumable
  * consumableStatus
* Contact Sensor - contactSensor
  * contact
* Door Control - doorControl
  * door
	  * open
	  * close
* DoubleTapable Button - doubleTapableButton
  * doubleTapped
* Energy Meter - energyMeter
  * energy
* Estimated Time Of Arrival - estimatedTimeOfArrival
  * eta
* Fan Control - fanControl
  * speed
* Filter Status - filterStatus
  * filterStatus
* Health Check - healthCheck
  * checkInterval
* Illuminance Measurement - illuminanceMeasurement
  * illuminance
* Image Capture - imageCapture
  * image
* Light Effects - lightEffects
  * effectName
  * lightEffects
	  * setEffect
	  * setNextEffect
	  * setPreviousEffect
* Location Mode - locationMode
  * mode
* Lock Codes - lock
  * lock
    * lock
    * unlock
* Lock Codes - lockCode
  * codeChanged
  * codeLength
  * lockCodes
    * deleteCode
    * getCodes
    * setCode
    * setCodeLength
  * maxCodes
* Media Controller - mediaController
  * activities
  * currentActivity
* Momentary - momentary
  * - 
* Motion Sensor - motionSensor
  * motion
    * active
    * inactive
* Notification - notification
  * - 
* pH Measurement - pHMeasurement
  * pH
* Power Meter - powerMeter
  * power
* Power Source - powerSource
  * powerSource
* Presence Sensor - presenceSensor
  * presence
    * present
    * not present
* PressureMeasurement - pressureMeasurement
  * pressure
* Refresh - refresh
  * -
* Pushable Button - pushableButton
  * numberOfButtons
  * pushed
* Relative Humidity Measurement - relativeHumidityMeasurement
  * humidity
* ReleasableButton - releasableButton
  * released
* Samsung TV - samsungTV
  * messageButton
  * mute
  * pictureMode
  * soundMode
  * switch
  * volume
    * mute
    * off
    * on
    * setPictureMode
    * setSoundMode
    * setVolume
    * showMessage
    * unmute
    * volumeDown
    * volumeUp
* Security Keypad - securityKeypad
  * codeChanged
  * codeLength
  * lockCodes
  * maxCodes
  * securityKeypad
    * armAway
    * armHome
    * deleteCode
    * disarm
    * getCodes
    * setCode
    * setCodeLength
    * setEntryDelay
    * setExitDelay
* Signal Strength - signalStrength
  * lqi
  * rssi
* Sleep Sensor - sleepSensor
  * sleeping
* Smoke Detector - smokeDetector
  * smoke
* Sound Pressure Level - soundPressureLevel
  * soundPressureLevel
* Sound Sensor - soundSensor
  * sound
* Speech Recognition - speechRecognition
  * phraseSpoken
* Speech Synthesis - speechSynthesis
  * - 
* Step Sensor - stepSensor
  * goal
  * steps
* Switch Level - switchLevel
  * level
* Switch - switch
  * switch
    * on
    * off
* Tamper Alert - tamperAlert
  * tamper
* Temperature Measurement - temperatureSensor
  * temperature
* Thermostat Cooling Setpoint - thermostatCoolingSetpoint
  * coolingSetpoint
* Thermostat Fan Mode - thermostatFanMode
  * thermostatFanMode
    * fanAuto
    * fanCirculate
    * fanOn
    * setThermostatFanMode
* Thermostat Heating Setpoint - thermostatHeatingSetpoint
  * heatingSetpoint
* Thermostat Mode - thermostatMode
  * thermostatMode
    * auto
    * cool
    * emergencyHeat
    * heat
    * off
    * setThermostatMode
* Thermostat Operating State - thermostatOperatingState
  * thermostatOperatingState
* Thermostat Schedule - thermostatSchedule
  * schedule
* Three Axis - threeAxis
  * threeAxis
* Timed Session - timedSession
  * sessionStatus
  * timeRemaining
    * setTimeRemaining
    * start
    * stop
    * pause
    * cancel
* Tone - tone
  * -
* TV - tv
  * channel
    * channelUp
    * channelDown
  * movieMode
  * picture
  * power
  * sound
  * volume
	  * volumeUp
	  * volumeDown
* Temperature Measurement - temperatureMeasurement
  * temperature
* Ultraviolet Index - ultravioletIndex
  * ultravioletIndex
* Valve - valve
  * contact
    * open
    * closed
  * valve
    * open
    * closed
* Video Camera - videoCamera
  * camera
    * flip
  * mute
    * mute
    * unmute
  * settings
  * statusMessage
    * on
    * off
* Video Capture - videoCapture
  * clip
* Window Shade - windowShades
  * windowShade
* Water Sensor - waterSensor
  * water
* Window Shade - windowShade
  * windowShade
    * close
    * open
    * presetPosition
* ZW Multichannel - zwMultichannel
  * epEvent
  * epInfo

### Release Notes

# Update in Release 1.0.0
* BREAKING CHANGES
* Replace spaces in hub name with dashes to prevent MQTT topic with spaces in the name. `hub name` becomes `hub-name`
# Update in Release 0.3.0
* Added support for all Hubitat Virtual Devices
# Update in Release 0.2.1
* Minor fix that added device attibute name to notification raised from the app to the driver
# Update in Release 0.2.0
* Added scheduled job that runs every minute that reads and publishes device state messages to MQTT 
# Update in Releadse 0.1.0
* Initial release with Hubitat Package Manager support

