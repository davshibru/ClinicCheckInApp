package com.advmeds.cliniccheckinapp.ui

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.MotionEvent
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.advmeds.cardreadermodule.AcsResponseModel
import com.advmeds.cardreadermodule.UsbDeviceCallback
import com.advmeds.cardreadermodule.acs.usb.AcsUsbDevice
import com.advmeds.cardreadermodule.acs.usb.decoder.*
import com.advmeds.cardreadermodule.castles.CastlesUsbDevice
import com.advmeds.cliniccheckinapp.BuildConfig
import com.advmeds.cliniccheckinapp.R
import com.advmeds.cliniccheckinapp.databinding.ActivityMainBinding
import com.advmeds.cliniccheckinapp.dialog.CheckingDialogFragment
import com.advmeds.cliniccheckinapp.dialog.ErrorDialogFragment
import com.advmeds.cliniccheckinapp.dialog.SuccessDialogFragment
import com.advmeds.cliniccheckinapp.models.remote.mScheduler.response.GetPatientsResponse
import com.advmeds.printerlib.usb.BPT3XPrinterService
import com.advmeds.printerlib.usb.UsbPrinterService
import com.advmeds.printerlib.utils.PrinterBuffer
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val USB_PERMISSION = "${BuildConfig.APPLICATION_ID}.USB_PERMISSION"
//        private const val SCAN_TIME_OUT: Long = 15
    }

    private val viewModel: MainViewModel by viewModels()

    private lateinit var binding: ActivityMainBinding

    private val detectUsbDeviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val usbDevice = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE) ?: return

            when (intent.action) {
                USB_PERMISSION -> {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        // user choose YES for your previously popup window asking for grant permission for this usb device
                        when (usbDevice.productId) {
                            acsUsbDevice.supportedDevice?.productId -> {
                                acsUsbDevice.connectDevice(usbDevice)
                            }
                            ezUsbDevice.supportedDevice?.productId -> {
                                ezUsbDevice.connectDevice(usbDevice)
                            }
                            usbPrinterService.supportedDevice?.productId -> {
                                try {
                                    usbPrinterService.connectDevice(usbDevice)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Snackbar.make(binding.root, "Fail to connect the usb printer.", Snackbar.LENGTH_LONG).show()
                                }
                            }
                        }
                    } else {
                        // user choose NO for your previously popup window asking for grant permission for this usb device
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    connectUSBDevice(usbDevice)
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    when (usbDevice.productId) {
                        acsUsbDevice.connectedDevice?.productId -> {
                            acsUsbDevice.disconnect()
                        }
                        ezUsbDevice.connectedDevice?.productId -> {
                            ezUsbDevice.disconnect()
                        }
                        usbPrinterService.connectedDevice?.productId -> {
                            usbPrinterService.disconnect()
                        }
                    }
                }
            }
        }
    }

    private lateinit var usbManager: UsbManager

    private lateinit var acsUsbDevice: AcsUsbDevice

    private lateinit var ezUsbDevice: CastlesUsbDevice

    private val usbDeviceCallback = object : UsbDeviceCallback {
        override fun onCardPresent() {

        }

        override fun onCardAbsent() {

        }

        override fun onConnectDevice() {

        }

        override fun onFailToConnectDevice() {
            Snackbar.make(binding.root, "Fail to connect usb card reader.", Snackbar.LENGTH_LONG).show()
        }

        override fun onReceiveResult(result: Result<AcsResponseModel>) {
            result.onSuccess {
                if (!usbPrinterService.isConnected) {
                    Snackbar.make(binding.root, "The printer is disconnect.", Snackbar.LENGTH_LONG).show()
                    return
                }
                
                getPatients(it.icId)
            }.onFailure {
                it.message?.let { it1 ->
                    Snackbar.make(binding.root, it1, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

//    /** 確認使用者授權的Callback */
//    private val bleForResult =
//        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
//            if (result.values.find { !it } == null) {
//                enableBluetoothService()
//            }
//        }

//    /** Create a BroadcastReceiver for ACTION_STATE_CHANGED. */
//    private val detectBluetoothStateReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent) {
//            when(intent.action) {
//                BluetoothAdapter.ACTION_STATE_CHANGED -> {
//                    // Discovery has found a device. Get the BluetoothDevice
//                    // object and its info from the Intent.
//                    val prevState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, 0)
//                    val currState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0)
//                    Timber.d("prevState: $prevState, currState: $currState")
//
//                    when (currState) {
//                        BluetoothAdapter.STATE_ON -> {
//                            startScan()
//                        }
//                        BluetoothAdapter.STATE_TURNING_OFF -> {
//                            stopScan()
//                        }
//                        BluetoothAdapter.STATE_OFF -> {
//                            BluetoothAdapter.getDefaultAdapter().enable()
//                        }
//                    }
//                }
//            }
//        }
//    }

    // Create a BroadcastReceiver for ACTION_FOUND.
//    private val detectBluetoothDeviceReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent) {
//            when(intent.action) {
//                BluetoothDevice.ACTION_FOUND -> {
//                    // Discovery has found a device. Get the BluetoothDevice
//                    // object and its info from the Intent.
//                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
//
//                    device ?: return
//
//                    Timber.d(device.name)
//
//                    if (device.name == "58Printer") {
//                        printService.connect(device)
//                    }
//                }
//            }
//        }
//    }

//    private lateinit var printService: BluetoothPrinterService

    private lateinit var usbPrinterService: UsbPrinterService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val statusBarVisible = insets.isVisible(WindowInsetsCompat.Type.statusBars())

            if (!imeVisible && statusBarVisible) {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        delay(100)
                    }
                    hideSystemUI()
                }
            }

            insets
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUSB()
//        setupBluetooth()

        var dialog: AppCompatDialogFragment? = null

        viewModel.checkInStatus.observe(this) {
            dialog?.dismiss()
            dialog = null

            when (it) {
                is MainViewModel.CheckInStatus.NotChecking -> {
                    if (it.response.success) {
                        dialog = SuccessDialogFragment()
                        dialog?.showNow(supportFragmentManager, null)

                        it.response.patients.forEach { patient ->
                            printPatient(patient)
                        }
                    } else {
                        dialog = ErrorDialogFragment(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                Html.fromHtml(it.response.message, Html.FROM_HTML_MODE_COMPACT)
                            } else {
                                Html.fromHtml(it.response.message)
                            }
                        )

                        dialog?.showNow(supportFragmentManager, null)
                    }
                }
                MainViewModel.CheckInStatus.Checking -> {
                    dialog = CheckingDialogFragment()
                    dialog?.showNow(supportFragmentManager, null)
                }
                is MainViewModel.CheckInStatus.Fail -> {
                    if (it.throwable is CancellationException) {

                    } else {
                        dialog = ErrorDialogFragment(
                            it.throwable.message ?: "Unknown"
                        )

                        dialog?.showNow(supportFragmentManager, null)
                    }
                }
            }
        }
    }

    private fun setupUSB() {
        val usbFilter = IntentFilter(USB_PERMISSION)
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)

        registerReceiver(
            detectUsbDeviceReceiver,
            usbFilter
        )

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        acsUsbDevice = AcsUsbDevice(
            usbManager,
            arrayOf(AcsUsbTWDecoder()),
        ).apply { callback = usbDeviceCallback }
        acsUsbDevice.supportedDevice?.also {
            connectUSBDevice(it)
        }

        ezUsbDevice = CastlesUsbDevice(this).apply { callback = usbDeviceCallback }
        ezUsbDevice.supportedDevice?.also {
            connectUSBDevice(it)
        }

        usbPrinterService = BPT3XPrinterService(usbManager)
        usbPrinterService.supportedDevice?.also {
            connectUSBDevice(it)
        }
    }

    private fun connectUSBDevice(device: UsbDevice) {
        val mPermissionIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(USB_PERMISSION),
            0
        )

        usbManager.requestPermission(device, mPermissionIntent)
    }

    /** 列印患者報到資訊 */
    private fun printPatient(patient: GetPatientsResponse.PatientBean) {
        val now = Date()
        val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

        val commandList = arrayListOf(
            PrinterBuffer.initializePrinter(),
            PrinterBuffer.selectAlignment(PrinterBuffer.Alignment.CENTER),

            PrinterBuffer.setLineSpacing(120),
            PrinterBuffer.selectCharacterSize(PrinterBuffer.CharacterSize.XSMALL),
            strToBytes("屏東基督教醫院"),
            PrinterBuffer.printAndFeedLine(),

            PrinterBuffer.setLineSpacing(160),
            PrinterBuffer.selectCharacterSize(PrinterBuffer.CharacterSize.SMALL),
            strToBytes(patient.division),
            PrinterBuffer.printAndFeedLine(),

            PrinterBuffer.setLineSpacing(120),
            PrinterBuffer.selectCharacterSize(PrinterBuffer.CharacterSize.XSMALL),
            strToBytes(getString(R.string.print_serial_no)),
            PrinterBuffer.printAndFeedLine(),

            PrinterBuffer.setLineSpacing(160),
            PrinterBuffer.selectCharacterSize(PrinterBuffer.CharacterSize.SMALL),
            strToBytes(String.format("%04d", patient.serialNo)),
            PrinterBuffer.printAndFeedLine(),

            PrinterBuffer.setLineSpacing(120),
            PrinterBuffer.selectCharacterSize(PrinterBuffer.CharacterSize.XSMALL),
            strToBytes(formatter.format(now)),
            PrinterBuffer.printAndFeedLine(),

            PrinterBuffer.setLineSpacing(120),
            PrinterBuffer.selectCharacterSize(PrinterBuffer.CharacterSize.XSMALL),
            strToBytes(getString(R.string.print_footer)),
            PrinterBuffer.printAndFeedLine(),

            PrinterBuffer.selectCutPagerModerAndCutPager(66, 1)
        )

        commandList.forEach { command ->
            usbPrinterService.write(command)
        }
    }

    /** 將字串用萬國編碼轉成ByteArray防止中文亂碼 */
    private fun strToBytes(str: String): ByteArray = str.toByteArray(charset("big5"))

//    private fun setupBluetooth() {
//        val bluetoothStateFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
//        registerReceiver(
//            detectBluetoothStateReceiver,
//            bluetoothStateFilter
//        )
//
//        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
//        registerReceiver(detectBluetoothDeviceReceiver, filter)
//
//        requestBluetoothPermissions()
//
//        printService = BluetoothPrinterService(
//            this,
//            object : PrinterServiceDelegate {
//                override fun onStateChanged(state: PrinterServiceDelegate.State) {
//                    when (state) {
//                        PrinterServiceDelegate.State.NONE -> {
//                            startScan()
//                        }
//                        PrinterServiceDelegate.State.CONNECTING -> {
//                            stopScan()
//                        }
//                        PrinterServiceDelegate.State.CONNECTED -> {
//
//                        }
//                    }
//                }
//            }
//        )
//    }
//
//    private fun requestBluetoothPermissions() {
//        val permissions = arrayOf(
//            android.Manifest.permission.BLUETOOTH,
//            android.Manifest.permission.BLUETOOTH_ADMIN,
//            android.Manifest.permission.ACCESS_FINE_LOCATION,
//            android.Manifest.permission.ACCESS_COARSE_LOCATION
//        )
//
//        bleForResult.launch(permissions)
//    }
//
//    private fun enableBluetoothService() {
//        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
//
//        if (bluetoothAdapter.isEnabled) {
//            startScan()
//        } else {
////            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
////            bleForResult.launch(enableBtIntent)
//            bluetoothAdapter.enable()
//        }
//    }
//
//    var timeOutJob: Job? = null
//
//    /** 開始掃描藍牙設備 */
//    private fun startScan() {
//        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
//
//        if (bluetoothAdapter.isDiscovering || printService.state != PrinterServiceDelegate.State.NONE) return
//        Timber.d("startDiscovery")
//
//        if (bluetoothAdapter.isEnabled) {
//            timeOutJob?.cancel()
//            timeOutJob = lifecycleScope.launch {
//                withContext(Dispatchers.IO) {
//                    delay(TimeUnit.SECONDS.toMillis(SCAN_TIME_OUT))
//                }
//
//                if (bluetoothAdapter.isEnabled && printService.state != PrinterServiceDelegate.State.CONNECTED) {
//                    bluetoothAdapter.disable()
//                }
//            }
//
//            bluetoothAdapter.startDiscovery()
//        }
//    }
//
//    /** 停止掃描藍牙設備 */
//    private fun stopScan() {
//        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
//
//        if (!bluetoothAdapter.isDiscovering) return
//        Timber.d("cancelDiscovery")
//
//        if (bluetoothAdapter.isEnabled) {
//            bluetoothAdapter.cancelDiscovery()
//        }
//    }

    /** 取得病患今天預約掛號資訊 */
    fun getPatients(nationalId: String, checkInCompletion: (() -> Unit)? = null) {
        viewModel.getPatients(nationalId, checkInCompletion)
    }

    private fun hideSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView) ?: return
        // Configure the behavior of the hidden system bars
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Hide both the status bar and the navigation bar
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        // hide soft keyboard when touching outside
        val v = currentFocus

        if (v != null &&
            (ev?.action == MotionEvent.ACTION_DOWN) &&
            v is EditText &&
            !v::class.java.simpleName.startsWith("android.webkit")
        ) {
            val sourceCoordinates = IntArray(2)

            v.getLocationOnScreen(sourceCoordinates)

            val x = ev.rawX + v.left - sourceCoordinates[0]
            val y = ev.rawY + v.top - sourceCoordinates[1]

            if (x < v.left || x > v.right || y < v.top || y > v.bottom) {
                v.clearFocus()

                WindowCompat.getInsetsController(window, window.decorView)?.run {
                    // Hide both the status bar and the navigation bar
                    hide(WindowInsetsCompat.Type.ime())
                }
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        super.onDestroy()

//        printService.disconnect()

//        stopScan()

        try {
            unregisterReceiver(detectUsbDeviceReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }

//        try {
//            unregisterReceiver(detectBluetoothStateReceiver)
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//
//        try {
//            unregisterReceiver(detectBluetoothDeviceReceiver)
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
    }
}