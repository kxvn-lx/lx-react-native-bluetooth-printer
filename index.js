import { NativeModules } from "react-native";
const { BluetoothManager, BluetoothEscposPrinter } = NativeModules;

const ERROR_CORRECTION = {
  L: 1,
  M: 0,
  Q: 3,
  H: 2,
};

const BARCODETYPE = {
  UPC_A: 65, //11<=n<=12
  UPC_E: 66, //11<=n<=12
  JAN13: 67, //12<=n<=12
  JAN8: 68, //7<=n<=8
  CODE39: 69, //1<=n<=255
  ITF: 70, //1<=n<=255(even numbers)
  CODABAR: 71, //1<=n<=255
  CODE93: 72, //1<=n<=255
  CODE128: 73, //2<=n<=255
};
const ROTATION = {
  OFF: 0,
  ON: 1,
};
const ALIGN = {
  LEFT: 0,
  CENTER: 1,
  RIGHT: 2,
};
const MODE = {
  DISABLE: 0,
  ENABLE: 1,
};

const PAGE_WIDTH = {
  WIDTH_58: 384,
  WIDTH_80: 576,
};

module.exports = {
  BluetoothManager,
  BluetoothEscposPrinter,
  ERROR_CORRECTION,
  BARCODETYPE,
  ROTATION,
  ALIGN,
  MODE,
  PAGE_WIDTH,
};
