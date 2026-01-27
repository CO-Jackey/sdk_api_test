package com.itri.multible.itriuwbhr32hz;

//Android Handler
//import android.os.Handler;
//import android.os.Looper;
//import android.os.Message;

//Java Handler
// import android.bluetooth.BluetoothGatt;
// import android.bluetooth.BluetoothGattCharacteristic;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

//import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class HealthCalculate {
    private String TAG = this.getClass().getSimpleName();

    private static class JavaHandler {
        private final ScheduledExecutorService executorService;

        public JavaHandler() {
            executorService = Executors.newSingleThreadScheduledExecutor();
        }

        public void post(Runnable runnable) {
            executorService.submit(runnable);
        }

        public void postDelayed(Runnable runnable, long delayMillis) {
            executorService.schedule(runnable, delayMillis, TimeUnit.MILLISECONDS);
        }

        public void shutdown() {
            executorService.shutdown();
        }
    }

    private JavaHandler handler = new JavaHandler();

    private String version = "1.3"; // 20250925

    /***
     * splitPackage回傳，OK
     */
    public static int RESULT_OK = -1;
    /***
     * 輸入data的Header錯誤
     */
    public static int ERROR_FIRST_BYTE = 101;
    /***
     * 輸入data的CheckSum錯誤
     */
    public static int ERROR_CHECKSUM = 102;

    private MainInfo mainInfo;
    private long last_ble_time = 0, last_send_time = 0;
    private final int RawdataCounter = 512;
    private HRBRCalculate mlib_HRCalculate = new HRBRCalculate();
    private HRBRCalculate mlib_BRCalculate = new HRBRCalculate();
    private final boolean[] InitBit_HR = { true };
    private final boolean[] InitBit_BR = { true };

    private int HRCanvasCounter = 0;
    private int c_drawValueP2P_HR = 31;
    private int BRCanvasCounter = 0;
    private int c_drawValueP2P_BR = 31;

    private int RX_datacount = 0;
    private final int[] m_HRdoubleRawData = new int[RawdataCounter];
    private final double[] m_HRdoubleRawOut = new double[RawdataCounter];

    private final int[] m_BRdoubleRawData = new int[RawdataCounter];
    private final double[] m_BRdoubleRawOut = new double[RawdataCounter];

    // private final int[] DrawHRFiltered = new int[DRAW_SIZE];
    private final int[] HRValue_median = new int[5];
    private final int[] BRValue_median = new int[5];
    private int HRValue = 0, BRValue = 0;

    private int wearingLimit = 32 * 3;
    private int NotWearingCount = 0; // 沒有配戴的計數器 >=wearingLimit代表沒有配戴 <=wearingLimit代表有配戴
    private int lastGyroValueX = -1, lastGyroValueY = -1, lastGyroValueZ = -1;
    // private int GyroValueX = -1, GyroValueY = -1, GyroValueZ = -1;
    // private int type = -1; //預設-1 一進來就會先設定type

    private int averageRange = 0;

    /**
     * 建構子，設定量測類型(人、貓、兔子、狗)
     * 
     * @param type 0: human, 1: cat, 2: rabbit, 3: dog
     */
    public HealthCalculate(int type) {
        mainInfo = new MainInfo(TAG, 0);
        setType(type);
    }

    /**
     * 取得目前SDK版本
     * 
     * @return version
     */
    public String getVersion() {
        return version;
    }

    /**
     * 取得BLE裝置回傳TimeStamp
     * 
     * @return TimeStamp
     */
    public long getTimeStamp() {
        return mainInfo.TimeStamp;
    }

    /**
     * 取得目前是否配戴之狀態
     * 
     * @return true代表有配戴，false代表未配戴
     */
    public boolean getIsWearing() {
        return mainInfo.isWearing;
    }

    /**
     * 取得計算後之心跳
     * 
     * @return HRValue
     */
    public int getHRValue() {
        return mainInfo.HRValue;
    }

    /***
     * 取得計算後之呼吸
     * 
     * @return BRValue
     */
    public int getBRValue() {
        return mainInfo.BRValue;
    }

    /**
     * 取得計算後之RRI
     * 
     * @return RRIValue
     */
    public int getRRIValue() {
        return mainInfo.RRIValue;
    }

    /**
     * 此項ESP版才有提供，
     * 取得計算後之溫度
     * 
     * @return TempValue
     */
    public float getTempValue() {
        return mainInfo.TempValue;
    }

    /**
     * 取得計算後之步數
     * 
     * @return StepValue
     */
    public int getStepValue() {
        return mainInfo.StepValue;
    }

    /**
     * 取得計算後之濕度
     * 
     * @return HumValue
     */
    public int getHumValue() {
        return mainInfo.HumValue;
    }

    /**
     * 取得計算後之壓力感測數值
     * 
     * @return PressureValue
     */
    public int getPressureValue() {
        return mainInfo.PressureValue;
    }

    /***
     * 取得目前電源百分比 0~100
     * 
     * @return PowerValue
     */
    public int getPowerValue() {
        return mainInfo.PowerValue;
    }

    /***
     * 此項ESP版才有提供，
     * 取得Gyro X
     * 
     * @return GyroValueX
     */
    public int getGyroValueX() {
        return mainInfo.GyroValueX;
    }

    /***
     * 此項ESP版才有提供，
     * 取得Gyro Y
     * 
     * @return GyroValueY
     */
    public int getGyroValueY() {
        return mainInfo.GyroValueY;
    }

    /***
     * 此項ESP版才有提供，
     * 取得Gyro Z
     * 
     * @return GyroValueZ
     */
    public int getGyroValueZ() {
        return mainInfo.GyroValueZ;
    }

    /***
     * 此項STM版才有提供，
     * 取得寵物姿態，未取得數值前為-1
     * //0 =「行走walking」
     * //1 =「嗅探sniffing」
     * //2 =「小跑trotting」
     * //3 =「奔跑galloping」
     * //4 =「靜態休息static resting」
     * 
     * @return PetPose
     */
    public int getPetPoseValue() {
        return mainInfo.PetPose;
    }

    /***
     * 取得濾過的呼吸波形，size = 320
     * 
     * @return DrawBRFiltered
     */
    public Double[] getBRFiltered() {
        return mainInfo.DrawBRFiltered;
    }

    /***
     * 取得RawData，size = 320
     * 
     * @return DrawRawData
     */
    public Integer[] getRawData() {
        return mainInfo.DrawBRRawData;
    }

    /***
     * 取得濾過的心跳波形，size = 320
     * 
     * @return DrawHRFiltered
     */
    public Double[] getHRFiltered() {
        return mainInfo.DrawHRFiltered;
    }

    /***
     * 取得目前量測類型(人、貓、兔子、狗)
     * 
     * @return 0: human, 1: cat, 2: rabbit, 3: dog, 預設:0
     */
    public int getType() {
        return mlib_HRCalculate.getType();
    }

    /***
     * 取得心跳計算後的傅立葉，size = 128
     * 
     * @return FFTout
     */
    public Double[] getFFTOut() {
        return mainInfo.FFTout;
    }

    /***
     * 處理從BLE裝置Notification收到的資料
     * 
     * @param data package from ble notification, data size = 19
     * @return Result Code
     *         RESULT_OK = -1;
     *         ERROR_FIRST_BYTE = 101, data[0] != 0xFF;
     *         ERROR_CHECKSUM = 102, CheckSum != 0;
     */
    public int splitPackage(final byte[] data) {

        // Log.i("Device1", "split Data.");
        // try {
        // --------- first byte -------//
        // 0xFF or 0xFA
        if ((data[0] & 0xFF) == 255 || (data[0] & 0xFF) == 250) {
            int dataSize = data.length;
            if (dataSize == 17) {
                dataSize = 16;
            }
            // --------- CheckSum -------//
            int CheckSum = 0;
            for (int i = 0; i < dataSize; i++) {
                CheckSum += (data[i] & 0xFF);
            }
            CheckSum = (CheckSum % 16);

            if (CheckSum == 0) {
                long timestamp_1 = (long) (data[BLEConst.BLE_TIMESTAMP_1] & 0xFF);
                long timestamp_2 = (long) (data[BLEConst.BLE_TIMESTAMP_2] & 0xFF);
                long timestamp_3 = (long) (data[BLEConst.BLE_TIMESTAMP_3] & 0xFF);
                long timestamp_4 = (long) (data[BLEConst.BLE_TIMESTAMP_4] & 0xFF);
                long timestamp_5 = (long) (data[BLEConst.BLE_TIMESTAMP_5] & 0xFF);
                mainInfo.TimeStamp = 10 * (timestamp_1 + timestamp_2 * 256 + timestamp_3 * 256 * 256
                        + timestamp_4 * 256 * 256 * 256 + timestamp_5 * 256 * 256 * 256 * 256);

                int Lb1 = (data[BLEConst.BLE_RAW_LOW] & 0xFF);
                int Hb1 = (data[BLEConst.BLE_RAW_HIGH] & 0xFF);
                int i_HR = Lb1 + Hb1 * 256;
                m_HRdoubleRawData[RX_datacount] = i_HR;
                m_BRdoubleRawData[RX_datacount] = i_HR;
                int Lbs1 = (data[BLEConst.BLE_STEP_LOW] & 0xFF);
                int Hbs1 = (data[BLEConst.BLE_STEP_HIGH] & 0xFF);
                mainInfo.StepValue = Lbs1 + Hbs1 * 256;

                // 只有每次傳輸19個byte的版本有傳輸gyro
                if (dataSize == 19) {
                    mainInfo.GyroValueX = (data[BLEConst.BLE_GYRO_X] & 0xFF);
                    mainInfo.GyroValueY = (data[BLEConst.BLE_GYRO_Y] & 0xFF);
                    mainInfo.GyroValueZ = (data[BLEConst.BLE_GYRO_Z] & 0xFF);

                    // 只有19個byte的情形有溫度
                    int tpl = (data[BLEConst.BLE_TEMPERATURE_LOW] & 0xFF);
                    int tph = (data[BLEConst.BLE_TEMPERATURE_HIGH] & 0xFF);
                    float temp_value = (tpl + tph * 256) / 100f;
                    temp_value = (temp_value - 32.2f) * (28.1f - 26.3f) / (35f - 32.2f) + 26.3f;
                    if (temp_value > 100f)
                        temp_value = 100f;
                    if (temp_value < 0f)
                        temp_value = 0f;
                    mainInfo.TempValue = temp_value;

                    int hum_value = (data[BLEConst.BLE_HUMIDITY] & 0xFF);
                    hum_value = (hum_value - 44) * (71 - 55) / (74 - 44) + 55;
                    if (hum_value > 100)
                        hum_value = 100;
                    if (hum_value < 0)
                        hum_value = 0;
                    mainInfo.HumValue = hum_value;
                } else {
                    mainInfo.PetPose = (data[BLEConst.BLE_PET_POSE] & 0xFF);
                    mainInfo.PressureValue = (data[BLEConst.BLE_Pressure] & 0xFF);
                }
                mainInfo.isWearing = checkIsWearing(mainInfo.GyroValueX, mainInfo.GyroValueY, mainInfo.GyroValueZ);

                int powerValue = (data[BLEConst.BLE_BATTERY] & 0xFF);
                if (powerValue >= 100)
                    powerValue = 100;
                mainInfo.PowerValue = powerValue;

                int spo2_value = (data[BLEConst.BLE_RRI] & 0xFF);
                if (spo2_value >= 20) {
                    mainInfo.RRIValue = spo2_value;
                } else if (spo2_value == 0) {
                    mainInfo.RRIValue = 0;
                }

                RX_datacount++;
                if ((RX_datacount % 16) == 0) {
                    long currentTime = System.currentTimeMillis();
                    long diff_time = currentTime - last_ble_time;
                    // Log.i(mainInfo.TAG, "diff_time = " + diff_time);
                    last_ble_time = currentTime;
                    // handler.sendEmptyMessage(1);
                    handler.post(() -> {
                        Algorithm();

                        mainInfo.HRValue = HRValue;
                        mainInfo.BRValue = BRValue;
                        for (int i = 0; i < 128; i++) {
                            mainInfo.FFTout[i] = mlib_HRCalculate.m_HRdoubleFFT[i]; // 反過來畫
                        }
                    });
                }
                if (RX_datacount >= RawdataCounter)
                    RX_datacount = 0;
            } else {
                // Log.e(mainInfo.TAG, "DataError: CheckSum != 0");
                return ERROR_CHECKSUM;
            }
        } else {
            // Log.e(mainInfo.TAG, "DataError: data[0] != 255");
            return ERROR_FIRST_BYTE;
        }
        // } catch (Exception e) {
        // e.printStackTrace();
        // }
        return RESULT_OK;
    }

    /***
     * 設定量測類型(人、貓、兔子、狗)
     * 
     * @param type 0: human, 1: cat, 2: rabbit, 3: dog, 預設:0
     */
    public void setType(int type) {
        // if (type != detection_type) {
        // type = detection_type;

        // Log.i(TAG, "Set Type: " + type);
        // if(type == 3) {
        // getCustomFilterParam();
        // } else {
        // }
        mlib_HRCalculate.setType(type);
        mlib_BRCalculate.setType(type);
        // type: 0: human, 1: cat, 2: rabbit, 3: dog
        if (type == 0) {
            Arrays.fill(HRValue_median, 80);
        } else if (type == 1) {
            Arrays.fill(HRValue_median, 100);
        }
        // 兔子
        else if (type == 2) {
            Arrays.fill(HRValue_median, 160);
        } else if (type == 3) {
            Arrays.fill(HRValue_median, 90);
        } else {
            Arrays.fill(HRValue_median, 80);
        }
    }

    /***
     * 設定呼吸閥值，過濾不是呼吸的雜訊
     * 
     * @param threshold 呼吸閥值, 預設200
     */
    public void setBRThreshold(int threshold) {
        mlib_BRCalculate.setBRThreshold(threshold);
    }

    private int gyroThreshold = 5;

    /***
     * 設定Gyro閥值，如果小於閥值 代表未配戴
     * 
     * @param threshold Gyro閥值, 預設5
     */
    public void setGyroThreshold(int threshold) {
        gyroThreshold = threshold;
    }

    /***
     * 取得Gyro閥值，如果小於閥值 代表未配戴
     * 
     * @return Gyro閥值, 預設5
     */
    public int getGyroThreshold() {
        return gyroThreshold;
    }

    private boolean checkIsWearing(int GyroX, int GyroY, int GyroZ) {
        boolean isWearing; // 是否有配戴

        boolean isBRWearing = true; // BR判斷是否有配戴
        boolean isGyroWearing = true; // Gyro判斷是否有配戴
        if (Math.abs(lastGyroValueX - GyroX) <= gyroThreshold
                && Math.abs(lastGyroValueY - GyroY) <= gyroThreshold
                && Math.abs(lastGyroValueZ - GyroZ) <= gyroThreshold) {
            isGyroWearing = false;
        }
        if (BRValue <= 2) {
            isBRWearing = false;
        }

        // 兩個一起判斷的版本
        // if(Math.abs(lastGyroValueX - GyroX) <= gyroThreshold
        // && Math.abs(lastGyroValueY - GyroY) <= gyroThreshold
        // && Math.abs(lastGyroValueZ - GyroZ) <= gyroThreshold) {
        // if(BRValue == 0) {
        // isWearing = false;
        // }
        // }

        lastGyroValueX = GyroX;
        lastGyroValueY = GyroY;
        lastGyroValueZ = GyroZ;

        if (!isBRWearing || !isGyroWearing) {
            isWearing = false;
        } else {
            isWearing = true;
        }

        if (isWearing) {
            // NotWearingCount--;
            // if(NotWearingCount <= 0) {
            NotWearingCount = 0;
            // }
        } else {
            NotWearingCount++;
            if (NotWearingCount >= wearingLimit) {
                NotWearingCount = wearingLimit;
            }
        }
        if (NotWearingCount >= wearingLimit) {
            return false;
        } else {
            return true;
        }

    }

    // private final Handler handler = new Handler(Looper.myLooper()) {
    // /**
    // *
    // * @param msg if what = 1, do algorithm(not in ui thread), what = 2, run in ui
    // thread.
    // */
    // @Override
    // public void handleMessage(@NonNull Message msg) {
    // super.handleMessage(msg);
    // switch (msg.what) {
    // case 1:
    //// setType();
    //
    // Algorithm();
    //
    // mainInfo.HRValue = HRValue;
    // mainInfo.BRValue = BRValue;
    // for (int i = 0; i < 128; i++) {
    // mainInfo.FFTout[i] = mlib_HRCalculate.m_HRdoubleFFT[i]; //反過來畫
    // }
    //// Log.d(TAG, "HRValue = " + HRValue);
    //// Log.d(TAG, "BRValue = " + BRValue);
    //
    // break;
    // case 2:
    // break;
    // default:
    // break;
    // }
    //
    //
    // }
    // };
    private void Algorithm() {
        double[] tempHRdoubleRawOut_1 = new double[1];
        double[] m_HR_rate = new double[1];
        double[] tempBRdoubleRawOut_1 = new double[1];
        double[] m_BR_rate = new double[1];

        for (int i = 0; i < 16; i++) {
            mlib_BRCalculate.BRCallIn(m_BRdoubleRawData[BRCanvasCounter], InitBit_BR, m_BR_rate, tempBRdoubleRawOut_1);
            m_BRdoubleRawOut[BRCanvasCounter] = tempBRdoubleRawOut_1[0];

            ArrayAddValue(mainInfo.DrawBRRawData, m_BRdoubleRawData[BRCanvasCounter]);
            ArrayAddValue(mainInfo.DrawBRFiltered, tempBRdoubleRawOut_1[0]);

            mlib_HRCalculate.HRCallIn(m_HRdoubleRawData[HRCanvasCounter], InitBit_HR, m_HR_rate, BRValue,
                    tempHRdoubleRawOut_1);

            m_HRdoubleRawOut[HRCanvasCounter] = tempHRdoubleRawOut_1[0];

            ArrayAddValue(mainInfo.DrawHRRawData, m_HRdoubleRawData[HRCanvasCounter]);
            ArrayAddValue(mainInfo.DrawHRFiltered, tempHRdoubleRawOut_1[0]);
            // Log.i(TAG, "RawData = " + m_HRdoubleRawData[HRCanvasCounter]);
            // Log.i(TAG, "HR Filtered = " + tempHRdoubleRawOut_1[0]);
            // Log.i(TAG, "BR Filtered = " + tempBRdoubleRawOut_1[0]);

            c_drawValueP2P_BR++;
            BRCanvasCounter++;

            c_drawValueP2P_HR++;
            HRCanvasCounter++;

            // ============ 32 point = 1 second =========//
            if (c_drawValueP2P_BR >= 32) {
                c_drawValueP2P_BR = 0;
                // -----BR-----//
                BRValue = 0;
                for (int j = 0; j < 4; j++) {
                    BRValue_median[j] = BRValue_median[j + 1];
                    // BRValue += BRValue_median[j];
                }
                BRValue_median[4] = (int) m_BR_rate[0];
                BRValue = averageByPercentage(BRValue_median, 0.6);
                // BRValue += BRValue_median[4];
                // BRValue = (int) Math.round(BRValue / 5.0);
            }

            if (c_drawValueP2P_HR >= 32) {
                c_drawValueP2P_HR = 0;

                for (int k = 0; k < 4; k++)
                    HRValue_median[k] = HRValue_median[k + 1];
                HRValue_median[4] = (int) (m_HR_rate[0] * 1);

                HRValue = averageByPercentage(HRValue_median, 0.6);
                // int tmphr = 0;
                // for (int j = 1; j < 4; j++)
                // tmphr = HRValue_median[j] + tmphr;
                //
                // HRValue = (int) Math.round(tmphr / 3.0);

            }
            if (BRCanvasCounter == 512)
                BRCanvasCounter = 0;
            if (HRCanvasCounter == 512)
                HRCanvasCounter = 0;
        }
    }

    /**
     * 將data塞入陣列，先進先出
     * 
     * @param DataArray Array
     * @param data      data put in Array's end
     */
    private <T extends Comparable<T>> void ArrayAddValue(T[] DataArray, T data) {
        if (DataArray.length - 1 >= 0)
            System.arraycopy(DataArray, 0, DataArray, 1, DataArray.length - 1);
        DataArray[0] = data;
    }

    private int averageByPercentage(int[] values, double percentage) {
        // 確認百分比有效性
        if (percentage < 0 || percentage > 1) {
            throw new IllegalArgumentException("Percentage must be between 0 and 1");
        }

        // 複製一份陣列並進行排序
        int[] copiedValue = values.clone();
        Arrays.sort(copiedValue);

        int totalLength = values.length;
        int middleLength = (int) (totalLength * percentage);

        int start = (totalLength - middleLength) / 2;
        int end = start + middleLength;

        int sum = 0;
        // 計算指定百分比的總和
        for (int i = start; i < end; i++) {
            sum += copiedValue[i];
        }

        // System.out.println("averageByPercentage: start = " + start + ", end = " +
        // end);

        // 計算平均並四捨五入
        return Math.round((float) sum / middleLength);
    }

    // private long lastHeartBeatTime = 0;

    /**
     * 發送資料到指定 GATT
     * 
     * @param gatt           對方 BLE 連線的 BluetoothGatt 物件
     * @param characteristic 目標特徵值
     * @param data           要寫入的 byte[]
     * @return true=寫入成功，false=失敗
     */
    // private boolean sendData(BluetoothGatt gatt, BluetoothGattCharacteristic
    // characteristic, byte[] data) {
    // if (gatt == null || characteristic == null) {
    // System.out.println("GATT 或 Characteristic 無效");
    // return false;
    // }

    // characteristic.setValue(data);
    // boolean status = gatt.writeCharacteristic(characteristic);
    // return status;
    // }

    /**
     * 發送心跳訊號，限制時間間隔
     * 
     * @param gatt           對方 BLE 連線的 BluetoothGatt 物件
     * @param characteristic 目標特徵值
     * @param intervalSec    發送週期
     * @return 0: 失敗, 1: 成功, -1: 未發送
     */
    // public int sendHeartBeat(BluetoothGatt gatt, BluetoothGattCharacteristic
    // characteristic, int intervalSec) {
    // long currentTime = System.currentTimeMillis();
    // if (currentTime - lastHeartBeatTime > intervalSec * 1000L) {

    // byte[] msg = new byte[3];
    // msg[0] = (byte) 0xFD;
    // msg[1] = (byte) 0xFD;
    // msg[2] = calculateChecksum(msg);
    // boolean status = sendData(gatt, characteristic, msg);
    // // System.out.println("Send HeartBeat status = " + status);
    // if (status) {
    // lastHeartBeatTime = currentTime;
    // }
    // return status ? 1 : 0;
    // }
    // return -1;
    // }

    // private byte calculateChecksum(byte[] data) {
    // int sum = 0;

    // // 計算前兩個 byte 的總和
    // for (int i = 0; i < data.length - 1; i++) {
    // sum += data[i] & 0xFF; // 確保將 byte 視為無符號
    // }

    // // 計算使總和為 16 的倍數所需的 checksum
    // int checksum = (16 - (sum % 16)) % 16;
    // return (byte) checksum;
    // }
}
