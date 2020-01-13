package com.handstandsam.biometricks

import android.content.Context
import android.hardware.fingerprint.FingerprintManager
import androidx.annotation.MainThread
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.handstandsam.biometricks.internal.AuthenticationCallbackWrapper
import com.handstandsam.biometricks.internal.BiometricksHelper
import com.handstandsam.biometricks.internal.UiHelpers
import com.handstandsam.biometricks.internal.toAndroidX
import kotlin.coroutines.suspendCoroutine

/**
 * Biometricks is the type of Biometric Authentication that
 * is available for this device, represented as a sealed class.
 */
sealed class Biometricks {

    /** The device has no support for Biometric Authentication */
    object None : Biometricks()

    /** The device has support for Biometric Authentication */
    sealed class Available : Biometricks() {

        /** Device has Face Unlock ONLY */
        object Face : Available()

        /** Device has Fingerprint ONLY */
        object Fingerprint : Available()

        /** Device has Iris ONLY */
        object Iris : Available()

        /** Device has more than one biometric feature available ONLY */
        object Multiple : Available()


        /**
         * Device has a biometric type this library isn't aware of.
         *
         * This could happen if an older version of this library is
         * used on a newer device, with new biometric features.
         */
        object Unknown : Available()
    }

    companion object {

        /** Allows us to cache an instance of this helper */
        private var biometricksHelper: BiometricksHelper? = null

        /**
         * Allows a client to query the type of Biometrics available on the device.
         */
        fun from(context: Context): Biometricks {
            return biometricksHelper(context).type
        }

        private fun biometricksHelper(context: Context): BiometricksHelper {
            val biometricksHelper = biometricksHelper ?: BiometricksHelper(context).also {
                biometricksHelper = it
            }
            return biometricksHelper
        }

        /**
         * Wrapper around [BiometricPrompt.authenticate] which handles many tricky issues for you.
         * It will:
         * - Wait for the app to be focused if not currently, as the prompt will fail to show
         * otherwise.
         * - Allow you to show/hide a loading indicator for when there's a delay showing the prompt.
         * - Give you information on if you need to show an error to the user or not.
         * ```
         * lifecycleScope.launch {
         *     try {
         *         val unlockedCryptObject = Biometricks.showPrompt(
         *             this@MainActivity,
         *             biometricPromptInfo
         *         ) { showLoading -> progressBar.visibility = if (showLoading) View.VISIBLE else View.INVISIBLE }
         *
         *         // success
         *     } catch (e: BiometricException) {
         *         // failure
         *         if (e.shouldShow) {
         *             // show error to the user
         *         }
         *     }
         * }
         * ```
         *
         * @param activity The host activity.
         * @param biometricPromptInfo The [BiometricPromptInfo] to display in the prompt.
         * @param showLoading Callback to show/hide a loading indicator for when there's a delay
         * showing the prompt. This is only used on api 28 as after that it shows immediately.
         */
        @MainThread
        suspend fun showPrompt(
            activity: FragmentActivity,
            promptInfo: BiometricPromptInfo,
            showLoading: (Boolean) -> Unit
        ): BiometricPrompt.CryptoObject {
            return UiHelpers.handleApi28LoadingAndEnsureFocus(activity, showLoading) {
                suspendCoroutine<BiometricPrompt.CryptoObject> { continuation ->
                    BiometricPrompt(
                        activity,
                        ContextCompat.getMainExecutor(activity),
                        AuthenticationCallbackWrapper(continuation)
                    ).authenticate(promptInfo.toAndroidX(), promptInfo.cryptoObject)
                }
            }
        }

        /**
         * Wrapper around [BiometricPrompt.authenticate] which handles many tricky issues for you.
         * It will:
         * - Wait for the app to be focused if not currently, as the prompt will fail to show
         * otherwise.
         * - Allow you to show/hide a loading indicator for when there's a delay showing the prompt.
         * - Give you information on if you need to show an error to the user or not.
         * ```
         * lifecycleScope.launch {
         *     try {
         *         val unlockedCryptObject = Biometricks.showPrompt(
         *             this@MainActivity,
         *             lockedCryptoObject,
         *             biometricPromptInfo
         *         ) { showLoading -> progressBar.visibility = if (showLoading) View.VISIBLE else View.INVISIBLE }
         *
         *         // success
         *     } catch (e: BiometricException) {
         *         // failure
         *         if (e.shouldShow) {
         *             // show error to the user
         *         }
         *     }
         * }
         * ```
         *
         * @param fragment The host fragment.
         * as you many not get 'secure' biometrics if you don't include it.
         * @param biometricPromptInfo The [BiometricPromptInfo] to display in the prompt.
         * @param showLoading Callback to show/hide a loading indicator for when there's a delay
         * showing the prompt. This is only used on api 28 as after that it shows immediately.
         */
        @MainThread
        suspend fun showPrompt(
            fragment: Fragment,
            promptInfo: BiometricPromptInfo,
            showLoading: (Boolean) -> Unit
        ): BiometricPrompt.CryptoObject {
            val activity = fragment.requireActivity()

            return UiHelpers.handleApi28LoadingAndEnsureFocus(activity, showLoading) {
                suspendCoroutine<BiometricPrompt.CryptoObject> { continuation ->
                    BiometricPrompt(
                        fragment,
                        ContextCompat.getMainExecutor(activity),
                        AuthenticationCallbackWrapper(continuation)
                    ).authenticate(promptInfo.toAndroidX(), promptInfo.cryptoObject)
                }
            }
        }

        /**
         * Checks if we can securely authenticate, i.e. we have secure biometrics hardware and the user is
         * enrolled. [androidx.biometric.BiometricManager.canAuthenticate] is unusable for this for a couple
         * of reasons:
         * 1. On api 28 it falls back to [FingerprintManager.hasEnrolledFingerprints] as the system method
         * does not exist. However, there may still be non-fingerprint biometrics on those devices so it'll
         * return a false-negative.
         * 2. On api 29+ it checks for any form of biometrics, not just ones that are 'secure', so we can
         * get a false-positive if the user is enrolled in an insecure form of biometrics.
         */
        fun canSecurelyAuthenticate(context: Context): Boolean {
            return biometricksHelper(context).canSecurelyAuthenticate()
        }
    }
}