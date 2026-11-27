package dev.yiqidi.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

object NativeBridge {
    init { System.loadLibrary("yiqidi_core") }
    @JvmStatic external fun callNative(method: String, payload: String): String
    private val gate = Mutex()

    private fun raw(method: String, input: JSONObject = JSONObject()): JSONObject {
        val response = JSONObject(callNative(method, input.toString()))
        if (response.has("error")) throw MusicError(response.optString("error"), response.optInt("code"))
        return response
    }

    fun evaluate(method: String, input: JSONObject = JSONObject()): JSONObject = raw(method, input)

    suspend fun request(method: String, input: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        gate.withLock { raw(method, input) }
    }
}

class MusicError(message: String, val code: Int = 0) : Exception(message)