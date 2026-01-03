package com.jack.qrcodefor1922.ui

import android.app.Application
import android.content.*
import android.net.Uri
import android.os.*
import android.text.TextUtils
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.google.mlkit.vision.barcode.common.Barcode
import com.jack.qrcodefor1922.Utils.getDatabaseDao
import com.jack.qrcodefor1922.ui.MainActivity.Companion.PREFKEY
import com.jack.qrcodefor1922.ui.database.AppDatabase
import com.jack.qrcodefor1922.ui.database.ScanResult
import com.jack.qrcodefor1922.ui.database.TYPE
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.net.toUri

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var mLastTriggerText: String = ""
    private var mHasTrigger = false
    private var bAgreement = false
    private var bRedirectDialogShowing = false
    private var bSettingsShow = false
    private var mTempIntent: Intent? = null

    private val _showAgreement = MutableLiveData<Boolean?>()
    private val _startCamera = MutableLiveData<Boolean?>()
    private val _copyAlready = MutableLiveData<String>()
    private val _startActivity = MutableLiveData<Intent?>()
    private val _finishActivity = MutableLiveData<Boolean?>()
    private val _showScanResultDialog = MutableLiveData<ScanResultInfo?>()
    private val _showHistoryPrompt = MutableLiveData<Boolean?>()
    private val _vibrate = MutableLiveData<Boolean?>()
    val showAgreement:LiveData<Boolean?> = _showAgreement
    val startCamera:LiveData<Boolean?> = _startCamera
    val copyAlready:LiveData<String> = _copyAlready
    val startActivity:LiveData<Intent?> = _startActivity
    val finishActivity:LiveData<Boolean?> = _finishActivity
    val showScanResultDialog:LiveData<ScanResultInfo?> = _showScanResultDialog
    val showHistoryPrompt:LiveData<Boolean?> = _showHistoryPrompt
    val vibrate:LiveData<Boolean?>  = _vibrate

    private lateinit var mPref: SharedPreferences
    private var mBgThread: HandlerThread
    private var mHandler: BgHandler

    init {
        try {
            mPref = application.getSharedPreferences(PREFKEY, AppCompatActivity.MODE_PRIVATE)
        } catch (e: Exception) {
        }

        // Using another thread to delay trigger QRCode
        mBgThread = HandlerThread("timer")
        mBgThread.start()
        mHandler = BgHandler(mBgThread.looper)
    }

    fun ready() {
        bAgreement = mPref.getBoolean(AGREEMENT, false)
        val historyPrompt = mPref.getBoolean(FEATURE_HISTORY, false)

        if (!bAgreement) {
            _showAgreement.value = true
        } else if (!historyPrompt)
            viewModelScope.launch {
                delay(1000)
                _showHistoryPrompt.value = !historyPrompt
            }
        else {
            _startCamera.value = true
        }
    }

    fun userAgree() {
        mPref.edit().putBoolean(AGREEMENT, true).apply()
        ready()
    }

    fun confirmNewFeature() {
        mPref.edit().putBoolean(FEATURE_HISTORY, true).apply()
        ready()
    }

    private fun triggerBarcode(barcode: Barcode) {
        val rawValue = barcode.rawValue
        if (rawValue == null || mHasTrigger ||
            TextUtils.equals(mLastTriggerText, rawValue)
            || bSettingsShow || bRedirectDialogShowing
        ) {
            return
        }

        _vibrate.value = true
        var intent: Intent? = null
        var isCopied = false
        var shouldClose = false

        // Check if it is a valid intent (URL, SMS, etc.)
        if (barcode.valueType == Barcode.TYPE_SMS) {
             intent = Intent(Intent.ACTION_SENDTO).apply {
                type = "text/plain"
                data = Uri.parse("smsto:${barcode.sms?.phoneNumber}")
                putExtra("sms_body", "${barcode.sms?.message}")
            }
            if (mPref.getBoolean(PREF_CLOSE_APP_AFTER_SCAN, false)) {
                shouldClose = true
            }
            saveResultToDb(barcode.sms?.message, TYPE.SMS_1922)
        } else {
            val possibleIntent = Intent(Intent.ACTION_VIEW).apply {
                data = rawValue.toUri()
            }
            if (getApplication<Application>().packageManager?.queryIntentActivities(
                    possibleIntent,
                    0
                ) != null
            ) {
                intent = possibleIntent
                saveResultToDb(rawValue, TYPE.REDIRECT)
            } else {
                // Pure text
                if (mPref.getBoolean(PREF_AUTO_COPY_TEXT, true)) {
                    copyToClipboard(rawValue)
                    isCopied = true
                }
                saveResultToDb(rawValue, TYPE.TEXT)
            }
        }

        _showScanResultDialog.value = ScanResultInfo(barcode, isCopied, intent, shouldClose)
        bRedirectDialogShowing = true

        mHandler.postDelayed(Runnable {
            mLastTriggerText = ""
            mHasTrigger = false
        }, 1500)
        mLastTriggerText = rawValue
        mHasTrigger = true
    }

    fun newBarcodes(barcodes: List<Barcode>) {
        if (barcodes.isNotEmpty()) {
            if (!mHandler.hasMessages(MSG_FORCE_TRIGGER)) {
                mHandler.sendEmptyMessageDelayed(MSG_FORCE_TRIGGER, 700)
                mHandler.sendEmptyMessageDelayed(MSG_COOLING_TIME, 4000)
            }
            // Trigger first QRCode directly
            triggerBarcode(barcodes.first())
        }
    }

    fun copyToClipboard(text: String) {
        val clipboardManager =
            getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip: ClipData = ClipData.newPlainText("simple text", text)
        clipboardManager.setPrimaryClip(clip)
    }

    fun isSettingsShowing(show: Boolean) {
        bSettingsShow = show
    }

    fun activeIntent(intent: Intent?) {
        _startActivity.value = intent
        _startActivity.value = null
    }

    fun resetRedirectDialog() {
        bRedirectDialogShowing = false
    }

    fun resetVibrate() {
        _vibrate.value = null
    }

    fun resetAgreement() {
        _showAgreement.value = null
    }

    fun resetStartCamera() {
        _startCamera.value = null
    }

    fun saveResultToDb(rawValue: String?, type: TYPE) {
        rawValue?.let {
            saveResultToDbLocked(it, type)
        }
    }

    private fun saveResultToDbLocked(data: String, type: TYPE) {
        val resultDao = getDatabaseDao(getApplication())
        viewModelScope.launch {
            resultDao.insertAll(ScanResult(Date(), data, type))
        }
    }

    private class BgHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                MSG_FORCE_TRIGGER -> {
                    mForeTrigger.set(true)
                }
                MSG_COOLING_TIME -> {
                    mForeTrigger.set(false)
                }
            }
        }
    }


    companion object {
        private const val AGREEMENT = "agreement"
        private const val FEATURE_HISTORY = "feature_history"
        private const val PREF_CLOSE_APP_AFTER_SCAN = "close_after_scan"
        private const val PREF_AUTO_COPY_TEXT = "auto_copy_non_1922"
        // Wait a mount of time. If no 1922 number then trigger first QRCode
        private const val MSG_FORCE_TRIGGER = 1
        // Time interval to trigger next QRCode
        private const val MSG_COOLING_TIME = 2
        private val mForeTrigger = AtomicBoolean(false)
        private val obj = Object()
    }
}

data class ScanResultInfo(
    val barcode: Barcode,
    val isCopied: Boolean,
    val intent: Intent?,
    val shouldClose: Boolean
)