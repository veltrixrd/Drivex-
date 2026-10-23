package com.drivex.app

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("drivex", Context.MODE_PRIVATE)

    fun minPrice(c: Context) = sp(c).getFloat("minPrice", 0f)
    fun maxKm(c: Context) = sp(c).getFloat("maxKm", 0f)
    fun minPerKm(c: Context) = sp(c).getFloat("minPerKm", 0f)
    fun isActive(c: Context) = sp(c).getBoolean("active", false)
    fun lastEvent(c: Context) = sp(c).getString("last", "Sin lecturas todavía") ?: ""

    fun saveFilters(c: Context, price: Float, km: Float, perKm: Float) {
        sp(c).edit().putFloat("minPrice", price).putFloat("maxKm", km).putFloat("minPerKm", perKm).apply()
    }

    fun setActive(c: Context, v: Boolean) = sp(c).edit().putBoolean("active", v).apply()
    fun setLast(c: Context, v: String) = sp(c).edit().putString("last", v).apply()
}
