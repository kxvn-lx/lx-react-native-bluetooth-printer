import CoreImage
import Foundation
import React
import UIKit

@objc(BluetoothEscposPrinter)
final class BluetoothEscposPrinter: NSObject, RCTBridgeModule {
  static func moduleName() -> String! { "BluetoothEscposPrinter" }
  static func requiresMainQueueSetup() -> Bool { true }

  private let session = BluetoothSession.shared
  private var deviceWidth = 384

  @objc
  func constantsToExport() -> [AnyHashable: Any]! {
    ["width58": 384, "width80": 576]
  }

  @objc
  func setWidth(_ width: NSNumber) {
    deviceWidth = width.intValue
  }

  @objc
  func printerInit(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    send(Escpos.initPrinter(), resolve: resolve, reject: reject)
  }

  @objc
  func printAndFeed(_ feed: NSNumber, withResolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    send(Escpos.feed(lines: feed.intValue), resolve: resolve, reject: reject)
  }

  @objc
  func printerLeftSpace(_ sp: NSNumber, withResolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    send(Escpos.leftSpace(sp.intValue), resolve: resolve, reject: reject)
  }

  @objc
  func printerLineSpace(_ sp: NSNumber, withResolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    send(Escpos.lineSpace(sp.intValue), resolve: resolve, reject: reject)
  }

  @objc
  func printerUnderLine(_ sp: NSNumber, withResolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    send(Escpos.underline(sp.intValue), resolve: resolve, reject: reject)
  }

  @objc
  func printerAlign(_ align: NSNumber, withResolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    send(Escpos.align(align.intValue), resolve: resolve, reject: reject)
  }

  @objc
  func printText(_ text: NSString, withOptions options: NSDictionary?, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let encoding = options?["encoding"] as? String ?? "GBK"
    let codepage = options?["codepage"] as? Int ?? 0
    let widthTimes = options?["widthtimes"] as? Int ?? 0
    let heightTimes = options?["heigthtimes"] as? Int ?? 0
    let fontType = options?["fonttype"] as? Int ?? 0
    send(
      Escpos.text(
        String(text),
        encoding: encoding,
        codepage: codepage,
        widthTimes: widthTimes,
        heightTimes: heightTimes,
        fontType: fontType
      ),
      resolve: resolve,
      reject: reject
    )
  }

  @objc
  func printColumn(
    _ columnWidths: NSArray,
    withAligns columnAligns: NSArray,
    texts columnTexts: NSArray,
    options: NSDictionary?,
    resolver resolve: @escaping RCTPromiseResolveBlock,
    rejecter reject: @escaping RCTPromiseRejectBlock
  ) {
    guard columnWidths.count == columnAligns.count, columnWidths.count == columnTexts.count else {
      reject("COLUMN_WIDTHS_ALIGNS_AND_TEXTS_NOT_MATCH", "Mismatched widths/aligns/texts", nil)
      return
    }
    let totalLen = columnWidths.reduce(0) { $0 + ( $1 as? Int ?? 0 ) }
    let maxLen = deviceWidth / 8
    if totalLen > maxLen {
      reject("COLUNM_WIDTHS_TOO_LARGE", "Column width too large", nil)
      return
    }
    let encoding = options?["encoding"] as? String ?? "GBK"
    let codepage = options?["codepage"] as? Int ?? 0
    let widthTimes = options?["widthtimes"] as? Int ?? 0
    let heightTimes = options?["heigthtimes"] as? Int ?? 0
    let fontType = options?["fonttype"] as? Int ?? 0
    let payload = Escpos.columns(
      widths: columnWidths as? [Int] ?? [],
      aligns: columnAligns as? [Int] ?? [],
      texts: columnTexts as? [String] ?? [],
      encoding: encoding,
      codepage: codepage,
      widthTimes: widthTimes,
      heightTimes: heightTimes,
      fontType: fontType
    )
    send(payload, resolve: resolve, reject: reject)
  }

  @objc
  func printPic(_ base64encodeStr: NSString, withOptions options: NSDictionary?, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    let widthOption = options?["width"] as? Int ?? 0
    let leftPadding = options?["left"] as? Int ?? 0
    let feedHeight = options?["height"] as? Int ?? 20
    var width = widthOption
    if width == 0 || width > deviceWidth { width = deviceWidth }
    guard let data = Data(base64Encoded: String(base64encodeStr)),
          let image = UIImage(data: data) else {
      reject("INVALID_IMAGE", "Unable to decode image", nil)
      return
    }
    var payload = Escpos.raster(image: image, maxWidth: width, leftPadding: leftPadding)
    payload.append(Escpos.feed(lines: feedHeight))
    send(payload, resolve: resolve, reject: reject)
  }

  @objc
  func cutLine(_ line: NSNumber, withResolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    send(Escpos.cut(lines: line.intValue), resolve: resolve, reject: reject)
  }

  @objc
  func selfTest(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    send(Escpos.selfTest(), resolve: resolve, reject: reject)
  }

  @objc
  func rotate(_ rotate: NSNumber, withResolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    send(Escpos.rotate(rotate.intValue), resolve: resolve, reject: reject)
  }

  @objc
  func setBold(_ weight: NSNumber, withResolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    send(Escpos.bold(weight.intValue), resolve: resolve, reject: reject)
  }

  @objc
  func printQRCode(
    _ content: NSString,
    withSize size: NSNumber,
    withErrorCorrectionLevel correctionLevel: NSNumber,
    withPadding leftPadding: NSNumber,
    resolver resolve: @escaping RCTPromiseResolveBlock,
    rejecter reject: @escaping RCTPromiseRejectBlock
  ) {
    guard let qrImage = Escpos.generateQRCode(String(content), size: size.intValue) else {
      reject("QR_ERROR", "Failed to build QR image", nil)
      return
    }
    let payload = Escpos.raster(image: qrImage, maxWidth: size.intValue, leftPadding: leftPadding.intValue)
    send(payload, resolve: resolve, reject: reject)
  }

  @objc
  func printBarCode(
    _ str: NSString,
    withType nType: NSNumber,
    withWidthX nWidthX: NSNumber,
    withHeight nHeight: NSNumber,
    withHriFontType nHriFontType: NSNumber,
    withHriFontPosition nHriFontPosition: NSNumber
  ) {
    let payload = Escpos.barcode(
      content: String(str),
      type: nType.intValue,
      width: nWidthX.intValue,
      height: nHeight.intValue,
      fontType: nHriFontType.intValue,
      fontPosition: nHriFontPosition.intValue
    )
    _ = sendSync(payload)
  }

  @objc
  func openDrawer(_ mode: NSNumber, withOnTime time1: NSNumber, withOffTime time2: NSNumber) {
    _ = sendSync(Escpos.cashDrawer(mode: mode.intValue, time1: time1.intValue, time2: time2.intValue))
  }

  @objc
  func cutOnePoint() {
    _ = sendSync(Escpos.cutOnePoint())
  }

  // MARK: - Helpers
  private func send(_ data: Data, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
    guard session.isConnected else {
      reject("COMMAND_NOT_SEND", "Not connected", nil)
      return
    }
    session.write(data) { success in
      if success { resolve(nil) } else { reject("COMMAND_NOT_SEND", "Write failed", nil) }
    }
  }

  @discardableResult
  private func sendSync(_ data: Data) -> Bool {
    guard session.isConnected else { return false }
    var completed = false
    let semaphore = DispatchSemaphore(value: 0)
    session.write(data) { success in
      completed = success
      semaphore.signal()
    }
    _ = semaphore.wait(timeout: .now() + 2.0)
    return completed
  }
}

// MARK: - ESC/POS helpers
private enum Escpos {
  static func initPrinter() -> Data { Data([0x1B, 0x40]) }

  static func feed(lines: Int) -> Data { Data([0x1B, 0x64, UInt8(clamping: lines)]) }

  static func leftSpace(_ sp: Int) -> Data {
    let low = UInt8(sp % 256)
    let high = UInt8(sp / 256)
    return Data([0x1D, 0x4C, low, high])
  }

  static func lineSpace(_ sp: Int) -> Data {
    if sp > 0 { return Data([0x1B, 0x33, UInt8(clamping: sp)]) }
    return Data([0x1B, 0x32])
  }

  static func underline(_ level: Int) -> Data { Data([0x1B, 0x2D, UInt8(clamping: level)]) }

  static func align(_ align: Int) -> Data { Data([0x1B, 0x61, UInt8(clamping: align)]) }

  static func bold(_ weight: Int) -> Data { Data([0x1B, 0x45, UInt8(clamping: weight)]) }

  static func rotate(_ rotate: Int) -> Data { Data([0x1B, 0x56, UInt8(clamping: rotate)]) }

  static func cut(lines: Int) -> Data { Data([0x1D, 0x56, 0x42, UInt8(clamping: lines)]) }

  static func cutOnePoint() -> Data { Data([0x1D, 0x56, 0x42, 0x00]) }

  static func selfTest() -> Data { Data([0x1D, 0x28, 0x41]) }

  static func cashDrawer(mode: Int, time1: Int, time2: Int) -> Data {
    Data([0x1B, 0x70, UInt8(clamping: mode), UInt8(clamping: time1), UInt8(clamping: time2)])
  }

  static func text(
    _ text: String,
    encoding: String,
    codepage: Int,
    widthTimes: Int,
    heightTimes: Int,
    fontType: Int
  ) -> Data {
    var data = Data()
    let size: UInt8 = UInt8(((widthTimes & 0x0F) << 4) | (heightTimes & 0x0F))
    data.append(Data([0x1D, 0x21, size]))
    data.append(Data([0x1B, 0x74, UInt8(clamping: codepage)]))
    data.append(Data([0x1B, 0x4D, UInt8(clamping: fontType)]))
    if let textData = text.data(using: nsEncoding(for: encoding)) {
      data.append(textData)
    }
    return data
  }

  static func columns(
    widths: [Int],
    aligns: [Int],
    texts: [String],
    encoding: String,
    codepage: Int,
    widthTimes: Int,
    heightTimes: Int,
    fontType: Int
  ) -> Data {
    var output = Data()
    let padding = 1
    var table: [[String]] = []
    for i in widths.indices {
      let width = widths[i] - padding
      let text = texts[i]
      var split: [ColumnLine] = []
      var shorter = 0
      var counter = 0
      var temp = ""
      for ch in text {
        let l = ch.isChinese ? 2 : 1
        if l == 2 { shorter += 1 }
        temp.append(ch)
        if counter + l < width {
          counter += l
        } else {
          split.append(ColumnLine(shorter: shorter, text: temp))
          temp = ""
          counter = 0
          shorter = 0
        }
      }
      if !temp.isEmpty { split.append(ColumnLine(shorter: shorter, text: temp)) }
      let align = aligns[i]
      var formatted: [String] = []
      for line in split {
        var empty = String(repeating: " ", count: width + padding - line.shorter)
        let ss = line.text
        var startIdx = 0
        if align == 1 && ss.count < width - line.shorter {
          startIdx = (width - line.shorter - ss.count) / 2
          if startIdx + ss.count > width - line.shorter { startIdx -= 1 }
          if startIdx < 0 { startIdx = 0 }
        } else if align == 2 && ss.count < width - line.shorter {
          startIdx = width - line.shorter - ss.count
        }
        let replaceRange = empty.index(empty.startIndex, offsetBy: startIdx)..<empty.index(empty.startIndex, offsetBy: startIdx + ss.count)
        empty.replaceSubrange(replaceRange, with: ss)
        formatted.append(empty)
      }
      table.append(formatted)
    }

    let maxRows = table.map { $0.count }.max() ?? 0
    for row in 0..<maxRows {
      var line = ""
      for (colIdx, column) in table.enumerated() {
        if row < column.count {
          line.append(column[row])
        } else {
          line.append(String(repeating: " ", count: widths[colIdx]))
        }
      }
      line.append("\n\r")
      output.append(text(line, encoding: encoding, codepage: codepage, widthTimes: widthTimes, heightTimes: heightTimes, fontType: fontType))
    }
    return output
  }

  static func barcode(content: String, type: Int, width: Int, height: Int, fontType: Int, fontPosition: Int) -> Data {
    var data = Data()
    data.append(Data([0x1D, 0x48, UInt8(clamping: fontPosition)]))
    data.append(Data([0x1D, 0x66, UInt8(clamping: fontType)]))
    data.append(Data([0x1D, 0x77, UInt8(clamping: width)]))
    data.append(Data([0x1D, 0x68, UInt8(clamping: height)]))
    if let bytes = content.data(using: .ascii) {
      data.append(Data([0x1D, 0x6B, UInt8(clamping: type)]))
      data.append(bytes)
      data.append(0x00)
    }
    return data
  }

  static func generateQRCode(_ content: String, size: Int) -> UIImage? {
    guard let filter = CIFilter(name: "CIQRCodeGenerator") else { return nil }
    filter.setValue(content.data(using: .utf8), forKey: "inputMessage")
    filter.setValue("M", forKey: "inputCorrectionLevel")
    guard let output = filter.outputImage else { return nil }
    let scale = CGFloat(size) / output.extent.size.width
    let transformed = output.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
    let context = CIContext()
    guard let cgImage = context.createCGImage(transformed, from: transformed.extent) else { return nil }
    return UIImage(cgImage: cgImage)
  }

  static func raster(image: UIImage, maxWidth: Int, leftPadding: Int) -> Data {
    guard let cgImage = image.cgImage else { return Data() }
    let scale = min(1.0, CGFloat(maxWidth) / CGFloat(cgImage.width))
    let targetWidth = Int(CGFloat(cgImage.width) * scale)
    let targetHeight = Int(CGFloat(cgImage.height) * scale)
    let colorSpace = CGColorSpaceCreateDeviceGray()
    let bytesPerRow = targetWidth
    guard let context = CGContext(
      data: nil,
      width: targetWidth,
      height: targetHeight,
      bitsPerComponent: 8,
      bytesPerRow: bytesPerRow,
      space: colorSpace,
      bitmapInfo: CGImageAlphaInfo.none.rawValue
    ) else { return Data() }
    context.interpolationQuality = .high
    context.draw(cgImage, in: CGRect(x: 0, y: 0, width: targetWidth, height: targetHeight))
    guard let buffer = context.data else { return Data() }
    let widthBytes = (targetWidth + leftPadding + 7) / 8
    var output = Data([0x1D, 0x76, 0x30, 0x00, UInt8(widthBytes & 0xFF), UInt8((widthBytes >> 8) & 0xFF), UInt8(targetHeight & 0xFF), UInt8((targetHeight >> 8) & 0xFF)])
    for y in 0..<targetHeight {
      var row = Data(count: widthBytes)
      let rowPtr = buffer.advanced(by: y * bytesPerRow).assumingMemoryBound(to: UInt8.self)
      for x in 0..<targetWidth {
        let pixel = rowPtr[x]
        if pixel < 128 {
          let bitIndex = leftPadding + x
          let byteIndex = bitIndex / 8
          let bit = 7 - (bitIndex % 8)
          row[byteIndex] |= UInt8(1 << bit)
        }
      }
      output.append(row)
    }
    return output
  }

  private static func nsEncoding(for encoding: String) -> String.Encoding {
    switch encoding.lowercased() {
    case "utf-8": return .utf8
    case "gbk", "gb18030": return String.Encoding(rawValue: CFStringConvertEncodingToNSStringEncoding(CFStringEncoding(CFStringEncodings.GB_18030_2000.rawValue)))
    default: return .utf8
    }
  }

  private struct ColumnLine {
    let shorter: Int
    let text: String
  }
}

private extension UInt8 {
  init(clamping value: Int) {
    if value < 0 { self = 0 } else if value > 255 { self = 255 } else { self = UInt8(value) }
  }
}

private extension Character {
  var isChinese: Bool {
    guard let scalar = unicodeScalars.first else { return false }
    return (0x4E00...0x9FFF).contains(Int(scalar.value))
  }
}

