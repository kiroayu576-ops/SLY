package com.sly.xcloud.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class ScriptStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _scripts = MutableStateFlow(loadScripts())
    val scriptsFlow: StateFlow<List<UserScript>> = _scripts.asStateFlow()

    fun add(script: UserScript) {
        val updated = _scripts.value + script
        setScripts(updated)
    }

    fun update(script: UserScript) {
        val updated = _scripts.value.map { if (it.id == script.id) script else it }
        setScripts(updated)
    }

    fun delete(script: UserScript) {
        val updated = _scripts.value.filterNot { it.id == script.id }
        setScripts(updated)
    }

    fun setEnabled(script: UserScript, enabled: Boolean) {
        update(script.copy(enabled = enabled))
    }

    private fun setScripts(scripts: List<UserScript>) {
        _scripts.value = scripts
        saveScripts(scripts)
    }

    private fun loadScripts(): List<UserScript> {
        val raw = prefs.getString(KEY_SCRIPTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(scriptFromJson(obj))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveScripts(scripts: List<UserScript>) {
        val array = JSONArray()
        scripts.forEach { array.put(scriptToJson(it)) }
        prefs.edit().putString(KEY_SCRIPTS, array.toString()).apply()
    }

    private fun scriptToJson(script: UserScript): JSONObject {
        return JSONObject()
            .put("id", script.id)
            .put("name", script.name)
            .put("enabled", script.enabled)
            .put("code", script.code)
    }

    private fun scriptFromJson(obj: JSONObject): UserScript {
        return UserScript(
            id = obj.optString("id"),
            name = obj.optString("name"),
            enabled = obj.optBoolean("enabled", true),
            code = obj.optString("code")
        )
    }

    companion object {
        private const val PREFS_NAME = "scripts"
        private const val KEY_SCRIPTS = "scripts_json"
    }
}
