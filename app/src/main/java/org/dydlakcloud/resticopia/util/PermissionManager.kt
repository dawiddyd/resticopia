package org.dydlakcloud.resticopia.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue

abstract class PermissionManager {
    abstract fun hasStoragePermission(context: Context, write: Boolean): Boolean

    protected abstract fun requestStoragePermissionInternal(
        activity: ComponentActivity,
        write: Boolean
    ): CompletableFuture<Unit>

    fun requestStoragePermission(
        activity: ComponentActivity,
        write: Boolean
    ): CompletableFuture<Boolean> =
        requestStoragePermissionInternal(activity, write).thenApply {
            hasStoragePermission(activity, write)
        }

    /**
     * Checks whether the app is allowed to post notifications.
     *
     * On Android 13 (API 33) and above this reflects the runtime
     * POST_NOTIFICATIONS permission. On older versions notifications are
     * granted by default on install, so this returns true.
     */
    fun hasNotificationPermission(context: Context): Boolean =
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Launches the system request for the POST_NOTIFICATIONS runtime
     * permission (only effective on API 33+). The returned future completes
     * with `true` when the permission has been granted.
     */
    fun requestNotificationPermission(
        activity: ComponentActivity
    ): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()

        // On API < 33 notifications are enabled by default, nothing to request.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            future.complete(true)
            return future
        }

        if (hasNotificationPermission(activity)) {
            future.complete(true)
            return future
        }

        val resultLauncher =
            activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                future.complete(granted)
            }

        try {
            resultLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch POST_NOTIFICATIONS permission request")
            future.complete(false)
        }

        return future
    }

    open fun onRequestPermissionsResult(requestCode: Int) {}

    companion object {
        val instance: PermissionManager =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                RPermissionManager()
            } else {
                LegacyPermissionManager()
            }

        @RequiresApi(Build.VERSION_CODES.R)
        class RPermissionManager : PermissionManager() {
            override fun hasStoragePermission(context: Context, write: Boolean): Boolean =
                Environment.isExternalStorageManager()

            // https://stackoverflow.com/questions/62782648/android-11-scoped-storage-permissions
            // https://gist.github.com/Sonderman/db57d4407dfb496658786bb9c4d2fa5e
            override fun requestStoragePermissionInternal(
                activity: ComponentActivity,
                write: Boolean
            ): CompletableFuture<Unit> {
                val future = CompletableFuture<Unit>()

                val resultLauncher =
                    activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        future.complete(Unit)
                    }

                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse(String.format("package:%s", activity.packageName))
                    resultLauncher.launch(intent)
                } catch (e: Exception) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    resultLauncher.launch(intent)
                }

                return future
            }

        }

        private const val PERMISSION_REQUEST_CODE = 2290

        class LegacyPermissionManager : PermissionManager() {
            override fun hasStoragePermission(context: Context, write: Boolean): Boolean =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED &&
                        (!write || ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED)

            private var legacyCallbacks: Queue<CompletableFuture<Unit>> = ConcurrentLinkedQueue()

            override fun requestStoragePermissionInternal(
                activity: ComponentActivity,
                write: Boolean
            ): CompletableFuture<Unit> {
                val future = CompletableFuture<Unit>()

                legacyCallbacks.offer(future)

                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_CODE
                )

                return future
            }

            override fun onRequestPermissionsResult(requestCode: Int) {
                when (requestCode) {
                    PERMISSION_REQUEST_CODE ->
                        legacyCallbacks.poll()?.complete(Unit)
                }
            }
        }
    }
}
