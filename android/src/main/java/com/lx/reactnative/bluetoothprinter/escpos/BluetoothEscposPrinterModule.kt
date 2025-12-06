package com.lx.reactnative.bluetoothprinter.escpos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.lx.reactnative.bluetoothprinter.BluetoothService
import com.lx.reactnative.bluetoothprinter.BluetoothServiceStateObserver
import com.lx.reactnative.bluetoothprinter.escpos.command.sdk.Command
import com.lx.reactnative.bluetoothprinter.escpos.command.sdk.PrintPicture
import com.lx.reactnative.bluetoothprinter.escpos.command.sdk.PrinterCommand
import java.nio.charset.Charset
import java.util.Hashtable

class BluetoothEscposPrinterModule(
  reactContext: ReactApplicationContext,
  private val service: BluetoothService,
) : ReactContextBaseJavaModule(reactContext), BluetoothServiceStateObserver {

  companion object {
    private const val TAG = "BluetoothEscposPrinter"
    private const val WIDTH_58 = 384
    private const val WIDTH_80 = 576
    const val NAME = "BluetoothEscposPrinter"
  }

  private var deviceWidth = WIDTH_58

  init {
    service.addStateObserver(this)
  }

  override fun getName(): String = NAME

  override fun getConstants(): MutableMap<String, Any> = hashMapOf(
    "width58" to WIDTH_58,
    "width80" to WIDTH_80,
  )

  @ReactMethod
  fun printerInit(promise: Promise) {
    if (sendDataByte(PrinterCommand.POS_Set_PrtInit())) {
      promise.resolve(null)
    } else {
      promise.reject("COMMAND_NOT_SEND")
    }
  }

  @ReactMethod
  fun printAndFeed(feed: Int, promise: Promise) {
    if (sendDataByte(PrinterCommand.POS_Set_PrtAndFeedPaper(feed))) {
      promise.resolve(null)
    } else {
      promise.reject("COMMAND_NOT_SEND")
    }
  }

  @ReactMethod
  fun printerLeftSpace(sp: Int, promise: Promise) {
    if (sendDataByte(PrinterCommand.POS_Set_LeftSP(sp))) {
      promise.resolve(null)
    } else {
      promise.reject("COMMAND_NOT_SEND")
    }
  }

  @ReactMethod
  fun printerLineSpace(sp: Int, promise: Promise) {
    val command = if (sp > 0) PrinterCommand.POS_Set_LineSpace(sp) else PrinterCommand.POS_Set_DefLineSpace()
    if (command == null || !sendDataByte(command)) {
      promise.reject("COMMAND_NOT_SEND")
    } else {
      promise.resolve(null)
    }
  }

  @ReactMethod
  fun printerUnderLine(line: Int, promise: Promise) {
    if (sendDataByte(PrinterCommand.POS_Set_UnderLine(line))) {
      promise.resolve(null)
    } else {
      promise.reject("COMMAND_NOT_SEND")
    }
  }

  @ReactMethod
  fun printerAlign(align: Int, promise: Promise) {
    if (sendDataByte(PrinterCommand.POS_S_Align(align))) {
      promise.resolve(null)
    } else {
      promise.reject("COMMAND_NOT_SEND")
    }
  }

  @ReactMethod
  fun printText(text: String, options: ReadableMap?, promise: Promise) {
    try {
      val encoding = options?.getString("encoding") ?: "GBK"
      val codepage = options?.getInt("codepage") ?: 0
      val widthTimes = options?.getInt("widthtimes") ?: 0
      val heightTimes = options?.getInt("heigthtimes") ?: 0
      val fonttype = options?.getInt("fonttype") ?: 0

      val bytes = PrinterCommand.POS_Print_Text(
        text,
        encoding,
        codepage,
        widthTimes,
        heightTimes,
        fonttype,
      )
      if (sendDataByte(bytes)) {
        promise.resolve(null)
      } else {
        promise.reject("COMMAND_NOT_SEND")
      }
    } catch (e: Exception) {
      promise.reject(e.message, e)
    }
  }

  @ReactMethod
  fun printColumn(
    columnWidths: ReadableArray,
    columnAligns: ReadableArray,
    columnTexts: ReadableArray,
    options: ReadableMap?,
    promise: Promise,
  ) {
    if (columnWidths.size() != columnTexts.size() || columnWidths.size() != columnAligns.size()) {
      promise.reject("COLUMN_WIDTHS_ALIGNS_AND_TEXTS_NOT_MATCH")
      return
    }
    val totalLen = (0 until columnWidths.size()).sumOf { columnWidths.getInt(it) }
    val maxLen = deviceWidth / 8
    if (totalLen > maxLen) {
      promise.reject("COLUNM_WIDTHS_TOO_LARGE")
      return
    }

    var encoding = "GBK"
    var codepage = 0
    var widthTimes = 0
    var heightTimes = 0
    var fonttype = 0
    if (options != null) {
      encoding = options.getString("encoding") ?: "GBK"
      codepage = options.getInt("codepage")
      widthTimes = options.getInt("widthtimes")
      heightTimes = options.getInt("heigthtimes")
      fonttype = options.getInt("fonttype")
    }

    val table: MutableList<List<String>> = mutableListOf()
    val padding = 1
    for (i in 0 until columnWidths.size()) {
      val width = columnWidths.getInt(i) - padding
      val text = columnTexts.getString(i) ?: ""
      val splitRows = mutableListOf<ColumnSplitedString>()
      var shorter = 0
      var counter = 0
      var temp = StringBuilder()
      text.forEach { ch ->
        val l = if (isChinese(ch)) 2 else 1
        if (l == 2) shorter++
        temp.append(ch)
        if (counter + l < width) {
          counter += l
        } else {
          splitRows.add(ColumnSplitedString(shorter, temp.toString()))
          temp = StringBuilder()
          counter = 0
          shorter = 0
        }
      }
      if (temp.isNotEmpty()) {
        splitRows.add(ColumnSplitedString(shorter, temp.toString()))
      }
      val align = columnAligns.getInt(i)
      val formatted = mutableListOf<String>()
      splitRows.forEach { s ->
        val empty = StringBuilder()
        repeat(width + padding - s.shorter) { empty.append(" ") }
        var startIdx = 0
        val ss = s.str
        if (align == 1 && ss.length < width - s.shorter) {
          startIdx = (width - s.shorter - ss.length) / 2
          if (startIdx + ss.length > width - s.shorter) startIdx--
          if (startIdx < 0) startIdx = 0
        } else if (align == 2 && ss.length < width - s.shorter) {
          startIdx = width - s.shorter - ss.length
        }
        empty.replace(startIdx, startIdx + ss.length, ss)
        formatted.add(empty.toString())
      }
      table.add(formatted)
    }

    val maxRowCount = table.maxOfOrNull { it.size } ?: 0
    val rowsToPrint = Array(maxRowCount) { StringBuilder() }
    for (column in table.indices) {
      val rows = table[column]
      for (row in 0 until maxRowCount) {
        if (rowsToPrint[row].isEmpty()) rowsToPrint[row] = StringBuilder()
        if (row < rows.size) {
          rowsToPrint[row].append(rows[row])
        } else {
          val empty = StringBuilder()
          repeat(columnWidths.getInt(column)) { empty.append(" ") }
          rowsToPrint[row].append(empty)
        }
      }
    }

    rowsToPrint.forEach { row ->
      row.append("\n\r")
      try {
        if (!sendDataByte(
            PrinterCommand.POS_Print_Text(
              row.toString(),
              encoding,
              codepage,
              widthTimes,
              heightTimes,
              fonttype,
            ),
          )
        ) {
          promise.reject("COMMAND_NOT_SEND")
          return
        }
      } catch (e: Exception) {
        Log.e(TAG, "printColumn error", e)
      }
    }
    promise.resolve(null)
  }

  @ReactMethod
  fun setWidth(width: Int) {
    deviceWidth = width
  }

  @ReactMethod
  fun printPic(base64encodeStr: String, options: ReadableMap?) {
    var width = options?.getInt("width") ?: 0
    var leftPadding = options?.getInt("left") ?: 0
    val height = options?.getInt("height") ?: 20
    if (width > deviceWidth || width == 0) width = deviceWidth
    if (leftPadding < 0) leftPadding = 0

    val bytes = Base64.decode(base64encodeStr, Base64.DEFAULT)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    if (bitmap != null) {
      val data = PrintPicture.POS_PrintBMP(bitmap, width, 0, leftPadding)
      sendDataByte(Command.ESC_Init)
      sendDataByte(Command.LF)
      sendDataByte(data)
      sendDataByte(PrinterCommand.POS_Set_PrtAndFeedPaper(height))
      sendDataByte(PrinterCommand.POS_Set_PrtInit())
    }
  }

  @ReactMethod
  fun cutLine(line: Int, promise: Promise) {
    if (sendDataByte(PrinterCommand.POS_Set_Cut(line))) {
      promise.resolve(null)
    } else {
      promise.reject("COMMAND_NOT_SEND")
    }
  }

  @ReactMethod
  fun selfTest(cb: Callback?) {
    val result = sendDataByte(PrinterCommand.POS_Set_PrtSelfTest())
    cb?.invoke(result)
  }

  @ReactMethod
  fun rotate(rotate: Int, promise: Promise) {
    if (sendDataByte(PrinterCommand.POS_Set_Rotate(rotate))) {
      promise.resolve(null)
    } else {
      promise.reject("COMMAND_NOT_SEND")
    }
  }

  @ReactMethod
  fun setBold(weight: Int, promise: Promise) {
    if (sendDataByte(PrinterCommand.POS_Set_Bold(weight))) {
      promise.resolve(null)
    } else {
      promise.reject("COMMAND_NOT_SEND")
    }
  }

  @ReactMethod
  fun printQRCode(content: String, size: Int, correctionLevel: Int, leftPadding: Int, promise: Promise) {
    try {
      val hints: Hashtable<EncodeHintType, Any> = Hashtable()
      hints[EncodeHintType.CHARACTER_SET] = "utf-8"
      hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.forBits(correctionLevel)
      val bitMatrix: BitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
      var width = bitMatrix.width
      if (width > deviceWidth || width == 0) width = deviceWidth
      val height = bitMatrix.height
      val pixels = IntArray(width * height)
      for (y in 0 until height) {
        for (x in 0 until width) {
          pixels[y * width + x] = if (bitMatrix[x, y]) 0xff000000.toInt() else 0xffffffff.toInt()
        }
      }
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
      val data = PrintPicture.POS_PrintBMP(bitmap, size, 0, leftPadding)
      if (sendDataByte(data)) {
        promise.resolve(null)
      } else {
        promise.reject("COMMAND_NOT_SEND")
      }
    } catch (e: Exception) {
      promise.reject(e.message, e)
    }
  }

  @ReactMethod
  fun printBarCode(
    str: String,
    nType: Int,
    nWidthX: Int,
    nHeight: Int,
    nHriFontType: Int,
    nHriFontPosition: Int,
  ) {
    val command = PrinterCommand.getBarCodeCommand(str, nType, nWidthX, nHeight, nHriFontType, nHriFontPosition)
    sendDataByte(command)
  }

  @ReactMethod
  fun openDrawer(nMode: Int, nTime1: Int, nTime2: Int) {
    try {
      val command = PrinterCommand.POS_Set_Cashbox(nMode, nTime1, nTime2)
      sendDataByte(command)
    } catch (e: Exception) {
      Log.d(TAG, "Error opening drawer: ${e.message}")
    }
  }

  @ReactMethod
  fun cutOnePoint() {
    try {
      val command = PrinterCommand.POS_Cut_One_Point()
      sendDataByte(command)
    } catch (e: Exception) {
      Log.d(TAG, "Error cutting: ${e.message}")
    }
  }

  private fun sendDataByte(data: ByteArray?): Boolean {
    if (data == null || service.getState() != BluetoothService.STATE_CONNECTED) return false
    service.write(data)
    return true
  }

  private fun isChinese(c: Char): Boolean {
    val block = Character.UnicodeBlock.of(c)
    return block === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
      block === Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
      block === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
      block === Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
      block === Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
      block === Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS ||
      block === Character.UnicodeBlock.GENERAL_PUNCTUATION
  }

  override fun onBluetoothServiceStateChanged(state: Int, bundle: Map<String, Any?>?) = Unit

  private data class ColumnSplitedString(val shorter: Int, val str: String)
}

