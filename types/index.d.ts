declare module "lx-react-native-bluetooth-printer" {
  export enum ERROR_CORRECTION {
    L = 1,
    M = 0,
    Q = 3,
    H = 2,
  }
  export enum BARCODETYPE {
    UPC_A = 65, //11<=n<=12
    UPC_E = 66, //11<=n<=12
    JAN13 = 67, //12<=n<=12
    JAN8 = 68, //7<=n<=8
    CODE39 = 69, //1<=n<=255
    ITF = 70, //1<=n<=255(even numbers)
    CODABAR = 71, //1<=n<=255
    CODE93 = 72, //1<=n<=255
    CODE128 = 73, //2<=n<=255
  }
  export enum ESC_ROTATION {
    OFF = 0,
    ON = 1,
  }
  export enum ALIGN {
    LEFT = 0,
    CENTER = 1,
    RIGHT = 2,
  }
  export enum PAGE_WIDTH {
    WIDTH_58 = 384,
    WIDTH_80 = 576,
  }
  export enum MODE {
    DISABLE = 0,
    ENABLE = 1,
  }

  export type BluetoothDevice = {
    name: string;
    address: string;
  };

  export type ScannedBluetoothDevices = {
    paired: BluetoothDevice[];
    found: BluetoothDevice[];
  };

  export type PrintTextOptions = {
    encoding?: string;
    codepage?: number;
    widthtimes?: number;
    heigthtimes?: number;
    fonttype?: number;
  };

  export type PrintPictureOptions = {
    width?: number;
    height?: number;
    left?: number;
  };

  export class BluetoothManager {
    static enableBluetooth(): Promise<BluetoothDevice[] | null>;
    static disableBluetooth(): Promise<boolean>;
    static isBluetoothEnabled(): Promise<boolean>;
    static scanDevices(): Promise<ScannedBluetoothDevices>;
    static stopScan(): Promise<void>;
    static connect(address: string): Promise<void>;
    static disconnect(address: string): Promise<string>;
    static getConnectedDevice(): Promise<BluetoothDevice | null>;
    static unpair(address: string): Promise<string>;
    static isDeviceConnected(): Promise<boolean>;
    static getConnectedDeviceAddress(): Promise<string | null>;
  }

  export class BluetoothEscposPrinter {
    static printerInit(): Promise<void>;
    static printAndFeed(feed: number): Promise<void>;
    static printerLeftSpace(space: number): Promise<void>;
    static printerLineSpace(space: number): Promise<void>;
    static printerUnderLine(line: number | typeof READABLE): Promise<void>;
    static printerAlign(space: number | typeof ALIGN): Promise<void>;
    static printText(text: string, options?: PrintTextOptions): Promise<void>;
    static printColumn(
      columnWidths: number[],
      columnAligns: number[] | (typeof ALIGN)[],
      columnTexts: string[],
      options?: PrintTextOptions
    ): Promise<void>;
    static setWidth(width: number | typeof PAGE_WIDTH): Promise<void>;
    static printPic(base64Image: string, options?: PrintPictureOptions): Promise<void>;
    static cutLine(line: number): Promise<void>;
    static selfTest(): Promise<void>;
    static rotate(rotate: number | typeof ESC_ROTATION): Promise<void>;
    static setBold(weight: number | typeof ESC_ROTATION): Promise<void>;
    static printQRCode(
      content: string,
      size: number,
      correctionLevel: number | typeof ERROR_CORRECTION,
      leftPadding?: number
    ): Promise<void>;
    static printBarCode(
      content: string,
      barcodeType: number | typeof BARCODETYPE,
      width: number,
      height: number,
      fontType: number | typeof FONTTYPE,
      fontPosition: number
    ): Promise<void>;
    static openDrawer(time: number): Promise<void>;
    static cutOnePoint(): Promise<void>;
  }

}
