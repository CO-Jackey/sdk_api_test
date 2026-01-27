package com.itri.multible.itriuwbhr32hz;


import java.util.Arrays;

public class MainInfo {
    String title;
    String name;
    String TAG = "";
    int index;
    private final int DRAW_SIZE = 320;
    Double[] DrawHRFiltered = new Double[DRAW_SIZE];
    Double[] DrawBRFiltered = new Double[DRAW_SIZE];
    Integer[] DrawHRRawData = new Integer[DRAW_SIZE];
    Integer[] DrawBRRawData = new Integer[DRAW_SIZE];
    Double[] FFTout = new Double[128];

    long TimeStamp = 0;

    boolean isWearing = true;
    int StepValue;
    int HRValue;
    int BRValue;
    int PowerValue;
    int RRIValue;
    int HumValue;
    int PressureValue;
    float TempValue;
    int GyroValueX, GyroValueY, GyroValueZ;
    int PetPose = -1;
    MainInfo(String title, int index) {
        this.title = title;
        this.index = index;
        this.TAG = title;
        Arrays.fill(DrawHRFiltered, 0.0);
        Arrays.fill(DrawBRFiltered, 0.0);
        Arrays.fill(DrawHRRawData, 0);
        Arrays.fill(DrawBRRawData, 0);
        Arrays.fill(FFTout, 0.0);
    }
}
