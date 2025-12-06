package com.lx.reactnative.bluetoothprinter

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.lx.reactnative.bluetoothprinter.BuildConfig
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class BluetoothService {
  companion object {
    private const val TAG = "BluetoothService"
    private const val NAME = "BTPrinter"
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    const val STATE_NONE = 0
    const val STATE_CONNECTING = 2
    const val STATE_CONNECTED = 3

    const val MESSAGE_STATE_CHANGE = 4
    const val MESSAGE_READ = 5
    const val MESSAGE_WRITE = 6
    const val MESSAGE_DEVICE_NAME = 7
    const val MESSAGE_CONNECTION_LOST = 8
    const val MESSAGE_UNABLE_CONNECT = 9

    const val DEVICE_NAME = "device_name"
    const val DEVICE_ADDRESS = "device_address"
    const val TOAST = "toast"
  }

  private val observers = CopyOnWriteArrayList<BluetoothServiceStateObserver>()
  private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
  private var connectedThread: ConnectedThread? = null
  @Volatile private var state: Int = STATE_NONE
  @Volatile private var lastConnectedDeviceAddress: String? = null

  fun addStateObserver(observer: BluetoothServiceStateObserver) {
    observers.addIfAbsent(observer)
  }

  fun removeStateObserver(observer: BluetoothServiceStateObserver) {
    observers.remove(observer)
  }

  @Synchronized
  fun getState(): Int = state

  @Synchronized
  private fun setState(newState: Int, bundle: Map<String, Any?>? = null) {
    if (BuildConfig.DEBUG) Log.d(TAG, "state $state -> $newState")
    state = newState
    notifyObservers(newState, bundle)
  }

  private fun notifyObservers(code: Int, bundle: Map<String, Any?>?) {
    observers.forEach { it.onBluetoothServiceStateChanged(code, bundle) }
  }

  @Synchronized
  fun connect(device: BluetoothDevice) {
    if (state == STATE_CONNECTED && connectedThread?.device?.address == device.address) {
      setState(
        STATE_CONNECTED,
        mapOf(DEVICE_NAME to device.name, DEVICE_ADDRESS to device.address),
      )
      return
    }

    stop()
    connectedThread = ConnectedThread(device).also {
      it.start()
      setState(STATE_CONNECTING, null)
    }
  }

  @Synchronized
  fun getConnectedDevice(): BluetoothDevice? {
    val device = connectedThread?.device
    return if (state == STATE_CONNECTED && device != null) device else null
  }

  @Synchronized
  fun stop() {
    connectedThread?.cancel()
    connectedThread = null
    state = STATE_NONE
  }

  fun write(out: ByteArray) {
    val thread = synchronized(this) {
      if (state != STATE_CONNECTED) return
      connectedThread
    } ?: return

    thread.write(out)
  }

  fun getLastConnectedDeviceAddress(): String? = lastConnectedDeviceAddress

  fun setLastConnectedDeviceAddress(address: String?) {
    lastConnectedDeviceAddress = address
  }

  private fun connectionFailed() {
    setState(STATE_NONE, null)
    notifyObservers(MESSAGE_UNABLE_CONNECT, null)
  }

  private fun connectionLost() {
    setState(STATE_NONE, null)
    notifyObservers(MESSAGE_CONNECTION_LOST, null)
  }

  private inner class ConnectedThread(val device: BluetoothDevice) : Thread("RNBTConnect") {
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    override fun run() {
      tryConnect()
      setupStreams()
      readLoop()
    }

    private fun tryConnect() {
      adapter?.cancelDiscovery()
      socket = buildSocket() ?: run {
        connectionFailed()
        return
      }
      try {
        socket?.connect()
      } catch (e: Exception) {
        Log.e(TAG, "connect failed", e)
        try { socket?.close() } catch (_: Exception) {}
        connectionFailed()
        return
      }
    }

    private fun buildSocket(): BluetoothSocket? {
      // Try classic RFCOMM channels first for broader printer support.
      repeat(3) { channel ->
        try {
          val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
          val socket = method.invoke(device, channel + 1) as? BluetoothSocket
          if (socket != null) return socket
        } catch (_: Exception) {
        }
      }
      return try {
        device.createRfcommSocketToServiceRecord(SPP_UUID)
      } catch (e: IOException) {
        Log.e(TAG, "fallback socket failed", e)
        null
      }
    }

    private fun setupStreams() {
      try {
        input = socket?.inputStream
        output = socket?.outputStream
      } catch (e: IOException) {
        Log.e(TAG, "stream setup failed", e)
        connectionFailed()
        return
      }

      setState(
        STATE_CONNECTED,
        mapOf(DEVICE_NAME to device.name, DEVICE_ADDRESS to device.address),
      )
      lastConnectedDeviceAddress = device.address
    }

    private fun readLoop() {
      val buffer = ByteArray(256)
      while (true) {
        try {
          val bytes = input?.read(buffer) ?: -1
          if (bytes <= 0) {
            connectionLost()
            break
          }
          notifyObservers(MESSAGE_READ, mapOf("bytes" to bytes))
        } catch (e: IOException) {
          Log.e(TAG, "disconnected", e)
          connectionLost()
          break
        }
      }
    }

    fun write(buffer: ByteArray) {
      try {
        output?.write(buffer)
        output?.flush()
        notifyObservers(MESSAGE_WRITE, mapOf("bytes" to buffer))
      } catch (e: IOException) {
        Log.e(TAG, "write failed", e)
      }
    }

    fun cancel() {
      try {
        socket?.close()
      } catch (e: IOException) {
        Log.e(TAG, "close failed", e)
      } finally {
        connectionLost()
      }
    }
  }
}

