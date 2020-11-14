package com.framgia.android.emulator

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.*

@Suppress("DEPRECATION", "unused", "MemberVisibilityCanBePrivate")
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
class EmulatorDetector private constructor(private val context: Context) {

    var isDebug = false
        private set
    var isCheckTelephony = false
        private set
    var isCheckPackage = true
        private set
    private val listPackageName: MutableList<String> = ArrayList()

    init {
        listPackageName.add("com.google.android.launcher.layouts.genymotion")
        listPackageName.add("com.bluestacks")
        listPackageName.add("com.bignox.app")
    }

    fun setDebug(isDebug: Boolean): EmulatorDetector {
        this.isDebug = isDebug
        return this
    }

    fun setCheckTelephony(telephony: Boolean): EmulatorDetector {
        isCheckTelephony = telephony
        return this
    }

    fun setCheckPackage(chkPackage: Boolean): EmulatorDetector {
        isCheckPackage = chkPackage
        return this
    }

    fun addPackageName(pPackageName: String): EmulatorDetector {
        listPackageName.add(pPackageName)
        return this
    }

    fun addPackageName(pListPackageName: List<String>?): EmulatorDetector {
        listPackageName.addAll(pListPackageName!!)
        return this
    }

    val packageNameList: List<String>
        get() = listPackageName

    fun detect(pOnEmulatorDetectorListener: OnEmulatorDetectorListener?) {
        Thread {
            val isEmulator = detect()
            log("This System is Emulator: $isEmulator")
            pOnEmulatorDetectorListener?.onResult(isEmulator)
        }.start()
    }

    private fun detect(): Boolean {
        var result = false
        log(deviceInfo)

        // Check Basic
        if (!result) {
            result = checkBasic()
            log("Check basic $result")
        }

        // Check Advanced
        if (!result) {
            result = checkAdvanced()
            log("Check Advanced $result")
        }

        // Check Package Name
        if (!result) {
            result = checkPackageName()
            log("Check Package Name $result")
        }
        return result
    }

    @SuppressLint("HardwareIds")
    private fun checkBasic(): Boolean {
        var result = (Build.FINGERPRINT.startsWith("generic")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.toLowerCase(Locale.ROOT).contains("droid4x")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HARDWARE == "goldfish" || Build.HARDWARE == "vbox86" || Build.PRODUCT == "sdk" || Build.PRODUCT == "google_sdk" || Build.PRODUCT == "sdk_x86" || Build.PRODUCT == "vbox86p" || Build.BOARD.toLowerCase(Locale.ROOT).contains("nox")
                || Build.BOOTLOADER.toLowerCase(Locale.ROOT).contains("nox")
                || Build.HARDWARE.toLowerCase(Locale.ROOT).contains("nox")
                || Build.PRODUCT.toLowerCase(Locale.ROOT).contains("nox")
                || Build.SERIAL.toLowerCase(Locale.ROOT).contains("nox"))
        if (result) return true
        result = result or (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
        if (result) return true
        result = "google_sdk" == Build.PRODUCT
        return result
    }

    private fun checkAdvanced(): Boolean {
        return (checkTelephony()
                || checkFiles(GENY_FILES, "Geny")
                || checkFiles(ANDY_FILES, "Andy")
                || checkFiles(NOX_FILES, "Nox")
                || checkQEmuDrivers()
                || checkFiles(PIPES, "Pipes")
                || checkIp()
                || checkQEmuProps() && checkFiles(X86_FILES, "X86"))
    }

    private fun checkPackageName(): Boolean {
        if (!isCheckPackage || listPackageName.isEmpty()) {
            return false
        }
        val packageManager = context.packageManager
        for (pkgName in listPackageName) {
            val tryIntent = packageManager.getLaunchIntentForPackage(pkgName)
            if (tryIntent != null) {
                val resolveInfos = packageManager.queryIntentActivities(tryIntent, PackageManager.MATCH_DEFAULT_ONLY)
                if (resolveInfos.isNotEmpty()) {
                    return true
                }
            }
        }
        return false
    }

    private fun checkTelephony(): Boolean {
        return if ((ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                        == PackageManager.PERMISSION_GRANTED) && isCheckTelephony && isSupportTelePhony) {
            (checkPhoneNumber()
                    || checkDeviceId()
                    || checkImsi()
                    || checkOperatorNameAndroid())
        } else false
    }

    private fun checkPhoneNumber(): Boolean {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        try {
            @SuppressLint("HardwareIds", "MissingPermission") val phoneNumber = telephonyManager.line1Number
            for (number in PHONE_NUMBERS) {
                if (number.equals(phoneNumber, ignoreCase = true)) {
                    log(" check phone number is detected")
                    return true
                }
            }
        } catch (e: Exception) {
            log("No permission to detect access of Line1Number")
        }
        return false
    }

    private fun checkDeviceId(): Boolean {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        try {
            @SuppressLint("HardwareIds", "MissingPermission") val deviceId = telephonyManager.deviceId
            for (known_deviceId in DEVICE_IDS) {
                if (known_deviceId.equals(deviceId, ignoreCase = true)) {
                    log("Check device id is detected")
                    return true
                }
            }
        } catch (e: Exception) {
            log("No permission to detect access of DeviceId")
        }
        return false
    }

    private fun checkImsi(): Boolean {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        try {
            @SuppressLint("HardwareIds", "MissingPermission") val imsi = telephonyManager.subscriberId
            for (known_imsi in IMSI_IDS) {
                if (known_imsi.equals(imsi, ignoreCase = true)) {
                    log("Check imsi is detected")
                    return true
                }
            }
        } catch (e: Exception) {
            log("No permission to detect access of SubscriberId")
        }
        return false
    }

    private fun checkOperatorNameAndroid(): Boolean {
        val operatorName = (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).networkOperatorName
        if (operatorName.equals("android", ignoreCase = true)) {
            log("Check operator name android is detected")
            return true
        }
        return false
    }

    private fun checkQEmuDrivers(): Boolean {
        for (drivers_file in arrayOf(File("/proc/tty/drivers"), File("/proc/cpuinfo"))) {
            if (drivers_file.exists() && drivers_file.canRead()) {
                val data = ByteArray(1024)
                try {
                    val `is`: InputStream = FileInputStream(drivers_file)
                    `is`.read(data)
                    `is`.close()
                } catch (exception: Exception) {
                    exception.printStackTrace()
                }
                val driverData = String(data)
                for (known_qemu_driver in QEMU_DRIVERS) {
                    if (driverData.contains(known_qemu_driver)) {
                        log("Check QEmuDrivers is detected")
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun checkFiles(targets: Array<String>, type: String): Boolean {
        for (pipe in targets) {
            val qemuFile = File(pipe)
            if (qemuFile.exists()) {
                log("Check $type is detected")
                return true
            }
        }
        return false
    }

    private fun checkQEmuProps(): Boolean {
        var foundProps = 0
        for (property in PROPERTIES) {
            val propertyValue = getProp(context, property.name)
            if (property.seek_value == null && propertyValue != null) {
                foundProps++
            }

            property.seek_value?.let {
                if (propertyValue!!.contains(it)) {
                    foundProps++
                }
            }
        }
        if (foundProps >= MIN_PROPERTIES_THRESHOLD) {
            log("Check QEmuProps is detected")
            return true
        }
        return false
    }

    private fun checkIp(): Boolean {
        var ipDetected = false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET)
                == PackageManager.PERMISSION_GRANTED) {
            val args = arrayOf("/system/bin/netcfg")
            val stringBuilder = StringBuilder()
            try {
                val builder = ProcessBuilder(*args)
                builder.directory(File("/system/bin/"))
                builder.redirectErrorStream(true)
                val process = builder.start()
                val `in` = process.inputStream
                val re = ByteArray(1024)
                while (`in`.read(re) != -1) {
                    stringBuilder.append(String(re))
                }
                `in`.close()
            } catch (ex: Exception) {
                // empty catch
            }
            val netData = stringBuilder.toString()
            log("netcfg data -> $netData")
            if (!TextUtils.isEmpty(netData)) {
                val array = netData.split("\n").toTypedArray()
                for (lan in array) {
                    if ((lan.contains("wlan0") || lan.contains("tunl0") || lan.contains("eth0"))
                            && lan.contains(IP)) {
                        ipDetected = true
                        log("Check IP is detected")
                        break
                    }
                }
            }
        }
        return ipDetected
    }

    @SuppressLint("PrivateApi")
    private fun getProp(context: Context, property: String): String? {
        try {
            val classLoader = context.classLoader
            val systemProperties = classLoader.loadClass("android.os.SystemProperties")
            val get = systemProperties.getMethod("get", String::class.java)
            val params = arrayOfNulls<Any>(1)
            params[0] = property
            return get.invoke(systemProperties, *params) as String
        } catch (exception: Exception) {
            // empty catch
        }
        return null
    }

    private val isSupportTelePhony: Boolean
        get() {
            val packageManager = context.packageManager
            val isSupport = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
            log("Supported TelePhony: $isSupport")
            return isSupport
        }

    private fun log(str: String) {
        if (isDebug) {
            Log.d(javaClass.name, str)
        }
    }

    companion object {
        private val PHONE_NUMBERS = arrayOf(
                "15555215554", "15555215556", "15555215558", "15555215560", "15555215562", "15555215564",
                "15555215566", "15555215568", "15555215570", "15555215572", "15555215574", "15555215576",
                "15555215578", "15555215580", "15555215582", "15555215584"
        )
        private val DEVICE_IDS = arrayOf(
                "000000000000000",
                "e21833235b6eef10",
                "012345678912345"
        )
        private val IMSI_IDS = arrayOf(
                "310260000000000"
        )
        private val GENY_FILES = arrayOf(
                "/dev/socket/genyd",
                "/dev/socket/baseband_genyd"
        )
        private val QEMU_DRIVERS = arrayOf("goldfish")
        private val PIPES = arrayOf(
                "/dev/socket/qemud",
                "/dev/qemu_pipe"
        )
        private val X86_FILES = arrayOf(
                "ueventd.android_x86.rc",
                "x86.prop",
                "ueventd.ttVM_x86.rc",
                "init.ttVM_x86.rc",
                "fstab.ttVM_x86",
                "fstab.vbox86",
                "init.vbox86.rc",
                "ueventd.vbox86.rc"
        )
        private val ANDY_FILES = arrayOf(
                "fstab.andy",
                "ueventd.andy.rc"
        )
        private val NOX_FILES = arrayOf(
                "fstab.nox",
                "init.nox.rc",
                "ueventd.nox.rc"
        )
        private val PROPERTIES = arrayOf(
                Property("init.svc.qemud", null),
                Property("init.svc.qemu-props", null),
                Property("qemu.hw.mainkeys", null),
                Property("qemu.sf.fake_camera", null),
                Property("qemu.sf.lcd_density", null),
                Property("ro.bootloader", "unknown"),
                Property("ro.bootmode", "unknown"),
                Property("ro.hardware", "goldfish"),
                Property("ro.kernel.android.qemud", null),
                Property("ro.kernel.qemu.gles", null),
                Property("ro.kernel.qemu", "1"),
                Property("ro.product.device", "generic"),
                Property("ro.product.model", "sdk"),
                Property("ro.product.name", "sdk"),
                Property("ro.serialno", null)
        )
        private const val IP = "10.0.2.15"
        private const val MIN_PROPERTIES_THRESHOLD = 0x5

        @SuppressLint("StaticFieldLeak") //Since we use application context now this won't leak memory anymore. This is only to please Lint
        private var emulatorDetector: EmulatorDetector? = null

        @JvmStatic
        fun with(context: Context): EmulatorDetector {
            if (emulatorDetector == null)
                emulatorDetector = EmulatorDetector(context.applicationContext)
            return emulatorDetector!!
        }

        @JvmStatic
        val deviceInfo: String
            get() = """
                   Build.PRODUCT: ${Build.PRODUCT}
                   Build.MANUFACTURER: ${Build.MANUFACTURER}
                   Build.BRAND: ${Build.BRAND}
                   Build.DEVICE: ${Build.DEVICE}
                   Build.MODEL: ${Build.MODEL}
                   Build.HARDWARE: ${Build.HARDWARE}
                   Build.FINGERPRINT: ${Build.FINGERPRINT}
                   """.trimIndent()
    }

}
