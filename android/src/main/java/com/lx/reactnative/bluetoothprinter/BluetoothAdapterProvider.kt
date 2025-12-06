package com.lx.reactnative.bluetoothprinter

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build

internal fun bluetoothAdapter(context: Context): BluetoothAdapter? {
  val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    context.getSystemService(BluetoothManager::class.java)
  } else {
    @Suppress("DEPRECATION")
    (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
  }
  return manager?.adapter ?: legacyAdapter()
}

@Suppress("DEPRECATION")
private fun legacyAdapter(): BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

