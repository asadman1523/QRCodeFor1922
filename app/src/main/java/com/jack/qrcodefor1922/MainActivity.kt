package com.jack.qrcodefor1922

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.util.Size
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.barcode.Barcode
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean


typealias QRCodeListener = (barcodes: List<Barcode>) -> Unit

class MainActivity2 : AppCompatActivity() {

    companion object {
        private const val FRAGMENT_TAG_SETTINGS = "settings"
        private const val AGREEMENT = "agreement"
        const val PREFKEY = "1922qrcode"
        private const val TAG = "QRCodeFor1922_new_api"
        private const val VAILD_NUMBER = "1922"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private val VIBRATE_PATTERN = longArrayOf(500, 500)


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
    }

    private var mLastTriggerText: String = ""
    private var bAgreement = false
    private var bRedirectDialogShowing = false
    private var mAccompanyNum = 0

    private lateinit var mPref: SharedPreferences
    private lateinit var mBgThread: HandlerThread
    private lateinit var mHandler: BgHandler
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        try {
            mPref = getSharedPreferences(PREFKEY, MODE_PRIVATE)
        } catch (e: Exception) {
        }

        // Show agreement at first open
        showAgreement()

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Using another thread to delay trigger QRCode
        mBgThread = HandlerThread("timer")
        mBgThread.start()
        mHandler = BgHandler(mBgThread.looper)

        initAccompanyView()
    }

    private fun showAgreement() {
        bAgreement = mPref.getBoolean(AGREEMENT, false)
        if (!bAgreement) {
            val builder = AlertDialog.Builder(this)
            builder.setCancelable(false)
                .setTitle(getString(R.string.claim_title))
                .setMessage(getString(R.string.claim_mes))
                .setPositiveButton(
                    getString(R.string.agree)
                ) { _, _ ->
                    mPref.edit().putBoolean(AGREEMENT, true).apply()
                    // Request camera permissions
                    startCamera()
                }
                .setNegativeButton(
                    getString(R.string.not_agree)
                ) { _, _ ->
                    finish()
                }
                .show()
        } else {
            startCamera()
        }
    }

    private fun startCamera() {
        if (allPermissionsGranted()) {
            startCameraLock()
        } else {
            ActivityCompat.requestPermissions(
                this@MainActivity2, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun initAccompanyView() {
        val bar = findViewById<SeekBar>(R.id.with_family_bar)
        val view = findViewById<TextView>(R.id.with_family_view)
        val plus = findViewById<TextView>(R.id.plus_sign)
        view.setOnClickListener {
            showEnterNumberDialog(view)
        }
        plus.setOnClickListener {
            showEnterNumberDialog(view)
        }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                mAccompanyNum = progress
                view.text = mAccompanyNum.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }

        })
    }

    /**
     * Let user enter accompany number
     */
    private fun showEnterNumberDialog(view: TextView) {
        val editText = EditText(this)
        editText.inputType = InputType.TYPE_CLASS_NUMBER
        // Limit input length
        val filter = InputFilter.LengthFilter(2)
        editText.filters = arrayOf(filter)

        AlertDialog.Builder(this)
            .setCancelable(false)
            .setTitle(getString(R.string.enter_accompany_num))
            .setView(editText)
            .setPositiveButton(getString(R.string.dialog_confirm)) { _, _ ->
                editText.let {
                    if (it.text.isNotEmpty()) {
                        var num = it.text.toString().toInt()
                        if (num > 10) num = 10
                        mAccompanyNum = num
                    } else {
                        mAccompanyNum = 0
                    }
                    view.text = mAccompanyNum.toString()
                }
            }.show()

        // Keyboard not showing when dialog show. Trigger manually.
        editText.postDelayed({
            editText.requestFocus()
            val input = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            input.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }, 300)

    }

    private fun startCameraLock() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(1024, 768))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, QRCodeAnalyzer(callback))
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))

    }

    private fun copyToClipboard(text: String) {
        if (mPref.getBoolean(PREF_AUTO_COPY_TEXT, true)) {
            if (mPref.getBoolean(PREF_COPY_TEXT_VIBRATE, true)) {
                vibrate()
            }
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("simple text", text)
            clipboardManager.setPrimaryClip(clip)
            Toast.makeText(
                this,
                String.format(getString(R.string.copy_already), text),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun vibrate() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            v.vibrate(VIBRATE_PATTERN, -1)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCameraLock()
            } else {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.hint_permission_title))
                    .setMessage(getString(R.string.hint_permission_mes))
                    .setPositiveButton(getString(R.string.dialog_confirm)) { _, _ ->
                        finish()
                    }.show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.options_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.darkness -> {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("https://github.com/asadman1523/QRCodeFor1922/releases/")
                startActivity(intent)
            }
            R.id.settings -> {
                if (supportFragmentManager.findFragmentByTag(FRAGMENT_TAG_SETTINGS) == null) {
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.fragment_pref, SettingsPreference(), FRAGMENT_TAG_SETTINGS)
                        .addToBackStack(FRAGMENT_TAG_SETTINGS)
                        .commit()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private val callback = object : QRCodeListener {
        override fun invoke(barcodes: List<Barcode>) {
            if (barcodes.isNotEmpty()) {
                if (!mHandler.hasMessages(MSG_FORCE_TRIGGER)) {
                    mHandler.sendEmptyMessageDelayed(MSG_FORCE_TRIGGER, 1000)
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

    private fun triggerBarcode(barcode: Barcode) {
        val showSettings = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG_SETTINGS)
            .takeIf { it != null }?.isVisible ?: false
        if (barcode.rawValue == null || TextUtils.equals(mLastTriggerText, barcode.rawValue)
            || showSettings || bRedirectDialogShowing
        ) {
            return
        }

        // Filter 1922 number
        if (barcode.valueType == Barcode.TYPE_SMS) {
            if (mPref.getBoolean(PREF_VIBRATE_WHEN_SUCCESS, true)) {
                vibrate()
            }
            if (TextUtils.equals(barcode.sms?.phoneNumber, VAILD_NUMBER)) {
                val sendIntent = Intent(Intent.ACTION_SENDTO).apply {
                    type = "text/plain"
                    data = Uri.parse("smsto:${barcode.sms?.phoneNumber}")
                    var appendFamilyStr = ""
                    if (mAccompanyNum != 0) {
                        appendFamilyStr = "+$mAccompanyNum"
                    }
                    putExtra("sms_body", "${barcode.sms?.message} $appendFamilyStr")
                }
                startActivity(sendIntent)
                if (mPref.getBoolean(PREF_CLOSE_APP_AFTER_SCAN, false)) {
                    finish()
                }
            }
        } else {
            if (mPref.getBoolean(PREF_AUTO_OPEN_SCHEMA, false)) {
                val sendIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(barcode.rawValue)
                }
                if (packageManager?.queryIntentActivities(sendIntent, 0) != null) {
                    val dialog = MaterialAlertDialogBuilder(this@MainActivity2)
                    dialog.setTitle(getString(R.string.detect_schema))
                    dialog.setMessage(
                        String.format(
                            getString(R.string.confirm_open_schema),
                            barcode.rawValue
                        )
                    )
                    dialog.setPositiveButton(
                        getString(android.R.string.ok)
                    ) { dialog, which ->
                        bRedirectDialogShowing = false
                        startActivity(sendIntent)
                    }
                    dialog.setNeutralButton(
                        getString(R.string.copy_to_clipboard)
                    ) { dialog, which ->
                        bRedirectDialogShowing = false
                        copyToClipboard(barcode.rawValue)
                    }
                    dialog.setNegativeButton(
                        android.R.string.cancel
                    ) { _, _ -> bRedirectDialogShowing = false }
                    dialog.setOnCancelListener {
                        bRedirectDialogShowing = false
                    }
                    dialog.show()
                    bRedirectDialogShowing = true
                } else {
                    copyToClipboard(barcode.rawValue)
                }
            } else {
                copyToClipboard(barcode.rawValue)
            }
        }
        mHandler.postDelayed(Runnable {
            mLastTriggerText = ""
        }, 1500)
        mLastTriggerText = barcode.rawValue
    }
}