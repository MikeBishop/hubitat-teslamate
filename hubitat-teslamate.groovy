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
            name: "areaPresence",
            type: "bool",
            title: "Create presence device for larger area",
            required: false,
            default: false
        )
        if( areaPresence ) {
            input(
                name: "areaPresenceRadius",
                type: "number",
                title: "Radius for area presence device (in km)",
                required: false,
                default: "130"
            )
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
            debug("[d:initialize] full topic: ${fullTopic}")
            interfaces.mqtt.subscribe(fullTopic)
        }

        if( areaPresence ) {
            // Need to identify all known vehicles and start the check
            def thisId = device.deviceNetworkId
            def childIds = getChildDevices().collect { it.deviceNetworkId.minus("${thisId}-") }
            debug("[d:initialize] childIds: ${childIds}")
            def vehicles = childIds.findAll { it.split("-").size() == 1 }
            debug("[d:initialize] Schedule proximity check for children ${vehicles.inspect()}")
            for( vehicle in vehicles ) {
                startProximityCheck([vehicleId: vehicle])
            }
            getChildDevices().collect{ it.deviceNetworkId }.
                // Keep known vehicles
                minus(vehicles.collect {[thisId, it].join("-")}).
                // Keep area presence sensors for known vehicles
                minus(vehicles.collect {[thisId, it, "areaPresence"].join("-")}).
                // Delete everything else
                each {
                    debug("Removing unknown child device ${it}}")
                    deleteChildDevice(it)
                };
        }
        else {
            // Remove all area presence sensors
            getChildDevices().findAll { it.deviceNetworkId.endsWith("-areaPresence") }.
                each {
                    debug("Removing area presence sensor ${it.deviceNetworkId}}")
                    deleteChildDevice(it.deviceNetworkId)
                };
        }
        connected()
    } catch(Exception e) {
        error("[d:initialize] ${e}")
        reconnect()
    }
}

void updated() {
    disconnect()
    if( !areaPresenceRadius ) {
        device.updateSetting("areaPresenceRadius", [value: 130, type: "number"])
    }
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
        warn("Disconnection from broker failed, ${e.message}")
        if (interfaces.mqtt.isConnected()) connected()
    }
}

def reconnect() {
    disconnect()
    if( !state.reconnectDelay )
        state.reconnectDelay = 1
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
    def childId = "${thisId}-${id}"
    def cd = getChildDevice(childId)
    if (!cd) {
        // Child device doesn't exist; need to create it.
        cd = addChildDevice("evequefou", "TeslaMate Vehicle", childId, [name: "TeslaMate Vehicle ${id}", isComponent: false])
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
            case "latitude":
            case "longitude":
                if( areaPresence ) {
                    runIn(1, "handleLocationEvent", [
                        overwrite: true,
                        data: [
                            vehicleId: id
                        ]
                    ])
                }
                break
            case "geofence":
                def newPresence = value == settings?.homeGeofence ? "present" : "not present"
                if( areaPresence &&
                    cd.currentValue("presence") == "present" &&
                    newPresence == "not present" )
                {
                    schedule_next_check(0, id);
                }
                toProcess.add([
                    "name": "presence",
                    "value": newPresence
                ])
                break

            case "inside_temp":
            case "outside_temp":
                value = Float.parseFloat(value)
                if( settings?.tempFormat == "Fahrenheit (°F)" ) {
                    // Convert temperature formats
                    value = celsiusToFahrenheit(value).floatValue();
                }
                value = value.round(1)
                if( (settings?.temperatureIndoor == "Indoor") ^ (property == "outside_temp") ) {
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

        debug "Sending ${toProcess.inspect()} to child device ${cd.getLabel()}}"
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
// Area Presence
// ========================================================

def startProximityCheck(data) {
    def vehicleId = data.vehicleId
    // Subscribe to lat/long events
    ["latitude", "longitude"].each {
        def fullTopic = "${getTopicPrefix()}${vehicleId}/${it}"
        debug("[d:startProximityCheck] full topic: ${fullTopic}")
        interfaces.mqtt.subscribe(fullTopic)
    }
}

def handleLocationEvent(data) {
    def thisId = device.deviceNetworkId
    def vehicleId = data.vehicleId
    def cd = getChildDevice("${thisId}-${vehicleId}")
    def carLat = cd.currentValue("latitude")
    def carLon = cd.currentValue("longitude")
    def homeLat = location.latitude.doubleValue()
    def homeLon = location.longitude.doubleValue()

    if( !carLat || !carLon ) {
        debug("[d:handleLocationEvent] Don't know car location yet!");
        return
    }

    debug("[d:handleLocationEvent] carLat: ${carLat}, carLon: ${carLon}, homeLat: ${homeLat}, homeLon: ${homeLon}")

    // Unsubscribe from lat/long events
    ["latitude", "longitude"].each {
        interfaces.mqtt.unsubscribe("${getTopicPrefix()}${vehicleId}/${it}")
    }

    // Determine the distance from home
    final int R = 6371; // Radius of the earth

    def latDistance = Math.toRadians(homeLat - carLat);
    def lonDistance = Math.toRadians(homeLon - carLon);
    def a = (Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
            + Math.cos(Math.toRadians(carLat)) * Math.cos(Math.toRadians(homeLat))
            * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2))
    def c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    def distance = R * c; // distance in km

    // If the car is within the specified radius, set the area presence sensor to true
    def areaPresenceId = [thisId, data.vehicleId, "areaPresence"].join("-")
    def areaPresence = getChildDevice(areaPresenceId)
    if( !areaPresence ) {
        // Child device doesn't exist; need to create it.
        areaPresence = addChildDevice(
            "hubitat",
            "Generic Component Presence Sensor",
            areaPresenceId,
            [name: "${cd.getLabel()} Nearby", isComponent: false]
        )
    }
    areaPresence.setLabel("${cd.getLabel()} Nearby")
    areaPresence.parse([
        [name: "presence", value: distance < areaPresenceRadius ? "present" : "not present"]
    ])

    // Determine the update period, unless car is at home.
    // (If it's at home, we'll next check an hour after it departs.)
    if( cd.currentValue("geofence") != settings?.homeGeofence ) {
        schedule_next_check(distance, vehicleId)
    }
    else {
        debug("[d:handleLocationEvent] No check scheduled; car is at home")
    }
}

def schedule_next_check(distance, vehicleId) {
        def travelDistance = Math.abs(distance - areaPresenceRadius)
        def timeToNextCheck = Math.max(travelDistance / 130, 0.1 )
        debug("[d:schedule_next_check] distance: ${(int) distance} km, check in ${(int) (60 * timeToNextCheck)} minutes")
        runIn((long) (60 * 60 * timeToNextCheck), "startProximityCheck", [
            data: [
                vehicleId: vehicleId
            ]
        ])
}

def componentRefresh(child) {
    if( child.deviceNetworkId.endsWith("-areaPresence") ) {
        // Area presence sensor; refresh
        startProximityCheck([
            vehicleId: child.deviceNetworkId.
                        minus("${device.deviceNetworkId}-").
                        minus("-areaPresence")
        ]);
    }
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

