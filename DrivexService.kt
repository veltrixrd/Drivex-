package com.drivex.app

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Lee la pantalla de inDrive (solo lectura), evalúa los filtros y AVISA al conductor.
 * No realiza toques ni acepta viajes: la aceptación siempre es manual.
 */
class DrivexService : AccessibilityService() {

    companion object {
        const val CHANNEL = "drivex_alerts"

        private val PRICE = Regex(
            """(?i)(?:S/\.?|\$|₡|₲|Bs\.?)\s*([0-9][0-9.,]*)|([0-9][0-9.,]*)\s*(?:PEN|COP|MXN|ARS|CLP|BOB|USD|PYG|CRC|GTQ|HNL|BRL|UYU|DOP)"""
        )
        private val KM = Regex("""(?i)([0-9]+(?:[.,][0-9]+)?)\s?km""")
        private val CANCEL = Regex("""(?i)(cancelad[oa]|cancel[óo]|cancell?ed)""")
    }

    private var lastSig = ""
    private var lastSigTime = 0L
    private var lastCancelTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL, "Alertas DRIVEX", NotificationManager.IMPORTANCE_HIGH)
        ch.enableVibration(true)
        ch.vibrationPattern = longArrayOf(0, 300, 150, 300)
        nm.createNotificationChannel(ch)
    }

    override fun onInterrupt() {}

    private fun isInDrive(pkg: String) =
        pkg.contains("indriver", ignoreCase = true) || pkg.startsWith("sinet.startup", ignoreCase = true)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (!isInDrive(pkg)) return
        if (!Prefs.isActive(this)) return

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString()?.let { isInDrive(it) } != true) return

        val texts = ArrayList<String>()
        collect(root, texts, 0)
        val all = texts.joinToString(" | ")
        val now = System.currentTimeMillis()

        // Cancelación del pasajero (solo aviso)
        if (CANCEL.containsMatchIn(all) && now - lastCancelTime > 20_000) {
            lastCancelTime = now
            Prefs.setLast(this, "Pasajero canceló el viaje")
            alert(2, "DRIVEX: el pasajero canceló", "Vuelve a la lista de solicitudes en inDrive.")
            return
        }

        // Solicitud de viaje
        val pm = PRICE.find(all) ?: return
        val price = parseNum(pm.groupValues[1].ifEmpty { pm.groupValues[2] }, true) ?: return
        val km = KM.findAll(all).mapNotNull { parseNum(it.groupValues[1], false) }.sum()

        val minPrice = Prefs.minPrice(this)
        val maxKm = Prefs.maxKm(this)
        val minPerKm = Prefs.minPerKm(this)

        val perKm = if (km > 0) price / km else 0.0
        val okPrice = price >= minPrice
        val okKm = maxKm <= 0f || (km > 0 && km <= maxKm)
        val okPerKm = minPerKm <= 0f || (km > 0 && perKm >= minPerKm)
        val ok = okPrice && okKm && okPerKm

        val sig = "$price|$km"
        if (sig == lastSig && now - lastSigTime < 15_000) return
        lastSig = sig
        lastSigTime = now

        val resumen = "%.2f · %.1f km · %.2f/km".format(price, km, perKm)
        Prefs.setLast(this, (if (ok) "CUMPLE: " else "No cumple: ") + resumen)
        if (ok) {
            alert(1, "DRIVEX: viaje que cumple tus filtros", "$resumen. Acéptalo manualmente en inDrive.")
        }
    }

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (n == null || depth > 30) return
        n.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        n.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        for (i in 0 until n.childCount) collect(n.getChild(i), out, depth + 1)
    }

    private fun parseNum(raw: String, thousands: Boolean): Double? {
        val s0 = raw.trim().trimEnd('.', ',')
        if (s0.isEmpty()) return null
        val ld = s0.lastIndexOf('.')
        val lc = s0.lastIndexOf(',')
        val s = when {
            ld >= 0 && lc >= 0 ->
                if (ld > lc) s0.replace(",", "") else s0.replace(".", "").replace(',', '.')
            ld >= 0 || lc >= 0 -> {
                val i = maxOf(ld, lc)
                val dec = s0.length - i - 1
                if (thousands && dec == 3) s0.replace(".", "").replace(",", "")
                else s0.replace(',', '.')
            }
            else -> s0
        }
        return s.toDoubleOrNull()
    }

    private fun alert(id: Int, title: String, text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }
}
