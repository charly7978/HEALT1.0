package com.example.myapplication.ppg

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

/**
 * Gestor de perfiles de calibración por dispositivo.
 * Almacena y recupera perfiles de calibración SpO₂.
 */
class DeviceCalibrationManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "ppg_calibration",
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    private val profilesKey = "calibration_profiles"
    private val activeProfileKey = "active_profile_id"

    /**
     * Guarda un nuevo perfil de calibración.
     */
    fun saveProfile(profile: CalibrationProfile) {
        val profiles = getAllProfiles().toMutableList()
        profiles.add(profile)
        saveProfiles(profiles)
    }

    /**
     * Obtiene todos los perfiles de calibración.
     */
    fun getAllProfiles(): List<CalibrationProfile> {
        val json = prefs.getString(profilesKey, null) ?: return emptyList()
        val type = object : TypeToken<List<CalibrationProfile>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Busca un perfil compatible con los parámetros actuales.
     */
    fun findCompatibleProfile(
        deviceModel: String,
        cameraId: String,
        exposureNs: Long,
        iso: Int
    ): CalibrationProfile? {
        val profiles = getAllProfiles()
        return profiles.firstOrNull { profile ->
            profile.isCompatible(deviceModel, cameraId, exposureNs, iso)
        }
    }

    /**
     * Establece el perfil activo.
     */
    fun setActiveProfile(profileId: String) {
        prefs.edit().putString(activeProfileKey, profileId).apply()
    }

    /**
     * Obtiene el perfil activo.
     */
    fun getActiveProfile(): CalibrationProfile? {
        val activeId = prefs.getString(activeProfileKey, null) ?: return null
        return getAllProfiles().firstOrNull { it.id == activeId }
    }

    /**
     * Elimina un perfil.
     */
    fun deleteProfile(profileId: String) {
        val profiles = getAllProfiles().toMutableList()
        profiles.removeAll { it.id == profileId }
        saveProfiles(profiles)

        // Si era el activo, limpiar
        if (prefs.getString(activeProfileKey, null) == profileId) {
            prefs.edit().remove(activeProfileKey).apply()
        }
    }

    /**
     * Limpia todos los perfiles.
     */
    fun clearAllProfiles() {
        prefs.edit().remove(profilesKey).remove(activeProfileKey).apply()
    }

    private fun saveProfiles(profiles: List<CalibrationProfile>) {
        val json = gson.toJson(profiles)
        prefs.edit().putString(profilesKey, json).apply()
    }

    /**
     * Verifica si hay un perfil de calibración válido.
     */
    fun hasValidCalibration(): Boolean {
        return getActiveProfile()?.isValid == true
    }
}
