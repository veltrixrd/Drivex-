package com.drivex.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val BG = Color.parseColor("#000000")
    private val CARD = Color.parseColor("#0D1320")
    private val BLUE = Color.parseColor("#1F6BFF")
    private val GRAY = Color.parseColor("#8A94A6")

    private lateinit var etPrice: EditText
    private lateinit var etKm: EditText
    private lateinit var etPerKm: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvLast: TextView
    private lateinit var btnToggle: Button
    private lateinit var btnAccess: Button
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 1000) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, r: Int = 16) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat()
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = s; setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun field(parent: LinearLayout, label: String, hint: String, value: Float): EditText {
        parent.addView(text(label, 13f, GRAY).apply { setPadding(0, dp(14), 0, dp(6)) })
        val et = EditText(this).apply {
            setHintTextColor(Color.parseColor("#4A5366"))
            setTextColor(Color.WHITE)
            this.hint = hint
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            background = rounded(Color.parseColor("#141C2E"), 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            if (value > 0f) setText(if (value % 1f == 0f) value.toInt().toString() else value.toString())
        }
        parent.addView(et, LinearLayout.LayoutParams(-1, -2))
        return et
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(36), dp(20), dp(28))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG); isFillViewport = true; addView(root)
        })

        root.addView(text("DRIVEX", 36f, BLUE, true).apply { letterSpacing = 0.12f })
        root.addView(text("Asistente para conductores de inDrive", 14f, GRAY))

        // Estado
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(CARD)
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        tvStatus = text("", 18f, Color.WHITE, true)
        tvLast = text("", 13f, GRAY).apply { setPadding(0, dp(6), 0, 0) }
        card.addView(tvStatus); card.addView(tvLast)
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })

        // Filtros
        root.addView(text("FILTROS", 12f, BLUE, true).apply { setPadding(0, dp(26), 0, 0) })
        etPrice = field(root, "Precio mínimo", "0 = sin mínimo", Prefs.minPrice(this))
        etKm = field(root, "Distancia máxima (km)", "0 = sin límite", Prefs.maxKm(this))
        etPerKm = field(root, "Ganancia mínima por km", "0 = sin mínimo", Prefs.minPerKm(this))

        // Botones
        btnToggle = Button(this).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD; isAllCaps = false
            setOnClickListener { toggle() }
        }
        root.addView(btnToggle, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(26) })

        btnAccess = Button(this).apply {
            text = "Abrir ajustes de accesibilidad"
            setTextColor(BLUE); isAllCaps = false
            background = rounded(Color.parseColor("#0D1A33"), 16)
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        root.addView(btnAccess, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(12) })

        val btnRestricted = Button(this).apply {
            text = "Permitir ajustes restringidos (Android 13+)"
            setTextColor(GRAY); isAllCaps = false
            background = rounded(Color.parseColor("#0D1320"), 16)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
        root.addView(btnRestricted, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })

        root.addView(text(
            "DRIVEX solo avisa cuando un viaje cumple tus filtros. La aceptación es siempre manual.",
            12f, GRAY
        ).apply { gravity = Gravity.CENTER; setPadding(0, dp(20), 0, 0) })

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun readFloat(e: EditText) = e.text.toString().replace(',', '.').toFloatOrNull() ?: 0f

    private fun save() {
        Prefs.saveFilters(this, readFloat(etPrice), readFloat(etKm), readFloat(etPerKm))
    }

    private fun serviceEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val full = "$packageName/${DrivexService::class.java.name}"
        return s.split(':').any { it.equals(full, true) || it.equals("$packageName/.DrivexService", true) }
    }

    private fun toggle() {
        save()
        if (!serviceEnabled()) {
            Toast.makeText(this, "Primero activa DRIVEX en Accesibilidad", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        Prefs.setActive(this, !Prefs.isActive(this))
        refresh()
    }

    private fun refresh() {
        val enabled = serviceEnabled()
        val active = Prefs.isActive(this)
        when {
            !enabled -> { tvStatus.text = "Permiso de accesibilidad pendiente"; tvStatus.setTextColor(Color.parseColor("#FFB020")) }
            active -> { tvStatus.text = "ASISTENCIA ACTIVA"; tvStatus.setTextColor(BLUE) }
            else -> { tvStatus.text = "Asistencia detenida"; tvStatus.setTextColor(Color.WHITE) }
        }
        tvLast.text = "Última lectura: " + Prefs.lastEvent(this)
        btnToggle.text = if (active && enabled) "DETENER" else "ACTIVAR"
        btnToggle.background = rounded(if (active && enabled) Color.parseColor("#2A3248") else BLUE, 16)
        btnAccess.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    override fun onResume() { super.onResume(); handler.post(ticker) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(ticker); save() }
}
