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

    private val AGREEMENT = "agreement"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestPermission()
        val pref = getSharedPreferences("1922qrcode", MODE_PRIVATE)
        val agree = pref.getBoolean(AGREEMENT, false)
        if (!agree) {
            val builder = AlertDialog.Builder(this)
            builder.setCancelable(false)
                    .setTitle(getString(R.string.claim_title))
                    .setMessage(getString(R.string.claim_mes))
                    .setPositiveButton(getString(R.string.agree),object :DialogInterface.OnClickListener{
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            pref.edit().putBoolean(AGREEMENT, true).apply()
                        }
                    })
                    .setNegativeButton(getString(R.string.not_agree), object :DialogInterface.OnClickListener{
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            finish()
                        }
                    })
                    .show()
        }


    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.SEND_SMS)
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result: MutableMap<String, Boolean> ->
                // 請求結果，返回一個map ，其中 key 為權限名稱，value 為是否權限是否賦予
                if (result[Manifest.permission.CAMERA] == true && result[Manifest.permission.SEND_SMS] == true) {
                    supportFragmentManager.commit {
                        setReorderingAllowed(true)
                        add<ScannerFragment>(R.id.fragment_container_view)
                    }
                }

            }.launch(permissions)
        } else {
            val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE)
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result: MutableMap<String, Boolean> ->
                // 請求結果，返回一個map ，其中 key 為權限名稱，value 為是否權限是否賦予
                if (result[Manifest.permission.CAMERA] == true && result[Manifest.permission.SEND_SMS] == true) {
                    supportFragmentManager.commit {
                        setReorderingAllowed(true)
                        add<ScannerFragment>(R.id.fragment_container_view)
                    }
                }

            }.launch(permissions)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}