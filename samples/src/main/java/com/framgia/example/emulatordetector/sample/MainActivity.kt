package com.framgia.example.emulatordetector.sample

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.framgia.android.emulator.BuildConfig
import com.framgia.android.emulator.EmulatorDetector
import com.framgia.android.emulator.EmulatorDetector.Companion.deviceInfo
import com.framgia.android.emulator.EmulatorDetector.Companion.with
import com.framgia.android.emulator.OnEmulatorDetectorListener
import kotlinx.android.synthetic.main.activity_main.*
import permissions.dispatcher.*

@RuntimePermissions
class MainActivity : AppCompatActivity() {
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textMain.text = "Checking...."

        // Last check
        // BlueStacksPlayer
        // NoxPlayer
        // KoPlayer
        // MEmu
        MainActivityPermissionsDispatcher.checkEmulatorDetectorWithPermissionCheck(this)
    }

    @NeedsPermission(Manifest.permission.READ_PHONE_STATE)
    fun checkEmulatorDetector() {
        checkWith(true)
    }

    @OnShowRationale(Manifest.permission.READ_PHONE_STATE)
    fun showRationaleForCamera(request: PermissionRequest) {
        AlertDialog.Builder(this)
                .setMessage("Need READ_PHONE_STATE permission for check with Telephony function")
                .setPositiveButton("Allow") { dialog: DialogInterface?, button: Int -> request.proceed() }
                .setNegativeButton("Deny") { dialog: DialogInterface?, button: Int -> request.cancel() }
                .show()
    }

    private fun checkWith(telephony: Boolean) {
        EmulatorDetector.with(this)
                .setCheckTelephony(telephony)
                .addPackageName("com.bluestacks")
                .setDebug(true)
                .detect(object : OnEmulatorDetectorListener {
                    @SuppressLint("SetTextI18n")
                    override fun onResult(isEmulator: Boolean) {
                        runOnUiThread {
                            if (isEmulator) {
                                textMain.text = "This device is emulator$checkInfo"
                            } else {
                                textMain.text = "This device is not emulator$checkInfo"
                            }
                        }
                        Log.d(javaClass.name, "Running on emulator --> $isEmulator")
                    }
                })
    }

    @OnPermissionDenied(Manifest.permission.READ_PHONE_STATE)
    fun showDeniedForCamera() {
        checkWith(false)
        Toast.makeText(this, "We check without Telephony function", Toast.LENGTH_SHORT).show()
    }

    @OnNeverAskAgain(Manifest.permission.READ_PHONE_STATE)
    fun showNeverAskForCamera() {
        Toast.makeText(this, "Never check with Telephony function", Toast.LENGTH_SHORT).show()
    }

    private val checkInfo: String
        get() = """

            Telephony enable is ${with(this@MainActivity).isCheckTelephony}
            $deviceInfo
            Emulator Detector version ${BuildConfig.VERSION_NAME}
            """.trimIndent()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }
}