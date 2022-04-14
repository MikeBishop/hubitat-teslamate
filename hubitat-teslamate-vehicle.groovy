/*
    Hubitat-Teslamate MQTT Integration, Vehicle Instance
    Copyright 2022 Mike Bishop,  All Rights Reserved
*/

metadata {
    definition(name: "Teslamate Vehicle", namespace: "evequefou", author: "Mike Bishop", component: false) {
        // capability "PresenceSensor"
        // capability "PowerSource"
        // capability "PowerMeter"
        capability "Battery"
        // capability "TemperatureMeasurement"
        // capability "ContactSensor"

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
        attribute "est_battery_range_km", "NUMBER"
        attribute "rated_battery_range_km", "NUMBER"
        attribute "ideal_battery_range_km", "NUMBER"
        attribute "usable_battery_level", "NUMBER"
        attribute "plugged_in", "ENUM", ["true", "false"]
        attribute "charge_energy_added", "NUMBER"
        attribute "charge_limit_soc", "NUMBER"
        attribute "charge_port_door_open", "ENUM", ["true", "false"]
        attribute "charger_actual_current", "NUMBER"
        attribute "charger_phases", "NUMBER"
        attribute "charger_power", "NUMBER"
        attribute "charger_voltage", "NUMBER"
        attribute "charge_current_request", "NUMBER"
        attribute "charge_current_request_max", "NUMBER"
        attribute "scheduled_charging_start_time", "DATE"
        attribute "time_to_full_charge", "NUMBER"
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
    warn("description logging is: ${txtEnable == true}")
}

void installed() {
    info("Installed...")
    device.updateSetting("txtEnable",[type:"bool",value:true])
    refresh()
}

void parse(String description) { log.warn "parse(String description) not implemented" }

void parse(List description) {
    description.each {
        debug("Handling event ${it}")
        info("Test: ${getParent()?.settings?.brokerIp}")

        switch(it.value) {
            case "battery_level":
                sendEvent(["name": "battery", "value": it.value])
                break
            default:
                sendEvent(it)
                break
        }

        // TODO:  Translate other properties into sensor values
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
