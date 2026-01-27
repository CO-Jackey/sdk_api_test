package com.itri.multible.itriuwbhr32hz;

import static java.lang.Math.sqrt;

public class Ekg {
//    static {
//        System.loadLibrary("HRBRfilter");
//    }
    // public native void fftTest(int n, double[] inArray, double[] outArray);


    private final int fft_size = 256;       // fs32 * 16sec
    public double[] m_HRdoubleFFT = new double[fft_size];

    //public native void fftTest(int n, double[] inArray, double[] outArray);
    //	public native double chebyFilterEkg(double indata);
    //************* Define Final Parameters *****************************//
    //*********** common.h define ***********//
    //private final int ChebyIISections = 13; //fs64: 7 org
    private final int ChebyIISections = 7; //fs64: 7
    private final int size_rawbank = 1024 ;  //fs32 * 32sec
    //	   private final int fft_size = 1024;       // fs64 * 16sec

    //	   private int m_checkhrcounter=CheckHRCounter;
    //============150625==========//
    static int cnt = 0;
    private final int fs = 32;
    //************ this class defiine *******//
    public double FreqPerIdx = (double)fs / fft_size * 60.0; // Frequency per FFT index = fs/FFTpoints*60 = 64/1024*60=3.75  beats/idx
    //fs64: 9 , fs32:18
    private int tIndex2=9;//,newMainTonIdx=18, MainTonIdx=18, tIndex=18, FreqMultiPeak=0;
    private int newMainTonIdx=9, MainTonIdx=9, tIndex=9;
    private int LastMainTonIdx = 9, MainIdxCounter = 0;
    private double LastHRValue = 80;
    public int IsPeakClear=0,FreqMultiPeak=0;

    //************* Filter ***********************************************//
    private double[] Ekg_dbuffer = new double[ChebyIISections];

    //3組 20230131
    /**20230131******人心率*******40,[0.0375,0.25]******0.8hz~4hz*****/
    private double[] humanFilter0 = {0.013960527117611584, -0.040166619434549071,
            0.038559721419614355, 3.5648386876315381E-17, -0.038559721419614411,
            0.040166619434549078, -0.013960527117611577 };
    private double[] humanFilter1 = { 1.0, -5.0769458151149474, 10.988966708003565,
            -12.976888220770459, 8.8186161356103518, -3.2706160078289921,
            0.5176104082426467};
    /**20230131******貓心率*******50,[0.055,0.35]******0.8hz~5.6hz*****/
    private double[] catFilter0 = {  0.065000922806844857, -0.057313150195611118,
            -0.078820917274746827, 1.4433104224408324E-17, 0.078820917274746841,
            0.057313150195611035, -0.0650009228068448};
    private double[] catFilter1 = { 1.0, -3.5663296374389661, 5.6236329305871724,
            -5.1681306092566075, 2.9663320435458713, -0.988904576400806,
            0.1441654057588454};
    /********Rabbit心率******0.5 ~ 9.6hz***/
    /*private double[] rabbitFilter0 = { 0.050685550983603847, -0.05785073905122818,
            -0.035376122386063816, 1.6318957058164679E-16, 0.035376122386063732,
            0.057850739051228027, -0.050685550983603771 };
    private double[] rabbitFilter1 =  {1.0, -3.8913320633505384, 6.6433078273333015,
            -6.4701958709724039, 3.82344964866545, -1.2897311371876494,
            0.19087581230673736 };*/
    /**20230131******Rabbit心率******0.6 ~ 11hz***/
    private double[] rabbitFilter0 = { 0.16040422584966957, -0.0463311061223591,
            -0.38456248936666376, 0.0, 0.38456248936666332, 0.046331106122359383,
            -0.1604042258496694 };
    private double[] rabbitFilter1 =  {1.0, -2.3811292235353254, 2.2742303486020878,
            -1.47292153417521, 0.89040156621785427, -0.2975767819434495,
            0.012460318176474826 };
    /**20230131******狗心率******50,[0.375,0.3]******0.8hz~4.8hz*****/
    private double[] dogFilter0 = { 0.039647306049785482, -0.056525779205202888,
            -0.0051989103649765665, 1.3205205612249632E-17, 0.005198910364976648,
            0.056525779205202825, -0.039647306049785509};
    private double[] dogFilter1 = { 1.0, -4.1527505496016035, 7.5285147685566294,
            -7.6889142607153449, 4.685927869584118, -1.6107342859526272,
            0.24248641919060507 };



    private double[][] array_dv0 = {humanFilter0, catFilter0, rabbitFilter0, dogFilter0};

    private double[][] array_dv1 = {humanFilter1, catFilter1, rabbitFilter1, dogFilter1};

    //0.8-4Hz
/*
    private double[] dv0 = { 0.0089352553895381964, -0.0293525481797712,
            0.032024236538523217, 4.5632580816108105E-17, -0.032024236538523279,
            0.029352548179771204, -0.0089352553895381878 };
    private double[] dv1 = { 1.0, -5.2127675295211748, 11.645384488169435,
            -14.246884897762982, 10.063091127485045, -3.8927564495916429,
            0.64555134917615309 };

*/
//--------------------------------------------------------------------------------
//    private double[] dv0org = {0.015700164061008284, -0.11882345405104394,
//            0.42151408429987408, -0.94438955791684376, 1.52875222000525,
//            -1.9543477468112815, 2.10318858082699, -1.9543477468112826,
//            1.528752220005251, -0.94438955791684431, 0.42151408429987419,
//            -0.11882345405104396, 0.015700164061008284};
//    private double[] dv1org = {1.0, -9.553663764760536, 42.020951787505822,
//            -112.58623177140777, 204.7470948698043, -266.34175393899716,
//            254.17342682895145, -179.31721716347329, 92.824158666832361,
//            -34.385133639832276, 8.6524153383635145, -1.3281514773651404,
//            0.094104264470401239 };
    //== Heart ==//

    private int IsPeakClearTimes=0;//,IsPeakClear=0;



    //  public int tIndex_h=18,tIndex2_h=18,IsPeakClearTimes_h=0,IsPeakClear_h=0,MainTonIdx_h=18,FreqMultiPeak_h=0;
    //====//
    private double[] fftin = new double[fft_size];
    private double[] fftout = new double[fft_size];

    private final int signal_in_buffer_size = 32;
    private double[] signal_in_buffer = new double[signal_in_buffer_size];  // initial has be 0
    private double[] signal_in_sort = new double[signal_in_buffer_size];    // initial has be 0


    //type: 0: human, 1: cat, 2: rabbit, 3: dog
    private int type = 0;


    public void setType(int type) {
        this.type = type;
        if(type == 0) {
            MainTonIdx=9;
        } else if(type == 1) {
            MainTonIdx=12;
        }
        //兔子
        else if(type == 2 ) {
            MainTonIdx=18;
        }
        else if(type == 3) {
            MainTonIdx=12;
        } else {
            MainTonIdx=9;
        }
    }

    //======= Function ========//
    //===============================================================================================//
    //==  Function : InitEkgFilter()
    //==  Description : Init values of EkgFilter 0.9~3 Hz (fs = 64Hz)
    //==   Parameters:
    //==      no
    //===============================================================================================//
    void InitEkgFilter()
    {
        int i;
        for(i = 0; i < ChebyIISections; i++)
            Ekg_dbuffer[i] = 0;
        MainTonIdx=12;
        newMainTonIdx=12;
        IsPeakClear=0;
    }
    //===============================================================================================//
    //==  Function : BubbleSort(double[] iarray,  int ARRAY_SIZE )
    //==   Parameters: no
    //==      iarray : Input double array.(Can be read)
    //==      ARRAY_SIZE[] : Input size of iarray array
    //==
    //===============================================================================================//
    public static void BubbleSort(double[] iarray,  int ARRAY_SIZE)
    {
        int x, y;
        double holder;

        // Bubble sort method.
        for(x = 0; x < ARRAY_SIZE; x++)
        {
            for(y = 0; y < ARRAY_SIZE-1; y++)
            {
                if(iarray[y] > iarray[y+1]) {
                    holder = iarray[y+1];
                    iarray[y+1] = iarray[y];
                    iarray[y] = holder;
                }
            }
        }
    }

    double chebyFilterEkg(double indata)
    {
        int i, j, k;
        int  MedianIdx;
        double  inputDouble, inputDouble_temp;
        inputDouble = indata;
        inputDouble = inputDouble-30000;
        //================//
        for(i=1;i<signal_in_buffer_size;i++)
            signal_in_buffer[i-1]=signal_in_buffer[i];
        signal_in_buffer[signal_in_buffer_size-1]=inputDouble;
        //memcpy(signal_in_sort, signal_in_buffer, sizeof(signal_in_buffer)); //c code
        for(i=0;i<signal_in_buffer_size;i++)signal_in_sort[i]=signal_in_buffer[i]; //modify c code to java

        BubbleSort(signal_in_sort,signal_in_buffer_size);
        MedianIdx=(signal_in_buffer_size-1)/2;
        inputDouble_temp = inputDouble - signal_in_sort[MedianIdx];


        int n = ChebyIISections;


        double b_dbuffer;
        for (k = 0; k < n - 1; k++) {
            Ekg_dbuffer[k] = Ekg_dbuffer[k + 1];//n=13
        }
        Ekg_dbuffer[n - 1] = 0.0;
        for (k = 0; k < n; k++) {
            b_dbuffer = Ekg_dbuffer[k] + inputDouble_temp * array_dv0[type][k];
//            Log.i("TestDV0", "k = " + k + ", " + dv0[k]);
            Ekg_dbuffer[k] = b_dbuffer;
        }

        for (k = 0; k < n - 1; k++) {
            Ekg_dbuffer[k + 1] -= Ekg_dbuffer[0] * array_dv1[type][k + 1];
        }
//        Log.i("EKG", "type = " + type);


        return(Ekg_dbuffer[0]);

    }

    //===============================================================================================//
    //==  Function : HbRate
    //==  Description : Calculating the heart beat rate from received UWB data
    //==   Parameters:
    //==      stopidx :
    //===============================================================================================//

    double HbProcess(int stopidx,double[] buf_ekg,int surl,int surh, int[] max_index,double[] main,double[] left,double[] right)
    {
        int i,j,buf_idx;
        double datamean=0;
        double HB;

        for(i=0;i<fft_size;i++)fftin[i]=0;    //initial fft_size=1024

        //// fftin minus mean of fftin ////
        buf_idx = stopidx;
        for(i=0;i<(fft_size);i++){
            datamean+=buf_ekg[buf_idx--];
            if(buf_idx<0)buf_idx=size_rawbank-1;	  // initial size_rawbank=2048
        }
        datamean/=i;

        //// get the filtered data from the bottom of the buffer  ////
        j=fft_size - 1;    //j=511
        buf_idx = stopidx;
        for(i=0;i<(fft_size);i++)
        {
            fftin[j--] = buf_ekg[buf_idx--]-datamean;
//            fftin[j--] = buf_ekg[buf_idx--];
            if(buf_idx<0) buf_idx=size_rawbank-1;
        }
//        for(i=0;i<(fft_size);i++)
//        {
//            Log.i("George", "fftin[" + String.valueOf(i) + "] = " + String.valueOf(fftin[i]));
//        }
        // fftTest(9, fftin, fftout);
        fft(8, fftin, fftout);

        for(i=0;i<(fft_size);i++) {
            //fftout[i] = Math.sqrt(fftout[i]);
        }


        for(i = 0; i < fft_size; i++)
            m_HRdoubleFFT[i] = fftout[i];
//        for(i = 160; i < 320; i++)
//            m_HBdoubleFFT[i] = fftout[i+fft_size/3];

        HB = get_HbRate(surl,surh, fftout, max_index,main,left,right); //by Fanny
        //System.out.println("main="+main);
        //Log.i("George", "get_HBRate return HB" + String.valueOf(HB));
        //for sleep by Fanny
        if(HB>200){
            //HB=(HB/2);
        }
        if(HB<48)
            HB=48;
        /*else if(HB>190)
            HB=190;*/

        return HB;
    }
    //===============================================================================================//
    //==  Function : fft
    //==  Description : Radix-2 fixed points FFT
    //==   Parameters:
    //==      *rx: the start pointer of input in time domain :
    //==      *rq: the start pointer of output in frequency domain
    //===============================================================================================//


    //===============================================================================================//
    //==  Function : get_Rate
    //==  Description : Get the beat rate from FFT
    //==   Parameters:
    //==
    //===============================================================================================//

    private double get_HbRate(int startPt, int stopPt, double[] indata, int[] max_index,double[] main,double[] left,double[] right)
    {
        int i,searchMax, searchMin;
        double tx,hx,B,powerSpec0,powerSpec1,powerSpec2,powerSpec3,powerSpec4,totalpowerSpec;
        double LargValue1,LargValue2,Rate;

        hx = 0;
        LargValue1 = 0;
        LargValue2 = 0;

        //// Search the largest FFT index
        for(i = startPt;i <= stopPt; i++)
        {
            if(indata[i]>LargValue1)
            {
                LargValue1=indata[i];
                max_index[0] = i;
            }
        }
        LargValue1 = 0;
        searchMin = startPt;
        searchMax = stopPt;

        //// Search the largest FFT peak_1 ////
        for(i = searchMin;i <= searchMax; i++)
        {
            if(indata[i]>LargValue1)
            {
                LargValue1=indata[i];
                newMainTonIdx = i;
            }
        }
//        System.out.println("newMainTonIdx="+newMainTonIdx);
//        System.out.println("LargValue1="+LargValue1);

        //// Search the 2nd FFT peak_2 ////
        searchMax = newMainTonIdx-2;

        if(searchMax < startPt)
            searchMax = startPt;
        else if(searchMax > stopPt)
            searchMax = stopPt;

        for(i = startPt;i <= searchMax; i++)
        {
            if(indata[i]>LargValue2)
            {
                LargValue2=indata[i];
            }
        }

        searchMin = newMainTonIdx+2;
        if(searchMin < startPt)
            searchMin = startPt;
        else if(searchMin > stopPt)
            searchMin = stopPt;

        for(i = searchMin;i <= stopPt; i++)
        {
            if(indata[i]>LargValue2)
            {
                LargValue2=indata[i];
            }
        }
//       System.out.println("LargValue2="+LargValue2);
        //// compare peak_1 and peak_2 to reset MainTon ////
        int tempMainTonIdx = MainTonIdx;
        if( LargValue1 > (LargValue2 * 1.8) )  // power compared = intensity^2
        {                                               //                  6 = 2.45^2
//            Log.i("Ekg", "Use New Peak");
            IsPeakClearTimes++;
            if(IsPeakClearTimes>=2)
            {
                IsPeakClear=1;
                MainTonIdx=newMainTonIdx;
            }
            FreqMultiPeak=0;

        }else if((LargValue1 < (LargValue2 * 1.8))|| LargValue1 == LargValue2) {
//            Log.i("Ekg", "Can't use New Peak: LargValue1 < (LargValue2 * 2)");
            IsPeakClearTimes=0;
            IsPeakClear=0;
            FreqMultiPeak=1;
        }else{
//            Log.i("Ekg", "Can't use New Peak:LargValue1 == (LargValue2 * 2)");
            IsPeakClearTimes=0;
            IsPeakClear=0;
            FreqMultiPeak=0;
        }

        //// search the highest idx within MainTon range for HB ////
//        searchMin = MainTonIdx-2; //MainTonIdx-3;   // MainTonIdx-7; for fast drop
//        if(searchMin < startPt)searchMin = startPt;
//
//        searchMax = MainTonIdx+3;
//        if(searchMax > stopPt)searchMax = stopPt;
//        // Log.e("Test", String.valueOf(searchMin)+","+String.valueOf(searchMax));
//        for(i=searchMin;i<=searchMax; i++)
//        {
//            tx = indata[i]+indata[i-1]+indata[i+1];
//            if(tx > hx)
//            {
//                hx = tx;
//                tIndex = i;
//            }
//        }
//        tIndex2=tIndex;  // bypassing tIndex2
        tIndex2 = MainTonIdx;
        //// Calculating weight  ////
        powerSpec0 = sqrt(indata[tIndex2-1]);
        left[0]=powerSpec0;
        powerSpec1 = sqrt(indata[tIndex2]);
        main[0]=powerSpec1;
        powerSpec2 = sqrt(indata[tIndex2+1]);
        right[0]=powerSpec2;

        if  (powerSpec1<=(powerSpec0+powerSpec2)) {
            if (powerSpec0 * 0.9 >= powerSpec2 && powerSpec2 >= powerSpec0 * 0.7) {
//                Log.e("get_HbRate", "powerSpec State: 0");
                B = ((tIndex2 - 1) * powerSpec0 * 2) + (tIndex2 * powerSpec1) + ((tIndex2 + 1) * powerSpec2);
                totalpowerSpec = powerSpec0 * 2 + powerSpec1 + powerSpec2;

            } else if (powerSpec2 * 0.9 >= powerSpec0 && powerSpec0 >= powerSpec2 * 0.7) {
//                Log.e("get_HbRate", "powerSpec State: 1");
                B = ((tIndex2 - 1) * powerSpec0) + (tIndex2 * powerSpec1) + ((tIndex2 + 1) * powerSpec2 * 2);
                totalpowerSpec = powerSpec0 + powerSpec1 + powerSpec2 * 2;

            } else if (powerSpec0 * 0.7 > powerSpec2) {
//                Log.e("get_HbRate", "powerSpec State: 2");
                B = ((tIndex2 - 1) * powerSpec0 * 3) + (tIndex2 * powerSpec1) + ((tIndex2 + 1) * powerSpec2);
                totalpowerSpec = powerSpec0 * 3 + powerSpec1 + powerSpec2;

            } else if (powerSpec2 * 0.7 > powerSpec0) {
//                Log.e("get_HbRate", "powerSpec State: 3");
                B = ((tIndex2 - 1) * powerSpec0) + (tIndex2 * powerSpec1) + ((tIndex2 + 1) * powerSpec2 * 3);
                totalpowerSpec = powerSpec0 + powerSpec1 + powerSpec2 * 3;

            }else {
//                Log.e("get_HbRate", "powerSpec State: 4");
                B = ((tIndex2 - 1) * powerSpec0) + (tIndex2 * powerSpec1) + ((tIndex2 + 1) * powerSpec2);
                totalpowerSpec = powerSpec0 + powerSpec1 + powerSpec2;
            }
        }else{
//            Log.e("get_HbRate", "powerSpec State: 5");
            B = ((tIndex2 - 1) * powerSpec0) + (tIndex2 * powerSpec1*3) + ((tIndex2 + 1) * powerSpec2);
            totalpowerSpec = powerSpec0 + powerSpec1*3 + powerSpec2;
        }
        //Log.e("get_HbRate", "max_index: " + max_index[0]);
//        Log.e("get_HbRate", "B before: " + B);
        if(totalpowerSpec == 0)
            B = 0;
        else
            B = B / totalpowerSpec;

        Rate = B*FreqPerIdx;
//        Log.e("get_HbRate", "totalpowerSpec: " + totalpowerSpec);
//        Log.e("get_HbRate", "B after: " + B);

        // 如果上次與此次相差太大, 超過一定次數才取
//        if(Math.abs(Rate - LastHRValue) > 20) {
//            MainIdxCounter++;
//            if(MainIdxCounter > 10) {
//                MainIdxCounter = 0;
//            } else {
//                Rate = LastHRValue;
//            }
//        } else {
//            MainIdxCounter = 0;
//        }
//
//        LastHRValue = Rate;

        return Rate;
    }
    //===============================================================================================//
    //==  Function : fft
    //==  Description : Radix-2 fixed points FFT
    //==   Parameters:
    //==      *rx: the start pointer of input in time domain :
    //==      *rq: the start pointer of output in frequency domain
    //===============================================================================================//

    //public void fft(int num, double[] rx, double[] rq, int stopidx)
    public void fft(int num, double[] rx, double[] rq)
    {
        int i,j,i1,l1,l2;
        long n,k,i2,l,scale;
        //long n,i,i1,j,k,i2,l,l1,l2,scale;
        double c1,c2,u1,u2,tx,ty,t1,t2,z;
        double mean=0;

        //// Calculate the number of points  ////
        switch(num)
        {
            default:
            case 6:
                n = 64;
                break;
            case 7:
                n = 128;
                break;
            case 8:
                n = 256;
                break;
            case 9:
                n = 512;
                break;
            case 10:
                n = 1024;
                break;
            case 11:
                n = 2048;
                break;
            case 12:
                n = 4096;
                break;
        }

        //// Reduce DC value ////
        for(i=0;i<n;i++) mean += rx[i];
        mean /= n;

        //// Prepare FFT data ////
        for(i=0;i<n;i++){
            rx[i] = (rx[i]-mean);
            rq[i] = 0;
        }

        //// Do the bit reversal ////
        i2 = n >> 1;
        j = 0;
        for (i=0;i<n-1;i++) {
            if (i < j)
            {
                tx = rx[i];
                ty = rq[i];
                rx[i] = rx[j];
                rq[i] = rq[j];
                rx[j] = tx;
                rq[j] = ty;
            }
            k = i2;
            while (k <= j) {
                j -= k;
                k /= 2;
            }
            j += k;
        }

        //// Compute the FFT  ////
        c1 = -1.0;
        c2 = 0.0;
        l2 = 1;


        for (l=0;l<num;l++)
        {
            l1 = l2;
            l2 *= 2;
            u1 = 1.0;
            u2 = 0;

            for (j=0;j<l1;j++) {
                for (i=j;i<n;i+=l2) {
                    i1 = i + l1;
                    t1 = (u1 * rx[i1] - u2 * rq[i1]);
                    t2 = (u1 * rq[i1] + u2 * rx[i1]);
                    rx[i1] = rx[i] - t1;
                    rq[i1] = rq[i] - t2;
                    rx[i] += t1;
                    rq[i] += t2;
                }
                z =  u1 * c1 - u2 * c2;
                u2 = u1 * c2 + u2 * c1;
                u1 = z;
            }
            c2 = sqrt((1.0 - c1) / 2.0);
            c2 = -c2;
            c1 = sqrt((1.0 + c1) / 2.0);
        }

        //// Scaling for forward transform  ////
        scale = n;
        for (i=0;i<n;i++) {
            rx[i] /= scale;
            rq[i] /= scale;
            rq[i] = rx[i]*rx[i] + rq[i]*rq[i];
        }


    }

    private final int sigLenMax = 64;
    private final double winRatio = 0.5;
    private final int winStep = 32;   //2017/3/23= 64
    //		private final int winStep = 32;
    private double[] buf_hr_rawdata = new double[sigLenMax];

    private long windowLen = fs / 2, windowMean = 0;	//windowLen要依照心跳更改
    private long rriNum = 0;
    private long retValue = 0;
    private long sigCounter=0;
    private double maxV_f = 0.0, minV_f = 0.0, AdjI = 0.0;

    private double wrongconter = 0; //如果沒收到會慢慢遞減

    public double FindPeakDist (double signal_in)
    {
        double HrRate = 0;
        //Shift signal data
        int i;
        int maxI = 0;
        double maxV = -99999.9;
        for (i = (sigLenMax - 1); i > 0; --i)
            buf_hr_rawdata[i] = buf_hr_rawdata[i - 1];

        buf_hr_rawdata[0] = signal_in;

        //find peak

        for (i = 0; i < windowLen; i++)
        {

            if (buf_hr_rawdata[i] > maxV)
            {
                maxV = buf_hr_rawdata[i];
                maxI = i;
            }
        }

        if (maxI == (long)windowLen / 2)
        {

            retValue = (int)((float)sigCounter / fs * 1000);

            windowMean = 0;
            sigCounter = 0;

        } else {
            sigCounter++;
            wrongconter++;
        }

        if (retValue != 0) {
            HrRate = (double) ((60000 / retValue) - wrongconter / 160);
        } else {
            HrRate = 0;
        }

        return HrRate;
    }
//    public double Signal2RRI_hr (double signal_in)
//    {
//
//        double maxV = -99999.9, minV = 99999.9, HrRate = 0, AdjI_l = 0.0;
//        int maxI = 0, minI = 0, retValueTemp = 0, StartAdjI = 0, StopAdjI = 0;
//        boolean isPeak = false, lowPeak = false;
//        int i;
//
//        //Shift signal data
//        for (i = (sigLenMax - 1); i > 0; --i)
//            buf_hr_rawdata[i] = buf_hr_rawdata[i - 1];
//
//        //for (i = 0; i>0; i++)
//        buf_hr_rawdata[0] = signal_in;
//
//        //find peak
//
//        for (i = 0; i < windowLen; i++)
//        {
//
//            if (buf_hr_rawdata[i] > maxV)
//            {
//                maxV = buf_hr_rawdata[i];
//                maxI = i;
//            }
//            if (buf_hr_rawdata[i] < minV)
//            {
//                minV = buf_hr_rawdata[i];
//                minI = i;
//            }
//        }
//
//        if (maxI == (long)windowLen / 2)
//        {
//            isPeak = true;
//            maxV_f = maxV;
//
//            System.out.println("Signal2RRI: maxV_f = " + maxV_f);
//        }
//        if (minI == (long)windowLen / 2)
//        {
//            isPeak = false;;
//            minV_f = minV;
//
//            System.out.println("Signal2RRI: minV_f = " + minV_f);
//        }
//
//
//        if (maxV_f - minV_f >= 500 && isPeak)
//        {
//            //	Log.e("Test", "RRI:" + String.valueOf(minV_f) + ", "  + String.valueOf(maxV_f));
//            //AdjI = (double)((maxI - (StopAdjI + StartAdjI) / 2) / 2);
//            StartAdjI = StopAdjI = 0;
//            maxV -= 32;
//            for (i = maxI; i>0; --i)
//                if (buf_hr_rawdata[i] > maxV)
//                    StartAdjI = i;
//            for (i = maxI; i<windowLen; ++i)
//                if (buf_hr_rawdata[i] > maxV)
//                    StopAdjI = i;
//            AdjI = (double)((maxI - (StopAdjI + StartAdjI) / 2) / 2);
//
//            retValueTemp = (int)((sigCounter - AdjI) / fs * 1000);
//
//
//            if (14000 > retValueTemp && retValueTemp > 909)
//            //  if (14000 > retValueTemp && retValueTemp > 100)
//            {
//                retValue = retValueTemp;
//
//                //Check and modify window length
//                windowMean += (int)(sigCounter);
//                sigCounter = 0;
//                ++rriNum;
//                if (rriNum == 5)
//                {
//                    windowMean = (long)(windowMean / rriNum * winRatio);
//                    if (windowMean > windowLen + winStep || windowMean < windowLen - winStep)
//                    {
//                        windowLen = windowMean;
//                        if (windowLen > sigLenMax)
//                            windowLen = sigLenMax;
//                    }
//                    rriNum = 0;
//                    windowMean = 0;
//                }
//                maxV_f = 0;
//                minV_f = 0;
//                wrongconter = 0;
//            }
//            else
//            {
//                rriNum = 0;
//                windowMean = 0;
//                sigCounter = 0;
//            }
//
//        }
//        //else if(isPeak)
//
//        else
//        {
//            ++sigCounter;
//            wrongconter++;
//        }
//
//
//
//        if (retValue != 0)
//        {
//
//            HrRate = (double)((60000 / retValue) - wrongconter/160);
//
//
//            if(HrRate <= 0) 	HrRate = 0;
//            //	wrongconter = 0;
//        }
//        else
//            HrRate = 0;
////        System.out.println("Signal2RRI: retValue = " + retValue + ", wrongconter = " + wrongconter);
//        return HrRate;
//    }


}
//===========================================================================//
