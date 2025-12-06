import CoreBluetooth
import Foundation

protocol BluetoothSessionDelegate: AnyObject {
  func sessionDidDiscover(peripheral: CBPeripheral)
  func sessionDidFinishDiscovery(found: [CBPeripheral])
  func sessionDidConnect(peripheral: CBPeripheral)
  func sessionDidFailToConnect(peripheral: CBPeripheral?, error: Error?)
  func sessionDidDisconnect(peripheral: CBPeripheral?, error: Error?)
}

final class BluetoothSession: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {
  static let shared = BluetoothSession()

  private let serviceUUID = CBUUID(string: "49535343-FE7D-4AE5-8FA9-9FAFD205E455")
  private let writeUUID = CBUUID(string: "49535343-8841-43F4-A8D4-ECBE34729BB3")

  private(set) var central: CBCentralManager!
  private var foundDevices: [UUID: CBPeripheral] = [:]
  private(set) var connected: CBPeripheral?
  private var writeCharacteristic: CBCharacteristic?
  private var pendingConnectId: UUID?
  private var pendingWriteQueue: [Data] = []
  private var pendingWriteCompletion: ((Bool) -> Void)?
  private var scanTimer: Timer?

  weak var delegate: BluetoothSessionDelegate?

  var isScanning: Bool { central?.isScanning == true }
  var isConnected: Bool { connected?.state == .connected }

  private override init() {
    super.init()
    central = CBCentralManager(delegate: self, queue: nil)
  }

  func startScan(timeout: TimeInterval = 10) {
    guard central.state == .poweredOn else { return }
    foundDevices.removeAll()
    central.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
    scanTimer?.invalidate()
    scanTimer = Timer.scheduledTimer(withTimeInterval: timeout, repeats: false) { [weak self] _ in
      self?.stopScan()
    }
  }

  func stopScan() {
    central.stopScan()
    scanTimer?.invalidate()
    scanTimer = nil
    delegate?.sessionDidFinishDiscovery(found: Array(foundDevices.values))
  }

  func connect(idString: String) {
    guard let uuid = UUID(uuidString: idString) else { return }
    pendingConnectId = uuid
    if let peripheral = foundDevices[uuid] {
      central.connect(peripheral, options: nil)
    } else {
      startScan()
    }
  }

  func disconnect() {
    if let peripheral = connected {
      central.cancelPeripheralConnection(peripheral)
    }
    connected = nil
    writeCharacteristic = nil
    pendingWriteQueue.removeAll()
    pendingWriteCompletion = nil
  }

  func peripheral(for idString: String) -> CBPeripheral? {
    guard let uuid = UUID(uuidString: idString) else { return nil }
    return foundDevices[uuid]
  }

  func write(_ data: Data, completion: @escaping (Bool) -> Void) {
    guard let peripheral = connected else {
      completion(false)
      return
    }
    if let characteristic = writeCharacteristic {
      peripheral.writeValue(data, for: characteristic, type: .withoutResponse)
      completion(true)
    } else {
      pendingWriteQueue.append(data)
      pendingWriteCompletion = completion
      peripheral.discoverServices([serviceUUID])
    }
  }

  // MARK: - CBCentralManagerDelegate
  func centralManagerDidUpdateState(_ central: CBCentralManager) {
    // No-op; the module queries central.state when needed.
  }

  func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
    foundDevices[peripheral.identifier] = peripheral
    delegate?.sessionDidDiscover(peripheral: peripheral)
    if let pending = pendingConnectId, pending == peripheral.identifier {
      central.connect(peripheral, options: nil)
      pendingConnectId = nil
    }
  }

  func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
    connected = peripheral
    peripheral.delegate = self
    peripheral.discoverServices([serviceUUID])
    delegate?.sessionDidConnect(peripheral: peripheral)
  }

  func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
    delegate?.sessionDidFailToConnect(peripheral: peripheral, error: error)
  }

  func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
    connected = nil
    writeCharacteristic = nil
    pendingWriteQueue.removeAll()
    pendingWriteCompletion = nil
    delegate?.sessionDidDisconnect(peripheral: peripheral, error: error)
  }

  // MARK: - CBPeripheralDelegate
  func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
    guard error == nil else { return }
    peripheral.services?.forEach { service in
      if service.uuid == serviceUUID {
        peripheral.discoverCharacteristics([writeUUID], for: service)
      }
    }
  }

  func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
    guard error == nil else { return }
    service.characteristics?.forEach { characteristic in
      if characteristic.uuid == writeUUID {
        writeCharacteristic = characteristic
        flushPendingWrites()
      }
    }
  }

  private func flushPendingWrites() {
    guard let characteristic = writeCharacteristic, let peripheral = connected else { return }
    let completion = pendingWriteCompletion
    pendingWriteCompletion = nil
    while let chunk = pendingWriteQueue.first {
      pendingWriteQueue.removeFirst()
      peripheral.writeValue(chunk, for: characteristic, type: .withoutResponse)
    }
    completion?(true)
  }
}

