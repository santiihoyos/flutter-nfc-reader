package it.matteocrippa.flutternfcreader

import android.Manifest
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.nio.charset.Charset


const val PERMISSION_NFC = 1007

class FlutterNfcReaderPlugin(val registrar: Registrar) : MethodCallHandler, EventChannel.StreamHandler, NfcAdapter.ReaderCallback {

    private val activity = registrar.activity()

    private var isReading = false
    private var nfcAdapter: NfcAdapter? = null

    //private var eventSink: EventChannel.EventSink? = null

    private var kId = "nfcId"
    private var kContent = "nfcContent"
    private var kError = "nfcError"
    private var kStatus = "nfcStatus"

    private var READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A

    private var handler: Handler

    private var pendingResult : Result? = null

    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val messenger = registrar.messenger()
            val channel = MethodChannel(messenger, "flutter_nfc_reader")
            val eventChannel = EventChannel(messenger, "it.matteocrippa.flutternfcreader.flutter_nfc_reader")
            val plugin = FlutterNfcReaderPlugin(registrar)
            channel.setMethodCallHandler(plugin)
            eventChannel.setStreamHandler(plugin)
        }
    }

    init {
        nfcAdapter = NfcAdapter.getDefaultAdapter(activity)
        handler = Handler(Looper.getMainLooper())
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "NfcRead" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    activity.requestPermissions(arrayOf(Manifest.permission.NFC), PERMISSION_NFC)
                }
                startNFC()
                if (!isReading && nfcAdapter?.isEnabled != true) {
                    result.error("404", "NFC Hardware not found", null)
                    return
                }
                pendingResult = result
            }
            "NfcStop" -> {
                stopNFC()
                val data = mapOf(kId to "", kContent to "", kError to "", kStatus to "stopped")
                result.success(data)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onListen(arguments: Any?, eventSink: EventChannel.EventSink?) {}

    override fun onCancel(arguments: Any?) = stopNFC()

    // handle discovered NDEF Tags
    override fun onTagDiscovered(tag: Tag?) {
        // convert tag to NDEF tag
        val ndef = Ndef.get(tag)
        // ndef will be null if the discovered tag is not a NDEF tag
        // read NDEF message
        ndef?.connect()
        val message = ndef?.ndefMessage
            ?.toByteArray()
            ?.toString(Charset.forName("UTF-8")) ?: ""
        //val id = tag?.id?.toString(Charset.forName("ISO-8859-1")) ?: ""
        val id = bytesToHexString(tag?.id) ?: ""
        ndef?.close()
        if (message.isNotEmpty()) {
            val data = mapOf(kId to id, kContent to message, kError to "", kStatus to "read")
            println("Native: put result in main thread")
            activity.runOnUiThread { pendingResult?.success(data) }
        }
    }

    private fun startNFC(): Boolean {
        isReading = if (nfcAdapter?.isEnabled == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                nfcAdapter?.enableReaderMode(activity, this, READER_FLAGS, null)
            }
            true
        } else {
            false
        }
        return isReading
    }

    private fun stopNFC() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            nfcAdapter?.disableReaderMode(registrar.activity())
        }
        isReading = false
    }

    private fun bytesToHexString(src: ByteArray?): String? {
        val stringBuilder = StringBuilder("0x")
        if (src == null || src.isEmpty()) {
            return null
        }
        val buffer = CharArray(2)
        for (i in src.indices) {
            buffer[0] = Character.forDigit(src[i].toInt().ushr(4).and(0x0F), 16)
            buffer[1] = Character.forDigit(src[i].toInt().and(0x0F), 16)
            stringBuilder.append(buffer)
        }
        return stringBuilder.toString()
    }
}