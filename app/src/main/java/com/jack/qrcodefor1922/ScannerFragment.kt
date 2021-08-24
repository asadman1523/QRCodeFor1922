package com.jack.qrcodefor1922

import android.content.*
import android.net.Uri
import android.os.*
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import java.util.*


class ScannerFragment : Fragment() {
    companion object {
        private val VIBRATE_PATTERN = longArrayOf(500, 500)
        private val PREFIX = "smsto"
        private val VAILD_NUMBER = "1922"
        private const val PREF_CLOSE_APP_AFTER_SCAN = "close_after_scan"
        private const val PREF_VIBRATE_WHEN_SUCCESS = "vibrate_when_success"
        private const val PREF_AUTO_OPEN_SCHEMA = "auto_open_identify_schema"
    }

    private lateinit var mBarcodeScannerView: DecoratedBarcodeView
    private var mLastText: String = ""
    private lateinit var mPref: SharedPreferences
    private lateinit var mBgThread: HandlerThread
    private lateinit var mHandler: Handler
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBgThread = HandlerThread("timer")
        mBgThread.start()
        mHandler = Handler(mBgThread.looper)
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_scanner, container, false)
        mBarcodeScannerView = view.findViewById(R.id.barcodeview)
        val formats: Collection<BarcodeFormat> = listOf(BarcodeFormat.QR_CODE)
        mBarcodeScannerView.barcodeView.decoderFactory = DefaultDecoderFactory(formats)
        mBarcodeScannerView.barcodeView.decodeContinuous(callback)
        try {
            mPref = requireContext().getSharedPreferences(MainActivity.PREFKEY, AppCompatActivity.MODE_PRIVATE)
        } catch (e: Exception) {
        }

        return view
    }

    private val callback = BarcodeCallback() {
        if (it.text == null || it.text == mLastText) {
            // Prevent duplicate scans
            return@BarcodeCallback
        }
        if (mPref.getBoolean(PREF_VIBRATE_WHEN_SUCCESS, true)) {
            val v = context?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v.vibrate(VIBRATE_PATTERN, -1)
            }
        }
        mHandler.postDelayed(Runnable {
            mLastText = ""
        }, 3000)
        mLastText = it.text
        val tmpText = it.text
        val arr = tmpText.split(":")
        val d = Uri.parse(it.text)
        if (arr.size >= 3 && arr[0].lowercase().contains(PREFIX) &&
            TextUtils.equals(arr[1], VAILD_NUMBER)) {
            val num = arr[1]
            var mes = arr[2]
            val sendIntent = Intent(Intent.ACTION_SENDTO).apply {
                type = "text/plain"
                data = Uri.parse("$PREFIX:$num")
                putExtra("sms_body", mes)
            }
            startActivity(sendIntent)
            if (mPref.getBoolean(PREF_CLOSE_APP_AFTER_SCAN, false)) {
                activity?.finish()
            }

        } else {
            if (mPref.getBoolean(PREF_AUTO_OPEN_SCHEMA, false)) {
                if (context != null) {
                    val sendIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(it.text)
                    }
                    if (context?.packageManager?.queryIntentActivities(sendIntent, 0) != null) {
                        val dialog = MaterialAlertDialogBuilder(requireContext())
                        dialog.setTitle(getString(R.string.detect_schema))
                        dialog.setMessage(String.format(getString(R.string.confirm_open_schema), it.text))
                        dialog.setPositiveButton(getString(android.R.string.ok)
                        ) { dialog, which -> startActivity(sendIntent) }
                        dialog.setNeutralButton(getString(R.string.copy_to_clipboard)
                        ) { dialog, which -> copyToClipboard(it.text) }
                        dialog.setNegativeButton(android.R.string.cancel, null)
                        dialog.show()
                    } else {
                        copyToClipboard(it.text)
                    }
                }
            } else {
                copyToClipboard(it.text)
            }
        }
    }

    private fun copyToClipboard(text:String) {
        val clipboardManager = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip: ClipData = ClipData.newPlainText("simple text", text)
        clipboardManager.setPrimaryClip(clip)
        Toast.makeText(context, String.format(getString(R.string.copy_already), text), Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        mBarcodeScannerView.resume()
    }

    override fun onPause() {
        super.onPause()
        mBarcodeScannerView.pause()
        mLastText = ""
        // To prevent vibrate infinite on some device
        val v = context?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.cancel()
    }
}