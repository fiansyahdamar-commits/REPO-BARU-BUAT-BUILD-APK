package com.zallrw.admin

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val api = "https://zallrw-vip-backend.vercel.app"
    private val executor = Executors.newSingleThreadExecutor()
    private var adminKey = ""
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var keyField: EditText
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showLogin()
    }

    private fun baseColumn(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(24, 28, 24, 24)
        setBackgroundColor(Color.rgb(10, 10, 10))
    }

    private fun text(value: String, size: Float = 16f, color: Int = Color.WHITE): TextView =
        TextView(this).apply { text = value; textSize = size; setTextColor(color); setPadding(0, 8, 0, 8) }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply { text = label; setOnClickListener { action() } }

    private fun field(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        setHintTextColor(Color.LTGRAY)
        setTextColor(Color.WHITE)
        setSingleLine(true)
    }

    private fun install(view: LinearLayout) {
        root = view
        setContentView(ScrollView(this).apply { addView(view) })
    }

    private fun showLogin() {
        val page = baseColumn()
        page.addView(text("ZALL RW", 15f, Color.RED))
        page.addView(text("ADMIN PANEL", 30f))
        page.addView(text("Masukkan Admin Key backend untuk mengakses panel."))
        keyField = field("Admin Key")
        keyField.inputType = 129
        page.addView(keyField)
        page.addView(button("MASUK") {
            val candidate = keyField.text.toString().trim()
            if (candidate.isBlank()) { keyField.error = "Admin Key wajib diisi"; return@button }
            adminKey = candidate
            runRequest("GET", "/admin/stats", null) { _, err ->
                if (err == null) showPanel() else {
                    adminKey = ""
                    status.text = "Login gagal: $err"
                }
            }
        })
        status = text("")
        page.addView(status)
        install(page)
    }

    private fun showPanel() {
        val page = baseColumn()
        page.addView(text("ZALL RW ADMIN", 26f, Color.RED))
        page.addView(text("Terhubung ke backend Zall RW"))
        page.addView(button("Dashboard / Muat Statistik") { load("/admin/stats") })
        page.addView(button("Daftar Redeem Code") { load("/admin/codes") })
        page.addView(button("Riwayat Aktivasi") { load("/admin/activations") })
        page.addView(text("Buat kode", 21f))
        val prefix = field("Prefix (contoh ZALL)")
        val count = field("Jumlah kode")
        count.inputType = 2
        val days = field("Durasi hari (0 = permanen)")
        days.inputType = 2
        val uses = field("Batas pemakaian")
        uses.inputType = 2
        page.addView(prefix); page.addView(count); page.addView(days); page.addView(uses)
        page.addView(button("BUAT KODE") {
            val payload = JSONObject()
                .put("prefix", prefix.text.toString().ifBlank { "ZALL" })
                .put("count", count.text.toString().toIntOrNull() ?: 1)
                .put("duration_days", days.text.toString().toIntOrNull() ?: 30)
                .put("max_uses", uses.text.toString().toIntOrNull() ?: 1)
            runRequest("POST", "/admin/codes", payload) { body, err ->
                showResult(body, err)
            }
        })
        page.addView(button("KUNCI PANEL") { adminKey = ""; showLogin() })
        status = text("")
        output = text("Hasil API akan muncul di sini", 13f, Color.LTGRAY)
        page.addView(status); page.addView(output)
        install(page)
    }

    private fun load(path: String) {
        runRequest("GET", path, null) { body, err -> showResult(body, err) }
    }

    private fun showResult(body: String?, err: String?) {
        runOnUiThread {
            status.text = if (err == null) "Berhasil" else "Gagal: $err"
            output.text = body ?: ""
        }
    }

    private fun runRequest(method: String, path: String, payload: JSONObject?, done: (String?, String?) -> Unit) {
        status.text = "Memproses..."
        executor.execute {
            try {
                val conn = URL(api + path).openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("x-admin-key", adminKey)
                if (payload != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                if (code !in 200..299) throw IllegalStateException("HTTP $code: $body")
                runOnUiThread { done(body, null) }
            } catch (e: Exception) {
                runOnUiThread { done(null, e.message ?: "Kesalahan jaringan") }
            }
        }
    }
}
