package com.rlg.inappupdates

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.google.android.material.snackbar.Snackbar

object InAppUpdateSnackbarUtil {

    /**
     * Shows a Snackbar prompting the user to restart and install the downloaded update.
     *
     * @param activity The activity to show the Snackbar in.
     * @param onInstallClicked The callback to invoke when the install button is clicked.
     */
    fun showInstallPromptSnackbar(activity: Activity, onInstallClicked: () -> Unit) {
        val rootView = activity.findViewById<View>(android.R.id.content)
        val snackbar =
            Snackbar.make(
                    rootView,
                    activity.getString(R.string.update_downloaded_restart_to_install),
                    Snackbar.LENGTH_INDEFINITE,
                )
                .setAction(activity.getString(R.string.install)) { onInstallClicked() }
                .setActionTextColor(activity.getColor(R.color.colorPrimary))

        val snackbarView = snackbar.view
        val params = snackbarView.layoutParams as FrameLayout.LayoutParams
        params.gravity = Gravity.TOP // Move Snackbar to the top of the screen
        snackbarView.layoutParams = params

        snackbar.show()
    }

    /**
     * Shows a flexible update Snackbar with predefined message, action, and dismiss buttons.
     *
     * @param activity The activity to show the Snackbar in.
     * @param onDownloadClicked The callback to invoke when the download button is clicked.
     * @param onDismissClicked The optional callback to invoke when the dismiss button is clicked.
     */
    fun showFlexibleUpdateSnackbar(
        activity: Activity,
        onDownloadClicked: () -> Unit,
        onDismissClicked: (() -> Unit)? = null,
    ) {
        val message = activity.getString(R.string.update_available_flexible_message)
        val actionText = activity.getString(R.string.download)
        val dismissText = activity.getString(R.string.update_available_later)

        showSnackbarWithActionAndDismiss(
            activity = activity,
            message = message,
            positiveActionButtonText = actionText,
            dismissButtonText = dismissText,
            onPositiveActionClicked = onDownloadClicked,
            onDismissClicked = { onDismissClicked?.invoke() },
        )
    }
}
