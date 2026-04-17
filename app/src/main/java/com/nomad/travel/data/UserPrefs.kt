package com.nomad.travel.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nomad_prefs")

class UserPrefs(private val context: Context) {

    private val KEY_LANGUAGE = stringPreferencesKey("ui_language")
    private val KEY_ACTIVE_MODEL = stringPreferencesKey("active_model_id")
    private val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
    private val KEY_CONTEXT_STRATEGY = stringPreferencesKey("context_strategy")
    private val KEY_LAST_SESSION_ID = longPreferencesKey("last_session_id")
    private val KEY_AUTO_UPDATE_CHECK = booleanPreferencesKey("auto_update_check")

    val language: Flow<String?> = context.dataStore.data.map { it[KEY_LANGUAGE] }
    val autoUpdateCheck: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_UPDATE_CHECK] != false }
    val activeModelId: Flow<String?> = context.dataStore.data.map { it[KEY_ACTIVE_MODEL] }
    val systemPrompt: Flow<String?> = context.dataStore.data.map { it[KEY_SYSTEM_PROMPT] }
    val contextStrategy: Flow<String?> = context.dataStore.data.map { it[KEY_CONTEXT_STRATEGY] }
    val lastSessionId: Flow<Long?> = context.dataStore.data.map { it[KEY_LAST_SESSION_ID] }

    suspend fun languageBlocking(): String? = language.first()
    suspend fun activeModelIdBlocking(): String? = activeModelId.first()
    suspend fun systemPromptBlocking(): String? = systemPrompt.first()
    suspend fun contextStrategyBlocking(): String? = contextStrategy.first()
    suspend fun lastSessionIdBlocking(): Long? = lastSessionId.first()

    suspend fun setLanguage(code: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = code }
    }

    suspend fun setActiveModelId(id: String) {
        context.dataStore.edit { it[KEY_ACTIVE_MODEL] = id }
    }

    suspend fun setSystemPrompt(prompt: String) {
        context.dataStore.edit { it[KEY_SYSTEM_PROMPT] = prompt }
    }

    suspend fun setContextStrategy(key: String) {
        context.dataStore.edit { it[KEY_CONTEXT_STRATEGY] = key }
    }

    suspend fun setLastSessionId(id: Long) {
        context.dataStore.edit { it[KEY_LAST_SESSION_ID] = id }
    }

    suspend fun autoUpdateCheckBlocking(): Boolean =
        context.dataStore.data.first()[KEY_AUTO_UPDATE_CHECK] != false

    suspend fun setAutoUpdateCheck(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_UPDATE_CHECK] = enabled }
    }
}
