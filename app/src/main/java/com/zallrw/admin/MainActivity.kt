package com.zallrw.admin

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
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
    private lateinit var resultBox: LinearLayout
    private lateinit var codeResult: TextView
    private lateinit var passwordResult: TextView
    private lateinit var maxUses: EditText
    private lateinit var mode: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adminToken = prefs.getString("session_token", "") ?: ""
        showLogin()
        if (adminToken.isNotBlank()) {
            request("GET", "/me", null) { _, err ->
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
        page.addView(label("ADMIN", 30f))
        page.addView(label("Masukkan password admin."))
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
            request("POST", "/login", body) { response, err ->
                if (err != null) {
                    setStatus("Login gagal: $err")
                } else {
                    try {
                        adminToken = JSONObject(response ?: "{}").getString("token")
                        prefs.edit().putString("session_token", adminToken).apply()
                        showPanel()
                    } catch (_: Exception) {
                        setStatus("Respons login tidak valid")
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
        page.addView(label("BUAT KODE REDEEM", 19f, Color.WHITE))

        mode = Spinner(this)
        mode.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("SINGLE • 1 perangkat", "MULTI • banyak perangkat")
        )
        page.addView(label("Jenis kode"))
        page.addView(mode)

        page.addView(label("Batas perangkat / pengguna"))
        maxUses = field("Contoh: 10", true)
        maxUses.setText("10")
        page.addView(maxUses)
        mode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                maxUses.visibility = if (position == 0) View.GONE else View.VISIBLE
            }
        }

        page.addView(label("Masa aktif"))
        val duration = Spinner(this)
        duration.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("7 hari", "30 hari", "90 hari", "Permanen")
        )
        page.addView(duration)

        page.addView(button("BUAT KODE OTOMATIS") {
            val single = mode.selectedItemPosition == 0
            val uses = if (single) 1 else (maxUses.text.toString().toIntOrNull() ?: 0)
            if (uses < 1) {
                maxUses.error = "Masukkan batas minimal 1"
                return@button
            }
            val days: Any = when (duration.selectedItemPosition) {
                0 -> 7
                1 -> 30
                2 -> 90
                else -> JSONObject.NULL
            }
            val payload = JSONObject()
                .put("mode", if (single) "SINGLE" else "MULTI")
                .put("max_uses", uses)
                .put("duration_days", days)
            setStatus("Sedang membuat kode...")
            request("POST", "/codes", payload) { body, err ->
                if (err != null) {
                    setStatus("Gagal membuat kode: $err")
                } else {
                    try {
                        val data = JSONObject(body ?: "{}")
                        codeResult.text = data.optString("code", "-")
                        passwordResult.text = data.optString("redeem_password", "-")
                        resultBox.visibility = View.VISIBLE
                        setStatus("Kode berhasil dibuat. Simpan keduanya.")
                    } catch (_: Exception) {
                        setStatus("Respons pembuatan kode tidak valid")
                    }
                }
            }
        })

        resultBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 18, 0, 18)
            visibility = View.GONE
        }
        resultBox.addView(label("KODE REDEEM", 14f, Color.LTGRAY))
        codeResult = label("-", 19f, Color.WHITE)
        resultBox.addView(codeResult)
        resultBox.addView(button("SALIN KODE") { copy(codeResult.text.toString()) })
        resultBox.addView(label("PASSWORD REDEEM", 14f, Color.LTGRAY))
        passwordResult = label("-", 19f, Color.WHITE)
        resultBox.addView(passwordResult)
        resultBox.addView(button("SALIN PASSWORD") { copy(passwordResult.text.toString()) })
        page.addView(resultBox)

        page.addView(button("DAFTAR KODE") { loadCodes() })
        status = label("")
        page.addView(status)
        install(page)
    }

    private fun copy(value: String) {
        if (value.isBlank() || value == "-") return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Zall RW", value))
        setStatus("Berhasil disalin")
    }

    private fun loadCodes() {
        request("GET", "/codes", null) { body, err ->
            if (err != null) {
                setStatus("Gagal memuat kode: $err")
                return@request
            }
            try {
                val array = JSONObject(body ?: "{}").optJSONArray("codes")
                val lines = mutableListOf<String>()
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        lines.add(
                            "${item.optString("code_hint")} • ${item.optString("mode")} • " +
                            "${if (item.optBoolean("is_active")) "AKTIF" else "NONAKTIF"}"
                        )
                    }
                }
                val message = if (lines.isEmpty()) "Belum ada kode." else lines.joinToString("\n")
                AlertDialog.Builder(this)
                    .setTitle("Daftar Kode")
                    .setMessage(message)
                    .setPositiveButton("Tutup", null)
                    .show()
                setStatus("Daftar kode diperbarui")
            } catch (_: Exception) {
                setStatus("Data daftar kode tidak valid")
            }
        }
    }

    private fun clearSession() {
        adminToken = ""
        prefs.edit().remove("session_token").apply()
    }

    private fun request(
        method: String,
        path: String,
        payload: JSONObject?,
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
                    if (adminToken.isNotBlank()) setRequestProperty("Authorization", "Bearer $adminToken")
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
