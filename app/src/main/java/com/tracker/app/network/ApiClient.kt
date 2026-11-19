package com.tracker.app.network

import com.tracker.app.BuildConfig
import com.tracker.app.models.*
import com.tracker.app.utils.Prefs
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Blocking REST client; call from a background thread. */
object ApiClient {
    private val http = OkHttpClient()
    private val JSON = "application/json".toMediaType()

    class ApiError(val code: Int, message: String) : IOException(message)

    private fun call(method: String, path: String, body: JSONObject? = null, auth: Boolean = true): JSONObject? {
        val b = Request.Builder().url(BuildConfig.BASE_URL + "/api" + path)
        if (auth) Prefs.token?.let { b.header("Authorization", "Bearer $it") }
        b.method(method, if (body != null) body.toString().toRequestBody(JSON) else if (method == "GET" || method == "DELETE") null else "".toRequestBody(JSON))
        http.newCall(b.build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiError(r.code, runCatching { JSONObject(text).getString("error") }.getOrDefault("HTTP ${r.code}"))
            return if (text.isBlank()) null else JSONObject(text)
        }
    }
    private fun saveSession(o: JSONObject) {
        Prefs.token = o.getString("token"); val u = o.getJSONObject("user")
        Prefs.userId = u.getString("id"); Prefs.userName = u.getString("displayName")
    }
    fun register(email: String, pw: String, name: String) =
        saveSession(call("POST", "/auth/register", JSONObject().put("email", email).put("password", pw).put("displayName", name), false)!!)
    fun login(email: String, pw: String) =
        saveSession(call("POST", "/auth/login", JSONObject().put("email", email).put("password", pw), false)!!)
    fun createGroup(name: String) = parseGroup(call("POST", "/groups", JSONObject().put("name", name))!!)
    fun joinGroup(code: String) = parseGroup(call("POST", "/groups/join", JSONObject().put("code", code))!!)
    fun getGroup(id: String) = parseGroup(call("GET", "/groups/$id")!!)
    fun patchGroup(id: String, patch: JSONObject) = parseGroup(call("PATCH", "/groups/$id", patch)!!)
    fun kick(groupId: String, userId: String) { call("DELETE", "/groups/$groupId/members/$userId") }
    fun endGroup(id: String) { call("POST", "/groups/$id/end") }
    fun deleteAccount() { call("DELETE", "/me") }

    fun parseGroup(o: JSONObject): Group {
        val ms = o.getJSONArray("members")
        return Group(o.getString("id"), o.getString("name"), o.getString("inviteCode"),
            o.optString("captainId").ifEmpty { null }.takeIf { !o.isNull("captainId") },
            o.optString("sweeperId").ifEmpty { null }.takeIf { !o.isNull("sweeperId") },
            o.getInt("thresholdM"),
            (0 until ms.length()).map { ms.getJSONObject(it) }.map { Member(it.getString("id"), it.getString("displayName"), it.optString("photoUrl").ifEmpty { null }) })
    }
}
