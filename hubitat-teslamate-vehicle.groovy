/*
    Hubitat-TeslaMate MQTT Integration, Vehicle Instance
    Copyright 2022 Mike Bishop,  All Rights Reserved
*/
import groovy.transform.Field

metadata {
    definition(
        name: "TeslaMate Vehicle",
        namespace: "evequefou",
        author: "Mike Bishop",
        component: false,
        importUrl: "https://raw.githubusercontent.com/MikeBishop/hubitat-teslamate/main/hubitat-teslamate-vehicle.groovy"
    ) {
// Always Available
        capability "PresenceSensor"
        attribute "geofence", "STRING"
        capability "Battery"
        capability "PowerSource"
        attribute "state", "STRING"
        attribute "since", "DATE"
        attribute "healthy", "ENUM", ["true", "false"]
        attribute "version", "STRING"
        attribute "update_available", "ENUM", ["true", "false"]
        attribute "update_version", "STRING"
        attribute "scheduled_charging_start_time", "DATE"
        attribute "time_to_full_charge", "NUMBER"

// Power and Charging Details
        capability "VoltageMeasurement"
        capability "CurrentMeter"
        capability "PowerMeter"
        attribute "phases", "NUMBER"
        attribute "est_battery_range", "NUMBER"
        attribute "rated_battery_range", "NUMBER"
        attribute "ideal_battery_range", "NUMBER"
        attribute "usable_battery_level", "NUMBER"
        attribute "charge_energy_added", "NUMBER"
        attribute "charge_limit_soc", "NUMBER"
        attribute "charge_port_door_open", "ENUM", ["true", "false"]
        attribute "charge_current_request", "NUMBER"
        attribute "charge_current_request_max", "NUMBER"

// Temperature
        capability "TemperatureMeasurement"
        attribute "is_climate_on", "ENUM", ["true", "false"]
        attribute "inside_temp", "NUMBER"
        attribute "outside_temp", "NUMBER"
        attribute "is_preconditioning", "ENUM", ["true", "false"]

// Security
        capability "ContactSensor"
        capability "Lock"
        attribute "windows_open", "ENUM", ["true", "false"]
        attribute "doors_open", "ENUM", ["true", "false"]
        attribute "frunk_open", "ENUM", ["true", "false"]
        attribute "trunk_open", "ENUM", ["true", "false"]
        attribute "sentry_mode", "ENUM", ["true", "false"]

// Coarse Location & Driving
        attribute "shift_state", "STRING"
        attribute "odometer", "NUMBER"

// Detailed Location & Driving
// WARNING:  These are exceptionally chatty;
//    do not enable unless critical!
// What you want to do can probably be done
// by defining a geofence in TeslaMate instead.
        attribute "latitude", "NUMBER"
        attribute "longitude", "NUMBER"
        attribute "speed", "NUMBER"
        attribute "heading", "NUMBER"
        attribute "elevation", "NUMBER"

    }
    preferences {
        input(
            name: "debugLogging",
            type: "bool",
            title: "Enable debug logging",
            required: false,
            default: false
        )
    }
}

void updated() {
    info("Updated...")
}

void installed() {
    info("Installed...")
}

void parse(String description) { log.warn "parse(String description) not implemented" }

void lock() { log.warn "lock not supported" }

void unlock() { log.warn "unlock not supported" }

void parse(List description) {
    description.each {
        debug(it)

        switch(it.name) {
            case "plugged_in":
                sendEvent([
                    "name": "powerSource",
                    "value": it.value == "true" ? "mains" : "battery"
                ])
                break
            case "voltage":
            case "amperage":
            case "phases":
                it.value = Float.parseFloat(it.value)
                sendEvent(it)
                def voltage = it.name == "voltage" ? it.value : device.currentValue("voltage")
                def amperage = it.name == "amperage" ? it.value : device.currentValue("amperage")
                def phases = it.name == "phases" ? it.value : device.currentValue("phases")

                if( voltage && amperage && phases ) {
                    sendEvent([
                        "name": "power",
                        "value": voltage * amperage * phases
                    ])
                }
                break
            case "windows_open":
            case "doors_open":
            case "frunk_open":
            case "trunk_open":
                state[it.name] = it.value
                def open_things = ["windows_open", "doors_open", "frunk_open", "trunk_open"]
                def contact = "closed"
                for( thing in open_things) {
                    if( state[thing] == "true"){
                        contact = "open"
                        break;
                    }
                }
                sendEvent([
                    "name": "contact",
                    "value": contact
                ])
                sendEvent(it)
                break
            default:
                sendEvent(it)
                break
        }
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
