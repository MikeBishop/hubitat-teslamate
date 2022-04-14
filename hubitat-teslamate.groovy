/*
    Hubitat-Teslamate MQTT Integration
    Copyright 2022 Mike Bishop,  All Rights Reserved

    Based on:
        - MQTT Link Driver by jeubanks, https://github.com/mydevbox/hubitat-mqtt-link
        - Hubitat Parent/Child Examples by MikeMaxwell, https://github.com/hubitat/HubitatPublic/
*/

metadata {
    definition (name: "Teslamate", namespace: "evequefou", author: "Mike Bishop") {
        // Provided for broker setup and troubleshooting
		command "connect"
		command "disconnect"

        attribute "connectionState", "STRING"

    }
    preferences {
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
            name: "debugLogging",
            type: "bool",
            title: "Enable debug logging",
            required: false,
            default: false
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
}

void initialize() {
    debug("Initializing TeslaMate connection...")

    try {
        interfaces.mqtt.connect(getBrokerUri(),
                           getTopicPrefix(),
                           settings?.brokerUser,
                           settings?.brokerPassword)

        // delay for connection
        pauseExecution(1000)

        debug("[d:subscribe] full topic: ${getTopicPrefix()}#")
        interfaces.mqtt.subscribe("${getTopicPrefix()}#")
        connected()
    } catch(Exception e) {
        error("[d:initialize] ${e}")
    }
}

void updated() {
    initialize()
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

// ========================================================
// MQTT METHODS
// ========================================================

// Parse incoming message from the MQTT broker
def parse(String event) {
    def message = interfaces.mqtt.parseMessage(event)
    debug("[d:parse] Received MQTT message: ${message}")

    def (id, property) = message.topic.minus(getTopicPrefix()).tokenize('/')

    def thisId = device.deviceNetworkId
    def cd = getChildDevice("${thisId}-${id}")
    if (!cd) {
        // Child device doesn't exist; need to create it.
        cd = addChildDevice("evequefou", "Teslamate Vehicle", "${thisId}-${id}", [name: "TeslaMate Vehicle ${id}", isComponent: false])
    }

    // Display name must be set from the parent device, not the child
    if( property == "display_name" ) {
        cd.setLabel(message.payload)
    }
    else {
        cd.parse([[name:property, value:message.payload]])
    }
}

def mqttClientStatus(status) {
    debug("[d:mqttClientStatus] status: ${status}")
}

// ========================================================
// ANNOUNCEMENTS
// ========================================================

def connected() {
    debug("[d:connected] Connected to broker")
    state.connectionState = "connected"
    sendEvent (name: "connectionState", value: "connected")
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
