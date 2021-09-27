package com.jack.qrcodefor1922

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.core.view.setPadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.android.synthetic.main.activity_main2.*
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean


typealias QRCodeListener = (barcodes: List<Barcode>) -> Unit

class MainActivity2 : AppCompatActivity() {

    private val PREF_FRAGMENT_TAG = "settings"

    // porting from MainActivity
    private var mLastText: String = ""
    private var mAgreement = false
    private var bDialogShowing = false
    private var mWithFamilyNum = 0
    private lateinit var mPref: SharedPreferences
    private lateinit var mBgThread: HandlerThread
    private lateinit var mHandler: BgHandler


    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)
        try {
            mPref = getSharedPreferences(PREFKEY, MODE_PRIVATE)
        } catch (e: Exception) {
        }

        mAgreement = mPref.getBoolean(AGREEMENT, false)
        if (!mAgreement) {
            val builder = AlertDialog.Builder(this)
            builder.setCancelable(false)
                .setTitle(getString(R.string.claim_title))
                .setMessage(getString(R.string.claim_mes))
                .setPositiveButton(
                    getString(R.string.agree)
                ) { _, _ ->
                    mPref.edit().putBoolean(AGREEMENT, true).apply()
                    // Request camera permissions
                    if (allPermissionsGranted()) {
                        startCamera()
                    } else {
                        ActivityCompat.requestPermissions(
                            this@MainActivity2, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                        )
                    }
                }
                .setNegativeButton(
                    getString(R.string.not_agree)
                ) { _, _ ->
                    finish()
                }
                .show()
        } else {
            // Request camera permissions
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                )
            }
        }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()

        mBgThread = HandlerThread("timer")
        mBgThread.start()
        mHandler = BgHandler(mBgThread.looper)
        initWithFamily()

    }

    private fun initWithFamily() {
        val bar = findViewById<SeekBar>(R.id.with_family_bar)
        val view = findViewById<TextView>(R.id.with_family_view)
        val plus = findViewById<TextView>(R.id.plus_sign)
        view.setOnClickListener {
            showEdittextDialog(view)
        }
        plus.setOnClickListener {
            showEdittextDialog(view)
        }
        bar.setOnSeekBarChangeListener(object :SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                mWithFamilyNum = progress
                view.text = mWithFamilyNum.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }

        })
    }

    private fun showEdittextDialog(view: TextView) {
        val editText = EditText(this)
        editText.inputType = InputType.TYPE_CLASS_NUMBER
        val filter = InputFilter.LengthFilter(2)
        editText.filters = arrayOf(filter)

        AlertDialog.Builder(this)
            .setCancelable(false)
            .setTitle(getString(R.string.enter_accompany_num))
            .setView(editText)
            .setPositiveButton(getString(R.string.dialog_confirm)){_,_->
                editText.let {
                    if (it.text.isNotEmpty()) {
                        var num = it.text.toString().toInt()
                        if (num > 10) num = 10
                        mWithFamilyNum = num
                    } else {
                        mWithFamilyNum = 0
                    }
                    view.text = mWithFamilyNum.toString()
                }
            }.show()

        editText.postDelayed({
            editText.requestFocus()
            val input = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            input.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }, 300)

    }

    private fun startCamera() {

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

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val AGREEMENT = "agreement"
        const val PREFKEY = "1922qrcode"
        private const val TAG = "QRCodeFor1922_new_api"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

        // porting from MainActivity
        private val VIBRATE_PATTERN = longArrayOf(500, 500)
        private val PREFIX = "smsto"
        private val VAILD_NUMBER = "1922"
        private const val PREF_CLOSE_APP_AFTER_SCAN = "close_after_scan"
        private const val PREF_VIBRATE_WHEN_SUCCESS = "vibrate_when_success"
        private const val PREF_AUTO_OPEN_SCHEMA = "auto_open_identify_schema"
        private const val PREF_AUTO_COPY_TEXT = "auto_copy_non_1922"
        private const val PREF_COPY_TEXT_VIBRATE = "vibrate_when_copy_text_success"
        private val MSG_FORCE_TRIGGER = 1
        private val MSG_COOLING_TIME = 2
        private val mForeTrigger = AtomicBoolean(false)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private class QRCodeAnalyzer(private val listener: QRCodeListener) : ImageAnalysis.Analyzer {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC
            )
            .build()
        val scanner = BarcodeScanning.getClient(options)

        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {


            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

//                Log.d(TAG, "${image.width} ${image.height}")
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        listener(barcodes)
                    }
                    .addOnFailureListener {
                        // Task failed with an exception
                        // ...

                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }

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
                if (supportFragmentManager.findFragmentByTag(PREF_FRAGMENT_TAG) == null) {
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.fragment_pref, SettingsPreference(), PREF_FRAGMENT_TAG)
                        .addToBackStack(PREF_FRAGMENT_TAG)
                        .commit()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    val callback = object : QRCodeListener {
        override fun invoke(barcodes: List<Barcode>) {
            if (barcodes.isNotEmpty()) {
                if (!mHandler.hasMessages(MSG_FORCE_TRIGGER)) {
                    mHandler.sendEmptyMessageDelayed(MSG_FORCE_TRIGGER, 1000)
                    mHandler.sendEmptyMessageDelayed(MSG_COOLING_TIME, 4000)
                }
                // If not force trigger then wait 1922 barcode in 1 second
                if (!mForeTrigger.get()) {
                    for (barcode in barcodes) {
                        if (barcode.valueType == Barcode.TYPE_SMS
                            && TextUtils.equals(barcode.sms?.phoneNumber, VAILD_NUMBER)) {
                            mHandler.removeMessages(MSG_FORCE_TRIGGER)
                            mHandler.removeMessages(MSG_COOLING_TIME)
                            triggerBarcode(barcode)
                        }
                    }
                } else {
                    // Trigger directly
                    triggerBarcode(barcodes.first())
                }
            }
        }
    }

    private class BgHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when(msg.what) {
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
        val showSettings = supportFragmentManager.findFragmentByTag(PREF_FRAGMENT_TAG)
            .takeIf { it != null }?.isVisible ?: false
        if (barcode.rawValue == null || TextUtils.equals(mLastText, barcode.rawValue)
            || showSettings || bDialogShowing){
            return
        }

        // Filter 1922
        if (barcode.valueType == Barcode.TYPE_SMS) {
            // Vibrate
            if (mPref.getBoolean(PREF_VIBRATE_WHEN_SUCCESS, true)) {
                vibrate()
            }
            if (TextUtils.equals(barcode.sms.phoneNumber, VAILD_NUMBER)) {
                val sendIntent = Intent(Intent.ACTION_SENDTO).apply {
                    type = "text/plain"
                    data = Uri.parse("smsto:${barcode.sms.phoneNumber}")
                    var appendFamilyStr = ""
                    if (mWithFamilyNum != 0) {
                        appendFamilyStr = "+$mWithFamilyNum"
                    }
                    putExtra("sms_body", "${barcode.sms.message} $appendFamilyStr")
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
                        bDialogShowing = false
                        startActivity(sendIntent) }
                    dialog.setNeutralButton(
                        getString(R.string.copy_to_clipboard)
                    ) { dialog, which ->
                        bDialogShowing = false
                        copyToClipboard(barcode.rawValue) }
                    dialog.setNegativeButton(android.R.string.cancel
                    ) { _, _ -> bDialogShowing = false }
                    dialog.setOnCancelListener {
                        bDialogShowing = false
                    }
                    dialog.show()
                    bDialogShowing = true
                } else {
                    copyToClipboard(barcode.rawValue)
                }
            } else {
                copyToClipboard(barcode.rawValue)
            }
        }
        mHandler.postDelayed(Runnable {
            mLastText = ""
        }, 1500)
        mLastText = barcode.rawValue
    }
}