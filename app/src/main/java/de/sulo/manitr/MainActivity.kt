package de.sulo.manitr

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import de.sulo.manitr.ui.AppRoot
import de.sulo.manitr.ui.LockScreen
import de.sulo.manitr.ui.SuloTheme

/**
 * Einstieg. Die App ist gesperrt, bis der Nutzer sich per Fingerabdruck / Gesicht
 * oder der Bildschirmsperre des Geräts (PIN, Muster, Passwort) authentifiziert hat.
 */
class MainActivity : FragmentActivity() {

    private val vm: AppViewModel by viewModels()

    private var unlocked by mutableStateOf(false)
    private var lockMessage by mutableStateOf(DEFAULT_MESSAGE)
    private var needsSetup by mutableStateOf(false)
    private var authBusy = false
    private var stoppedAt = 0L

    private val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Kein Screenshot / keine Vorschau im App-Wechsler
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            SuloTheme {
                if (unlocked) {
                    AppRoot(vm, onLock = { unlocked = false; lockMessage = DEFAULT_MESSAGE })
                } else {
                    LockScreen(
                        message = lockMessage, needsSetup = needsSetup,
                        onUnlock = ::authenticate,
                        onOpenSecuritySettings = { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Nach längerer Abwesenheit erneut sperren
        if (unlocked && stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > LOCK_AFTER_MS) {
            unlocked = false
            lockMessage = DEFAULT_MESSAGE
        }
        if (!unlocked) authenticate()
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    private fun authenticate() {
        if (authBusy) return
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            needsSetup = true
            lockMessage = "Auf diesem Gerät ist keine Bildschirmsperre eingerichtet. " +
                "Bitte richte PIN, Muster, Passwort oder Fingerabdruck ein – danach lässt sich die App entsperren."
            return
        }
        needsSetup = false
        authBusy = true
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authBusy = false
                    unlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authBusy = false
                    lockMessage = "Entsperren abgebrochen – tippe auf „Entsperren“."
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Sülo ManiTR entsperren")
                .setSubtitle("Fingerabdruck oder Bildschirmsperre")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    private companion object {
        const val LOCK_AFTER_MS = 30_000L
        const val DEFAULT_MESSAGE = "Mit Fingerabdruck oder Bildschirmsperre entsperren"
    }
}
