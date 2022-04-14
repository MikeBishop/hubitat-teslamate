/**
 *  MQTT Link
 *
 * MIT License
 *
 * Copyright (c) 2020 license@mydevbox.com
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

public static String version() { return "v2.0.0" }
public static String rootTopic() { return "hubitat" }

definition(
	name: "MQTT Link",
	namespace: "mydevbox",
	author: "Chris Lawson, et al",
	description: "A link between Hubitat device events and MQTT Link Driver",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections@2x.png",
	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections@3x.png"
)

preferences {
    page(name: "devicePage", nextPage: "capabilitiesPage", uninstall: true) {
        section("Select the hub devices that MQTT Link should monitor and control.", hideable: false) {
            input (
                name: "selectedDevices", 
                type: "capability.*", 
                title: "Select devices", 
                multiple: true,
                required: true,
                submitOnChange: false
            )
        }
        section ("<h3>Specify MQTT Link Driver device</h3>") {
            paragraph "The MQTT Link Driver must be set up prior to the MQTT Link app otherwise the driver will not show up here."
		    input (
                name: "mqttLink", 
                type: "capability.notification", 
                title: "Notify this driver", 
                required: true, 
                multiple: false,
                submitOnChange: false
            )
    	}
        section("Debug Settings") {
            input("debugLogging", "bool", title: "Enable debug logging", required: false, default:false) 
        }
    }
    page(name: "capabilitiesPage", install: true)
}

def capabilitiesPage() {
    def deprecatedCapabilities = ["Actuator","Beacon","Bridge","Bulb","Button","Garage Door Control",
                                  "Indicator","Light","Lock Only","Music Player","Outlet","Polling","Relay Switch",
                                  "Sensor","Shock Sensor","Thermostat Setpoint","Thermostat","Touch Sensor",
                                  "Configuration","Refresh"]
    dynamicPage(name: "capabilitiesPage") {        
        section ("<h2>Specify Exposed Capabilities per Device</h2>") {
            paragraph """<style>.pill {border-radius:4px;background-color:#337ab7;color:#fff;padding:10px 15px;
                font-weight:bold;} .label {font-family: Helvetica,Arial,sans-serif;background-color: #5bc0de;
                display: inline;padding: .2em .6em .3em;font-size: 75%;font-weight: 700;line-height: 1;color: 
                #fff;text-align: center;white-space: nowrap;vertical-align: baseline;
                border-radius: .25em; }</style>""".stripMargin()

            // Build normalized list of selected device names 
            def selectedList = []
            selectedDevices.each {
                device -> selectedList.add(normalizeId(device))
            }
            state.selectedList = selectedList

            // Remove deselected device capabilities
            settings.each { setting ->
                if (setting.value.class == java.util.ArrayList) {
                    if (!state.selectedList.contains(setting.key)) {
                        app.removeSetting(setting.key)
                    }
                }
            }
            
            // Build normalized list of selected device names 
            def selectedLookup = [:]
            selectedDevices.each {
                device -> selectedLookup.put(normalizeId(device), device.getDisplayName())
            }
            state.selectedLookup = selectedLookup
            
            // List selected devices with capabilities chooser
            selectedDevices.sort{x -> x.getDisplayName()}.each { device ->

                def selectedCapabilities = []
                def deviceCapabilities = device.getCapabilities()
    
                deviceCapabilities.each { capability ->
                    if (!deprecatedCapabilities.contains(capability.getName())) {
                        selectedCapabilities.add(capability.getName())
                    }
                }
                
                def normalizeId = normalizeId(device)
                
                paragraph "<div class=\"pill\">${device.getDisplayName()}</div>"

                input (
                    name: normalizeId, 
                    type: "enum",
                    title: "",
                    options: selectedCapabilities,
                    multiple: true,
                    submitOnChange: false
                )
                paragraph "<div><strong style=\"font-size: 85%;\">Topic </strong><div class=\"label\">${getTopicPrefix()}${normalizeId}</div></div><hr />"
            }
        }
    }
}

// Massive lookup tree
@Field CAPABILITY_MAP = [
	"accelerationSensor": [
		name: "Acceleration Sensor",
		capability: "capability.accelerationSensor",
		attributes: [
			"acceleration" // ["inactive", "active"]
		]
	],
	"alarm": [
		name: "Alarm",
		capability: "capability.alarm",
		attributes: [
			"alarm" // ["strobe", "off", "both", "siren"]
		],
		action: "actionAlarm"
	],
	"audioNotification": [
		name: "Audio Notification",
		capability: "capability.audioNotification",
		attributes: [
		],
		action: "actionAudioNotification"
	],
	"audioVolume": [
		name: "Audio Volume",
		capability: "capability.audioVolume",
		attributes: [
            "mute", // ["unmuted", "muted"]
			"volume" // 0 - 100
		],
		action: "actionAudioVolume"
	],
	"battery": [
		name: "Battery",
		capability: "capability.battery",
		attributes: [
			"battery" // 0 - 100
		]
	],
	"carbonDioxideMeasurement": [
		name: "Carbon Dioxide Measurement",
		capability: "capability.carbonDioxideMeasurement",
		attributes: [
			"carbonDioxide"
		]
	],
	"carbonMonoxideDetector": [
		name: "Carbon Monoxide Detector",
		capability: "capability.carbonMonoxideDetector",
		attributes: [
			"carbonMonoxide" // 0 - 100
		]
	],
	"changeLevel": [
		name: "Change Level",
		capability: "capability.changeLevel",
		attributes: [
		],
		action: "actionChangeLevel"
	],
	"chime": [
		name: "Chime",
		capability: "capability.chime",
		attributes: [
			"soundEffects", // JSON_OBJ
			"soundName", // String
			"status" // ["playing", "stopped"]
		],
		action: "actionChime"
	],
	"colorControl": [
		name: "Color Control",
		capability: "capability.colorControl",
		attributes: [
            "RGB", // String
			"color", // String
            "colorName", // String
			"hue", // 0 - 100
			"saturation" // 0 - 100
		],
		action: "actionColorControl"
	],
	"colorMode": [
		name: "Color Mode",
		capability: "capability.colorMode",
		attributes: [
			"colorMode" // ["CT", "RGB"]
		]
	],
	"colorTemperature": [
		name: "Color Temperature",
		capability: "capability.colorTemperature",
		attributes: [
			"colorName", // String
			"colorTemperature" // 0 - 100
		],
		action: "actionColorTemperature"
	],
	"configuration": [
		name: "Configuration",
		capability: "capability.configuration",
		attributes: [
		],
		action: "actionConfiguration"
	],
	"consumable": [
		name: "Consumable",
		capability: "capability.consumable",
		attributes: [
			"consumableStatus" // ["missing", "order", "maintenance_required", "good", "replace"]
		],
		action: "actionConsumable"
	],
	"contactSensor": [
		name: "Contact Sensor",
		capability: "capability.contactSensor",
		attributes: [
			"contact" // ["closed", "open"]
		]
	],
	"doorControl": [
		name: "Door Control",
		capability: "capability.doorControl",
		attributes: [
			"door" // ["unknown", "closed", "open", "closing", "opening"]
		],
		action: "actionOpenClose"
	],
	"doubleTapableButton": [
		name: "DoubleTapable Button",
		capability: "capability.doubleTapableButton",
		attributes: [
			"doubleTapped"
		]
	],
	"energyMeter": [
		name: "Energy Meter",
		capability: "capability.energyMeter",
		attributes: [
			"energy" // 0 - 100
		]
	],
	"estimatedTimeOfArrival": [
		name: "Estimated Time Of Arrival",
		capability: "capability.estimatedTimeOfArrival",
		attributes: [
			"eta" // Date
		]
	],
	"fanControl": [
		name: "Fan Control",
		capability: "capability.fanControl",
		attributes: [
			"speed" // ["low","medium-low","medium","medium-high","high","on","off","auto"]
		],
        action: "actionFanControl"
	],
	"filterStatus": [
		name: "Filter Status",
		capability: "capability.filterStatus",
		attributes: [
			"filterStatus" // ["normal", "replace"]
		]
	],
    "garageDoorControl": [
		name: "Garage Door Control",
		capability: "capability.garageDoorControl",
		attributes: [
			"door" // ["unknown", "open", "closing", "closed", "opening"]
		],
		action: "actionOpenClose"
	], 	
	"healthCheck": [
		name: "Health Check",
		capability: "capability.healthCheck",
		attributes: [
			"checkInterval" // 0 - 100
		],
		action: "actionHealthCheck"
	],
	"holdableButton": [
		name: "Holdable Button",
		capability: "capability.holdableButton",
		attributes: [
			"held" // 0 - 100
		]
	],	
	"illuminanceMeasurement": [
		name: "Illuminance Measurement",
		capability: "capability.illuminanceMeasurement",
		attributes: [
			"illuminance" // 0 - 100
		]
	],
	"imageCapture": [
		name: "Image Capture",
		capability: "capability.imageCapture",
		attributes: [
			"image" // String
			],
		action: "actionImageCapture"
	],
	"lightEffects": [
		name: "Light Effects",
		capability: "capability.lightEffects",
		attributes: [
			"effectName", // String
            "lightEffects" // JSON_OBJ
		],
		action: "actionLightEffects"
	],
	"locationMode": [
		name: "Location Mode",
		capability: "capability.locationMode",
		attributes: [
			"mode"
		]
	],
	"lock": [
		name: "Lock Codes",
		capability: "capability.lock",
		attributes: [
			"lock" // ["locked", "unlocked with timeout", "unlocked", "unknown"]
		],
		action: "actionLock"
	],
	"lockCodes": [
		name: "Lock Codes",
		capability: "capability.lockCodes",
		attributes: [
			"codeChanged", // ["added", "changed", "deleted", "failed"]
            "codeLength",
            "lockCodes", // JSON_OBJ
            "maxCodes"
		],
		action: "actionLockCodes"
	],     
	"mediaController": [
		name: "Media Controller",
		capability: "capability.mediaController",
		attributes: [
			"activities", // JSON_OBJ
			"currentActivity" // String
		],
		action: "actionMediaController"
	],
	"momentary": [
		name: "Momentary",
		capability: "capability.momentary",
		attributes: [
		],
		action: "actionMomentary"
	],
	"motionSensor": [
		name: "Motion Sensor",
		capability: "capability.motionSensor",
		attributes: [
			"motion" // ["inactive", "active"]
		]
	],
	"notification": [
		name: "Notification",
		capability: "capability.notification",
		attributes: [
		],
		action: "actionNotification"
	],
	"pHMeasurement": [
		name: "pH Measurement",
		capability: "capability.pHMeasurement",
		attributes: [
			"pH" // 0 - 100
		]
	],
	"powerMeter": [
		name: "Power Meter",
		capability: "capability.powerMeter",
		attributes: [
			"power" // 0 - 100
		]
	],
	"powerSource": [
		name: "Power Source",
		capability: "capability.powerSource",
		attributes: [
			"powerSource" // ["battery", "dc", "mains", "unknown"]
		]
	],
	"presenceSensor": [
		name: "Presence Sensor",
		capability: "capability.presenceSensor",
		attributes: [
			"presence" // ["present", "not present"]
		]
	],
	"pressureMeasurement": [
		name: "PressureMeasurement",
		capability: "capability.pressureMeasurement",
		attributes: [
			"pressure" // 0 - 100
		]
	],
	"pushableButton": [
		name: "Pushable Button",
		capability: "capability.pushableButton",
		attributes: [
            "numberOfButtons", // 1 - #
            "pushed" // 1 - #
		]
	],	
	"refresh": [
		name: "Refresh",
		capability: "capability.refresh",
		attributes: [
		],
		action: "actionRefresh"
	],
	"relativeHumidityMeasurement": [
		name: "Relative Humidity Measurement",
		capability: "capability.relativeHumidityMeasurement",
		attributes: [
            "humidity" // 0 - 100
		]
	],    
	"releasableButton": [
		name: "ReleasableButton",
		capability: "capability.releasableButton",
		attributes: [
			"released"
		]
	],
	"samsungTV": [
		name: "Samsung TV",
		capability: "capability.samsungTV",
		attributes: [
            "messageButton", // JSON_OBJ
            "mute", // ["muted", "unknown", "unmuted"]
            "pictureMode", // ["unknown", "standard", "movie", "dynamic"]
            "soundMode", // ["speech", "movie", "unknown", "standard", "music"]
            "switch", // ["on", "off"]
            "volume" // 0 - 100
		],
		action: "actionSamsungTV"
	],
	"securityKeypad": [
		name: "Security Keypad",
		capability: "capability.securityKeypad",
		attributes: [
            "codeChanged", // ["added", "changed", "deleted", "failed"]
            "codeLength",
            "lockCodes", // JSON_OBJ
            "maxCodes",
            "securityKeypad" // ["disarmed", "armed home", "armed away", "unknown"]
		],
		action: "actionSecurityKeypad"
	],
	"signalStrength": [
		name: "Signal Strength",
		capability: "capability.signalStrength",
		attributes: [
			"lqi", // 0 - 100
			"rssi" // 0 - 100
		]
	],
	"sleepSensor": [
		name: "Sleep Sensor",
		capability: "capability.sleepSensor",
		attributes: [
			"sleeping" // ["not sleeping", "sleeping"]
		]
	],
	"smokeDetector": [
		name: "Smoke Detector",
		capability: "capability.smokeDetector",
		attributes: [
			"smoke" // ["clear", "tested", "detected"]
		]
	],
	"soundPressureLevel": [
		name: "Sound Pressure Level",
		capability: "capability.soundPressureLevel",
		attributes: [
			"soundPressureLevel" // 0 - 100
		]
	],
	"soundSensor": [
		name: "Sound Sensor",
		capability: "capability.soundSensor",
		attributes: [
			"sound" // ["detected", "not detected"]
		]
	],
	"speechRecognition": [
		name: "Speech Recognition",
		capability: "capability.speechRecognition",
		attributes: [
			"phraseSpoken" // String
		]
	],
	"speechSynthesis": [
		name: "Speech Synthesis",
		capability: "capability.speechSynthesis",
		attributes: [
		],
		action: "actionSpeechSynthesis"
	],
	"stepSensor": [
		name: "Step Sensor",
		capability: "capability.stepSensor",
		attributes: [
			"goal", // 0 - #
			"steps" // 0 - #
		]
	],
	"switch": [
		name: "Switch",
		capability: "capability.switch",
		attributes: [
			"switch" // ["on", "off"]
		],
		action: "actionOnOff"
	],	
	"switchLevel": [
		name: "Switch Level",
		capability: "capability.switchLevel",
		attributes: [
			"level" // 0 - 100
		],
		action: "actionSwitchLevel"
	],
	"tv": [
		name: "TV",
		capability: "capability.TV",
		attributes: [
            "channel", // 0 - #
            "movieMode", // String
            "picture", // String
            "power", // String
            "sound", // String
            "volume" // 0 - 100
		],
		action: "actionTV"
	],	
	"tamperAlert": [
		name: "Tamper Alert",
		capability: "capability.tamperAlert",
		attributes: [
			"tamper" // ["clear", "detected"]
		]
	],
	"temperatureSensor": [
		name: "Temperature Measurement",
		capability: "capability.temperatureMeasurement",
		attributes: [
			"temperature" // 0 - 100
		]
	],
	"temperatureMeasurement": [
		name: "Temperature Measurement",
		capability: "capability.temperatureMeasurement",
		attributes: [
			"temperature" // 0 - 100
		]
	],	
	"thermostatCoolingSetpoint": [
		name: "Thermostat Cooling Setpoint",
		capability: "capability.thermostatCoolingSetpoint",
		attributes: [
			"coolingSetpoint" // 0 - 100
		],
		action: "actionThermostatCoolingSetpoint"
	],
	"thermostatFanMode": [
		name: "Thermostat Fan Mode",
		capability: "capability.thermostatFanMode",
		attributes: [
			"thermostatFanMode" // ["auto", "circulate", "on"]
		],
		action: "actionThermostatFanMode"
	],
	"thermostatHeatingSetpoint": [
		name: "Thermostat Heating Setpoint",
		capability: "capability.thermostatHeatingSetpoint",
		attributes: [
			"heatingSetpoint" // 0 - 100
		],
		action: "actionThermostatHeatingSetpoint"
	],  
	"thermostatMode": [
		name: "Thermostat Mode",
		capability: "capability.thermostatMode",
		attributes: [
			"thermostatMode" // ["heat", "cool", "emergency heat", "auto", "off"]
		],
		action: "actionThermostatMode"
	],
	"thermostatOperatingState": [
		name: "Thermostat Operating State",
		capability: "capability.thermostatOperatingState",
		attributes: [
			"thermostatOperatingState" // ["vent economizer", "pending cool", "cooling", "heating", "pending heat", "fan only", "idle"]
		]
	],
    "thermostatSchedule": [
		name: "Thermostat Schedule",
		capability: "capability.thermostatSchedule",
		attributes: [
			"schedule" // JSON_OBJ
		],
		action: "actionThermostatSchedule"
	],
	"threeAxis": [
		name: "Three Axis",
		capability: "capability.threeAxis",
		attributes: [
			"threeAxis" // VECTOR3
		]
	],
	"timedSession": [
		name: "Timed Session",
		capability: "capability.timedSession",
		attributes: [
			"sessionStatus", // ["stopped", "canceled", "running", "paused"]
			"timeRemaining" // 0 - 100
		],
		action: "actionTimedSession"
	],
	"tone": [
		name: "Tone",
		capability: "capability.tone",
		attributes: [
		],
		action: "actionTone"
	],
	"ultravioletIndex": [
		name: "Ultraviolet Index",
		capability: "capability.ultravioletIndex",
		attributes: [
			"ultravioletIndex" // 0 - 100
		]
	],
	"valve": [
		name: "Valve",
		capability: "capability.valve",
		attributes: [
			"valve" // ["open", "closed"]
		],
		action: "actionOpenClose"
	],
    "videoCamera": [
		name: "Video Camera",
		capability: "capability.videoCamera",
		attributes: [
			"camera", // ["on", "off", "restarting", "unavailable"]
            "mute", // ["unmuted", "muted"]
            "settings", // JSON_OBJ
            "statusMessage" // String
		],
		action: "actionVideoCamera"
	],
    "videoCapture": [
		name: "Video Capture",
		capability: "capability.videoCapture",
		attributes: [
			"clip" // JSON_OBJ
		],
		action: "actionVideoCapture"
	],
    "voltageMeasurement": [
		name: "Voltage Measurement",
		capability: "capability.voltageMeasurement",
		attributes: [
			"voltage" // 0 - #
		],
		action: "actionVideoCapture"
	],
	"waterSensor": [
		name: "Water Sensor",
		capability: "capability.waterSensor",
		attributes: [
			"water" // ["wet", "dry"]
		]
	],	
	"windowShades": [
		name: "Window Shade",
		capability: "capability.windowShade",
		attributes: [
			"windowShade"
		],
		action: "actionWindowShade"
	],
	"windowShade": [
		name: "Window Shade",
		capability: "capability.windowShade",
		attributes: [
			"position", // 0 - 100
			"windowShade" // ["opening", "partially open", "closed", "open", "closing", "unknown"]
		],
		action: "actionWindowShade"
	],
	"zwMultichannel": [
		name: "ZW Multichannel",
		capability: "capability.zwMultichannel",
		attributes: [
			"epEvent", // String
            "epInfo" // String
		],
		action: "actionZwMultichannel"
	]
]

def installed() {
	debug("[a:installed] Installed with settings: ${settings}")

	runEvery15Minutes(initialize)
	runEvery1Minute(pingState)
    
	initialize()
}

def updated() {
	debug("[a:updated] Updated with settings: ${settings}")

	// Unsubscribe from all events
	unsubscribe()

	// Subscribe to stuff
	initialize()
}

def initialize() {
    debug("Initializing app...")
    
	// subscribe to mode/routine changes
	subscribe(location, "mode", inputHandler)
	subscribe(location, "routineExecuted", inputHandler)
    
    def attributes = [
        notify: ["Contacts", "System"]
    ]
    
    settings.selectedDevices.each { device ->
        def normalizeId = normalizeId(device)
        
        settings[normalizeId].each { capability ->
            def capabilityCamel = lowerCamel(capability)
            def capabilitiesMap = CAPABILITY_MAP[capabilityCamel]

            capabilitiesMap["attributes"].each { attribute ->
			    subscribe(device, attribute, inputHandler)
		    }
            
            if (!attributes.containsKey(capabilityCamel)) {
				attributes[capabilityCamel] = []
			}
            
            attributes[capabilityCamel].push(normalizeId)
        }
    }
    
	// Subscribe to new events from devices
	CAPABILITY_MAP.each { key, capability ->
		capability["attributes"].each { attribute ->
			subscribe(settings[key], attribute, inputHandler)
		}
	}

	// Subscribe to events from the mqttLink
	subscribe(mqttLink, "message", mqttLinkHandler)

    updateSubscription(attributes)
}

// Update the mqttLink's subscription
def updateSubscription(attributes) {
	def json = new groovy.json.JsonOutput().toJson([
		path: "/subscribe",
		body: [
			devices: attributes
		]
	])

	debug("[a:updateSubscription] Updating subscription: ${json}")

    mqttLink.deviceNotification(json)
}

// Receive an inbound event from the MQTT Link Driver
def mqttLinkHandler(evt) {    
	def json = new JsonSlurper().parseText(evt.value)
	debug("[a:mqttLinkHandler] Received inbound device event from MQTT Link Driver: ${json}")
    
	if (json.type == "notify") {
		sendNotificationEvent("${json.value}")
		return
	} else if (json.type == "modes") {
		actionModes(json.value)
		return
	} else if (json.type == "routines") {
		actionRoutines(json.value)
		return
	}
    
    def attribute = json.type
    def capability = CAPABILITY_MAP[attribute]
    def normalizedId = json.device.toString()
    def deviceName = state.selectedLookup[normalizedId]
    
    def selectedDevice = settings.selectedDevices.find { 
        device -> (device.displayName == deviceName)
    }
    
    if (selectedDevice && settings[normalizedId] && capability["attributes"].contains(attribute)) {
        if (capability.containsKey("action")) {
            def action = capability["action"]
            json['action'] = action
            debug("[a:mqttLinkHandler] MQTT incoming target action: ${json}")
            // Yes, this is calling the method dynamically
            "$action"(selectedDevice, attribute, json.value)
        }
    }
}

// Receive an event from a device
def inputHandler(evt) {
    
    // Incoming MQTT event will tigger a hub event which in-turn triggers a second call
    // to inputHandler. If the evt is a hub Event and not json, it is swallowed
    // to prevent triggering an outbound MQTT event for the incoming MQTT event.    
	if (state.ignoreEvent
		&& state.ignoreEvent.name == evt.displayName
		&& state.ignoreEvent.type == evt.name
		&& state.ignoreEvent.value == evt.value
	) {
		debug("[a:inputHandler] Ignoring event: ${state.ignoreEvent}")
		state.ignoreEvent = false;
	}
	else {
		def json = new JsonOutput().toJson([
            path: "/push",
            body: [
                archivable: evt.archivable,
                date: evt.date,
                description: evt.description,
                descriptionText: evt.descriptionText,
                deviceId: evt.deviceId,
                deviceLabel: evt.displayName,
                displayed: evt.displayed,
                eventId: evt.id,
                hubId: evt.hubId,
                installedAppId: evt.installedAppId,
                isStateChange: evt.isStateChange,
                locationId: evt.locationId,
                name: evt.name,
                normalizedId: normalizedId(evt),
                source: evt.source,
                translatable: evt.translatable,
                type: evt.type,
                value: evt.value,
                unit: evt.unit,
            ]
		])
        
		debug("[a:inputHandler] Forwarding device event to driver: ${json}")
        mqttLink.deviceNotification(json)
	}
}

def pingState() {
    def pingList = []
    settings.selectedDevices.each { device ->
        def deviceId = normalizeId(device)
        def attributes = device.getSupportedAttributes()
        def capabilities = device.getCapabilities()

        capabilities.each { capability ->
            
            def found = false
            settings[deviceId].find { cap ->
                if (cap == capability.name) {
                    found = true
                    return true
                }
                return false 
            }

            if (found) {
                capability.getAttributes().each { attribute ->
            
                    def attributeName = upperCamel(attribute.toString())
                    def currentValue = device."current${attributeName}"
            
                    debug("[a:pingState] Sending state refresh: ${device}:${attribute}:${currentValue}")
                    
                    pingList.add([
                            normalizedId: deviceId,
                            name: attribute.name,
                            value: currentValue.toString(),
                            pingRefresh: true
                        ])
                }
            }
        }
    }
    
    if (pingList.size > 0) {
        def json = new JsonOutput().toJson([
                        path: "/ping",
                        body: pingList
                    ])
        
        mqttLink.deviceNotification(json)
    }
    
}

// ========================================================
// HELPERS
// ========================================================

def getDeviceObj(id) {
    def found
    settings.allDevices.each { device -> 
        if (device.getId() == id) {
            debug("[a:getDeviceObj] Found at $device for $id with id: ${device.id}")
            found = device
        }
    }
    return found
}

def getHubId() {
    def hub = location.hubs[0]
    def hubNameNormalized = normalize(hub.name)
    return "${hubNameNormalized}-${hub.hardwareID}".toLowerCase()
}

def getTopicPrefix() {
    return "${rootTopic()}/${getHubId()}/"
}

def upperCamel(str) {
    def c = str.charAt(0)
    return "${c.toUpperCase()}${str.substring(1)}".toString();
}

def lowerCamel(str) {
    def c = str.charAt(0)
    return "${c.toLowerCase()}${str.substring(1)}".toString();
}

def normalize(name) {
    return name.replaceAll("[^a-zA-Z0-9]+","-").toLowerCase()
}

def normalizeId(name, id) {
    def normalizedName = normalize(name)
    return "${normalizedName}-${id}".toString()
}

def normalizeId(device) {
    return normalizeId(device.displayName, device.id)
}

def normalizedId(com.hubitat.hub.domain.Event evt) {
    def deviceId = evt.deviceId
    
    if (!deviceId && evt.type == "LOCATION_MODE_CHANGE") {
        return normalizeId(evt.displayName, "mode")
    }
    
    return normalizeId(evt.displayName, deviceId)
}

// ========================================================
// LOGGING
// ========================================================

def debug(msg) {
	if (debugLogging) {
    	log.debug msg
    }
}

def info(msg) {
    log.info msg
}

def warn(msg) {
    log.warn msg
}

def error(msg) {
    log.error msg
}

// ========================================================
// ACTIONS
// ========================================================

// +---------------------------------+
// | WARNING, BEYOND HERE BE DRAGONS |
// +---------------------------------+
// These are the functions that handle incoming messages from MQTT.
// I tried to put them in closures but apparently SmartThings Groovy sandbox
// restricts you from running closures from an object (it's not safe).
// --
// John E - Note there isn't the same sandbox for Hubitat.  So heed
// the original warning.

def actionAirConditionerMode(device, attribute, value) {
	device.setAirConditionerMode(value)
}

def actionAlarm(device, attribute, value) {
	switch (value) {
		case "both":
			device.both()
			break
		case "off":
			device.off()
			break
		case "siren":
			device.siren()
			break
		case "strobe":
			device.strobe()
			break
	}
}

def actionAudioMute(device, attribute, value) {
	device.setMute(value)
}

def actionAudioNotification(device, attribute, value) {
//value0: URI/URL of track to play
//value1: Volume level (0 to 100)  
    def (texttrackuri, volumelevel) = value.split(',')
	switch (attribute) {
		case "playText":
    		device.playText(texttrackuri, volumelevel)
			break
		case "playTextAndRestore":
			device.playTextAndRestore(texttrackuri, volumelevel)
			break
		case "playTextAndResume":
			device.playTextAndResume(texttrackuri, volumelevel)
			break
		case "playTrack":
			device.playTrack(texttrackuri, volumelevel)
			break
        case "playTrackAndResume":
			device.playTrackAndResume(texttrackuri, volumelevel)
			break
		case "playTrackAndRestore":
			device.playTrackAndRestore(texttrackuri, volumelevel)
			break
	}
}

def actionAudioVolume(device, attribute, value) {
	switch (attribute) {
        case "mute":
			device.mute()
			break            
		case "setVolume":
			device.setVolume(value)
			break
        case "unmute":
			device.unmute()
			break           
		case "volumeUp":
			device.volumeUp()
			break
		case "volumeDown":
			device.volumeDown()
			break
	}
}

def actionColorControl(device, attribute, value) {
	switch (attribute) {
		case "setColor":
			def values = value.split(',')
			def colormap = ["hue": values[0] as int, "saturation": values[1] as int]

			if (values[2]) {
				colormap["level"] = values[2] as int
			}

			device.setColor(colormap)
			break
		case "setHue":
			device.setHue(value as int)
			break
		case "setSaturation":
			device.setSaturation(value as int)
			break
	}
}

def actionChangeLevel(device, attribute, value) {
	switch (attribute) {
		case "startLevelChange":
			device.startLevelChange(value)
			break
		case "stopLevelChange":
			device.stopLevelChange()
			break
	}
}

def actionChime(device, attribute, value) {
	switch (attribute) {
		case "playSound":
			device.playSound(value)
			break
		case "stop":
			device.stop()
			break
	}
}
        
def actionColorTemperature(device, attribute, value) {
	device.setColorTemperature(value as int)
}

def actionConfiguration(device, attribute, value) {
//	device.configure()
}

def actionConsumable(device, attribute, value) {
	device.setConsumableStatus(value)
}

def actionFanControl(device, attribute, value) {
// value: speed - ENUM ["low","medium-low","medium","medium-high","high","on","off","auto"]
    device.setSpeed(value)
}

def actionHealthCheck(device, attribute, value) {
	device.ping()
}

def actionImageCapture(device, attribute, value) {
	device.take()
}

def actionLightEffects(device, attribute, value) {
	switch (value) {
		case "setEffect":
			device.setEffect(value)
			break
		case "setNextEffect":
			device.setNextEffect()
			break
		case "setPreviousEffect":
			device.setPreviousEffect()
			break
	}
}

def actionLock(device, attribute, value) {
	if (value == "lock") {
		device.lock()
	} else if (value == "unlock") {
		device.unlock()
	}
}

def actionLockCodes(device, attribute, value) {
// codeposition required (NUMBER) - Code position number
// pincode required (STRING) - Numeric PIN code
// name optional (STRING) - Name for this lock code
	switch (value) {
		case "deleteCode":
			device.deleteCode(value)
			break
		case "getCodes":
			device.getCodes()
			break
		case "setCode":
            def (codeposition, pincode, name) = value.split(",")
			device.setCode(codeposition, pincode, name)
			break
		case "setCodeLength":
			device.setCodeLength()
			break
	}
}

def actionMediaController(device, attribute, value) {
	switch (value) {
		case "getAllActivities":
			device.getAllActivities()
			break
		case "getCurrentActivity":
			device.getCurrentActivity()
			break
		case "startActivity":	
			device.startActivity(value)
			break
	}
}

def actionPlaybackShuffle(device, attribute, value) {
	device.setPlaybackShuffle(value)
}

def actionMomentary(device, attribute, value) {
	device.push()
}

def actionNotification(device, attribute, value) {
	device.deviceNotification(value)
}

def actionSamsungTV(device, attribute, value) {
	switch (value) {
		case "mute":
			device.mute()
			break
		case "off":
			device.off()
			break
		case "on":
			device.on()
			break
		case "setPictureMode":
			device.setPictureMode(value)
			break
		case "setSoundMode":
			device.setSoundMode(value)
			break
		case "setVolume":
			device.setVolume(value)
			break
		case "showMessage":
			device.showMessage(value)
			break
		case "unmute":
			device.unmute()
			break
		case "volumeDown":
			device.volumeDown()
			break
		case "volumeUp":
			device.volumeUp()
			break
	}
}

def actionSecurityKeypad(device, attribute, value) {
// codeposition required (NUMBER) - Code position number
// pincode required (STRING) - Numeric PIN code
// name optional (STRING) - Name for this lock code
	switch (value) {
		case "armAway":
			device.armAway()
			break
		case "armHome":
			device.armHome()
			break
		case "deleteCode":
			device.deleteCode(value)
			break
		case "disarm":
			device.disarm(value)
			break
		case "getCodes":
			device.getCodes()
			break
		case "setCode":
		    def (codeposition, pincode, name) = value.split(",")
			device.setCode(codeposition, pincode, name)
			break
		case "setCodeLength":
			device.setCodeLength(value)
			break
		case "setEntryDelay":
			device.setEntryDelay(value)
			break
		case "setExitDelay":
			device.setExitDelay(value)
			break
	}
}

def actionSpeechSynthesis(device, attribute, value) {
	device.speak(value)
}

def actionSwitchLevel(device, attribute, value) {
	device.setLevel(value as int)
}

def actionTimedSession(device, attribute, value) {
	switch (attribute) {
		case "cancel":
			device.cancel()
			break
		case "pause":
			device.pause()
			break
		case "setTimeRemaining":
			device.setTimeRemaining(value)
			break
		case "start":
			device.start()
			break
		case "stop":
			device.stop()
			break
	}
}

def actionTone(device, attribute, value) {
	device.beep()
}

def actionTV(device, attribute, value) {
	switch (attribute) {
		case "channelDown":
			device.channelDown()
			break
		case "channelUp":
			device.channelUp()
			break
		case "volumeDown":
			device.volumeDown()
			break
		case "volumeUp":
			device.volumeUp()
			break
	}
}

def actionThermostatCoolingSetpoint(device, attribute, value) {
    device.setCoolingSetpoint(value)
}

def actionThermostatFanMode(device, attribute, value) {
	switch (attribute) {
		case "fanAuto":
			device.fanAuto()
			break
		case "fanCirculate":
			device.fanCirculate()
			break
		case "fanOn":
			device.fanOn()
			break
		case "setThermostatFanMode":
			device.setThermostatFanMode(value)
			break
    }
}

def actionThermostatHeatingSetpoint(device, attribute, value) {
    device.setHeatingSetpoint(value)
}

def actionThermostatMode(device, attribute, value) {
	switch (attribute) {
		case "auto":
			device.auto()
			break
		case "cool":
			device.cool()
			break
		case "emergencyHeat":
			device.emergencyHeat()
			break
		case "heat":
			device.heat()
			break
		case "off":
			device.off()
			break
		case "setThermostatMode":
			device.setThermostatMode(value)
			break
    }
}

def actionThermostatSchedule(device, attribute, value) {
    device.setSchedule(value)
}

def actionVideoCamera(device, attribute, value) {
	switch (attribute) {
		case "flip":
			device.flip()
			break
		case "mute":
			device.mute()
			break
		case "off":
			device.off()
			break
		case "on":
			device.on()
			break
		case "unmute":
			device.unmute()
			break
    }
}

def actionVideoCapture(device, attribute, value) {
	// capture(DATE, DATE, DATE)
    device.capture(value)
}

def actionWindowShade(device, attribute, value) {
	switch (attribute) {
		case "close":
			device.close(value)
			break
		case "open":
			device.open()
			break
		case "setPosition":
			device.setPosition(value)
			break
	}
}

def actionZwMultichannel(device, attribute, value) {
	switch (attribute) {
		case "enableEpEvents":
			device.enableEpEvents(value)
			break
		case "epCmd":
            def (num, str) = value.split(",")
			device.epCmd(num, str)
			break
	}
}

/*
 * Generic Actions
 * Routines & Modes Actions
 */

def actionOpenClose(device, attribute, value) {
	if (value == "open") {
		device.open()
	} else if (value == "close") {
		device.close()
	}
}

def actionOnOff(device, attribute, value) {
	if (value == "off") {
		device.off()
	} else if (value == "on") {
		device.on()
	}
}

def actionRoutines(value) {
	location.helloHome?.execute(value)
}

def actionModes(value) {
	if (location.mode != value) {
		if (location.modes?.find{it.name == value}) {
			location.setMode(value)
		} else {
			warn("[actionModes] unknown mode: ${value}")
		}
	}
}