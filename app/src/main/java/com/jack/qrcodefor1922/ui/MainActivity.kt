package com.jack.qrcodefor1922.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.Size
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.mlkit.vision.barcode.common.Barcode
import com.jack.qrcodefor1922.QRCodeAnalyzer
import com.jack.qrcodefor1922.R
import com.jack.qrcodefor1922.SettingsPreference
import com.jack.qrcodefor1922.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


typealias QRCodeListener = (barcodes: List<Barcode>) -> Unit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        MobileAds.initialize(this) {}
        val adRequest = AdRequest.Builder().build()
        binding.adView.loadAd(adRequest)

        cameraExecutor = Executors.newSingleThreadExecutor()
        viewModel.showAgreement.observe(this) {
            it?.let {
                if (it) {
                    showAgreementDialog()
                }
            }
        }
        viewModel.vibrate.observe(this) {
            if (it == true) {
                vibrate()
            }
        }
        viewModel.startCamera.observe(this) {
            if (it == true) {
                startCamera()
            }
        }
        viewModel.copyAlready.observe(this) {
            if (it.isNotEmpty()) {
                Toast.makeText(
                    this,
                    String.format(getString(R.string.copy_already), it),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        viewModel.startActivity.observe(this) {
            it?.let {
                try {
                    startActivity(it)
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        getString(R.string.nothing_happen),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        viewModel.finishActivity.observe(this) {
            if (it == true) {
                finish()
            }
        }
        viewModel.showHistoryPrompt.observe(this) {
            if (it == true) {
                val builder = AlertDialog.Builder(this).apply {
                    setTitle(getString(R.string.new_feature))
                    setMessage(getString(R.string.history_feature_mes))
                    setPositiveButton(getText(R.string.dialog_confirm)
                    ) { _, _ -> viewModel.confirmNewFeature() }
                    setOnCancelListener { viewModel.confirmNewFeature() }
                }
                builder.show()
            }
        }
        viewModel.ready()
    }

    private fun showAgreementDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
            .setTitle(getString(R.string.claim_title))
            .setMessage(getString(R.string.claim_mes))
            .setPositiveButton(
                getString(R.string.agree)
            ) { _, _ ->
                viewModel.userAgree()
                viewModel.resetAgreement()
            }
            .setNegativeButton(
                getString(R.string.not_agree)
            ) { _, _ ->
                viewModel.resetAgreement()
                finish()
            }
            .show()
    }

    private fun startCamera() {
        if (allPermissionsGranted()) {
            startCameraLock()
        } else {
            ActivityCompat.requestPermissions(
                this@MainActivity, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
        viewModel.resetStartCamera()
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
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
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
                    viewModel.isSettingsShowing(true)
                }
            }
            R.id.history -> {
                if (supportFragmentManager.findFragmentByTag(FRAGMENT_TAG_HISTORY) == null) {
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.fragment_pref, ScanResultFragment(), FRAGMENT_TAG_HISTORY)
                        .addToBackStack(FRAGMENT_TAG_HISTORY)
                        .commit()
                    viewModel.isSettingsShowing(true)
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private val callback = object : QRCodeListener {
        override fun invoke(barcodes: List<Barcode>) {
            val isSettingsShow = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG_SETTINGS)
                .takeIf { it != null }?.isVisible ?: false
            val isHistoryShow = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG_HISTORY)
                .takeIf { it != null }?.isVisible ?: false
            viewModel.isSettingsShowing(isSettingsShow || isHistoryShow)
            viewModel.newBarcodes(barcodes)
        }
    }

    private fun vibrate() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            v.vibrate(VIBRATE_PATTERN, -1)
        }
        viewModel.resetVibrate()
    }

    companion object {
        const val PREFKEY = "1922qrcode"
        private const val TAG = "QRCodeFor1922_new_api"
        private const val FRAGMENT_TAG_SETTINGS = "settings"
        private const val FRAGMENT_TAG_HISTORY = "history"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val VIBRATE_PATTERN = longArrayOf(500, 500)
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}