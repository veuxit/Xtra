package com.github.andreyasadchy.xtra.ui.search.channels

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.github.andreyasadchy.xtra.R
import kotlinx.android.synthetic.main.dialog_user_result.view.*

class UserResultDialog : DialogFragment() {

    interface OnUserResultListener {
        fun onUserResult(resultCode: Int, checkedId: Int, result: String)
    }

    private var listener: OnUserResultListener? = null
    private lateinit var dialogView: View

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as? OnUserResultListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val builder = AlertDialog.Builder(context)
                .setView(layoutInflater.inflate(R.layout.dialog_user_result, null).also { dialogView = it })
        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            listener?.onUserResult(RESULT_POSITIVE, if (dialogView.radioButton.isChecked) 0 else 1, dialogView.editText.text.toString())
            dismiss()
        }
        builder.setNegativeButton(android.R.string.cancel) { _, _ ->
            listener?.onUserResult(RESULT_NEGATIVE, if (dialogView.radioButton.isChecked) 0 else 1, dialogView.editText.text.toString())
            dismiss()
        }
        builder.setNeutralButton(R.string.view_profile) { _, _ ->
            listener?.onUserResult(RESULT_NEUTRAL, if (dialogView.radioButton.isChecked) 0 else 1, dialogView.editText.text.toString())
            dismiss()
        }
        return builder.create()
    }

    companion object {
        const val RESULT_POSITIVE = 0
        const val RESULT_NEGATIVE = 1
        const val RESULT_NEUTRAL = 2

        fun show(fragmentManager: FragmentManager) {
            UserResultDialog().show(fragmentManager, null)
        }
    }
}