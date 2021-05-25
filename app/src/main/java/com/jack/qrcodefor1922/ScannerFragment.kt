package com.jack.qrcodefor1922

import android.R.attr.phoneNumber
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import java.util.*


class ScannerFragment : Fragment() {
    private lateinit var barcodeScannerView: DecoratedBarcodeView
    private var lastText: String = ""
    private val prefix = "smsto:"
    private lateinit var bgThread: HandlerThread
    private lateinit var handler: Handler
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bgThread = HandlerThread("timer")
        bgThread.start()
        handler = Handler(bgThread.looper)
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_scanner, container, false)
        barcodeScannerView = view.findViewById(R.id.barcodeview)
        val formats: Collection<BarcodeFormat> = listOf(BarcodeFormat.QR_CODE)
        barcodeScannerView.barcodeView.decoderFactory = DefaultDecoderFactory(formats)
        barcodeScannerView.barcodeView.decodeContinuous(callback)
        return view
    }

    private val callback = BarcodeCallback() {
        if(it.text == null || it.text == lastText) {
            // Prevent duplicate scans
            return@BarcodeCallback
        }
        handler.postDelayed(Runnable {
            lastText = ""
        }, 3000)
        lastText = it.text
        val tmpText = it.text.toLowerCase()
        val arr = tmpText.split(":")
        if (tmpText.contains(prefix) && arr.size > 2) {
            val num = arr[1].toIntOrNull()
            val mes = arr[2].toString()
            val sendIntent = Intent(Intent.ACTION_SENDTO).apply {
                type = "text/plain"
                data = Uri.parse("$prefix$num" )
                putExtra("sms_body", mes)
            }
            startActivity(sendIntent)

        } else {
            val clipboardManager = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("simple text", tmpText)
            clipboardManager.setPrimaryClip(clip)
            Toast.makeText(context, String.format(getString(R.string.copy_already), tmpText), Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        barcodeScannerView.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeScannerView.pause()
        lastText = ""
    }

    companion object {
        val REQUEST_CODE = 20210523
        val ACTION_SEND_SUCCESS = "action_send_success"
    }
}