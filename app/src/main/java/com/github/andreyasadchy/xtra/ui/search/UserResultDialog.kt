package com.github.andreyasadchy.xtra.ui.search

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogUserResultBinding

class UserResultDialog : DialogFragment() {

    interface OnUserResultListener {
        fun onUserResult(resultCode: Int, checkedId: Int, result: String)
    }

    private var _binding: DialogUserResultBinding? = null
    private val binding get() = _binding!!
    private var listener: OnUserResultListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as? OnUserResultListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogUserResultBinding.inflate(layoutInflater)
        val context = requireContext()
        val builder = AlertDialog.Builder(context)
                .setView(binding.root)
        with(binding) {
            builder.setPositiveButton(android.R.string.ok) { _, _ ->
                listener?.onUserResult(RESULT_POSITIVE, if (radioButton.isChecked) 0 else 1, editText.text.toString())
                dismiss()
            }
            builder.setNegativeButton(android.R.string.cancel) { _, _ ->
                listener?.onUserResult(RESULT_NEGATIVE, if (radioButton.isChecked) 0 else 1, editText.text.toString())
                dismiss()
            }
            builder.setNeutralButton(R.string.view_profile) { _, _ ->
                listener?.onUserResult(RESULT_NEUTRAL, if (radioButton.isChecked) 0 else 1, editText.text.toString())
                dismiss()
            }
        }
        return builder.create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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