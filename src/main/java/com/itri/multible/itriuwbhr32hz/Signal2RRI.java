package com.itri.multible.itriuwbhr32hz;

public class Signal2RRI {
    //************* Define Final Parameters *****************************//
    //*********** common.h define ***********//

    //	    private final int size_rawbank = 2048 ;  //fs64 * 32sec
    private final int size_rawbank = 512 ;  //fs32 * 32sec
    private double wrongconter = 0;
    public int HPeak_size = 0;
    public int LPeak_size = 0;
    public double HPeak_value = 0;
    public double LPeak_value = 0;
    private double HPeak_sum = 0;
    private double LPeak_sum = 0;


    public double HR_HPeak_value = 0;
    public double HR_LPeak_value = 0;
    private final int buf_hr_range_size = 10;
    private double[] buf_hr = new double[size_rawbank];
    private double[] buf_hr_range = new double[buf_hr_range_size];
    private int buf_hr_range_index = 0;
    private boolean isHRStable = true;

    //************ this class defiine *******//
//		private final int fs = 64;
    private final int fs = 32; //  fine tune <3
    private final int sigLenMax = size_rawbank;
    private final double winRatio = 0.5;
    private final int winStep = 32;   //2017/3/23= 64
    //		private final int winStep = 32;
    private double[] buf_breath = new double[sigLenMax];
    private long windowLen = fs, windowMean = 0;	//points
    private long rriNum = 0;
    private long retValue = 0;
    private long sigCounter=0;
    private double maxV_f = 0.0, minV_f = 0.0, AdjI = 0.0;
    private int threshold = 200; //呼吸的閥值
    public int FindPeaks (double signal_in)
    {

        //Shift signal data
        int i;
        int maxI = 0, minI = 0;
        double maxV = -99999.9, minV = 99999.9;
        for (i = (sigLenMax - 1); i > 0; --i)
            buf_breath[i] = buf_breath[i - 1];

        buf_breath[0] = signal_in;

        //find peak

        for (i = 0; i < windowLen; i++)
        {

            if (buf_breath[i] > maxV)
            {
                maxV = buf_breath[i];
                maxI = i;
            }
            if (buf_breath[i] < minV)
            {
                minV = buf_breath[i];
                minI = i;
            }
        }

        if (maxI == (long)windowLen / 2)
        {
            if(maxV < 60000) {
                HPeak_value = maxV;
                HPeak_sum += maxV;
                HPeak_size++;
                return 1;
            }
        }
        if (minI == (long)windowLen / 2)
        {
            if(minV > 8000) {
                LPeak_value = minV;
                LPeak_sum += minV;
                LPeak_size++;
                return -1;
            }
        }
        return 0;
    }
    public boolean FindHRStatus (double signal_in)
    {

        //Shift signal data
        int i;
        int maxI = 0, minI = 0;
        double maxV = -99999.0, minV = 99999.0;
        for (i = (sigLenMax - 1); i > 0; --i)
            buf_hr[i] = buf_hr[i - 1];

        buf_hr[0] = signal_in;

        //find peak

        for (i = 0; i < windowLen; i++)
        {

            if (buf_hr[i] > maxV)
            {
                maxV = buf_hr[i];
                maxI = i;
            }
            if (buf_hr[i] < minV)
            {
                minV = buf_hr[i];
                minI = i;
            }
        }

        if (maxI == windowLen / 2)
        {
            HR_HPeak_value = maxV;
        }
        if (minI == windowLen / 2)
        {

            HR_LPeak_value = minV;

            double average = 0.0;

            //先計算平均
            for(i = 0; i < buf_hr_range_size; i++) {
                average += buf_hr_range[i];
            }
            average /= buf_hr_range_size;

            //再計算這次peak的range
            double range = HR_HPeak_value - HR_LPeak_value;
            buf_hr_range[buf_hr_range_index] = range;
            buf_hr_range_index++;
            if(buf_hr_range_index >= buf_hr_range_size)
                buf_hr_range_index = 0;

            //如果range大於average * 1. 判斷這次狀態不穩定
            if(range > average * 2) {
                isHRStable = false;
            } else {
                isHRStable = true;
            }
        }
        return isHRStable;
    }

    public void setBRThreshold(int threshold) {
        this.threshold = threshold;
    }

    private final int buf_amp_size = 10;
    private double[] br_amp_sort = new double[buf_amp_size];
    private double[] br_amp_buf = new double[buf_amp_size];
    private int adaptive_count = 0;


    public double Signal2RRI_ITRI (double signal_in)
    {

        double maxV = -99999.9, minV = 99999.9, BrRate = 0, AdjI_l = 0.0;
        int maxI = 0, minI = 0, retValueTemp = 0, StartAdjI = 0, StopAdjI = 0;
        boolean isPeak = false, lowPeak = false;
        int i;

        //Shift signal data
        for (i = (sigLenMax - 1); i > 0; --i)
            buf_breath[i] = buf_breath[i - 1];

        //for (i = 0; i>0; i++)
        buf_breath[0] = signal_in;

        //find peak

        for (i = 0; i < windowLen; i++)
        {

            if (buf_breath[i] > maxV)
            {
                maxV = buf_breath[i];
                maxI = i;
            }
            if (buf_breath[i] < minV)
            {
                minV = buf_breath[i];
                minI = i;
            }
        }

        if (maxI == (long)windowLen / 2)
        {
            isPeak = true;
            maxV_f = maxV;
//            System.out.println("Signal2RRI: maxV_f = " + maxV_f);
        }
        if (minI == (long)windowLen / 2)
        {
            isPeak = false;;
            minV_f = minV;
//            System.out.println("Signal2RRI: minV_f = " + minV_f);
        }


        if (maxV_f - minV_f >= threshold && isPeak)
        {
            //	Log.e("Test", "RRI:" + String.valueOf(minV_f) + ", "  + String.valueOf(maxV_f));
            //AdjI = (double)((maxI - (StopAdjI + StartAdjI) / 2) / 2);
//            StartAdjI = StopAdjI = 0;
            maxV -= 32;
            for (i = maxI; i>0; --i)
                if (buf_breath[i] > maxV)
                    StartAdjI = i;
            for (i = maxI; i<windowLen; ++i)
                if (buf_breath[i] > maxV)
                    StopAdjI = i;
            AdjI = (double)((maxI - (StopAdjI + StartAdjI) / 2) / 2);

            retValueTemp = (int)((sigCounter - AdjI) / fs * 1000);


            if (14000 > retValueTemp && retValueTemp > 300)
              //  if (14000 > retValueTemp && retValueTemp > 900)
            {
                retValue = retValueTemp;

                //Check and modify window length
                windowMean += (int)(sigCounter);
                sigCounter = 0;
                ++rriNum;
                if (rriNum == 5)
                {
                    windowMean = (long)(windowMean / rriNum * winRatio);
                    if (windowMean > windowLen + winStep || windowMean < windowLen - winStep)
                    {
                        windowLen = windowMean;
                        if (windowLen > sigLenMax)
                            windowLen = sigLenMax;
                    }
                    rriNum = 0;
                    windowMean = 0;
                }
                maxV_f = 0;
                minV_f = 0;
                wrongconter = 0;
            }
            else
            {
                rriNum = 0;
                windowMean = 0;
                sigCounter = 0;
            }

        }
        //else if(isPeak)

        else
        {
            ++sigCounter;
            wrongconter++;
        }



        if (retValue != 0)
        {

            //wrongcounter一秒加32, 160代表呼吸五秒減1
            BrRate = (double)((60000 / retValue) - wrongconter/160);


            if(BrRate <= 0) 	BrRate = 0;
            //	wrongconter = 0;
        }
        else
            BrRate = 0;
//        System.out.println("Signal2RRI: retValue = " + retValue + ", wrongconter = " + wrongconter);
        return BrRate;
    }



}
