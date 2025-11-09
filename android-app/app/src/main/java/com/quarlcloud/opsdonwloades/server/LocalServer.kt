package com.quarlcloud.opsdonwloades.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.gson.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

object LocalServer {
    private var server: NettyApplicationEngine? = null
    private const val TAG = "LocalServer"
    private const val PORT = 8080

    fun start() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                server = embeddedServer(Netty, port = PORT) {
                    install(ContentNegotiation) {
                        gson()
                    }

                    routing {
                        post("/download") {
                            try {
                                val params = call.receive<Map<String, String>>()
                                val url = params["url"] ?: run {
                                    call.respond(mapOf("error" to "No URL provided"))
                                    return@post
                                }

                                Log.d(TAG, "Processing URL: $url")

                                val client = HttpClient(CIO) {
                                    engine {
                                        requestTimeout = 30000
                                    }
                                }

                                try {
                                    // Hacer request a TikTok
                                    val response: HttpResponse = client.get(url) {
                                        headers {
                                            append("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                        }
                                    }
                                    
                                    val html = response.bodyAsText()
                                    Log.d(TAG, "HTML length: ${html.length}")

                                    // Buscar diferentes patrones de video URL
                                    val patterns = listOf(
                                        Regex("\"playAddr\":\"(.*?)\""),
                                        Regex("\"downloadAddr\":\"(.*?)\""),
                                        Regex("\"play_addr\":\\{\"url_list\":\\[\"(.*?)\"\\]"),
                                        Regex("\"download_addr\":\\{\"url_list\":\\[\"(.*?)\"\\]")
                                    )

                                    var videoUrl: String? = null
                                    for (pattern in patterns) {
                                        val match = pattern.find(html)
                                        if (match != null) {
                                            videoUrl = match.groups[1]?.value?.replace("\\u0026", "&")
                                            if (videoUrl != null) {
                                                Log.d(TAG, "Found video URL with pattern: $pattern")
                                                break
                                            }
                                        }
                                    }

                                    if (videoUrl != null) {
                                        Log.d(TAG, "Video URL found: $videoUrl")
                                        call.respond(mapOf(
                                            "success" to true,
                                            "videoUrl" to videoUrl,
                                            "title" to extractTitle(html)
                                        ))
                                    } else {
                                        Log.w(TAG, "No video URL found in HTML")
                                        call.respond(mapOf(
                                            "success" to false,
                                            "error" to "No se encontró el enlace del video"
                                        ))
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error processing request: ${e.message}", e)
                                    call.respond(mapOf(
                                        "success" to false,
                                        "error" to "Error al procesar el video: ${e.message}"
                                    ))
                                } finally {
                                    client.close()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in /download endpoint: ${e.message}", e)
                                call.respond(mapOf(
                                    "success" to false,
                                    "error" to "Error interno del servidor: ${e.message}"
                                ))
                            }
                        }

                        get("/status") {
                            call.respond(mapOf(
                                "status" to "running",
                                "port" to PORT,
                                "message" to "TikTok Downloader Local Server"
                            ))
                        }
                    }
                }.start(wait = false)

                Log.i(TAG, "Local server started on port $PORT")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server: ${e.message}", e)
            }
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        Log.i(TAG, "Local server stopped")
    }

    private fun extractTitle(html: String): String {
        val titlePatterns = listOf(
            Regex("<title>(.*?)</title>"),
            Regex("\"desc\":\"(.*?)\""),
            Regex("\"title\":\"(.*?)\"")
        )

        for (pattern in titlePatterns) {
            val match = pattern.find(html)
            if (match != null) {
                val title = match.groups[1]?.value
                if (!title.isNullOrBlank() && title.length > 5) {
                    return title.take(50) // Limitar a 50 caracteres
                }
            }
        }
        
        return "TikTok Video ${System.currentTimeMillis()}"
    }
}