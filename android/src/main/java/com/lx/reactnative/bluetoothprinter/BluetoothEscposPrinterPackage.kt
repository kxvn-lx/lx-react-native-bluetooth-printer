package com.lx.reactnative.bluetoothprinter

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager
import com.lx.reactnative.bluetoothprinter.escpos.BluetoothEscposPrinterModule

class BluetoothEscposPrinterPackage : ReactPackage {
  override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
    val service = BluetoothService()
    return listOf(
      BluetoothManagerModule(reactContext, service),
      BluetoothEscposPrinterModule(reactContext, service),
    )
  }

  override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> =
    emptyList()
}

