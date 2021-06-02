package com.jack.qrcodefor1922

import android.content.*
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import java.lang.Exception
import java.util.*


class ScannerFragment : Fragment() {
    companion object {
        private val VIBRATE_PATTERN = longArrayOf(500, 500)
        private val PREFIX = "smsto:"
        private const val PREF_CLOSE_APP_AFTER_SCAN = "close_after_scan"
    }

    private lateinit var mBarcodeScannerView: DecoratedBarcodeView
    private lateinit var mCloseAppCheckBox: CheckBox
    private var mLastText: String = ""
    private lateinit var mPref: SharedPreferences
    private lateinit var mBgThread: HandlerThread
    private lateinit var mHandler: Handler
    private var mCloseApp = true
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
        mCloseAppCheckBox = view.findViewById(R.id.close_check_bot)
        try {
            mPref = requireContext().getSharedPreferences(MainActivity.PREFKEY, AppCompatActivity.MODE_PRIVATE)
        } catch (e: Exception) {
        }

        updateOptions()
        return view
    }

    private fun updateOptions() {
        mCloseAppCheckBox.isChecked = mPref.getBoolean(PREF_CLOSE_APP_AFTER_SCAN, true)
        mCloseAppCheckBox.setOnCheckedChangeListener { box, closeApp ->
            Log.v("QQAQ", "${box.isChecked}")
            mCloseApp = closeApp
        }
    }

    private val callback = BarcodeCallback() {
        if (it.text == null || it.text == mLastText) {
            // Prevent duplicate scans
            return@BarcodeCallback
        }
        val v = context?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            v.vibrate(VIBRATE_PATTERN, -1)
        }
        mHandler.postDelayed(Runnable {
            mLastText = ""
        }, 3000)
        mLastText = it.text
        val tmpText = it.text.toLowerCase()
        val arr = tmpText.split(":")
        if (tmpText.contains(PREFIX) && arr.size > 2) {
            val num = arr[1]
            var mes = arr[2]
            val sendIntent = Intent(Intent.ACTION_SENDTO).apply {
                type = "text/plain"
                data = Uri.parse("$PREFIX$num")
                putExtra("sms_body", mes)
            }
            startActivity(sendIntent)
            if (mCloseApp) {
                activity?.finish()
            }

        } else {
            val clipboardManager = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("simple text", tmpText)
            clipboardManager.setPrimaryClip(clip)
            Toast.makeText(context, String.format(getString(R.string.copy_already), tmpText), Toast.LENGTH_LONG).show()
        }
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
        Log.v("QQAQ", "final ${mCloseApp}")
        mPref.edit().putBoolean(PREF_CLOSE_APP_AFTER_SCAN, mCloseApp).apply()
    }
}