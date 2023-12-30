package com.github.andreyasadchy.xtra.util

import android.content.Context
import androidx.fragment.app.FragmentManager
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.ui.common.RadioButtonDialogFragment
import com.github.andreyasadchy.xtra.ui.player.PlayerGamesDialog
import com.github.andreyasadchy.xtra.ui.player.PlayerSettingsDialog
import com.github.andreyasadchy.xtra.ui.player.PlayerViewerListDialog
import com.github.andreyasadchy.xtra.ui.player.PlayerVolumeDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object FragmentUtils {

    /**
     * Use this when result should be a string resource id
     */
    fun showRadioButtonDialogFragment(context: Context, fragmentManager: FragmentManager, labels: List<Int>, checkedIndex: Int, requestCode: Int = 0) {
        RadioButtonDialogFragment.newInstance(
            requestCode,
            labels.map(context::getString),
            labels.toIntArray(),
            checkedIndex
        ).show(fragmentManager, "closeOnPip")
    }

    /**
     * Use this when result should be an index
     */
    fun showRadioButtonDialogFragment(fragmentManager: FragmentManager, labels: Collection<CharSequence>, checkedIndex: Int, requestCode: Int = 0) {
        RadioButtonDialogFragment.newInstance(
            requestCode,
            labels,
            null,
            checkedIndex
        ).show(fragmentManager, "closeOnPip")
    }

    fun showUnfollowDialog(context: Context, channelName: String?, positiveCallback: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setMessage(context.getString(R.string.unfollow_channel, channelName))
            .setPositiveButton(R.string.yes) { _, _ -> positiveCallback.invoke() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    fun showPlayerSettingsDialog(fragmentManager: FragmentManager, speedText: String? = null, vodGames: Boolean = false) {
        PlayerSettingsDialog.newInstance(speedText, vodGames).show(fragmentManager, "closeOnPip")
    }

    fun showPlayerVolumeDialog(fragmentManager: FragmentManager, volume: Float?) {
        PlayerVolumeDialog.newInstance(volume).show(fragmentManager, "closeOnPip")
    }

    fun showPlayerGamesDialog(fragmentManager: FragmentManager, gamesList: List<Game>) {
        PlayerGamesDialog.newInstance(gamesList).show(fragmentManager, "closeOnPip")
    }

    fun showPlayerViewerListDialog(fragmentManager: FragmentManager, login: String, repository: ApiRepository) {
        PlayerViewerListDialog.newInstance(login, repository).show(fragmentManager, "closeOnPip")
    }
}