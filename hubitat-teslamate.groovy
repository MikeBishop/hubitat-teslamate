/*
    Hubitat-TeslaMate MQTT Integration
    Copyright 2022 Mike Bishop,  All Rights Reserved

    Based on:
        - MQTT Link Driver by jeubanks, https://github.com/mydevbox/hubitat-mqtt-link
        - Hubitat Parent/Child Examples by MikeMaxwell, https://github.com/hubitat/HubitatPublic/
*/
import groovy.transform.Field

metadata {
    definition (
        name: "TeslaMate",
        namespace: "evequefou",
        author: "Mike Bishop",
        importUrl: "https://raw.githubusercontent.com/MikeBishop/hubitat-teslamate/main/hubitat-teslamate.groovy"
    ) {
        capability "Initialize"
        capability "Configuration"

        // Provided for broker setup and troubleshooting
		command "connect"
		command "disconnect"

        attribute "connectionState", "STRING"

    }
    preferences {
        section("MQTT Configuration") {
            input(
                name: "brokerIp",
                type: "string",
                title: "MQTT Broker IP Address",
                description: "e.g. 192.168.1.200",
                required: true,
                displayDuringSetup: true
            )
            input(
                name: "brokerPort",
                type: "string",
                title: "MQTT Broker Port",
                description: "e.g. 1883",
                required: true,
                displayDuringSetup: true
            )
            input(
                name: "brokerUser",
                type: "string",
                title: "MQTT Broker Username",
                description: "e.g. mqtt_user",
                required: false,
                displayDuringSetup: true
            )
            input(
                name: "brokerPassword",
                type: "password",
                title: "MQTT Broker Password",
                description: "e.g. ^L85er1Z7g&%2En!",
                required: false,
                displayDuringSetup: true
            )
            input(
                name: "namespace",
                type: "string",
                title: "TeslaMate custom namespace",
                description: "Typically used for multiple TeslaMate instances",
                required: false,
                displayDuringSetup: true
            )
        }
        section("Data Processing") {
            input(
                name: "homeGeofence",
                type: "string",
                title: "Geofence for Presence",
                required: false,
                defaultValue: "Home"
            )
            input(
                name: "showPower",
                type: "bool",
                title: "Expose power and charging details",
                required: false,
                default: false
            )
            input(
                name: "showClimate",
                type: "bool",
                title: "Expose climate and temperature details",
                required: false,
                default: false
            )
            input(
                name: "showSecurity",
                type: "bool",
                title: "Expose security and door state",
                required: false,
                default: false
            )
            input(
                name: "showCoarseDriving",
                type: "bool",
                title: "Expose low-frequency driving details",
                required: false,
                default: false
            )
            if( showCoarseDriving ) {
                input(
                    name: "showDetailedDriving",
                    type: "bool",
                    title: "Expose all driving details and precise location",
                    description: "This is a really bad idea",
                    required: false,
                    default: false
                )
            }
            if( showClimate ) {
                input(
                    name: "temperatureIndoor",
                    type: "enum",
                    title: "Temperature sensor reflects Indoor or Outdoor",
                    required: false,
                    defaultValue: "Outdoor",
                    options: ["Indoor", "Outdoor"]
                )
                input(
                    name: "tempFormat",
                    type: "enum",
                    required: true,
                    defaultValue: "Fahrenheit (°F)",
                    title: "Display Unit - Temperature: Fahrenheit (°F) or Celsius (°C)",
                    options: ["Fahrenheit (°F)", "Celsius (°C)"]
                )
            }
            if( showCoarseDriving || showDetailedDriving ) {
                input(
                    name: "rangeFormat",
                    type: "enum",
                    required: true,
                    defaultValue: "miles (mi)",
                    title: "Display Unit - Range: miles or kilometers",
                    options: ["miles (mi)", "kilometers (ki)"]
                )
            }
        }
        input(
            name: "debugLogging",
            type: "bool",
            title: "Enable debug logging",
            required: false,
            default: false
        )
    }
}

void initialize() {
    info("Initializing TeslaMate connection...")

    try {
        interfaces.mqtt.connect(getBrokerUri(),
                           getTopicPrefix(),
                           settings?.brokerUser,
                           settings?.brokerPassword)

        // delay for connection
        pauseExecution(1000)

        def subscriptions = AlwaysAvailable
        [
            showPower: PowerDetails,
            showClimate: ClimateDetails,
            showSecurity: SecurityDetails,
            showCoarseDriving: CoarseDriving,
            showDetailedDriving: BadIdeas
         ].each {
             setting, topics ->
                if( settings[setting] ) {
                    subscriptions += topics
                }
         }

        for( topic in subscriptions ) {
            def fullTopic = "${getTopicPrefix()}+/${topic}"
            debug("[d:subscribe] full topic: ${fullTopic}")
            interfaces.mqtt.subscribe(fullTopic)
        }
        connected()
    } catch(Exception e) {
        error("[d:initialize] ${e}")
        reconnect()
    }
}

void updated() {
    disconnect()
    initialize()
}

void configure() {
    updated()
}

// ========================================================
// MQTT COMMANDS
// ========================================================

def connect() {
    initialize()
}

def disconnect() {
    try {
        interfaces.mqtt.disconnect()
        disconnected()
    } catch(e) {
        warn("Disconnection from broker failed", ${e.message})
        if (interfaces.mqtt.isConnected()) connected()
    }
}

def reconnect() {
    disconnect()
    runIn(state.reconnectDelay, "connect")
    state.reconnectDelay *= 2
}

// ========================================================
// MQTT METHODS
// ========================================================

// Parse incoming message from the MQTT broker
def parse(String event) {
    def message = interfaces.mqtt.parseMessage(event)
    debug("[d:parse] Received MQTT message: ${message}")

    def (id, property) = message.topic.minus(getTopicPrefix()).tokenize('/')
    def value = message.payload

    def thisId = device.deviceNetworkId
    def cd = getChildDevice("${thisId}-${id}")
    if (!cd) {
        // Child device doesn't exist; need to create it.
        cd = addChildDevice("evequefou", "TeslaMate Vehicle", "${thisId}-${id}", [name: "TeslaMate Vehicle ${id}", isComponent: false])
    }

    // Display name must be set from the parent device, not the child
    if( property == "display_name" ) {
        cd.setLabel(message.payload)
    }
    else {
        if( Transforms[property] ) {
            property = Transforms[property]
        }

        def toProcess = []
        switch(property) {
            case "geofence":
                toProcess.add([
                    "name": "presence",
                    "value": value == settings?.homeGeofence ? "present" : "not present"
                ])
                break

            case "inside_temp":
            case "outside_temp":
                value = Float.parseFloat(value)
                if( settings?.tempFormat == "Fahrenheit (°F)" ) {
                    // Convert temperature formats
                    value = celsiusToFahrenheit(value)
                }
                value = value.round(1)
                if( settings?.temperatureIndoor ^ property == "outside_temp" ) {
                    toProcess.add([
                        "name": "temperature",
                        "value": value
                    ])
                }
                break

            case "lock":
                value = value == "true" ? "locked" : "unlocked"
                break

            case "est_battery_range":
            case "rated_battery_range":
            case "ideal_battery_range":
            case "odometer":
                value = Float.parseFloat(value)
                if( settings?.rangeFormat == "miles (mi)" ) {
                    value = value / 1.609344
                }
                value = value.round()
                break
        }
        toProcess.add([name: property, value: value])

        cd.parse(toProcess)
    }
}

def mqttClientStatus(status) {
    info("MQTT ${status}")
    if( status.startsWith("Error") ) {
        reconnect()
    }
}

// ========================================================
// ANNOUNCEMENTS
// ========================================================

def connected() {
    debug("[d:connected] Connected to broker")
    state.connectionState = "connected"
    state.reconnectDelay = 1
    sendEvent (name: "connectionState", value: "connected")
    runIn(24 * 60 * 60, "reconnect")
}

def disconnected() {
    debug("[d:disconnected] Disconnected from broker")
    state.connectionState = "disconnected"
    sendEvent (name: "connectionState", value: "disconnected")
}

// ========================================================
// HELPERS
// ========================================================

def getBrokerUri() {
    return "tcp://${settings?.brokerIp}:${settings?.brokerPort}"
}

def getTopicPrefix() {
    if( settings?.namespace ) return "teslamate/${settings?.namespace}/cars/"
    return "teslamate/cars/"
}

def mqttConnected() {
    return interfaces.mqtt.isConnected()
}

def notMqttConnected() {
    return !mqttConnected()
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

@Field static final List AlwaysAvailable = [
    "display_name",
    "geofence",
    "battery_level",
    "plugged_in",
    "state",
    "healthy",
    "version",
    "update_available",
    "update_version",
    "scheduled_charging_start_time",
    "time_to_full_charge"
]

// Power and Charging Details
@Field static final List PowerDetails = [
    "charger_voltage",
    "charger_actual_current",
    "charger_phases",
    "est_battery_range",
    "rated_battery_range",
    "ideal_battery_range",
    "usable_battery_level",
    "charge_limit_soc",
    "charge_port_door_open",
    "charge_current_request",
    "charge_current_request_max"
]

// Temperature
@Field static final List ClimateDetails = [
    "is_climate_on",
    "inside_temp",
    "outside_temp",
    "is_preconditioning"
]

// Security
@Field static final List SecurityDetails = [
    "windows_open",
    "doors_open",
    "frunk_open",
    "trunk_open",
    "locked",
    "sentry_mode"
]

// Coarse Location & Driving
@Field static final List CoarseDriving = [
    "shift_state",
    "odometer"
]

// Detailed Location & Driving
// WARNING:  These are exceptionally chatty;
//    do not enable unless critical!
// What you want to do can probably be done
// by defining a geofence in TeslaMate instead.
@Field static final List BadIdeas = [
    "latitude",
    "longitude",
    "speed",
    "heading",
    "elevation"
]

@Field final Map Transforms = [
    battery_level: "battery",
    charger_voltage: "voltage",
    charger_actual_current: "amperage",
    charger_phases: "phases",
    locked: "lock",
    est_battery_range_km: "est_battery_range",
    rated_battery_range_km: "rated_battery_range",
    ideal_battery_range_km: "ideal_battery_range"
]

