package com.jack.qrcodefor1922.ui

import android.app.Application
import android.content.*
import android.net.Uri
import android.os.*
import android.telephony.SmsManager
import android.text.TextUtils
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.google.mlkit.vision.barcode.Barcode
import com.jack.qrcodefor1922.Utils.getDatabaseDao
import com.jack.qrcodefor1922.ui.MainActivity.Companion.PREFKEY
import com.jack.qrcodefor1922.ui.database.AppDatabase
import com.jack.qrcodefor1922.ui.database.ScanResult
import com.jack.qrcodefor1922.ui.database.TYPE
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var mLastTriggerText: String = ""
    private var mHasTrigger = false
    private var bAgreement = false
    private var bRedirectDialogShowing = false
    private var mAccompanyNum = 0
    private var bSettingsShow = false
    private var mTempIntent: Intent? = null

    private val _showAgreement = MutableLiveData<Boolean?>()
    private val _startCamera = MutableLiveData<Boolean?>()
    private val _personNum = MutableLiveData<String>()
    private val _copyAlready = MutableLiveData<String>()
    private val _startActivity = MutableLiveData<Intent?>()
    private val _finishActivity = MutableLiveData<Boolean?>()
    private val _showDetectOtherDialog = MutableLiveData<Barcode?>()
    private val _showHistoryPrompt = MutableLiveData<Boolean?>()
    private val _vibrate = MutableLiveData<Boolean?>()
    val showAgreement:LiveData<Boolean?> = _showAgreement
    val startCamera:LiveData<Boolean?> = _startCamera
    val personNum:LiveData<String> = _personNum
    val copyAlready:LiveData<String> = _copyAlready
    val startActivity:LiveData<Intent?> = _startActivity
    val finishActivity:LiveData<Boolean?> = _finishActivity
    val showDetectOtherDialog:LiveData<Barcode?> = _showDetectOtherDialog
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

    fun onSeekBarChange(progress: Int) {
        mAccompanyNum = progress
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

    fun userEnterAccompanyNum(num: String) {
        if (num.isNotEmpty()) {
            var num = num.toInt()
            if (num > 10) num = 10
            mAccompanyNum = num
        } else {
            mAccompanyNum = 0
        }
        _personNum.value = mAccompanyNum.toString()
    }

    private fun triggerBarcode(barcode: Barcode) {
        if (barcode.rawValue == null || mHasTrigger ||
            TextUtils.equals(mLastTriggerText, barcode.rawValue)
            || bSettingsShow || bRedirectDialogShowing
        ) {
            return
        }

        // Filter 1922 number
        if (barcode.valueType == Barcode.TYPE_SMS) {
            if (mPref.getBoolean(PREF_VIBRATE_WHEN_SUCCESS, true)) {
                _vibrate.value = true
            }
            if (TextUtils.equals(barcode.sms?.phoneNumber, VAILD_NUMBER)) {
                var manager: SmsManager
                if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    manager = getApplication<Application>().getSystemService(SmsManager::class.java)
                    manager = manager.createForSubscriptionId(SmsManager.getDefaultSmsSubscriptionId())
                } else if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    manager = SmsManager.getSmsManagerForSubscriptionId(SmsManager.getDefaultSmsSubscriptionId())
                } else {
                    manager = SmsManager.getDefault()
                }
                var appendFamilyStr = ""
                if (mAccompanyNum != 0) {
                    appendFamilyStr = "+$mAccompanyNum"
                }
                manager.sendTextMessage(barcode.sms.phoneNumber, null, "${barcode.sms.message} $appendFamilyStr", null, null)
                val sendIntent = Intent(Intent.ACTION_SENDTO).apply {
                    type = "text/plain"
                    data = Uri.parse("smsto:${barcode.sms?.phoneNumber}")
                    var appendFamilyStr = ""
                    if (mAccompanyNum != 0) {
                        appendFamilyStr = "+$mAccompanyNum"
                    }
                    putExtra("sms_body", "")
                }
                _startActivity.value = sendIntent
                if (mPref.getBoolean(PREF_CLOSE_APP_AFTER_SCAN, false)) {
                    _finishActivity.value = true
                }
            }
            saveResultToDb(barcode.sms?.message, TYPE.SMS_1922)
        } else {
            if (mPref.getBoolean(PREF_AUTO_OPEN_SCHEMA, false)) {
                synchronized(obj) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(barcode.rawValue)
                    }
                    if (getApplication<Application>().packageManager?.queryIntentActivities(
                            intent,
                            0
                        ) != null
                    ) {
                        mTempIntent = intent
                        _showDetectOtherDialog.value = barcode
                        _showDetectOtherDialog.value = null
                        bRedirectDialogShowing = true
                    } else {
                        copyToClipboard(barcode.rawValue)
                    }
                }
                saveResultToDb(barcode.rawValue, TYPE.REDIRECT)
            } else {
                copyToClipboard(barcode.rawValue)
                saveResultToDb(barcode.rawValue, TYPE.TEXT)
            }
        }
        mHandler.postDelayed(Runnable {
            mLastTriggerText = ""
            mHasTrigger = false
        }, 1500)
        mLastTriggerText = barcode.rawValue
        mHasTrigger = true
    }

    fun newBarcodes(barcodes: List<Barcode>) {
        if (barcodes.isNotEmpty()) {
            if (!mHandler.hasMessages(MSG_FORCE_TRIGGER)) {
                mHandler.sendEmptyMessageDelayed(MSG_FORCE_TRIGGER, 700)
                mHandler.sendEmptyMessageDelayed(MSG_COOLING_TIME, 4000)
            }
            if (!mForeTrigger.get()) {
                // Find 1922 number in list
                for (barcode in barcodes) {
                    if (barcode.valueType == Barcode.TYPE_SMS
                        && TextUtils.equals(barcode.sms?.phoneNumber, VAILD_NUMBER)
                    ) {
                        mHandler.removeMessages(MSG_FORCE_TRIGGER)
                        mHandler.removeMessages(MSG_COOLING_TIME)
                        triggerBarcode(barcode)
                    }
                }
            } else {
                // Trigger first QRCode directly
                triggerBarcode(barcodes.first())
            }
        }
    }

    fun copyToClipboard(text: String) {
        if (mPref.getBoolean(PREF_AUTO_COPY_TEXT, true)) {
            if (mPref.getBoolean(PREF_COPY_TEXT_VIBRATE, true)) {
                _vibrate.value = true
            }
            val clipboardManager =
                getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("simple text", text)
            clipboardManager.setPrimaryClip(clip)
            _copyAlready.value = text
            _copyAlready.value = ""
        }
    }

    fun isSettingsShowing(show: Boolean) {
        bSettingsShow = show
    }

    fun activeIntent() {
        _startActivity.value = mTempIntent
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
        private const val VAILD_NUMBER = "1922"
        private const val PREF_CLOSE_APP_AFTER_SCAN = "close_after_scan"
        private const val PREF_VIBRATE_WHEN_SUCCESS = "vibrate_when_success"
        private const val PREF_AUTO_OPEN_SCHEMA = "auto_open_identify_schema"
        private const val PREF_AUTO_COPY_TEXT = "auto_copy_non_1922"
        private const val PREF_COPY_TEXT_VIBRATE = "vibrate_when_copy_text_success"
        // Wait a mount of time. If no 1922 number then trigger first QRCode
        private const val MSG_FORCE_TRIGGER = 1
        // Time interval to trigger next QRCode
        private const val MSG_COOLING_TIME = 2
        private val mForeTrigger = AtomicBoolean(false)
        private val obj = Object()
    }
}