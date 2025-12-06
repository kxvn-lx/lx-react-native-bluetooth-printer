import CoreBluetooth
import Foundation
import React

@objc(BluetoothManager)
final class BluetoothManager: RCTEventEmitter, BluetoothSessionDelegate {
  private let session = BluetoothSession.shared
  private var hasListenersFlag = false

  private var scanResolve: RCTPromiseResolveBlock?
  private var scanReject: RCTPromiseRejectBlock?
  private var connectResolve: RCTPromiseResolveBlock?
  private var connectReject: RCTPromiseRejectBlock?
  private var waitingConnectId: String?

  override init() {
    super.init()
    session.delegate = self
  }

  override static func requiresMainQueueSetup() -> Bool { true }

  override func startObserving() {
    hasListenersFlag = true
  }

  override func stopObserving() {
    hasListenersFlag = false
  }

  override func supportedEvents() -> [String]! {
    [
      "EVENT_DEVICE_DISCOVER_DONE",
      "EVENT_DEVICE_FOUND",
      "EVENT_UNABLE_CONNECT",
      "EVENT_CONNECTION_LOST",
      "EVENT_CONNECTED",
      "EVENT_DEVICE_ALREADY_PAIRED",
    ]
  }

  override func constantsToExport() -> [AnyHashable: Any]! {
    [
      "EVENT_DEVICE_ALREADY_PAIRED": "EVENT_DEVICE_ALREADY_PAIRED",
      "EVENT_DEVICE_DISCOVER_DONE": "EVENT_DEVICE_DISCOVER_DONE",
      "EVENT_DEVICE_FOUND": "EVENT_DEVICE_FOUND",
      "EVENT_CONNECTION_LOST": "EVENT_CONNECTION_LOST",
      "EVENT_UNABLE_CONNECT": "EVENT_UNABLE_CONNECT",
      "EVENT_CONNECTED": "EVENT_CONNECTED",
    ]
  }

  // MARK: - API
  @objc
  func getConnectedDevice(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    if let device = session.connected {
      resolve(["name": device.name ?? "", "address": device.identifier.uuidString])
    } else {
      resolve(nil)
    }
  }

  @objc
  func isBluetoothEnabled(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    resolve(session.central.state == .poweredOn)
  }

  @objc
  func enableBluetooth(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    resolve(nil) // iOS cannot programmatically enable Bluetooth.
  }

  @objc
  func disableBluetooth(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    resolve(nil)
  }

  @objc
  func scanDevices(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    guard session.central.state == .poweredOn else {
      reject("BLUETOOTH_INVALID_STATE", "Bluetooth is off", nil)
      return
    }
    scanResolve = resolve
    scanReject = reject
    session.startScan(timeout: 10)
    // EVENT_DEVICE_ALREADY_PAIRED is empty on iOS; emit immediately.
    if hasListenersFlag {
      sendEvent(withName: "EVENT_DEVICE_ALREADY_PAIRED", body: ["devices": "[]"])
    }
  }

  @objc
  func stopScan(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    session.stopScan()
    resolve(nil)
  }

  @objc
  func connect(_ address: String, findEventsWithResolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    waitingConnectId = address
    connectResolve = resolve
    connectReject = reject
    session.connect(idString: address)
  }

  @objc
  func unpair(_ address: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    if let peripheral = session.peripheral(for: address) {
      if session.connected?.identifier == peripheral.identifier {
        session.disconnect()
      }
      resolve(true)
    } else {
      reject("DEVICE_NOT_FOUND", "Device not found", nil)
    }
  }

  @objc
  func disconnect(_ address: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    session.disconnect()
    resolve(address)
  }

  @objc
  func isDeviceConnected(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    resolve(session.isConnected)
  }

  @objc
  func getConnectedDeviceAddress(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    resolve(session.connected?.identifier.uuidString)
  }

  // MARK: - BluetoothSessionDelegate
  func sessionDidDiscover(peripheral: CBPeripheral) {
    if hasListenersFlag {
      let payload: [String: Any] = [
        "device": [
          "address": peripheral.identifier.uuidString,
          "name": peripheral.name ?? "",
        ],
      ]
      sendEvent(withName: "EVENT_DEVICE_FOUND", body: payload)
    }
  }

  func sessionDidFinishDiscovery(found: [CBPeripheral]) {
    let devices = found.map { ["address": $0.identifier.uuidString, "name": $0.name ?? ""] }
    if hasListenersFlag {
      let jsonData = try? JSONSerialization.data(withJSONObject: devices, options: [])
      let jsonStr = String(data: jsonData ?? Data(), encoding: .utf8) ?? "[]"
      sendEvent(withName: "EVENT_DEVICE_DISCOVER_DONE", body: ["found": jsonStr, "paired": "[]"])
    }
    scanResolve?(["found": devices, "paired": []])
    scanResolve = nil
    scanReject = nil
  }

  func sessionDidConnect(peripheral: CBPeripheral) {
    if let waiting = waitingConnectId, waiting == peripheral.identifier.uuidString, let resolve = connectResolve {
      resolve(nil)
      waitingConnectId = nil
      connectResolve = nil
      connectReject = nil
    }
    if hasListenersFlag {
      sendEvent(
        withName: "EVENT_CONNECTED",
        body: ["device": ["name": peripheral.name ?? "", "address": peripheral.identifier.uuidString]]
      )
    }
  }

  func sessionDidFailToConnect(peripheral: CBPeripheral?, error: Error?) {
    connectReject?("UNABLE_CONNECT", error?.localizedDescription ?? "Unable to connect", error)
    connectResolve = nil
    connectReject = nil
    waitingConnectId = nil
    if hasListenersFlag {
      sendEvent(withName: "EVENT_UNABLE_CONNECT", body: nil)
    }
  }

  func sessionDidDisconnect(peripheral: CBPeripheral?, error: Error?) {
    if let error = error, connectReject != nil {
      connectReject?("DISCONNECTED", error.localizedDescription, error)
    } else if let resolve = connectResolve {
      resolve(nil)
    }
    connectResolve = nil
    connectReject = nil
    waitingConnectId = nil
    if hasListenersFlag {
      sendEvent(withName: "EVENT_CONNECTION_LOST", body: nil)
    }
  }
}

