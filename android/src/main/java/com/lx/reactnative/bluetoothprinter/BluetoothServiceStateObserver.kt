package com.lx.reactnative.bluetoothprinter

interface BluetoothServiceStateObserver {
  fun onBluetoothServiceStateChanged(state: Int, bundle: Map<String, Any?>?)
}

