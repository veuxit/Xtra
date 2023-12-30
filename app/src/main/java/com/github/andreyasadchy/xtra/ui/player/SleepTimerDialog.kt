package com.github.andreyasadchy.xtra.ui.player

import android.app.Dialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogSleepTimerBinding
import com.github.andreyasadchy.xtra.util.AdminReceiver
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SleepTimerDialog : DialogFragment() {

    interface OnSleepTimerStartedListener {
        fun onSleepTimerChanged(durationMs: Long, hours: Int, minutes: Int, lockScreen: Boolean)
    }

    private var _binding: DialogSleepTimerBinding? = null
    private val binding get() = _binding!!
    private lateinit var listener: OnSleepTimerStartedListener

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as OnSleepTimerStartedListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSleepTimerBinding.inflate(layoutInflater)
        val context = requireContext()
        val builder = MaterialAlertDialogBuilder(context)
                .setTitle(getString(R.string.sleep_timer))
                .setView(binding.root)
        with(binding) {
            val positiveListener: (dialog: DialogInterface, which: Int) -> Unit = { _, _ ->
                listener.onSleepTimerChanged(hours.value * 3600_000L + minutes.value * 60_000L,  hours.value, minutes.value, lockCheckbox.isChecked)
                dismiss()
            }
            if (requireArguments().getLong(KEY_TIME_LEFT) < 0L) {
                builder.setPositiveButton(getString(R.string.start), positiveListener)
                builder.setNegativeButton(android.R.string.cancel) { _, _ -> dismiss() }
            } else {
                builder.setPositiveButton(getString(R.string.set), positiveListener)
                builder.setNegativeButton(getString(R.string.stop)) { _, _ ->
                    listener.onSleepTimerChanged(-1L, 0, 0, lockCheckbox.isChecked)
                    dismiss()
                }
                builder.setNeutralButton(android.R.string.cancel) { _, _ -> dismiss() }
            }
        }
        return builder.create()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        with(binding) {
            hours.apply {
                minValue = 0
                maxValue = 23
            }
            minutes.apply {
                minValue = 0
                maxValue = 59
            }
            requireArguments().getLong(KEY_TIME_LEFT).let {
                if (it < 0L) {
                    minutes.value = 15
                } else {
                    val hours = it / 3600_000L
                    binding.hours.value = hours.toInt()
                    minutes.value = ((it - hours * 3600_000L) / 60_000L).toInt()
                }
            }
            val admin = ComponentName(requireContext(), AdminReceiver::class.java)
            if ((requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).isAdminActive(admin)) {
                lockCheckbox.apply {
                    isChecked = requireContext().prefs().getBoolean(C.SLEEP_TIMER_LOCK, true)
                    text = context.getString(R.string.sleep_timer_lock)
                }
            } else {
                lockCheckbox.apply {
                    isChecked = false
                    text = context.getString(R.string.sleep_timer_lock_permissions)
                    setOnClickListener {
                        val intent = Intent(
                            DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN
                        ).putExtra(
                            DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin
                        )
                        requireContext().startActivity(intent)
                        dismiss()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_TIME_LEFT = "timeLeft"

        fun show(fragmentManager: FragmentManager, timeLeft: Long) {
            SleepTimerDialog().apply {
                arguments = bundleOf(KEY_TIME_LEFT to timeLeft)
                show(fragmentManager, null)
            }
        }
    }
}