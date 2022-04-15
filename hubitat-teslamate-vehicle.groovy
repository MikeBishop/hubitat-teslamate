/*
    Hubitat-TeslaMate MQTT Integration, Vehicle Instance
    Copyright 2022 Mike Bishop,  All Rights Reserved
*/
import groovy.transform.Field

metadata {
    definition(name: "TeslaMate Vehicle", namespace: "evequefou", author: "Mike Bishop", component: false) {
        capability "PresenceSensor"
        capability "PowerSource"
        capability "VoltageMeasurement"
        capability "CurrentMeter"
        capability "PowerMeter"
        capability "Battery"
        capability "TemperatureMeasurement"
        capability "ContactSensor"
        capability "Lock"

        attribute "state", "STRING"
        attribute "since", "DATE"
        attribute "healthy", "ENUM", ["true", "false"]
        attribute "version", "STRING"
        attribute "update_available", "ENUM", ["true", "false"]
        attribute "update_version", "STRING"
        attribute "model", "STRING"
        attribute "trim_badging", "STRING"
        attribute "exterior_color", "STRING"
        attribute "wheel_type", "STRING"
        attribute "spoiler_type", "STRING"
        attribute "geofence", "STRING"
        attribute "latitude", "NUMBER"
        attribute "longitude", "NUMBER"
        attribute "shift_state", "STRING"
        attribute "power", "NUMBER"
        attribute "speed", "NUMBER"
        attribute "heading", "NUMBER"
        attribute "elevation", "NUMBER"
        attribute "locked", "ENUM", ["true", "false"]
        attribute "sentry_mode", "ENUM", ["true", "false"]
        attribute "is_user_present", "ENUM", ["true", "false"]
        attribute "is_climate_on", "ENUM", ["true", "false"]
        attribute "inside_temp", "NUMBER"
        attribute "outside_temp", "NUMBER"
        attribute "is_preconditioning", "ENUM", ["true", "false"]
        attribute "odometer", "NUMBER"
        attribute "est_battery_range", "NUMBER"
        attribute "rated_battery_range", "NUMBER"
        attribute "ideal_battery_range", "NUMBER"
        attribute "usable_battery_level", "NUMBER"
        attribute "charge_energy_added", "NUMBER"
        attribute "charge_limit_soc", "NUMBER"
        attribute "charge_port_door_open", "ENUM", ["true", "false"]
        attribute "charge_current_request", "NUMBER"
        attribute "charge_current_request_max", "NUMBER"
        attribute "scheduled_charging_start_time", "DATE"
        attribute "time_to_full_charge", "NUMBER"
    }
    preferences {
        input(
            name: "homeGeofence",
            type: "string",
            title: "Geofence for Presence",
            required: false,
            defaultValue: "Home"
        )
        input(
            name: "temperatureIndoor",
            type: "bool",
            title: "Temperature reflects Indoor (On) or Outdoor (Off)",
            required: false,
            defaultValue: true
        )
        input(
            name: "tempFormat",
            type: "enum",
            required: true,
            defaultValue: "Fahrenheit (°F)",
            title: "Display Unit - Temperature: Fahrenheit (°F) or Celsius (°C)",
            options: ["Fahrenheit (°F)", "Celsius (°C)"]
        )
        input(
            name: "rangeFormat",
            type: "enum",
            required: true,
            defaultValue: "miles (mi)",
            title: "Display Unit - Range: miles or kilometers",
            options: ["miles (mi)", "kilometers (ki)"]
        )
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
    warn("description logging is: ${txtEnable == true}")
}

void installed() {
    info("Installed...")
    device.updateSetting("txtEnable",[type:"bool",value:true])
    refresh()
}

void parse(String description) { log.warn "parse(String description) not implemented" }

void lock() { log.warn "lock not implemented" }

void unlock() { log.warn "unlock not implemented" }

@Field final Map Transforms = [
    battery_level: "battery",
    charger_voltage: "voltage",
    charger_actual_current: "current",
    charger_phases: "phases",
    locked: "lock",
    est_battery_range_km: "est_battery_range",
    rated_battery_range_km: "rated_battery_range",
    ideal_battery_range_km: "ideal_battery_range"
]

void parse(List description) {
    description.each {
        if( Transforms[it.name] ) {
            it.name = Transforms[it.name]
        }
        debug(it)

        switch(it.name) {
            case "geofence":
                sendEvent(it)
                sendEvent([
                    "name": "presence",
                    "value": it.value == settings?.homeGeofence ? "present" : "not present"
                ])
                break
            case "plugged_in":
                sendEvent([
                    "name": "powerSource",
                    "value": it.value == "true" ? "mains" : "battery"
                ])
                break
            case "voltage":
            case "current":
            case "phases":
                state[it.name] = it.value
                sendEvent(it)
                if( state?.voltage && state?.amperage && state?.phases ) {
                    sendEvent([
                        "name": "power",
                        "value": state?.voltage * state?.amperage * state?.phases
                    ])
                }
                break
            case "lock":
                sendEvent([
                    "name": it.name,
                    "value": it.value == "true" ? "locked" : "unlocked"
                ])
                break
            case "inside_temp":
            case "outside_temp":
                it.value = Float.parseFloat(it.value)
                if( settings?.tempFormat == "Fahrenheit (°F)" ) {
                    // Convert temperature formats
                    it.value = celsiusToFahrenheit(it.value)
                }
                if( settings?.temperatureIndoor ^ it.name == "outside_temp" ) {
                    sendEvent([
                        "name": "temperature",
                        "value": it.value
                    ])
                }
                sendEvent(it)
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
            case "est_battery_range":
            case "rated_battery_range":
            case "ideal_battery_range":
                if( settings?.rangeFormat == "miles (mi)" ) {
                    it.value = Float.parseFloat(it.value) / 1.609344
                }
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
