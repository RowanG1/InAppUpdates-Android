package com.rlg.inappupdates

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar

fun showSnackbarWithActionAndDismiss(
    activity: Activity,
    message: String,
    positiveActionButtonText: String,
    dismissButtonText: String,
    onPositiveActionClicked: () -> Unit,
    onDismissClicked: () -> Unit,
) {
    val rootView = activity.findViewById<View>(android.R.id.content)
    val snackbar = Snackbar.make(rootView, "", Snackbar.LENGTH_INDEFINITE)

    // Inflate a custom layout for Snackbar
    val customView = activity.layoutInflater.inflate(R.layout.custom_snackbar, null)

    // Find views in the custom layout
    val messageTextView = customView.findViewById<TextView>(R.id.snackbar_message)
    val actionButton = customView.findViewById<Button>(R.id.snackbar_button_action)
    val dismissButton = customView.findViewById<Button>(R.id.snackbar_button_dismiss)

    // Set the message and button click listeners
    messageTextView.text = message
    actionButton.text = positiveActionButtonText
    dismissButton.text = dismissButtonText

    actionButton.setOnClickListener {
        snackbar.dismiss()
        onPositiveActionClicked()
    }
    dismissButton.setOnClickListener {
        snackbar.dismiss()
        onDismissClicked()
    }

    // Replace the default Snackbar's content view with the custom layout
    val snackbarLayout = snackbar.view as ViewGroup
    snackbarLayout.setPadding(0, 0, 0, 0) // Remove default padding
    snackbarLayout.addView(customView, 0)

    // Adjust Snackbar's position to the top
    val layoutParams = snackbarLayout.layoutParams as FrameLayout.LayoutParams
    layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
    layoutParams.topMargin = 16
    snackbarLayout.layoutParams = layoutParams

    // Show the customized Snackbar
    snackbar.show()
}
