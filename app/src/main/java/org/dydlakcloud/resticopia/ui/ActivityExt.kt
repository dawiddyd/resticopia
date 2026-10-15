package org.dydlakcloud.resticopia.ui

import android.app.Activity
import android.app.Activity.OVERRIDE_TRANSITION_CLOSE
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/**
 * Fun to handle on back pressed.
 *
 * @param onBack - labda for action, finish activity by default
 */
fun ComponentActivity.onBackPressedFinish(onBack: () -> Unit = { finish() }) {
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = onBack()
    })
}

/**
 * Finishes the current Activity with custom enter/exit animations.
 * Uses the modern overrideActivityTransition() on Android 14+,
 * and safely falls back to the deprecated method on older versions.
 *
 * @param enterAnim Resource ID of the animation for the previous Activity (when it re-enters).
 * @param exitAnim Resource ID of the animation for the current Activity (when it exits).
 */
fun Activity.finishWithAnimation(enterAnim: Int, exitAnim: Int) {
    finish()
    overrideActivityTransition(exitAnim, exitAnim)
}

/**
 * Helper fun for overridePendingTransition
 * Uses the modern overrideActivityTransition() on Android 14+,
 * and safely falls back to the deprecated method on older versions.
 *
 * @param enterAnim Resource ID of the animation for the previous Activity (when it re-enters).
 * @param exitAnim Resource ID of the animation for the current Activity (when it exits).
 */
fun Activity.overrideActivityTransition(enterAnim: Int, exitAnim: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, enterAnim, exitAnim)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(enterAnim, exitAnim)
    }
}
