package com.github.andreyasadchy.xtra.model

import android.content.Context
import androidx.core.content.edit
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs

sealed class Account(val id: String?,
                     val login: String?) {

    companion object {
        private var account: Account? = null

        fun get(context: Context): Account {
            return account ?: with(context.prefs()) {
                val helixToken = TwitchApiHelper.getHelixHeaders(context)[C.HEADER_TOKEN].takeUnless { it.isNullOrBlank() }
                val gqlToken = TwitchApiHelper.getGQLHeaders(context, true)[C.HEADER_TOKEN].takeUnless { it.isNullOrBlank() }
                if (!helixToken.isNullOrBlank() || !gqlToken.isNullOrBlank()) {
                    val id = getString(C.USER_ID, null).takeUnless { it.isNullOrBlank() }
                    val name = getString(C.USERNAME, null).takeUnless { it.isNullOrBlank() }
                    if (TwitchApiHelper.checkedValidation) {
                        LoggedIn(id, name)
                    } else {
                        NotValidated(id, name)
                    }
                } else {
                    NotLoggedIn()
                }
            }.also { account = it }
        }

        fun set(context: Context, account: Account?) {
            this.account = account
            context.prefs().edit {
                if (account != null) {
                    putString(C.USER_ID, account.id)
                    putString(C.USERNAME, account.login)
                } else {
                    putString(C.USER_ID, null)
                    putString(C.USERNAME, null)
                }
            }
        }

        fun validated() {
            account = LoggedIn(account as NotValidated)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Account

        if (id != other.id) return false
        if (login != other.login) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + login.hashCode()
        return result
    }
}

class LoggedIn(id: String?, login: String?) : Account(id, login) {
    constructor(account: NotValidated) : this(account.id, account.login)
}
class NotValidated(id: String?, login: String?) : Account(id, login)
class NotLoggedIn : Account(null, null)