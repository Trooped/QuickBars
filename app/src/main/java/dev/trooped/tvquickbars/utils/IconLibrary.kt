package dev.trooped.tvquickbars.utils

import dev.trooped.tvquickbars.R
import dev.trooped.tvquickbars.data.IconPack

/**
 * IconLibrary Object
 * Provides a list of available icons for use in the application.
 * Each icon is represented by an IconPack data class instance.
 * Icons are categorized by their usage, such as lights, switches, media, etc.
 */
object IconLibrary {

    fun getAvailableIcons(): List<IconPack> {
        return listOf(
            // --- Lights ---
            IconPack(name = "Lightbulb", iconOnRes = R.drawable.lightbulb, iconOffRes = R.drawable.lightbulb_off, tags = listOf("light", "bulb", "idea", "lighting")),
            IconPack(name = "Lightbulb On", iconOnRes = R.drawable.lightbulb_on, tags = listOf("light", "bulb", "idea", "lighting")),
            IconPack(name = "Lightbulb On Outline", iconOnRes = R.drawable.lightbulb_on_outline, tags = listOf("light", "bulb", "idea", "lighting")),
            IconPack(name = "Lightbulb Outline", iconOnRes = R.drawable.lightbulb_outline, iconOffRes = R.drawable.lightbulb_off_outline, tags = listOf("light", "bulb", "idea", "lighting")),
            IconPack(name = "Light Switch", iconOnRes = R.drawable.light_switch, iconOffRes = R.drawable.light_switch_off, tags = listOf("light", "switch", "wall", "control")),
            IconPack(name = "Ceiling Light", iconOnRes = R.drawable.ceiling_light, tags = listOf("light", "ceiling", "lamp", "fixture")),
            IconPack(name = "Ceiling Light Outline", iconOnRes = R.drawable.ceiling_light_outline, tags = listOf("light", "ceiling", "lamp", "fixture")),
            IconPack(name = "Chandelier", iconOnRes = R.drawable.chandelier, tags = listOf("light", "ceiling", "lamp", "chandelier", "fancy")),
            IconPack(name = "LED Strip", iconOnRes = R.drawable.led_strip_variant, iconOffRes = R.drawable.led_strip_variant_off, tags = listOf("light", "led", "strip", "rgb", "ambience")),
            IconPack(name = "Post Lamp", iconOnRes = R.drawable.post_lamp, tags = listOf("light", "outdoor", "lamp", "post", "garden", "exterior")),
            IconPack(name = "Desk Lamp", iconOnRes = R.drawable.desk_lamp, iconOffRes = R.drawable.desk_lamp_off, tags = listOf("desk", "lamp", "light", "table", "reading")),
            IconPack(name = "Floor Lamp", iconOnRes = R.drawable.floor_lamp, tags = listOf("light", "floor", "lamp", "interior")),
            IconPack(name = "Outdoor Lamp", iconOnRes = R.drawable.outdoor_lamp, tags = listOf("light", "outdoor", "lamp", "exterior", "wall")),
            IconPack(name = "Coach Lamp", iconOnRes = R.drawable.coach_lamp, tags = listOf("light", "coach", "lamp", "outdoor", "vintage")),
            IconPack(name = "Recessed Light", iconOnRes = R.drawable.light_recessed, tags = listOf("light", "recessed", "ceiling", "downlight", "potlight")),
            IconPack(name = "Ceiling Fan Light", iconOnRes = R.drawable.ceiling_fan_light, tags = listOf("light", "ceiling", "fan", "fixture")),
            IconPack(name = "Globe Light", iconOnRes = R.drawable.globe_light, tags = listOf("light", "ceiling", "globe", "lamp")),
            IconPack(name = "Globe Light Outline", iconOnRes = R.drawable.globe_light_outline, tags = listOf("light", "globe", "ceiling", "lamp")),
            IconPack(name = "Light Flood Down", iconOnRes = R.drawable.light_flood_down, tags = listOf("light", "down", "point", "spotlight", "floodlight")),
            IconPack(name = "Light Flood Up", iconOnRes = R.drawable.light_flood_up, tags = listOf("light", "up", "point", "spotlight", "floodlight")),


            // --- Switches & Power ---
            IconPack(name = "Toggle Switch", iconOnRes = R.drawable.toggle_switch, iconOffRes = R.drawable.toggle_switch_off, tags = listOf("switch", "toggle", "control", "power")),
            IconPack(name = "Toggle Switch Outline", iconOnRes = R.drawable.toggle_switch_outline, iconOffRes = R.drawable.toggle_switch_off_outline, tags = listOf("switch", "toggle", "control", "power")),
            IconPack(name = "Toggle Switch Variant", iconOnRes = R.drawable.toggle_switch_variant, iconOffRes = R.drawable.toggle_switch_variant_off, tags = listOf("switch", "toggle", "control", "power", "wall")),
            IconPack(name = "Power Button", iconOnRes = R.drawable.power_on, iconOffRes = R.drawable.power_off, tags = listOf("power", "button", "shutdown", "on", "off")),
            IconPack(name = "Power Plug", iconOnRes = R.drawable.power_plug, iconOffRes = R.drawable.power_plug_off, tags = listOf("power", "plug", "outlet", "socket", "electricity")),
            IconPack(name = "Electric Switch", iconOnRes = R.drawable.electric_switch, iconOffRes = R.drawable.electric_switch_closed, tags = listOf("switch", "electric", "circuit", "breaker")),
            IconPack(name = "Power Socket EU", iconOnRes = R.drawable.power_socket_eu, tags = listOf("power", "socket", "eu", "europe", "outlet", "wall")),
            IconPack(name = "Power Socket US", iconOnRes = R.drawable.power_socket_us, tags = listOf("power", "socket", "us", "america", "outlet", "wall")),
            IconPack(name = "Power Socket DE", iconOnRes = R.drawable.power_socket_de, tags = listOf("power", "socket", "de", "germany", "outlet", "wall")),

            // --- Media & Control ---
            IconPack(name = "Television", iconOnRes = R.drawable.television, iconOffRes = R.drawable.television_off, tags = listOf("tv", "television", "media", "screen", "display")),
            IconPack(name = "Television Classic", iconOnRes = R.drawable.television_classic, iconOffRes = R.drawable.television_classic_off, tags = listOf("tv", "television", "classic", "retro", "media")),
            IconPack(name = "Remote TV", iconOnRes = R.drawable.remote_tv, tags = listOf("remote", "tv", "control", "media")),
            IconPack(name = "Speaker", iconOnRes = R.drawable.speaker, iconOffRes = R.drawable.speaker_off, tags = listOf("sound", "speaker", "audio", "music", "volume")),
            IconPack(name = "Knob", iconOnRes = R.drawable.knob, tags = listOf("control", "knob", "dial", "volume", "adjust")),
            IconPack(name = "Button Pointer", iconOnRes = R.drawable.button_pointer, tags = listOf("pointer", "button", "click", "ui", "cursor")),
            IconPack(name = "Button Gesture", iconOnRes = R.drawable.gesture_tap_button, tags = listOf("pointer", "button", "click", "ui", "gesture", "tap")),
            IconPack(name = "Cursor Default Click Outline", iconOnRes = R.drawable.cursor_default_click_outline, tags = listOf("pointer", "button", "click", "ui", "cursor")),
            IconPack(name = "Controller", iconOnRes = R.drawable.controller, iconOffRes = R.drawable.controller_off, tags = listOf("controller", "game", "pad", "joystick")),
            IconPack(name = "Remote", iconOnRes = R.drawable.remote, iconOffRes = null, tags = listOf("remote", "control", "tv")),
            IconPack(name = "Transfer Down", iconOnRes = R.drawable.transfer_down, iconOffRes = null, tags = listOf("transfer", "down", "arrow", "download")),
            IconPack(name = "Transfer Up", iconOnRes = R.drawable.transfer_up, iconOffRes = null, tags = listOf("transfer", "up", "arrow", "upload")),

            // --- Climate & Fans ---
            IconPack(name = "AC Unit", iconOnRes = R.drawable.ic_ac_unit, tags = listOf("ac", "air", "conditioner", "hvac", "climate")),
            IconPack(name = "AC", iconOnRes = R.drawable.air_conditioner, tags = listOf("ac", "air", "conditioner", "hvac", "climate", "cooling")),
            IconPack(name = "Fan", iconOnRes = R.drawable.fan, iconOffRes = R.drawable.fan_off, tags = listOf("fan", "climate", "air", "ventilate", "hvac")),
            IconPack(name = "Ceiling Fan", iconOnRes = R.drawable.ceiling_fan, tags = listOf("fan", "ceiling", "climate", "air")),
            IconPack(name = "Fan Plus", iconOnRes = R.drawable.fan_plus, tags = listOf("fan", "increase", "speed", "air")),
            IconPack(name = "Fan Minus", iconOnRes = R.drawable.fan_minus, tags = listOf("fan", "decrease", "speed", "air")),
            IconPack(name = "Fan Chevron Up", iconOnRes = R.drawable.fan_chevron_up, tags = listOf("fan", "up", "speed", "increase")),
            IconPack(name = "Fan Chevron Down", iconOnRes = R.drawable.fan_chevron_down, tags = listOf("fan", "down", "speed", "decrease")),
            IconPack(name = "Air Purifier", iconOnRes = R.drawable.air_purifier, iconOffRes = R.drawable.air_purifier_off, tags = listOf("air", "purifier", "hvac", "filter", "clean")),
            IconPack(name = "Air Humidifier", iconOnRes = R.drawable.air_humidifier, iconOffRes = R.drawable.air_humidifier_off, tags = listOf("air", "humidifier", "hvac", "water", "steam", "mist")),

            // --- Home & Security ---
            IconPack(name = "CCTV", iconOnRes = R.drawable.cctv, iconOffRes = R.drawable.cctv_off, tags = listOf("camera", "security", "cctv", "surveillance")),
            IconPack(name = "Garage", iconOnRes = R.drawable.garage_open_variant, iconOffRes = R.drawable.garage_variant, tags = listOf("garage", "door", "car", "home")),
            IconPack(name = "Door", iconOnRes = R.drawable.door, tags = listOf("door", "entry", "exit", "enter", "leave")),
            IconPack(name = "Door Lock", iconOnRes = R.drawable.lock_open_outline, iconOffRes = R.drawable.lock_outline, tags = listOf("door", "lock", "secure", "safety", "key")),
            IconPack(name = "Window Shutter", iconOnRes = R.drawable.window_shutter_open, iconOffRes = R.drawable.window_shutter, tags = listOf("window", "shutter", "cover", "blinds")),
            IconPack(name = "Blinds", iconOnRes = R.drawable.blinds_open, iconOffRes = R.drawable.blinds, tags = listOf("window", "blinds", "cover", "vertical")),
            IconPack(name = "Roller Shade", iconOnRes = R.drawable.roller_shade, iconOffRes = R.drawable.roller_shade_closed, tags = listOf("window", "roller", "shade", "cover", "blinds")),
            IconPack(name = "Blinds Horizontal", iconOnRes = R.drawable.blinds_horizontal, iconOffRes = R.drawable.blinds_horizontal_closed, tags = listOf("window", "blinds", "cover", "horizontal")),
            IconPack(name = "Curtains", iconOnRes = R.drawable.curtains, iconOffRes = R.drawable.curtains_closed, tags = listOf("curtains", "window", "cover", "blind", "drapes")),
            IconPack(name = "Robot Vacuum", iconOnRes = R.drawable.robot_vacuum, iconOffRes = R.drawable.robot_vacuum_off, tags = listOf("robot", "vacuum", "cleaning", "auto", "cleaner")),
            IconPack(name = "Robot Vacuum Variant", iconOnRes = R.drawable.robot_vacuum_variant, iconOffRes = R.drawable.robot_vacuum_variant_off, tags = listOf("robot", "vacuum", "cleaning", "auto", "cleaner")),
            IconPack(name = "Sprinkler", iconOnRes = R.drawable.sprinkler_variant, iconOffRes = null, tags = listOf("sprinkler", "garden", "lawn", "water", "irrigation")),
            IconPack(name = "Shower Head", iconOnRes = R.drawable.shower_head, iconOffRes = null, tags = listOf("shower", "bathroom", "water", "bath")),
            IconPack(name = "Pool", iconOnRes = R.drawable.pool, iconOffRes = null, tags = listOf("pool", "swimming", "water")),
            IconPack(name = "Countertop", iconOnRes = R.drawable.countertop_outline, iconOffRes = null, tags = listOf("countertop", "kitchen", "surface", "bench")),
            IconPack(name = "Alarm Panel", iconOnRes = R.drawable.alarm_panel, iconOffRes = null, tags = listOf("alarm", "panel", "security", "lock")),
            IconPack(name = "Alarm Panel Outline", iconOnRes = R.drawable.alarm_panel_outline, iconOffRes = null, tags = listOf("alarm", "panel", "security", "lock")),
            IconPack(name = "Shield Home", iconOnRes = R.drawable.shield_home, iconOffRes = null, tags = listOf("alarm", "panel", "security", "lock", "shield", "home")),
            IconPack(name = "Shield Home Outline", iconOnRes = R.drawable.shield_home_outline, iconOffRes = null, tags = listOf("alarm", "panel", "security", "lock", "shield", "home")),
            IconPack(name = "Shield Lock", iconOnRes = R.drawable.shield_lock, iconOffRes = null, tags = listOf("alarm", "panel", "security", "lock", "shield", "home")),
            IconPack(name = "Shield Lock Outline", iconOnRes = R.drawable.shield_lock_outline, iconOffRes = null, tags = listOf("alarm", "panel", "security", "lock", "shield", "home")),


            // --- Appliances ---
            IconPack(name = "Washing Machine", iconOnRes = R.drawable.washing_machine, iconOffRes = R.drawable.washing_machine_off, tags = listOf("appliance", "laundry", "wash", "clothes")),
            IconPack(name = "Dishwasher", iconOnRes = R.drawable.dishwasher, iconOffRes = R.drawable.dishwasher_off, tags = listOf("appliance", "kitchen", "dish", "wash")),
            IconPack(name = "Microwave", iconOnRes = R.drawable.microwave, iconOffRes = R.drawable.microwave_off, tags = listOf("appliance", "kitchen", "cook", "heat", "food")),
            IconPack(name = "Tumble Dryer", iconOnRes = R.drawable.tumble_dryer, iconOffRes = R.drawable.tumble_dryer_off, tags = listOf("appliance", "laundry", "dryer", "clothes")),
            IconPack(name = "Coffee Maker", iconOnRes = R.drawable.coffee_maker, tags = listOf("appliance", "kitchen", "coffee", "brew")),
            IconPack(name = "Coffee Maker Outline", iconOnRes = R.drawable.coffee_maker_outline, tags = listOf("appliance", "kitchen", "coffee", "brew")),
            IconPack(name = "Coffee", iconOnRes = R.drawable.coffee_outline, iconOffRes = R.drawable.coffee_off_outline, tags = listOf("appliance", "kitchen", "coffee", "drink", "cup")),
            IconPack(name = "Water Boiler", iconOnRes = R.drawable.water_boiler, iconOffRes = R.drawable.water_boiler_off, tags = listOf("appliance", "water", "boiler", "heat", "tank")),
            IconPack(name = "Fridge", iconOnRes = R.drawable.fridge_outline, tags = listOf("appliance", "fridge", "kitchen", "food", "refrigerator", "cold")),
            IconPack(name = "Toaster", iconOnRes = R.drawable.toaster, iconOffRes = R.drawable.toaster_off, tags = listOf("toaster", "kitchen", "appliance", "food", "bread")),
            IconPack(name = "Kettle", iconOnRes = R.drawable.kettle_steam, iconOffRes = R.drawable.kettle_off, tags = listOf("kettle", "tea", "water", "kitchen", "appliance", "boil")),
            IconPack(name = "Toaster Oven", iconOnRes = R.drawable.toaster_oven, iconOffRes = null, tags = listOf("toaster", "oven", "kitchen", "appliance", "cook")),
            IconPack(name = "Stove", iconOnRes = R.drawable.stove, iconOffRes = null, tags = listOf("stove", "cooktop", "kitchen", "appliance", "cook", "oven")),
            IconPack(name = "Play", iconOnRes = R.drawable.play_circle, iconOffRes = null, tags = listOf("media player", "play", "music", "video")),

            // --- Miscellaneous ---
            IconPack(name = "Script", iconOnRes = R.drawable.script_text_play, iconOffRes = R.drawable.script, tags = listOf("script", "automation", "action", "routine")),
            IconPack(name = "Script Outline", iconOnRes = R.drawable.script_text_play_outline, iconOffRes = R.drawable.script_outline, tags = listOf("script", "automation", "action", "routine")),
            IconPack(name = "Automation", iconOnRes = R.drawable.robot_happy, tags = listOf("robot", "automation", "action", "routine", "trigger", "schedule")),
            IconPack(name = "Laptop", iconOnRes = R.drawable.laptop, tags = listOf("computer", "device", "laptop", "pc")),
            IconPack(name = "Wifi", iconOnRes = R.drawable.wifi, iconOffRes = R.drawable.wifi_off, tags = listOf("network", "internet", "wifi", "wireless")),
            IconPack(name = "Weather Sunny", iconOnRes = R.drawable.weather_sunny, tags = listOf("weather", "sun", "day", "sunny", "forecast")),
            IconPack(name = "Weather Night", iconOnRes = R.drawable.weather_night, tags = listOf("weather", "moon", "night", "clear", "forecast")),
            IconPack(name = "Sync", iconOnRes = R.drawable.sync, tags = listOf("sync", "reload", "refresh", "update")),
            IconPack(name = "Rocket", iconOnRes = R.drawable.rocket_launch, iconOffRes = R.drawable.rocket, tags = listOf("script", "automation", "launch", "rocket", "fast")),
            IconPack(name = "Rocket Outline", iconOnRes = R.drawable.rocket_launch_outline, iconOffRes = R.drawable.rocket_outline, tags = listOf("script", "automation", "launch", "rocket")),
            IconPack(name = "Car Electric", iconOnRes = R.drawable.car_electric, tags = listOf("car", "vehicle", "electric", "ev")),
            IconPack(name = "Car", iconOnRes = R.drawable.car_back, tags = listOf("car", "vehicle", "automobile")),
            IconPack(name = "Arrow Up Bold Circle", iconOnRes = R.drawable.arrow_up_bold_circle, tags = listOf("arrow", "up", "navigation", "circle", "direction")),
            IconPack(name = "Arrow Down Bold Circle", iconOnRes = R.drawable.arrow_down_bold_circle, tags = listOf("arrow", "down", "navigation", "circle", "direction")),
            IconPack(name = "Arrow Up Bold Circle Outline", iconOnRes = R.drawable.arrow_up_bold_circle_outline, tags = listOf("arrow", "up", "navigation", "circle", "direction")),
            IconPack(name = "Arrow Down Bold Circle Outline", iconOnRes = R.drawable.arrow_down_bold_circle_outline, tags = listOf("arrow", "down", "navigation", "circle", "direction")),
            IconPack(name = "King Bed", iconOnRes = R.drawable.bed_king, tags = listOf("bed", "sleep", "king", "bedroom", "furniture")),
            IconPack(name = "King Bed Outline", iconOnRes = R.drawable.bed_king_outline, tags = listOf("bed", "sleep", "king", "bedroom", "furniture")),
            IconPack(name = "Single Bed Outline", iconOnRes = R.drawable.bed_single_outline, tags = listOf("bed", "sleep", "single", "bedroom", "furniture")),
            IconPack(name = "Single Bed", iconOnRes = R.drawable.bed_single, tags = listOf("bed", "sleep", "single", "bedroom", "furniture")),
            IconPack(name = "Plus Circle", iconOnRes = R.drawable.plus_circle, tags = listOf("plus", "add", "circle", "increase")),
            IconPack(name = "Plus Circle Outline", iconOnRes = R.drawable.plus_circle_outline, tags = listOf("plus", "add", "circle", "increase")),
            IconPack(name = "Minus Circle", iconOnRes = R.drawable.minus_circle, tags = listOf("minus", "subtract", "circle", "decrease")),
            IconPack(name = "Minus Circle Outline", iconOnRes = R.drawable.minus_circle_outline, tags = listOf("minus", "subtract", "circle", "decrease")),
            IconPack(name = "Movie", iconOnRes = R.drawable.movie_open, iconOffRes = R.drawable.movie, tags = listOf("movie", "film", "clapperboard", "video")),
            IconPack(name = "Cat", iconOnRes = R.drawable.cat, iconOffRes = null, tags = listOf("cat", "pet", "animal")),
            IconPack(name = "Dog", iconOnRes = R.drawable.dog, iconOffRes = null, tags = listOf("dog", "pet", "animal")),
            IconPack(name = "Dog Side", iconOnRes = R.drawable.dog_side, iconOffRes = null, tags = listOf("dog", "pet", "animal")),
            IconPack(name = "Water Outline", iconOnRes = R.drawable.water_outline, iconOffRes = null, tags = listOf("water", "drop", "liquid")),
            IconPack(name = "Tree", iconOnRes = R.drawable.tree, iconOffRes = null, tags = listOf("tree", "nature", "plant", "garden")),
            IconPack(name = "Grass", iconOnRes = R.drawable.grass, iconOffRes = null, tags = listOf("grass", "lawn", "nature", "garden")),
            IconPack(name = "Speedometer", iconOnRes = R.drawable.speedometer, iconOffRes = null, tags = listOf("speed", "sensor", "measurement","fast" )),
            IconPack(name = "Gauge", iconOnRes = R.drawable.gauge, iconOffRes = null, tags = listOf("gauge", "sensor", "measurement")),
            IconPack(name = "Chart", iconOnRes = R.drawable.chart_line, iconOffRes = null, tags = listOf("chart", "sensor", "measurement","cartesian")),
            IconPack(name = "Thermometer", iconOnRes = R.drawable.thermometer, iconOffRes = null, tags = listOf("thermometer", "sensor", "measurement","temperature", "hot")),
            IconPack(name = "Dip Switch", iconOnRes = R.drawable.dip_switch, iconOffRes = null, tags = listOf("dip", "switch", "binary","sensor", "on","off")),
            IconPack(name = "Fire", iconOnRes = R.drawable.fire, iconOffRes = null, tags = listOf("fire", "heat", "hot","pyro")),
            )
    }
}
