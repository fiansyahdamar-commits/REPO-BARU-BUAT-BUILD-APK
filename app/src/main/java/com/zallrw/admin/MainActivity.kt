package com.zallrw.admin

import android.app.Activity
import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val api = "https://rhdzwbrskonsinprsepr.supabase.co/functions/v1/admin-api"
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("zallrw_admin", Context.MODE_PRIVATE) }
    private var adminToken = ""
    private lateinit var status: TextView
    private lateinit var output: TextView
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adminToken = prefs.getString("session_token", "") ?: ""
        showLogin()
        if (adminToken.isNotBlank()) {
            request("GET", "/me", null, true) { _, err ->
                if (err == null) showPanel() else {
                    clearSession()
                    showLogin()
                }
            }
        }
    }

    private fun column(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(28, 28, 28, 28)
        setBackgroundColor(Color.rgb(10, 10, 10))
    }

    private fun label(value: String, size: Float = 16f, color: Int = Color.WHITE) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            setPadding(0, 8, 0, 8)
        }

    private fun field(hintText: String, numeric: Boolean = false): EditText =
        EditText(this).apply {
            hint = hintText
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            setSingleLine(true)
            if (numeric) inputType = 2
        }

    private fun button(caption: String, action: () -> Unit) =
        Button(this).apply {
            text = caption
            setOnClickListener { action() }
        }

    private fun install(page: LinearLayout) {
        root = page
        setContentView(ScrollView(this).apply {
            addView(page, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    private fun setStatus(message: String) {
        if (::status.isInitialized) status.text = message
    }

    private fun showLogin() {
        val page = column()
        page.addView(label("ZALL RW", 16f, Color.RED))
        page.addView(label("ADMIN PANEL", 29f))
        page.addView(label("Masukkan password panel admin."))
        val password = field("Password admin")
        password.inputType = 129
        page.addView(password)
        page.addView(button("MASUK") {
            val value = password.text.toString()
            if (value.isBlank()) {
                password.error = "Password wajib diisi"
                return@button
            }
            val body = JSONObject().put("password", value).put("deviceLabel", android.os.Build.MODEL)
            request("POST", "/login", body, false) { response, err ->
                if (err != null) {
                    setStatus("Login gagal: $err")
                } else {
                    try {
                        val token = JSONObject(response ?: "{}").getString("token")
                        adminToken = token
                        prefs.edit().putString("session_token", token).apply()
                        showPanel()
                    } catch (_: Exception) {
                        setStatus("Login gagal: respons server tidak valid")
                    }
                }
            }
        })
        status = label("")
        page.addView(status)
        install(page)
    }

    private fun showPanel() {
        val page = column()
        page.addView(label("ZALL RW ADMIN", 27f, Color.RED))
        page.addView(label("Backend Admin terpisah • sesi tersimpan 30 hari"))
        page.addView(button("DASHBOARD / STATISTIK") { load("/stats") })
        page.addView(button("DAFTAR REDEEM CODE") { load("/codes") })
        page.addView(button("RIWAYAT AKTIVASI") { load("/activations") })

        page.addView(label("BUAT REDEEM CODE", 21f, Color.RED))
        val mode = Spinner(this)
        mode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("SINGLE - 1 pengguna/perangkat", "MULTI - banyak pengguna"))
        page.addView(mode)
        val maxUses = field("Batas penggunaan untuk MULTI", true)
        maxUses.setText("10")
        page.addView(maxUses)
        val duration = Spinner(this)
        duration.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("7 hari", "30 hari", "90 hari", "Permanen"))
        page.addView(duration)
        val redeemPassword = field("Password redeem (minimal 6 karakter)")
        redeemPassword.inputType = 129
        page.addView(redeemPassword)
        page.addView(button("BUAT KODE") {
            val isSingle = mode.selectedItemPosition == 0
            val uses = if (isSingle) 1 else (maxUses.text.toString().toIntOrNull() ?: 0)
            if (uses < 1) {
                maxUses.error = "Batas penggunaan minimal 1"
                return@button
            }
            if (redeemPassword.text.length < 6) {
                redeemPassword.error = "Password minimal 6 karakter"
                return@button
            }
            val days: Any = when (duration.selectedItemPosition) {
                0 -> 7
                1 -> 30
                2 -> 90
                else -> JSONObject.NULL
            }
            val payload = JSONObject()
                .put("mode", if (isSingle) "SINGLE" else "MULTI")
                .put("max_uses", uses)
                .put("duration_days", days)
                .put("password", redeemPassword.text.toString())
            request("POST", "/codes", payload) { body, err -> showResult(body, err) }
        })

        page.addView(label("AKTIFKAN / NONAKTIFKAN KODE", 19f, Color.RED))
        val codeId = field("ID redeem code")
        page.addView(codeId)
        val active = Spinner(this)
        active.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Aktifkan", "Nonaktifkan"))
        page.addView(active)
        page.addView(button("SIMPAN STATUS KODE") {
            val id = codeId.text.toString().trim()
            if (id.isBlank()) {
                codeId.error = "ID kode wajib diisi"
                return@button
            }
            val payload = JSONObject().put("is_active", active.selectedItemPosition == 0)
            request("PATCH", "/codes/$id", payload) { body, err -> showResult(body, err) }
        })

        page.addView(button("MUAT ULANG DASHBOARD") { load("/stats") })
        page.addView(button("KUNCI PANEL / LOGOUT") {
            request("POST", "/logout", JSONObject(), true) { _, _ ->
                clearSession()
                showLogin()
            }
        })
        status = label("")
        output = label("Hasil server akan tampil di sini", 13f, Color.LTGRAY)
        page.addView(status)
        page.addView(output)
        install(page)
    }

    private fun clearSession() {
        adminToken = ""
        prefs.edit().remove("session_token").apply()
    }

    private fun load(path: String) {
        request("GET", path, null) { body, err -> showResult(body, err) }
    }

    private fun showResult(body: String?, err: String?) {
        runOnUiThread {
            setStatus(if (err == null) "Berhasil" else "Gagal: $err")
            if (::output.isInitialized) output.text = body ?: ""
        }
    }

    private fun request(
        method: String,
        path: String,
        payload: JSONObject?,
        allowEmptyToken: Boolean = false,
        done: (String?, String?) -> Unit
    ) {
        runOnUiThread { setStatus("Memproses...") }
        executor.execute {
            try {
                val conn = (URL(api + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 20000
                    readTimeout = 20000
                    setRequestProperty("Accept", "application/json")
                    if (adminToken.isNotBlank() && !allowEmptyToken) {
                        setRequestProperty("Authorization", "Bearer $adminToken")
                    } else if (adminToken.isNotBlank() && (path == "/me" || path == "/logout")) {
                        setRequestProperty("Authorization", "Bearer $adminToken")
                    }
                    if (payload != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                    }
                }
                if (payload != null) {
                    conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                if (code !in 200..299) {
                    val message = try { JSONObject(body).optString("error", body) } catch (_: Exception) { body }
                    throw IllegalStateException("HTTP $code: $message")
                }
                runOnUiThread { done(body, null) }
            } catch (e: Exception) {
                runOnUiThread { done(null, e.message ?: "Kesalahan jaringan") }
            }
        }
    }
}
