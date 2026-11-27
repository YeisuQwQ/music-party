package dev.yiqidi.mobile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class PlatformEffects(
    private val prefs: YiqidiPrefs,
    private val scope: CoroutineScope,
    private val send: (String, JSONObject) -> Unit,
    private val share: (String) -> Boolean,
) {
    private val requests = mutableMapOf<String, Job>()
    private val disk = Channel<JSONObject>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (effect in disk) {
                val session = effect.optString("kind") == "session"
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        if (session) prefs.saveSession(effect.optString("value")) else prefs.saveLibrary(effect.getJSONObject("data").toString())
                    }.getOrDefault(false)
                }
                if (session) send("storageResult", args("tag" to effect.getLong("tag"), "ok" to ok))
                else if (!ok) send("platformError", args("message" to "未能保存曲库设置，请检查设备存储"))
            }
        }
    }

    suspend fun restore(): JSONObject = withContext(Dispatchers.IO) {
        args("saved" to runCatching { prefs.library() }.getOrDefault(JSONObject()), "session" to runCatching { prefs.session() }.getOrDefault(""))
    }

    fun apply(effect: JSONObject) {
        when (effect.optString("kind")) {
            "request" -> request(effect)
            "cancel" -> requests.remove(effect.getString("slot"))?.cancel()
            "persist", "session" -> disk.trySend(effect)
            "share" -> share(effect.getString("text"))
        }
    }

    private fun request(effect: JSONObject) {
        val slot = effect.getString("slot")
        val tag = effect.getLong("tag")
        requests.remove(slot)?.cancel()
        val task = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            delay(effect.optLong("delay").coerceAtLeast(0))
            val result = try {
                NativeBridge.request(effect.getString("method"), effect.optJSONObject("input") ?: JSONObject())
            } catch (error: CancellationException) {
                throw error
            } catch (error: MusicError) {
                args("error" to error.message, "code" to error.code)
            } catch (_: Exception) {
                args("error" to "读取音乐服务失败，请重试")
            }
            requests.remove(slot)
            send("work", args("tag" to tag, "result" to result))
        }
        requests[slot] = task
        task.start()
    }
}