package com.lx.reactnative.bluetoothprinter

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class BluetoothManagerModule(
  reactContext: ReactApplicationContext,
  private val service: BluetoothService,
) : ReactContextBaseJavaModule(reactContext), ActivityEventListener, BluetoothServiceStateObserver {

  companion object {
    private const val TAG = "BluetoothManager"
    const val NAME = "BluetoothManager"

    const val EVENT_DEVICE_ALREADY_PAIRED = "EVENT_DEVICE_ALREADY_PAIRED"
    const val EVENT_DEVICE_FOUND = "EVENT_DEVICE_FOUND"
    const val EVENT_DEVICE_DISCOVER_DONE = "EVENT_DEVICE_DISCOVER_DONE"
    const val EVENT_CONNECTION_LOST = "EVENT_CONNECTION_LOST"
    const val EVENT_UNABLE_CONNECT = "EVENT_UNABLE_CONNECT"
    const val EVENT_CONNECTED = "EVENT_CONNECTED"
    const val EVENT_BLUETOOTH_NOT_SUPPORT = "EVENT_BLUETOOTH_NOT_SUPPORT"

    private const val REQUEST_ENABLE_BT = 2

    private const val PROMISE_ENABLE_BT = "ENABLE_BT"
    private const val PROMISE_SCAN = "SCAN"
    private const val PROMISE_CONNECT = "CONNECT"
  }

  private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
  private val promiseMap = ConcurrentHashMap<String, Promise>()
  private var pairedDevices = Arguments.createArray()
  private var foundDevices = Arguments.createArray()

  private val discoverReceiver: BroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      when (intent.action) {
        BluetoothDevice.ACTION_FOUND -> {
          val device: BluetoothDevice? =
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
          if (device != null && device.bondState != BluetoothDevice.BOND_BONDED) {
            if (!deviceAlreadyFound(device.address)) {
              val map = Arguments.createMap()
              map.putString("name", safeDeviceName(device))
              map.putString("address", device.address)
              foundDevices.pushMap(map)
              Arguments.createMap().apply {
                putMap("device", map)
                emitEvent(EVENT_DEVICE_FOUND, this)
              }
            }
          }
        }

        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
          promiseMap.remove(PROMISE_SCAN)?.let { promise ->
            val result = Arguments.createMap()
            result.putArray("paired", pairedDevices)
            result.putArray("found", foundDevices)
            promise.resolve(result)
          }
          val params = Arguments.createMap().apply {
            putArray("paired", pairedDevices)
            putArray("found", foundDevices)
          }
          emitEvent(EVENT_DEVICE_DISCOVER_DONE, params)
        }
      }
    }
  }

  init {
    reactContext.addActivityEventListener(this)
    service.addStateObserver(this)

    val filter = IntentFilter(BluetoothDevice.ACTION_FOUND).apply {
      addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
    }
    reactContext.registerReceiver(discoverReceiver, filter)
  }

  override fun onCatalystInstanceDestroy() {
    try {
      reactApplicationContext.unregisterReceiver(discoverReceiver)
    } catch (_: Exception) {
    }
    service.removeStateObserver(this)
    cancelDiscovery()
    super.onCatalystInstanceDestroy()
  }

  override fun getName(): String = NAME

  override fun getConstants(): MutableMap<String, Any> = hashMapOf(
    EVENT_DEVICE_ALREADY_PAIRED to EVENT_DEVICE_ALREADY_PAIRED,
    EVENT_DEVICE_DISCOVER_DONE to EVENT_DEVICE_DISCOVER_DONE,
    EVENT_DEVICE_FOUND to EVENT_DEVICE_FOUND,
    EVENT_CONNECTION_LOST to EVENT_CONNECTION_LOST,
    EVENT_UNABLE_CONNECT to EVENT_UNABLE_CONNECT,
    EVENT_CONNECTED to EVENT_CONNECTED,
    BluetoothService.DEVICE_NAME to BluetoothService.DEVICE_NAME,
    EVENT_BLUETOOTH_NOT_SUPPORT to EVENT_BLUETOOTH_NOT_SUPPORT,
  )

  private fun ensureAdapterOrReject(promise: Promise): BluetoothAdapter? {
    if (adapter == null) {
      promise.reject(EVENT_BLUETOOTH_NOT_SUPPORT, "Bluetooth not supported")
      return null
    }
    return adapter
  }

  @ReactMethod
  fun enableBluetooth(promise: Promise) {
    val btAdapter = ensureAdapterOrReject(promise) ?: return
    if (!btAdapter.isEnabled) {
      val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
      promiseMap[PROMISE_ENABLE_BT] = promise
      val activity = reactApplicationContext.currentActivity
        ?: return promise.reject("NO_ACTIVITY", "Current activity is null")
      activity.startActivityForResult(enableIntent, REQUEST_ENABLE_BT)
      return
    }

    promise.resolve(buildPairedArray(btAdapter))
  }

  @ReactMethod
  fun disableBluetooth(promise: Promise) {
    val btAdapter = ensureAdapterOrReject(promise) ?: run {
      promise.resolve(true)
      return
    }
    service.stop()
    promise.resolve(!btAdapter.isEnabled || btAdapter.disable())
  }

  @ReactMethod
  fun isBluetoothEnabled(promise: Promise) {
    promise.resolve(adapter?.isEnabled == true)
  }

  @ReactMethod
  fun scanDevices(promise: Promise) {
    val btAdapter = ensureAdapterOrReject(promise) ?: return
    val activity = reactApplicationContext.currentActivity
      ?: return promise.reject("NO_ACTIVITY", "Current activity is null")

    if (!ensureScanPermissions(activity, promise)) return

    cancelDiscovery()

    pairedDevices = Arguments.createArray()
    foundDevices = Arguments.createArray()
    btAdapter.bondedDevices.forEach { device ->
      val map = Arguments.createMap()
      map.putString("name", safeDeviceName(device))
      map.putString("address", device.address)
      pairedDevices.pushMap(map)
    }

    Arguments.createMap().apply {
      putArray("devices", pairedDevices)
      emitEvent(EVENT_DEVICE_ALREADY_PAIRED, this)
    }

    if (!btAdapter.startDiscovery()) {
      promise.reject("DISCOVER", "NOT_STARTED")
      cancelDiscovery()
    } else {
      promiseMap[PROMISE_SCAN] = promise
    }
  }

  @ReactMethod
  fun connect(address: String, promise: Promise) {
    val btAdapter = ensureAdapterOrReject(promise) ?: return
    if (!btAdapter.isEnabled) {
      promise.reject("BT_NOT_ENABLED", "Bluetooth is not enabled")
      return
    }
    val device = btAdapter.getRemoteDevice(address)
    promiseMap[PROMISE_CONNECT] = promise
    service.connect(device)
  }

  @ReactMethod
  fun getConnectedDevice(promise: Promise) {
    val device = service.getConnectedDevice()
    if (device != null) {
      val map = Arguments.createMap().apply {
        putString("name", safeDeviceName(device))
        putString("address", device.address)
      }
      promise.resolve(map)
    } else {
      promise.resolve(null)
    }
  }

  @ReactMethod
  fun unpair(address: String, promise: Promise) {
    val btAdapter = ensureAdapterOrReject(promise) ?: return
    if (!btAdapter.isEnabled) {
      promise.reject("BT_NOT_ENABLED", "Bluetooth is not enabled")
      return
    }
    try {
      val device = btAdapter.getRemoteDevice(address)
      unpairDevice(device)
      promise.resolve(address)
    } catch (e: Exception) {
      promise.reject("UNPAIR_ERROR", e)
    }
  }

  @ReactMethod
  fun disconnect(address: String, promise: Promise) {
    val btAdapter = ensureAdapterOrReject(promise) ?: return
    if (!btAdapter.isEnabled) {
      promise.reject("BT_NOT_ENABLED", "Bluetooth is not enabled")
      return
    }
    try {
      service.stop()
      promise.resolve(address)
    } catch (e: Exception) {
      promise.reject("DISCONNECT_ERROR", e)
    }
  }

  @ReactMethod
  fun isDeviceConnected(promise: Promise) {
    promise.resolve(service.getState() == BluetoothService.STATE_CONNECTED)
  }

  @ReactMethod
  fun getConnectedDeviceAddress(promise: Promise) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
      ContextCompat.checkSelfPermission(
        reactApplicationContext,
        android.Manifest.permission.BLUETOOTH_CONNECT,
      ) != PackageManager.PERMISSION_GRANTED) {
      promise.reject("PERMISSION_DENIED", "BLUETOOTH_CONNECT is required on Android 12+")
      return
    }

    val device = service.getConnectedDevice()
    if (device != null) {
      promise.resolve(device.address)
    } else {
      promise.resolve(service.getLastConnectedDeviceAddress())
    }
  }

  private fun buildPairedArray(btAdapter: BluetoothAdapter) =
    Arguments.createArray().apply {
      btAdapter.bondedDevices.forEach { device ->
        val map = Arguments.createMap()
        map.putString("name", safeDeviceName(device))
        map.putString("address", device.address)
        pushMap(map)
      }
    }

  private fun ensureScanPermissions(activity: Activity, promise: Promise): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val missing = mutableListOf<String>()
      val connectGranted = ContextCompat.checkSelfPermission(
        activity,
        android.Manifest.permission.BLUETOOTH_CONNECT,
      ) == PackageManager.PERMISSION_GRANTED
      val scanGranted = ContextCompat.checkSelfPermission(
        activity,
        android.Manifest.permission.BLUETOOTH_SCAN,
      ) == PackageManager.PERMISSION_GRANTED
      if (!connectGranted) missing.add(android.Manifest.permission.BLUETOOTH_CONNECT)
      if (!scanGranted) missing.add(android.Manifest.permission.BLUETOOTH_SCAN)
      if (missing.isNotEmpty()) {
        ActivityCompat.requestPermissions(activity, missing.toTypedArray(), 1)
        promise.reject("PERMISSION_REQUESTED", "Bluetooth permissions requested")
        return false
      }
    } else {
      val locationGranted = ContextCompat.checkSelfPermission(
        activity,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
      ) == PackageManager.PERMISSION_GRANTED
      if (!locationGranted) {
        ActivityCompat.requestPermissions(
          activity,
          arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
          1,
        )
        promise.reject("PERMISSION_REQUESTED", "Location permission requested")
        return false
      }
    }
    return true
  }

  private fun unpairDevice(device: BluetoothDevice) {
    try {
      val method: Method = device.javaClass.getMethod("removeBond")
      method.invoke(device, *emptyArray())
    } catch (e: Exception) {
      Log.e(TAG, "Unpair failed: ${e.message}")
    }
  }

  private fun cancelDiscovery() {
    try {
      if (adapter?.isDiscovering == true) {
        adapter.cancelDiscovery()
      }
    } catch (_: Exception) {
    }
  }

  private fun deviceAlreadyFound(address: String): Boolean {
    for (i in 0 until foundDevices.size()) {
      val existing = foundDevices.getMap(i)
      if (existing?.getString("address") == address) return true
    }
    return false
  }

  private fun safeDeviceName(device: BluetoothDevice?): String {
    return try {
      device?.name ?: ""
    } catch (e: SecurityException) {
      ""
    }
  }

  private fun emitEvent(event: String, params: WritableMap?) {
    reactApplicationContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit(event, params)
  }

  override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == REQUEST_ENABLE_BT) {
      val promise = promiseMap.remove(PROMISE_ENABLE_BT)
      if (resultCode == Activity.RESULT_OK && promise != null) {
        val btAdapter = adapter
        promise.resolve(btAdapter?.let { buildPairedArray(it) })
      } else {
        promise?.reject("ERR", "BT NOT ENABLED")
      }
    }
  }

  override fun onNewIntent(intent: Intent) = Unit

  override fun onBluetoothServiceStateChanged(state: Int, bundle: Map<String, Any?>?) {
    when (state) {
      BluetoothService.STATE_CONNECTED, BluetoothService.MESSAGE_DEVICE_NAME -> {
        val name = bundle?.get(BluetoothService.DEVICE_NAME) as? String ?: ""
        promiseMap.remove(PROMISE_CONNECT)?.resolve(name)
          ?: run {
            val params = Arguments.createMap().apply { putString(BluetoothService.DEVICE_NAME, name) }
            emitEvent(EVENT_CONNECTED, params)
          }
      }

      BluetoothService.MESSAGE_CONNECTION_LOST -> emitEvent(EVENT_CONNECTION_LOST, null)

      BluetoothService.MESSAGE_UNABLE_CONNECT -> {
        promiseMap.remove(PROMISE_CONNECT)?.reject("UNABLE_CONNECT", "Unable to connect")
          ?: emitEvent(EVENT_UNABLE_CONNECT, null)
      }
    }
  }
}

