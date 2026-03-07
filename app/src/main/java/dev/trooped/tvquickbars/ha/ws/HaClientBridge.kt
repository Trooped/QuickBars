package dev.trooped.tvquickbars.ha.ws

import android.content.Context
import dev.trooped.tvquickbars.ha.ConnectionState
import dev.trooped.tvquickbars.ha.HomeAssistantListener
import org.json.JSONObject

interface HaClientBridge {
    val listener: HomeAssistantListener
    val knownIds: MutableSet<String>

    fun getContext(): Context?
    fun fireEvent(type: String, data: JSONObject): Boolean
    fun send(obj: JSONObject): Boolean
    fun nextId(): Int
    fun updateConnectionState(state: ConnectionState)

    fun getLatestStatesJson(): org.json.JSONArray?
}
