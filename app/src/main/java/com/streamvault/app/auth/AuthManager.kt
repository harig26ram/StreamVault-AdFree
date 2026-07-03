package com.streamvault.app.auth

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.streamvault.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class UserProfile(
    val id: String,
    val displayName: String,
    val email: String,
    val photoUrl: String?,
    val serverAuthCode: String?
)

data class YouTubeTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Long
)

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AuthManager"
        val WEB_CLIENT_ID: String get() = BuildConfig.WEB_CLIENT_ID
        val WEB_CLIENT_SECRET: String get() = BuildConfig.WEB_CLIENT_SECRET
        private const val PREFS_NAME = "streamvault_auth"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_PHOTO_URL = "photo_url"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestId()
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope("https://www.googleapis.com/auth/youtube.readonly"))
            .requestServerAuthCode(WEB_CLIENT_ID, false)
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    init {
        restoreSession()
    }

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    fun handleSignInResult(task: Task<GoogleSignInAccount>, scope: CoroutineScope) {
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account != null) {
                val profile = UserProfile(
                    id = account.id ?: "",
                    displayName = account.displayName ?: "",
                    email = account.email ?: "",
                    photoUrl = account.photoUrl?.toString(),
                    serverAuthCode = account.serverAuthCode
                )
                _userProfile.value = profile
                _authState.value = AuthState.Authenticated(profile)
                saveSession(profile)
                Log.d(TAG, "Signed in: ${profile.displayName} (${profile.email})")

                // Exchange auth code for tokens in background
                account.serverAuthCode?.let { authCode ->
                    scope.launch {
                        exchangeAuthCodeForTokens(authCode)
                    }
                }
            } else {
                _authState.value = AuthState.Error("Sign-in failed: no account")
                Log.e(TAG, "Sign-in returned null account")
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            val message = when (e.statusCode) {
                12500 -> "Google Play Services error. Please check your device settings."
                12501 -> "Sign-in was cancelled."
                else -> "Sign-in failed (error code: ${e.statusCode})"
            }
            Log.e(TAG, "Sign-in failed: ${e.statusCode}", e)
            _authState.value = AuthState.Error(message)
        }
    }

    suspend fun exchangeAuthCodeForTokens(authCode: String): YouTubeTokens? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://oauth2.googleapis.com/token")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.doOutput = true

                val params = "code=$authCode" +
                    "&client_id=$WEB_CLIENT_ID" +
                    "&client_secret=$WEB_CLIENT_SECRET" +
                    "&grant_type=authorization_code"

                OutputStreamWriter(conn.outputStream).use { it.write(params) }

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val tokens = YouTubeTokens(
                        accessToken = json.getString("access_token"),
                        refreshToken = json.optString("refresh_token", null),
                        expiresIn = json.getLong("expires_in")
                    )
                    // Store tokens
                    prefs.edit().apply {
                        putString(KEY_ACCESS_TOKEN, tokens.accessToken)
                        tokens.refreshToken?.let { putString(KEY_REFRESH_TOKEN, it) }
                        apply()
                    }
                    Log.d(TAG, "Tokens exchanged successfully")
                    tokens
                } else {
                    Log.e(TAG, "Token exchange failed: ${conn.responseCode}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token exchange error", e)
                null
            }
        }
    }

    suspend fun refreshAccessToken(): String? {
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://oauth2.googleapis.com/token")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.doOutput = true

                val params = "client_id=$WEB_CLIENT_ID" +
                    "&client_secret=$WEB_CLIENT_SECRET" +
                    "&refresh_token=$refreshToken" +
                    "&grant_type=refresh_token"

                OutputStreamWriter(conn.outputStream).use { it.write(params) }

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val accessToken = json.getString("access_token")
                    prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).apply()
                    accessToken
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token refresh error", e)
                null
            }
        }
    }

    fun getAccessToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    fun signOut() {
        googleSignInClient.signOut().addOnCompleteListener {
            _userProfile.value = null
            _authState.value = AuthState.Unauthenticated
            clearSession()
            Log.d(TAG, "Signed out")
        }
    }

    fun isSignedIn(): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null
    }

    private fun saveSession(profile: UserProfile) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_ID, profile.id)
            putString(KEY_DISPLAY_NAME, profile.displayName)
            putString(KEY_EMAIL, profile.email)
            putString(KEY_PHOTO_URL, profile.photoUrl)
            apply()
        }
    }

    private fun clearSession() {
        prefs.edit().clear().apply()
    }

    private fun restoreSession() {
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        if (isLoggedIn) {
            val profile = UserProfile(
                id = prefs.getString(KEY_USER_ID, "") ?: "",
                displayName = prefs.getString(KEY_DISPLAY_NAME, "") ?: "",
                email = prefs.getString(KEY_EMAIL, "") ?: "",
                photoUrl = prefs.getString(KEY_PHOTO_URL, null),
                serverAuthCode = null
            )
            _userProfile.value = profile
            _authState.value = AuthState.Authenticated(profile)
            Log.d(TAG, "Session restored: ${profile.displayName}")
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }
}

sealed class AuthState {
    data object Loading : AuthState()
    data object Unauthenticated : AuthState()
    data class Authenticated(val profile: UserProfile) : AuthState()
    data class Error(val message: String) : AuthState()
}
