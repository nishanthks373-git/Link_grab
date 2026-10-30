package com.example.linkgrab

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Fingerprint (or screen lock) gate for the Private tab. The unlocked state lives only in memory,
 * so it resets whenever the app is closed or sent to the background.
 */
object PrivateLock {
    var unlocked by mutableStateOf(false)
        private set

    private var prompting = false

    // Fingerprint/face, with the phone's PIN, pattern or password as a fallback.
    private const val ALLOWED =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    enum class Availability { OK, NOT_SET_UP, UNAVAILABLE }

    fun relock() {
        unlocked = false
    }

    fun availability(context: android.content.Context): Availability =
        when (BiometricManager.from(context).canAuthenticate(ALLOWED)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.OK
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NOT_SET_UP
            else -> Availability.UNAVAILABLE
        }

    /** Shows the system prompt. [onResult] gets (true, null) on success or (false, reason) otherwise. */
    fun authenticate(activity: FragmentActivity, onResult: (Boolean, String?) -> Unit) {
        if (prompting) return
        prompting = true

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prompting = false
                unlocked = true
                onResult(true, null)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                prompting = false
                onResult(false, errString.toString())
            }
            // onAuthenticationFailed (a wrong fingerprint) keeps the prompt open so the user can retry.
        }

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock private files")
            .setSubtitle("Use your fingerprint or screen lock")
            .setAllowedAuthenticators(ALLOWED)
            .build()

        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }
}
