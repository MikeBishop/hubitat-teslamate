# Hubitat Elevation Teslamate Link

I've wanted to pull some basic information from my Tesla, but the primary API is
polling-heavy and not really something I wanted running on my hub. The WebSocket
API is complex to implement and I've done almost nothing in Groovy prior, so I
didn't really want to go down that road again. Plus you can only have one
WebSocket client active at a time, and that position is already occupied for me
by running TeslaMate.

But wait! TeslaMate exposes a fairly rich MQTT surface, which I'm already
consuming in another project,
[MMM-Powerwall](https://github.com/MikeBishop/MMM-Powerwall). Hubitat has an
MQTT client in-box. Thus was a potential match and solution made.

Not yet available through HPM, but I plan to do that after a few others have
banged on it or I've let it bake for a while on my system. In the meantime, I'd
love some help shaking bugs out if others are in a similar situation or want to
install the right bits.

What you need:

- One or more Tesla vehicles on your Tesla account; nothing to do otherwise!
- A working install of [TeslaMate](https://github.com/adriankumpf/teslamate),
  with the MQTT server reachable from the Hubitat
- Install the two drivers from this repo on your Hubitat
- Configure a new virtual device with the TeslaMate driver and point it to your
  MQTT server

It will pick up the vehicle(s) TeslaMate is issuing data about and create the
child devices using the other driver, including renaming the child device
whenever the vehicle's display name is published. The MQTT events can be very
chatty, especially while driving or charging, so I took a note from OWM-Alerts:
the behaviors are grouped into clusters of related information, and you only
subscribe to the clusters you need. (I've entirely dropped a few properties I
don't think anyone needs to automate, but if you need to trigger some action
based on when your car color changes, they're easy to add back.)

It's my first Hubitat driver, and it was a big chunk to bite off. I drew heavily
on a couple of other drivers for inspiration and reference:

- MQTT Link Driver by @jeubanks, [mydevbox/hubitat-mqtt-link](https://github.com/mydevbox/hubitat-mqtt-link)
- Hubitat Parent/Child Examples by @mike.maxwell, [hubitat/HubitatPublic](https://github.com/hubitat/HubitatPublic/)
- OpenWeatherMap-Alerts-Weather-Driver by @Matthew, [OpenWeatherMap-Alerts-Weather-Driver](https://community.hubitat.com/t/openweathermap-alerts-weather-driver/38249)

Important to note that because this is purely an MQTT consumer, it can't change
anything about the car, only display it. Even though it has the Lock/Unlock
commands, all those will do is log a warning that you can't do that.

#### Device Capabilities

Device capabilities are grouped into a couple distinct sets.  Only enable
what you need, to reduce the chattiness on the hub.  Note that because of the
way Hubitat handles attributes, once an attribute is enabled, it will remain
on the child device permanently.  If you disable something, it will just stop
updating.

You can delete the child device and the parent will promptly recreate it with
only the selected properties, but you'll lose the reference to it in your
automations.

The groups are:

- Always Available
   - capability "PresenceSensor"
   - attribute "geofence"
   - capability "Battery"
   - capability "PowerSource"
   - attribute "state"
   - attribute "healthy"
   - attribute "version"
   - attribute "update_available"
   - attribute "update_version"
   - attribute "scheduled_charging_start_time"
   - attribute "time_to_full_charge"

- Power and Charging Details
   - capability "VoltageMeasurement"
   - capability "CurrentMeter"
   - capability "PowerMeter"
   - attribute "est_battery_range"
   - attribute "rated_battery_range"
   - attribute "ideal_battery_range"
   - attribute "usable_battery_level"
   - attribute "charge_limit_soc"
   - attribute "charge_port_door_open"
   - attribute "charge_current_request"
   - attribute "charge_current_request_max"

- Temperature
   - capability "TemperatureMeasurement"
   - attribute "is_climate_on"
   - attribute "inside_temp"
   - attribute "outside_temp"
   - attribute "is_preconditioning"

- Security
   - capability "ContactSensor"
   - capability "Lock"
   - attribute "sentry_mode"
   - attribute "windows_open"
   - attribute "doors_open"
   - attribute "frunk_open"
   - attribute "trunk_open"

- Coarse Driving
   - attribute "shift_state"
   - attribute "odometer"

- Detailed Location & Driving
   - attribute "latitude"
   - attribute "longitude"
   - attribute "speed"
   - attribute "heading"
   - attribute "elevation"

Be aware that Detailed Location is very chatty while driving; what you want to
do can probably be done by defining a geofence in TeslaMate instead.


# Change Log

* [4/15/2022] Initial release
* [4/25/2022] Filter small changes (<100W) in power levels to reduce event
  frequency
* [5/4/2022]  Reconnect every 24 hours; attempt to catch connection loss and
  reconnect
* [5/15/2022] Bugfixes
* [6/7/2022]  Fix bug that caused "power" not to publish consistently
* [6/13/2022] Remove `charge_energy_added` to reduce event load
* [8/17/2022] Fix bug in disconnect error handling
