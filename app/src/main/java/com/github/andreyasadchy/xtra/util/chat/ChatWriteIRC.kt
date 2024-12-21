package com.github.andreyasadchy.xtra.util.chat

import android.util.Log
import com.github.andreyasadchy.xtra.util.TlsSocketFactory
import okhttp3.TlsVersion
import java.io.*
import java.net.Socket
import java.security.KeyStore
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

private const val TAG = "ChatWriteIRC"

class ChatWriteIRC(
    private val useSSL: Boolean,
    private val userLogin: String?,
    private val userToken: String?,
    channelName: String,
    private val onSendMessageError: (String, String) -> Unit,
    private val onNotice: (String) -> Unit,
    private val onUserState: (String) -> Unit) : Thread() {
    private var socketOut: Socket? = null
    private lateinit var readerOut: BufferedReader
    private lateinit var writerOut: BufferedWriter
    private val hashChannelName: String = "#$channelName"
    private val messageSenderExecutor: Executor = Executors.newSingleThreadExecutor()
    private var isActive = true

    override fun run() {

        fun handlePing(writer: BufferedWriter) {
            write("PONG :tmi.twitch.tv", writer)
            writer.flush()
        }

        do {
            try {
                connect()
                while (true) {
                    val messageOut = readerOut.readLine()!!
                    messageOut.run {
                        when {
                            contains("PRIVMSG") -> {}
                            contains("USERNOTICE") -> {}
                            contains("CLEARMSG") -> {}
                            contains("CLEARCHAT") -> {}
                            contains("NOTICE") -> onNotice(this)
                            contains("ROOMSTATE") -> {}
                            contains("USERSTATE") -> onUserState(this)
                            startsWith("PING") -> handlePing(writerOut)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.d(TAG, "Disconnecting from $hashChannelName")
                close()
                sleep(1000L)
            } catch (e: Exception) {
                close()
                sleep(1000L)
            }
        } while (isActive)
    }

    private fun connect() {
        Log.d(TAG, "Connecting to Twitch IRC - SSL $useSSL")
        try {
            val trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).run {
                init(null as KeyStore?)
                trustManagers.first { it is X509TrustManager } as X509TrustManager
            }
            val sslContext = SSLContext.getInstance(TlsVersion.TLS_1_2.javaName())
            sslContext.init(null, arrayOf(trustManager), null)
            socketOut = (if (useSSL) TlsSocketFactory(sslContext.socketFactory).createSocket("irc.twitch.tv", 6697) else Socket("irc.twitch.tv", 6667))?.apply {
                readerOut = BufferedReader(InputStreamReader(getInputStream()))
                writerOut = BufferedWriter(OutputStreamWriter(getOutputStream()))
                write("PASS oauth:$userToken", writerOut)
                write("NICK $userLogin", writerOut)
            }
            write("CAP REQ :twitch.tv/tags twitch.tv/commands", writerOut)
            write("JOIN $hashChannelName", writerOut)
            writerOut.flush()
            Log.d(TAG, "Successfully connected to - $hashChannelName")
        } catch (e: IOException) {
            Log.e(TAG, "Error connecting to Twitch IRC", e)
            throw e
        }
    }

    fun disconnect() {
        if (isActive) {
            val thread = Thread {
                isActive = false
                close()
            }
            thread.start()
            thread.join()
        }
    }

    private fun close() {
        try {
            socketOut?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error while closing socketOut", e)
        }
    }

    @Throws(IOException::class)
    private fun write(message: String, vararg writers: BufferedWriter?) {
        writers.forEach { it?.write(message + System.lineSeparator()) }
    }

    fun send(message: CharSequence, replyId: String?) {
        messageSenderExecutor.execute {
            try {
                val reply = replyId?.let { "@reply-parent-msg-id=${it} " } ?: ""
                write("${reply}PRIVMSG $hashChannelName :$message", writerOut)
                writerOut.flush()
                Log.d(TAG, "Sent message to $hashChannelName: $message")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message", e)
                onSendMessageError(e.toString(), e.stackTraceToString())
            }
        }
    }
}
