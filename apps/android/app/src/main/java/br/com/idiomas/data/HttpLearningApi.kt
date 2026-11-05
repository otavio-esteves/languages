package br.com.idiomas.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class HttpLearningApi(private val baseUrl: String) : LearningApi, PracticeApi {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private suspend fun execute(path: String, token: String? = null, body: String? = null, delete: Boolean = false): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(baseUrl + path).header("Accept", "application/json")
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (body != null) builder.post(body.toRequestBody("application/json".toMediaType()))
        if (delete) builder.delete()
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw ApiFailure(response.code)
            response.body?.string() ?: ""
        }
    }
    override suspend fun login(email: String, password: String): LoginResponse = json.decodeFromString(execute("auth/token", body = json.encodeToString(LoginRequest(email.trim(), password))))
    override suspend fun catalog(token: String): CatalogResponse = json.decodeFromString(execute("catalog", token))
    override suspend fun logout(token: String) { execute("auth/token", token, delete = true) }
    private fun segment(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
    private suspend fun bytes(path: String, token: String, limit: Int): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl + path).header("Authorization", "Bearer $token").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ApiFailure(response.code)
            val body = response.body ?: error("Arquivo vazio")
            require(body.contentLength() <= limit) { "Arquivo acima do limite" }
            val output = java.io.ByteArrayOutputStream()
            body.byteStream().use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count == -1) break
                    require(output.size() + count <= limit) { "Arquivo acima do limite" }
                    output.write(buffer,0,count)
                }
            }
            output.toByteArray()
        }
    }
    override suspend fun previews(token: String): CatalogResponse = json.decodeFromString(execute("editorial/catalog", token))
    override suspend fun manifest(token: String, id: String, version: Int, preview: Boolean): PackageManifest = json.decodeFromString(execute("content-packages/${segment(id)}/manifest?version=$version&preview=${if(preview) 1 else 0}",token))
    override suspend fun document(token: String, hash: String) = bytes("content-snapshots/${segment(hash)}",token,2*1024*1024)
    override suspend fun asset(token: String, hash: String, id: String) = bytes("content-snapshots/${segment(hash)}/assets/${segment(id)}",token,16*1024*1024)
    override suspend fun send(token: String, kind: String, events: List<kotlinx.serialization.json.JsonObject>): BatchResult {
        require(kind == "attempt" || kind == "exposure")
        val body = kotlinx.serialization.json.buildJsonObject { put("events", kotlinx.serialization.json.JsonArray(events)) }.toString()
        return json.decodeFromString(execute("${kind}s/batch", token, body))
    }
    override suspend fun results(token: String, sessionId: String): RemoteSession = json.decodeFromString(execute("learning-sessions/${segment(sessionId)}", token))

}
