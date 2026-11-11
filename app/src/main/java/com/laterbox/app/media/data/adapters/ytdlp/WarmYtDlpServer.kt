package com.laterbox.app.media.data.adapters.ytdlp

import android.content.Context
import android.util.Log
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WarmYtDlpServer(
    private val appContext: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    private val tag = "WarmYtDlpServer"
    private val startMutex = Mutex()
    private val concurrencySemaphore = Semaphore(2)

    @Volatile private var daemonProcess: Process? = null
    @Volatile private var serverPort: Int? = null
    @Volatile private var serverToken: String? = null
    private val isRunning = AtomicBoolean(false)

    private val daemonScriptContent = """
import sys
import os
import json
import socket
import threading
import time
import secrets

ytdlp_path = sys.argv[1] if len(sys.argv) > 1 else None
if ytdlp_path and ytdlp_path not in sys.path:
    sys.path.insert(0, ytdlp_path)

try:
    import yt_dlp
except Exception as e:
    sys.stderr.write(f"FATAL: cannot import yt_dlp from {ytdlp_path}: {e}\n")
    sys.stderr.flush()
    sys.exit(1)

TOKEN = secrets.token_hex(16)
IDLE_TIMEOUT_SEC = 180

server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
server_sock.bind(("127.0.0.1", 0))
port = server_sock.getsockname()[1]
server_sock.listen(5)

print(f"READY port={port} token={TOKEN}", flush=True)

last_active = time.time()
active_reqs = set()
lock = threading.Lock()
running = True

def idle_checker():
    global running
    while running:
        time.sleep(5)
        with lock:
            if not active_reqs and (time.time() - last_active > IDLE_TIMEOUT_SEC):
                running = False
                try:
                    server_sock.close()
                except:
                    pass
                os._exit(0)

threading.Thread(target=idle_checker, daemon=True).start()

def run_extraction(req_id, url, options):
    start = time.time()
    try:
        args = list(options)
        if url:
            args.append(url)
        parser, opts, all_urls, ydl_opts = yt_dlp.parse_options(args)
        ydl_opts["quiet"] = True
        ydl_opts["no_warnings"] = True

        target_url = all_urls[0] if all_urls else url
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(target_url, download=False)
            sanitized = ydl.sanitize_info(info)
            elapsed_ms = int((time.time() - start) * 1000)
            return {
                "id": req_id,
                "success": True,
                "output": json.dumps(sanitized),
                "elapsed_ms": elapsed_ms
            }
    except Exception as e:
        elapsed_ms = int((time.time() - start) * 1000)
        return {
            "id": req_id,
            "success": False,
            "error": str(e),
            "elapsed_ms": elapsed_ms
        }

def client_worker(conn):
    global last_active
    try:
        r = conn.makefile("r", encoding="utf-8")
        w = conn.makefile("w", encoding="utf-8")
        for line in r:
            line = line.strip()
            if not line:
                continue
            with lock:
                last_active = time.time()
            try:
                req = json.loads(line)
            except Exception:
                continue

            if req.get("token") != TOKEN:
                w.write(json.dumps({"id": req.get("id"), "success": False, "error": "Unauthorized token"}) + "\n")
                w.flush()
                continue

            action = req.get("action", "inspect")
            req_id = req.get("id")

            if action == "ping":
                w.write(json.dumps({"id": req_id, "success": True, "pong": True}) + "\n")
                w.flush()
            elif action == "shutdown":
                w.write(json.dumps({"id": req_id, "success": True, "status": "bye"}) + "\n")
                w.flush()
                os._exit(0)
            elif action == "cancel":
                with lock:
                    active_reqs.discard(req_id)
                w.write(json.dumps({"id": req_id, "success": True, "cancelled": True}) + "\n")
                w.flush()
            elif action == "inspect":
                url = req.get("url")
                opts = req.get("options", [])
                with lock:
                    active_reqs.add(req_id)
                try:
                    res = run_extraction(req_id, url, opts)
                    w.write(json.dumps(res) + "\n")
                    w.flush()
                finally:
                    with lock:
                        active_reqs.discard(req_id)
                        last_active = time.time()
    except Exception:
        pass
    finally:
        try:
            conn.close()
        except:
            pass

while running:
    try:
        c, _ = server_sock.accept()
        threading.Thread(target=client_worker, args=(c,), daemon=True).start()
    except:
        if not running:
            break
""".trimIndent()

    suspend fun isFastEngineEnabled(): Boolean {
        return try {
            userPreferencesRepository.userPreferencesFlow.first().fastEngineEnabled
        } catch (_: Throwable) {
            true
        }
    }

    private fun ensureDaemonScript(): File {
        val scriptFile = File(appContext.filesDir, "ytdlp_daemon.py")
        if (!scriptFile.exists() || scriptFile.length() == 0L || scriptFile.readText() != daemonScriptContent) {
            scriptFile.writeText(daemonScriptContent)
        }
        return scriptFile
    }

    private suspend fun startDaemon(): Boolean = withContext(Dispatchers.IO) {
        startMutex.withLock {
            val proc = daemonProcess
            val port = serverPort
            val token = serverToken
            if (proc != null && proc.isAlive && port != null && token != null) {
                return@withContext true
            }

            try {
                val pythonPath = try {
                    val field = YoutubeDL::class.java.getDeclaredField("pythonPath")
                    field.isAccessible = true
                    field.get(null) as? File
                } catch (_: Throwable) { null }

                val ytdlpPath = try {
                    val field = YoutubeDL::class.java.getDeclaredField("ytdlpPath")
                    field.isAccessible = true
                    field.get(null) as? File
                } catch (_: Throwable) { null }

                val binDir = try {
                    val field = YoutubeDL::class.java.getDeclaredField("binDir")
                    field.isAccessible = true
                    field.get(null) as? File
                } catch (_: Throwable) { null }

                val envLd = try {
                    val field = YoutubeDL::class.java.getDeclaredField("ENV_LD_LIBRARY_PATH")
                    field.isAccessible = true
                    field.get(null) as? String
                } catch (_: Throwable) { null }

                val envSsl = try {
                    val field = YoutubeDL::class.java.getDeclaredField("ENV_SSL_CERT_FILE")
                    field.isAccessible = true
                    field.get(null) as? String
                } catch (_: Throwable) { null }

                val envPyHome = try {
                    val field = YoutubeDL::class.java.getDeclaredField("ENV_PYTHONHOME")
                    field.isAccessible = true
                    field.get(null) as? String
                } catch (_: Throwable) { null }

                val tmpDir = try {
                    val field = YoutubeDL::class.java.getDeclaredField("TMPDIR")
                    field.isAccessible = true
                    field.get(null) as? String
                } catch (_: Throwable) { null }

                if (pythonPath == null || ytdlpPath == null) {
                    Log.w(tag, "Cannot start daemon: pythonPath or ytdlpPath is null")
                    return@withContext false
                }

                val scriptFile = ensureDaemonScript()
                val pb = ProcessBuilder(listOf(pythonPath.absolutePath, scriptFile.absolutePath, ytdlpPath.absolutePath))
                val env = pb.environment()
                if (envLd != null) env["LD_LIBRARY_PATH"] = envLd
                if (envSsl != null) env["SSL_CERT_FILE"] = envSsl
                if (envPyHome != null) {
                    env["PYTHONHOME"] = envPyHome
                    env["HOME"] = envPyHome
                }
                if (tmpDir != null) env["TMPDIR"] = tmpDir
                if (binDir != null) {
                    val path = System.getenv("PATH") ?: ""
                    env["PATH"] = "$path:${binDir.absolutePath}"
                }

                val newProc = pb.start()
                val reader = BufferedReader(InputStreamReader(newProc.inputStream))

                // Consume stderr in background so buffer doesn't block process
                Thread({
                    try {
                        val errReader = BufferedReader(InputStreamReader(newProc.errorStream))
                        var errLine: String?
                        while (errReader.readLine().also { errLine = it } != null) {
                            Log.d(tag, "daemon stderr: $errLine")
                        }
                    } catch (_: Throwable) {}
                }, "ytdlp-daemon-stderr").start()

                var readyLine: String? = null
                val deadline = System.currentTimeMillis() + 5000L
                while (System.currentTimeMillis() < deadline) {
                    if (reader.ready()) {
                        readyLine = reader.readLine()
                        break
                    }
                    Thread.sleep(50)
                }

                if (readyLine != null && readyLine.startsWith("READY ")) {
                    val parts = readyLine.substring(6).split(" ")
                    var parsedPort: Int? = null
                    var parsedToken: String? = null
                    for (part in parts) {
                        if (part.startsWith("port=")) {
                            parsedPort = part.substring(5).toIntOrNull()
                        } else if (part.startsWith("token=")) {
                            parsedToken = part.substring(6)
                        }
                    }
                    if (parsedPort != null && parsedToken != null) {
                        daemonProcess = newProc
                        serverPort = parsedPort
                        serverToken = parsedToken
                        isRunning.set(true)
                        Log.i(tag, "Warm yt-dlp daemon started on port $parsedPort")
                        return@withContext true
                    }
                }

                Log.w(tag, "Daemon handshake failed: readyLine='$readyLine'")
                newProc.destroy()
                false
            } catch (t: Throwable) {
                Log.w(tag, "Failed to start warm daemon: ${t.message}", t)
                false
            }
        }
    }

    /**
     * Executes inspection via warm loopback daemon.
     * Returns JSON string if successful, or null if warm engine is disabled/unavailable (to trigger fallback).
     * Throws YoutubeDLException if yt-dlp returns an error message.
     */
    suspend fun execute(
        url: String,
        options: List<String>,
        processId: String,
        timeoutMs: Long
    ): String? = withContext(Dispatchers.IO) {
        if (!isFastEngineEnabled()) {
            return@withContext null
        }

        concurrencySemaphore.withPermit {
            if (!startDaemon()) {
                return@withContext null
            }

            val port = serverPort ?: return@withContext null
            val token = serverToken ?: return@withContext null

            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress("127.0.0.1", port), 2000)
                socket.soTimeout = timeoutMs.coerceAtLeast(1000L).toInt()

                val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))

                val reqObj = JSONObject().apply {
                    put("id", processId)
                    put("token", token)
                    put("action", "inspect")
                    put("url", url)
                    val optsArr = JSONArray()
                    options.forEach { optsArr.put(it) }
                    put("options", optsArr)
                }

                writer.println(reqObj.toString())

                val respLine = reader.readLine() ?: throw java.io.EOFException("Premature EOF from daemon")
                val respObj = JSONObject(respLine)

                val success = respObj.optBoolean("success", false)
                if (success) {
                    respObj.optString("output")
                } else {
                    val error = respObj.optString("error", "Unknown extraction error")
                    throw YoutubeDLException(error)
                }
            } catch (te: SocketTimeoutException) {
                throw te
            } catch (ye: YoutubeDLException) {
                throw ye
            } catch (t: Throwable) {
                Log.w(tag, "Warm daemon execution failed: ${t.message}. Triggering fallback.", t)
                shutdown()
                null
            } finally {
                try {
                    socket?.close()
                } catch (_: Throwable) {}
            }
        }
    }

    fun cancel(processId: String) {
        val port = serverPort ?: return
        val token = serverToken ?: return
        Thread({
            try {
                Socket().use { sock ->
                    sock.connect(InetSocketAddress("127.0.0.1", port), 1000)
                    sock.soTimeout = 1000
                    val writer = PrintWriter(OutputStreamWriter(sock.getOutputStream(), "UTF-8"), true)
                    val cancelReq = JSONObject().apply {
                        put("id", processId)
                        put("token", token)
                        put("action", "cancel")
                    }
                    writer.println(cancelReq.toString())
                }
            } catch (_: Throwable) {}
        }, "ytdlp-daemon-cancel").start()
    }

    fun shutdown() {
        val proc = daemonProcess
        val port = serverPort
        val token = serverToken
        daemonProcess = null
        serverPort = null
        serverToken = null
        isRunning.set(false)

        if (port != null && token != null) {
            Thread({
                try {
                    Socket().use { sock ->
                        sock.connect(InetSocketAddress("127.0.0.1", port), 1000)
                        val writer = PrintWriter(OutputStreamWriter(sock.getOutputStream(), "UTF-8"), true)
                        writer.println(JSONObject().put("action", "shutdown").put("token", token).toString())
                    }
                } catch (_: Throwable) {}
            }, "ytdlp-daemon-shutdown").start()
        }

        try {
            proc?.destroy()
        } catch (_: Throwable) {}
    }
}
