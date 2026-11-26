package com.zallrw.admin.v3

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Intent
import android.provider.Settings
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val deviceLockRequest = 2301
    private val api = "https://vpcavoumahovwzeypxqa.supabase.co/functions/v1/admin-api"
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
        setContentView(LinearLayout(this).apply {
            setBackgroundColor(Color.rgb(10, 10, 10))
            gravity = Gravity.CENTER
            addView(TextView(this@MainActivity).apply {
                text = "ZALL RW ADMIN V3"
                setTextColor(Color.RED)
                textSize = 22f
            })
        })
        request("GET", "/status", null) { response, err ->
            if (err != null) {
                showLogin()
                setStatus("Tidak dapat memeriksa status setup: $err")
            } else {
                val setupRequired = try { JSONObject(response ?: "{}").optBoolean("setup_required", true) } catch (_: Exception) { true }
                if (setupRequired) {
                    showSetup()
                } else if (adminToken.isNotBlank()) {
                    request("GET", "/me", null) { _, sessionError ->
                        if (sessionError == null) unlockWithDeviceCredential() else {
                            clearSession()
                            showLogin()
                            setStatus("Sesi perlu login ulang.")
                        }
                    }
                } else {
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


    private fun showSetup() {
        val page = column()
        page.addView(label("ZALL RW", 16f, Color.RED))
        page.addView(label("SETUP ADMIN V3", 28f))
        page.addView(label("Setup satu kali. Masukkan token setup, lalu buat password admin baru minimal 12 karakter."))
        val setupToken = field("Token setup")
        page.addView(setupToken)
        val password = field("Password admin baru")
        password.inputType = 129
        page.addView(password)
        val confirm = field("Ulangi password admin")
        confirm.inputType = 129
        page.addView(confirm)
        page.addView(button("AKTIFKAN PANEL ADMIN") {
            val tokenValue = setupToken.text.toString().trim()
            val passValue = password.text.toString()
            if (tokenValue.isBlank()) {
                setupToken.error = "Token setup wajib diisi"
                return@button
            }
            if (passValue.length < 12) {
                password.error = "Minimal 12 karakter"
                return@button
            }
            if (passValue != confirm.text.toString()) {
                confirm.error = "Password tidak sama"
                return@button
            }
            request("POST", "/setup", JSONObject().put("setup_token", tokenValue).put("password", passValue)) { _, err ->
                if (err != null) {
                    setStatus("Setup gagal: $err")
                } else {
                    showLogin()
                    setStatus("Setup berhasil. Silakan login dengan password baru.")
                }
            }
        })
        status = label("")
        page.addView(status)
        install(page)
    }

    private fun showLogin() {
        val page = column()
        page.addView(label("ZALL RW", 16f, Color.RED))
        page.addView(label("ADMIN • V3.0", 30f))
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
                        unlockWithDeviceCredential()
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


    private fun unlockWithDeviceCredential() {
        if (android.os.Build.VERSION.SDK_INT < 23) {
            showPanel()
            return
        }
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure) {
            AlertDialog.Builder(this)
                .setTitle("Kunci layar diperlukan")
                .setMessage("Aktifkan PIN, pola, atau sandi layar perangkat agar panel admin tetap terlindungi.")
                .setCancelable(false)
                .setPositiveButton("BUKA PENGATURAN") { _, _ ->
                    startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                }
                .setNegativeButton("KELUAR") { _, _ ->
                    clearSession()
                    showLogin()
                }
                .show()
            return
        }
        val intent = keyguard.createConfirmDeviceCredentialIntent("Buka Zall RW Admin", "Verifikasi PIN, pola, atau sandi perangkat")
        if (intent == null) {
            showPanel()
        } else {
            startActivityForResult(intent, deviceLockRequest)
        }
    }

    @Deprecated("Deprecated in Android API; retained for compatibility with minSdk 23")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == deviceLockRequest) {
            if (resultCode == Activity.RESULT_OK) {
                showPanel()
            } else {
                showDeviceLocked()
            }
        }
    }

    private fun showDeviceLocked() {
        val page = column()
        page.addView(label("ZALL RW ADMIN", 25f, Color.RED))
        page.addView(label("Panel terkunci. Verifikasi kunci layar untuk melanjutkan."))
        page.addView(button("BUKA PANEL") { unlockWithDeviceCredential() })
        page.addView(button("KELUAR DARI AKUN") {
            request("POST", "/lock", JSONObject()) { _, _ ->
                clearSession()
                showLogin()
            }
        })
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
            listOf("1 hari", "2 hari", "3 hari", "7 hari", "30 hari", "90 hari", "365 hari", "Permanen")
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
                0 -> 1
                1 -> 2
                2 -> 3
                3 -> 7
                4 -> 30
                5 -> 90
                6 -> 365
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
                        passwordResult.text = data.optString("password", "-")
                        resultBox.visibility = View.VISIBLE
                        setStatus("Kode dan password berhasil dibuat. Berikan keduanya kepada pengguna.")
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
        page.addView(button("RIWAYAT AKTIVASI") { loadActivations() })
        page.addView(button("KUNCI PANEL") {
            request("POST", "/lock", JSONObject()) { _, err ->
                if (err != null) {
                    setStatus("Gagal mengunci panel: $err")
                } else {
                    clearSession()
                    showLogin()
                }
            }
        })
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
                val list = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(4, 4, 4, 4)
                }
                val scroll = ScrollView(this).apply {
                    addView(list, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                var listDialog: AlertDialog? = null

                if (array == null || array.length() == 0) {
                    list.addView(label("Belum ada kode redeem.", 15f, Color.LTGRAY))
                } else {
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val id = item.optString("id")
                        val hint = item.optString("code_hint", "------")
                        val codeMode = item.optString("mode", "SINGLE")
                        val max = item.optInt("max_uses", 1)
                        val durationDays = if (item.isNull("duration_days")) {
                            "Permanen"
                        } else {
                            "${item.optInt("duration_days")} hari"
                        }
                        val expires = item.optString("expires_at", "")
                        val revoked = item.optString("status") == "revoked"
                        val active = item.optBoolean("is_active") && !revoked
                        val state = when {
                            revoked -> "DICABUT"
                            active -> "AKTIF"
                            else -> "NONAKTIF"
                        }
                        val details = TextView(this).apply {
                            text = "••••$hint  •  $codeMode  •  $state\nBatas: $max perangkat  •  Durasi: $durationDays" +
                                if (expires.isBlank()) "" else "\nBatas tanggal: $expires"
                            textSize = 13f
                            setTextColor(Color.WHITE)
                            setPadding(0, 10, 10, 10)
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        val action = Button(this).apply {
                            text = if (active) "NONAKTIFKAN" else "AKTIFKAN"
                            isEnabled = !revoked
                            textSize = 10f
                            setPadding(8, 6, 8, 6)
                        }
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(4, 6, 4, 6)
                            addView(details)
                            addView(action, LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ))
                        }
                        list.addView(row)
                        if (i < array.length() - 1) {
                            val divider = View(this).apply {
                                setBackgroundColor(Color.DKGRAY)
                            }
                            list.addView(divider, LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, 1
                            ))
                        }
                        action.setOnClickListener {
                            val enable = !active
                            AlertDialog.Builder(this)
                                .setTitle(if (enable) "Aktifkan kode?" else "Nonaktifkan kode?")
                                .setMessage("Kode ••••$hint akan ${if (enable) "diaktifkan" else "dinonaktifkan"}.")
                                .setNegativeButton("Batal", null)
                                .setPositiveButton("Ya") { _, _ ->
                                    request(
                                        "PATCH",
                                        "/codes/$id",
                                        JSONObject().put("is_active", enable)
                                    ) { _, updateError ->
                                        if (updateError != null) {
                                            setStatus("Gagal mengubah status: $updateError")
                                        } else {
                                            setStatus(if (enable) "Kode berhasil diaktifkan." else "Kode berhasil dinonaktifkan.")
                                            listDialog?.dismiss()
                                            loadCodes()
                                        }
                                    }
                                }
                                .show()
                        }
                    }
                }

                listDialog = AlertDialog.Builder(this)
                    .setTitle("Daftar Kode Redeem")
                    .setView(scroll)
                    .setPositiveButton("Tutup", null)
                    .create()
                listDialog?.show()
                setStatus("Daftar kode diperbarui")
            } catch (_: Exception) {
                setStatus("Data daftar kode tidak valid")
            }
        }
    }

    private fun loadActivations() {
        request("GET", "/activations", null) { body, err ->
            if (err != null) {
                setStatus("Gagal memuat riwayat: $err")
                return@request
            }
            try {
                val rows = JSONObject(body ?: "{}").optJSONArray("activations")
                val lines = mutableListOf<String>()
                if (rows != null) {
                    for (i in 0 until rows.length()) {
                        val item = rows.getJSONObject(i)
                        lines.add(
                            "${item.optString("redeemed_at")} • Kode #${item.optString("code_id")}\n" +
                            "Perangkat: ${item.optString("device_id")}\n" +
                            "Berlaku sampai: ${item.optString("expires_at", "Permanen")}"
                        )
                    }
                }
                AlertDialog.Builder(this)
                    .setTitle("Riwayat Aktivasi")
                    .setMessage(if (lines.isEmpty()) "Belum ada aktivasi." else lines.joinToString("\n\n"))
                    .setPositiveButton("Tutup", null)
                    .show()
                setStatus("Riwayat aktivasi diperbarui")
            } catch (_: Exception) {
                setStatus("Data riwayat tidak valid")
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
                if (code == 401 && adminToken.isNotBlank() && path != "/login") {
                    runOnUiThread {
                        clearSession()
                        showLogin()
                        setStatus("Sesi admin berakhir. Silakan masuk kembali.")
                        done(null, "Sesi admin berakhir. Silakan masuk kembali.")
                    }
                    return@execute
                }
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
