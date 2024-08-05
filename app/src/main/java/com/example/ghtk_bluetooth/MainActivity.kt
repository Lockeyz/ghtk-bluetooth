package com.example.ghtk_bluetooth

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_ADMIN
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

import androidx.core.app.ActivityCompat

import com.example.ghtk_bluetooth.databinding.ActivityMainBinding
import java.io.IOException
import java.util.UUID

class MainActivity : AppCompatActivity(){

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val REQUEST_ENABLE_BT = 101
        private const val REQUEST_PERMISSION_BT = 102
        private const val REQUEST_PERMISSION_LOCATION = 103
    }

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter

    private lateinit var pairedDevices: Set<BluetoothDevice>
    private val availableDevices = mutableListOf<BluetoothDevice>()

    private lateinit var pairedDeviceAdapter: BluetoothDeviceAdapter
    private val pairedDevicesList = mutableListOf<BluetoothDeviceModel>() // Danh sách để hiển thị

    private lateinit var availableDeviceAdapter: BluetoothDeviceAdapter
    private val availableDevicesList = mutableListOf<BluetoothDeviceModel>() // Danh sách để hiển thị

    // UUID này là một UUID phổ biến được sử dụng cho Bluetooth SPP (Serial Port Profile)
    private val myId: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

//    private lateinit var acceptThread: AcceptThread
//    private lateinit var connectThread: ConnectThread



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Tạo thực thể bt adapter
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter

        pairedDeviceAdapter = BluetoothDeviceAdapter(pairedDevicesList) { connectDevice(it) }
        binding.recyclerViewPairedDevice.adapter = pairedDeviceAdapter

        availableDeviceAdapter = BluetoothDeviceAdapter(availableDevicesList) { connectDevice(it) }
        binding.recyclerViewAvailableDevice.adapter = availableDeviceAdapter

//        acceptThread = AcceptThread()


        // Dang ky cho nhung broadcasts khi mot thiet bi duoc tim thay


        if (bluetoothAdapter.isEnabled) {
            showPairedDevices()
            startDiscovery()
            enableDiscoverability()

//            acceptThread.start()
        }

        setStatus()

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)

        binding.btnConnect.setOnClickListener {
            toggleBluetooth()
        }
    }

    private fun connectDevice(position: Int) {
        Toast.makeText(this, "Connect device", Toast.LENGTH_SHORT).show()
//        availableDevices.forEach{ device ->
//            if (device.address == availableDevicesList[position].address) {
//                connectToDevice(device)
//            }
//        }
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "On", Toast.LENGTH_SHORT).show()
            showPairedDevices()
            startDiscovery()
            enableDiscoverability()
//            acceptThread.start()
        }
    }

    @SuppressLint("SetTextI18n", "NotifyDataSetChanged")
    private fun setStatus() {
        if (!bluetoothAdapter.isEnabled) {
            binding.btnConnect.text = "On"
            pairedDevicesList.clear()
            availableDevicesList.clear()
            pairedDeviceAdapter.notifyDataSetChanged()
            availableDeviceAdapter.notifyDataSetChanged()
        } else {
            binding.btnConnect.text = "Off"
            pairedDeviceAdapter.notifyDataSetChanged()
            availableDeviceAdapter.notifyDataSetChanged()
        }
    }

    @SuppressLint("SetTextI18n", "NotifyDataSetChanged")
    private fun toggleBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        if (!bluetoothAdapter.isEnabled) {
            // Kiem tra Api de check dung quyen can dung
            // Cac quyen Bluetooth deu duoc cap san, chi can check dung, khong can nhanh else
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Api 31 tro len can quyen BLUETOOTH_CONNECT de bat/tat va ket noi thiet bi
                if (ActivityCompat.checkSelfPermission(
                        this, BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d("Main Activity", "granted")
                    // enable() da deprecated tai api 33 TIRAMISU, bat bluetooth nhung khong hoi
                    // bluetoothAdapter.enable()

                    // Hoi de bat bluetooth
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                }
            } else {
                // Api 31 tro xuong can quyen BLUETOOTH de bat/tat va ket noi thiet bi
                if (ActivityCompat.checkSelfPermission(
                        this, BLUETOOTH
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                }
            }
        } else {
            // disable() chi co the hoat dong voi api duoi 33
            bluetoothAdapter.disable()
            Toast.makeText(this, "Off", Toast.LENGTH_SHORT).show()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                setStatus()
//                acceptThread.cancel()
//                connectThread.cancel()
            }
        }

    }

    // Hien thi danh sach thiet bi da ket noi
    @SuppressLint("NotifyDataSetChanged")
    private fun showPairedDevices() {
        pairedDevicesList.clear()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this, BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                pairedDevices = bluetoothAdapter.bondedDevices
                pairedDevices.forEach { device ->
                    pairedDevicesList.add(BluetoothDeviceModel(device.name, device.address))
                }
                setStatus()
            }
        } else {
            if (ActivityCompat.checkSelfPermission(
                    this, BLUETOOTH
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
                pairedDevices.forEach { device ->
                    pairedDevicesList.add(BluetoothDeviceModel(device.name, device.address))
                }
                setStatus()
            }
        }
    }

    // tim kiem thiet bi hien co
    // Api duoi 33 phai check BLUETOOTH_ADMIN
    private fun startDiscovery() {
        if (bluetoothAdapter.isDiscovering) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        this, BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    bluetoothAdapter.cancelDiscovery()
                }
            } else {
                if (ActivityCompat.checkSelfPermission(
                        this, BLUETOOTH_ADMIN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    bluetoothAdapter.cancelDiscovery()
                }
            }
        }
        bluetoothAdapter.startDiscovery()
//        registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
    }

    // Tạo 1 BroadcastReceiver cho ACTION_FOUND
    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("NotifyDataSetChanged")
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                    val device: BluetoothDevice =
//                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice:class.java)
//                    if (ActivityCompat.checkSelfPermission(
//                            this@MainActivity, BLUETOOTH_CONNECT
//                        ) == PackageManager.PERMISSION_GRANTED
//                    ) {
//                        val deviceName = device?.name
//                        val deviceAddress = device?.address
//                        if (deviceName != null && deviceAddress != null) {
//                            devicesList.add(BluetoothDeviceModel(deviceName.toString(), deviceAddress))
//                            deviceAdapter.notifyDataSetChanged()
//                        }
//                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    val device: BluetoothDevice?
                        = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            BLUETOOTH_SCAN
                        ) == PackageManager.PERMISSION_GRANTED
                        && ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        val deviceName = device?.name
                        val deviceAddress = device?.address
                        if (deviceName != null && deviceAddress != null) {
                            availableDevices.add(device)
                            availableDevicesList.add(
                                BluetoothDeviceModel(
                                    deviceName.toString(),
                                    deviceAddress
                                )
                            )
                            availableDeviceAdapter.notifyDataSetChanged()
                        }
                    } else {
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            arrayOf(ACCESS_FINE_LOCATION),
                            REQUEST_PERMISSION_LOCATION
                        )
                    }
                } else {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity, BLUETOOTH
                        ) == PackageManager.PERMISSION_GRANTED
                    ) { 
                        if (device !in availableDevices){
                            val deviceName = device?.name
                            val deviceAddress = device?.address
                            if (deviceName != null && deviceAddress != null) {
                                availableDevices.add(device)
                                availableDevicesList.add(
                                    BluetoothDeviceModel(
                                        deviceName.toString(),
                                        deviceAddress
                                    )
                                )
                                availableDeviceAdapter.notifyDataSetChanged()
                                Log.d("Main Activity bluetooth", "new device")
                            }
                        }
                        
                    }
                }
            }
        }
    }

    // Ket noi nhu mot Server


//    @SuppressLint("MissingPermission")
//    private inner class AcceptThread : Thread() {
//        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
//            bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("My Device", myId)
//        }
//        override fun run() {
//            // Keep listening until exception occurs or a socket is returned.
//            var shouldLoop = true
//            while (shouldLoop) {
//                val socket: BluetoothSocket? = try {
//                    mmServerSocket?.accept()
//                } catch (e: IOException) {
//                    Log.e(TAG, "Socket's accept() method failed", e)
//                    shouldLoop = false
//                    null
//                }
//                socket?.also {
////                    manageMyConnectedSocket(it)
//                    mmServerSocket?.close()
//                    shouldLoop = false
//                }
//            }
//        }
//
//        // Closes the connect socket and causes the thread to finish.
//        fun cancel() {
//            try {
//                mmServerSocket?.close()
//            } catch (e: IOException) {
//                Log.e(TAG, "Could not close the connect socket", e)
//            }
//        }
//    }

//    @SuppressLint("MissingPermission")
//    private inner class ConnectThread(device: BluetoothDevice) : Thread() {
//        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
//            device.createRfcommSocketToServiceRecord(myId)
//        }
//
//        override fun run() {
//
//            bluetoothAdapter.cancelDiscovery()
//            mmSocket?.connect()
//        }
//
//        // Closes the client socket and causes the thread to finish.
//        fun cancel() {
//            try {
//                mmSocket?.close()
//            } catch (e: IOException) {
//                Log.e(TAG, "Could not close the client socket", e)
//            }
//        }
//    }

    // Tạo phương thức để kết nối với một thiết bị Bluetooth
    private fun connectToDevice(device: BluetoothDevice) {
        var bluetoothSocket: BluetoothSocket? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
                if (ActivityCompat.checkSelfPermission(
                        this,
                        BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // Tạo một BluetoothSocket sử dụng UUID cho SPP
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(myId)
                }
            } else {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        BLUETOOTH
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // Tạo một BluetoothSocket sử dụng UUID cho SPP
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(myId)
                }
            }

            // Hủy việc discover các thiết bị khác vì nó có thể làm chậm quá trình kết nối
            bluetoothAdapter.cancelDiscovery()

            // Kết nối với thiết bị, đây là một hoạt động blocking
            bluetoothSocket?.connect()
            Log.d("MainActivity", "Kết nối thành công với ${device.name}")

            // Sau khi kết nối thành công, bạn có thể truyền dữ liệu qua bluetoothSocket
            // ...

        } catch (e: IOException) {
            Log.e("MainActivity", "Lỗi kết nối với ${device.name}", e)
            try {
                bluetoothSocket?.close()
            } catch (closeException: IOException) {
                Log.e("MainActivity", "Không thể đóng socket", closeException)
            }
        }
    }

    // Cho phep kha nang hien thi truoc cac thiet bi khac
    private fun enableDiscoverability() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        startActivity(discoverableIntent)
//        startActivityForResult(discoverableIntent, requestCode)
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_BT) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        // Huy dang ky ACTION_FOUND receiver
        unregisterReceiver(receiver)

        // Huy dang ky AcceptThread
//        if (::acceptThread.isInitialized) {
//            acceptThread.cancel()
//        }
        // Huy dang ky ConnectThread
//        if (::connectThread.isInitialized) {
//            connectThread.cancel()
//        }


    }

}