package com.jack.qrcodefor1922

import android.Manifest
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.add
import androidx.fragment.app.commit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val AGREEMENT = "agreement"
        const val PREFKEY = "1922qrcode"
    }
    private var mAgreement = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val titlePostFix = resources.getString(R.string.app_name_postfix)
        title = "$title($titlePostFix)"

        val pref = getSharedPreferences(PREFKEY, MODE_PRIVATE)
        mAgreement = pref.getBoolean(AGREEMENT, false)
        if (!mAgreement) {
            val builder = AlertDialog.Builder(this)
            builder.setCancelable(false)
                    .setTitle(getString(R.string.claim_title))
                    .setMessage(getString(R.string.claim_mes))
                    .setPositiveButton(getString(R.string.agree),object :DialogInterface.OnClickListener{
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            pref.edit().putBoolean(AGREEMENT, true).apply()
                            addFragment()
                        }
                    })
                    .setNegativeButton(getString(R.string.not_agree), object :DialogInterface.OnClickListener{
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            finish()
                        }
                    })
                    .show()
        }
        requestPermission()

    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.SEND_SMS)
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result: MutableMap<String, Boolean> ->
                // 請求結果，返回一個map ，其中 key 為權限名稱，value 為是否權限是否賦予
                if (result[Manifest.permission.CAMERA] == true && result[Manifest.permission.SEND_SMS] == true) {
                    if (mAgreement) {
                        addFragment()
                    }
                }
            }.launch(permissions)
        } else {
            val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE)
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result: MutableMap<String, Boolean> ->
                // 請求結果，返回一個map ，其中 key 為權限名稱，value 為是否權限是否賦予
                if (result[Manifest.permission.CAMERA] == true && result[Manifest.permission.SEND_SMS] == true) {
                    if (mAgreement) {
                        addFragment()
                    }
                }
            }.launch(permissions)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    fun addFragment() {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            add<ScannerFragment>(R.id.fragment_container_view)
        }
    }
}