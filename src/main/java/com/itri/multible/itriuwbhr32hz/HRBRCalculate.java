package com.itri.multible.itriuwbhr32hz;

import java.util.Arrays;

public class HRBRCalculate {
	public double[] m_HRdoubleFFT = new double[256];
	private int show_fft = 256;
	public int FFT_MaxIndex;
	public double FFT_main, FFT_left, FFT_right;
	// ************* Define Final Parameters *****************************//
	// *********** common.h define ***********//
	// private final int size_rawbank = 2048; //fs64 * 32sec
	private final int size_rawbank = 1024;

	// ************ this class defiine *******//
	// public final int fs = 64;
	private final int HBinitTime = 20; // for init HB period ,seconds
	private final int HB_StoreRaw_size = HBinitTime;

	// ************* Define variable parameters ***************************//
	private int rawMax = 0, rawMin = 60000, HB_StoreRaw_idx = 0, RawDataOrig = 0;
	private int secCount = 0, secCount_BR = 0, FirstInit_HR = 1, FirstInit_BR = 1;
	// private int stablecount=0;
	private int rawbank_idx = -1, motion = 0;

	private double[] rawbank_Br = new double[size_rawbank];
	private double[] rawbank = new double[size_rawbank];

	private double HB = 80, BR = 10, lastBR = 10, oldBR = 10, oldHB = 80, lastHB = 80;//

	private int surh = 45, surl = 7;// surh = 51~13

	private boolean minus1s = false;
	// **********************************************************************************************//
	public Ekg m_Ekg = new Ekg();
	private BreatheFilter m_BreatheFilter = new BreatheFilter();
	private Signal2RRI m_Signal2RRI_br = new Signal2RRI();
	private Signal2RRI m_Signal2RRI_hr = new Signal2RRI();
	// private boolean isStable = false;
	public int type = 0;

	// RRI
	private double HRValue_RRI = -1;

	public double getHRValue_RRI() {
		if (HRValue_RRI == -1) {
			HRValue_RRI = oldHB;
		}
		return HRValue_RRI;
	}

	// ===============================================================================================//
	// == Function : HRBRCallIn( int HBrawdata, int InitBit, double BR_rate[],
	// double HB_rate[],
	// == double HBdoubleRawOut[], double BRdoubleRawOut[])
	// == Parameters:
	// == HBrawdata : Input High Byte and Low Byte
	// == InitBit : when InitBit = 0 is initial value,InitBit = 1 is running value
	// == BR_rate[] : Output Breathe Rate
	// == HB_rate[] : Output Heart Rate
	// == HBdoubleRawOut[] : Output Heart Raw Data
	// == BRdoubleRawOut[] : Output Breathe Raw Data
	// ===============================================================================================//
	public void ArrayMinus1S() {
		if (!minus1s) {
			rawbank_idx -= 32; // 32
			if (rawbank_idx < 0)
				rawbank_idx += size_rawbank;
			minus1s = true;
		}
	}

	public int getType() {
		return this.type;
	}

	public void setType(int type) {
		this.type = type;
		m_Ekg.setType(type);
		m_BreatheFilter.setType(type);
		EKG_init_value();
		BR_init_value();
	}

	public void setBRThreshold(int threshold) {
		m_Signal2RRI_br.setBRThreshold(threshold);
	}

	public void HRCallIn(int HBrawdata, boolean[] InitBit, double[] HB_rate, final int BR_rate,
			double[] HBdoubleRawOut) {
		minus1s = false;
		double[] HBdoubleRaw = new double[1];
		int[] Ismotion = new int[1];

		RawDataOrig = HBrawdata;
		HBdoubleRaw[0] = HBrawdata;

		if ((FirstInit_HR == 1) || (InitBit[0])) {
			EKG_Init();
			InitBit[0] = false;
		}

		// type: 0: human, 1: cat, 2: rabbit, 3: dog
		if (type == 0) {
			surl = 5;
			surh = 35;
		} else if (type == 1) {
			surl = 5;
			surh = 50;
		} else if (type == 2) {
			surl = 15;
			surh = 50;
		} else if (type == 3) {
			surl = 5;
			surh = 40;
		} else {
			surl = 5;
			surh = 50;
		}

		// 20210630 呼吸推算心跳
		// BR_surl = (int) (this.BR_rate * HRBR_low_ratio / m_Ekg.FreqPerIdx);
		// BR_surh = (int) (this.BR_rate * HRBR_high_ratio / m_Ekg.FreqPerIdx);
		//// System.out.println("BR_surl="+BR_surl);
		// if(BR_surl > surl) {
		// surl = BR_surl;
		// }
		// if(BR_surh < surh) {
		// surh = BR_surh;
		// }
		HB_rate[0] = EKG_process(HBdoubleRaw, Ismotion, surl, surh);

		HBdoubleRawOut[0] = rawbank[rawbank_idx];
		// return isStable;

	}

	public void BRCallIn(int BRrawdata, boolean[] InitBit, double[] BR_rate, double[] BRdoubleRawOut) {
		double[] BRdoubleRaw = new double[1];
		int[] BR_rate_temp = new int[1];

		RawDataOrig = BRrawdata;

		BRdoubleRaw[0] = BRrawdata;

		if ((FirstInit_BR == 1) || (InitBit[0])) {
			BR_Init();
			InitBit[0] = false;
		}
		BR_process(BRdoubleRaw, BR_rate_temp);

		BR_rate[0] = BR_rate_temp[0];
		BRdoubleRawOut[0] = rawbank_Br[rawbank_idx];

	}

	// ===============================================================================================//
	// == Function : EKG_process(double HBdoubleRaw, double BRdoubleRaw, unsigned
	// short int InitBit,
	// short int *BR_rate, short int *is_motion )
	// == Parameters: no
	// == HBdoubleRaw[] : Input Hreat raw data
	// == BRdoubleRaw[] : Input Breathe raw data
	// == InitBit : Input initial flag
	// == BR_rate[] : Input Breathe rate
	// == is_motion[] : Input motion value
	// ==
	// ===============================================================================================//

	public void BR_process(double[] BRdoubleRaw, int[] BR_rate) {
		//// function Init ////

		rawbank_idx++;

		if (rawbank_idx >= size_rawbank)
			rawbank_idx = 0; // define size_rawbank =2048;

		rawbank_Br[rawbank_idx] = m_BreatheFilter.chebyFilterBr(BRdoubleRaw[0]);
		// BR = m_Signal2RRI.Signal2RRI_itri(rawbank_Br[rawbank_idx]); // store filtered
		// data for breath
		// BR_itri = BR;
		BR = m_Signal2RRI_br.Signal2RRI_ITRI(rawbank_Br[rawbank_idx]);

		//// check the marginal value within 1 sec ////
		if (rawMin > RawDataOrig)
			rawMin = RawDataOrig;
		else if (rawMax < RawDataOrig)
			rawMax = RawDataOrig;

		//// count HB every second ////

		if (++secCount_BR >= 32) {
			secCount_BR = 0;

			if (BR != 0) {
				lastBR = (lastBR + BR + 1) / 2;
			} else {
				lastBR = BR;
			}
			if (lastBR > (oldBR + 3))
				lastBR = oldBR + 3;
			else if (lastBR < (oldBR - 3))
				lastBR = oldBR - 3;

			oldBR = lastBR;

		}

		BR_rate[0] = (int) oldBR;

	}

	// ===============================================================================================//
	// == Function : EKG_process(double HBdoubleRaw, double BRdoubleRaw, unsigned
	// short int InitBit,
	// short int *BR_rate, short int *is_motion )
	// == Parameters: no
	// == HBdoubleRaw[] : Input Hreat raw data
	// == BRdoubleRaw[] : Input Breathe raw data
	// == InitBit : Input initial flag
	// == BR_rate[] : Input Breathe rate
	// == is_motion[] : Input motion value
	// ==
	// ===============================================================================================//
	public double EKG_process(double[] HBdoubleRaw, int[] is_motion, int search_l, int search_h) {
		int i;
		//// function Init ////

		rawbank_idx++;

		if (rawbank_idx >= size_rawbank)
			rawbank_idx = 0; // define size_rawbank =2048;
		// inital value rawbank_idx= -1;
		//// chebyshev inequality ////
		rawbank[rawbank_idx] = m_Ekg.chebyFilterEkg(HBdoubleRaw[0]);
		// HRValue_RRI = m_Ekg.FindPeakDist(rawbank[rawbank_idx]);

		// isStable = m_Signal2RRI_hr.FindHRStatus(rawbank[rawbank_idx]);
		// if (!isStable) {
		// stablecount = 8;
		// }
		// Log.e("George", "Rawdata before filter: " + String.valueOf(HBdoubleRaw[0]));
		//// count HB every second ////
		if (++secCount >= 32) {
			secCount = 0;
			int[] max_index = new int[1];
			double[] main = new double[1];
			double[] left = new double[1];
			double[] right = new double[1];
			// HbProcess(rawbank_idx); // process HB by FFT

			// if(stablecount <=0) {
			HB = m_Ekg.HbProcess(rawbank_idx, rawbank, search_l, search_h, max_index, main, left, right); // process HB
																											// by FFT
			// }else {
			// stablecount--;
			//// System.out.println("stablecount="+stablecount);
			// HB = m_Ekg.HbProcess(rawbank_idx, rawbank, search_l + 1, search_h, max_index,
			// main, left, right); // process HB by FFT
			// }
			FFT_MaxIndex = max_index[0];
			FFT_main = main[0];
			FFT_left = left[0];
			FFT_right = right[0];

			for (i = 0; i < show_fft; i++)
				m_HRdoubleFFT = Arrays.copyOf(m_Ekg.m_HRdoubleFFT, m_Ekg.m_HRdoubleFFT.length);

			HB_StoreRaw_idx++;
			if (HB_StoreRaw_idx >= HB_StoreRaw_size)
				HB_StoreRaw_idx = 0;

			// if(stablecount>0) {
			// lastHB = (lastHB * 6 + HB) /7;
			//
			// if (Math.abs(lastHB - oldHB) < 1)
			// lastHB = HB;
			// else if (lastHB > (oldHB + 1))
			// //lastHB = oldHB + 1;
			// lastHB = oldHB;
			// else if (lastHB < (oldHB - 1))
			// //lastHB = oldHB - 1;
			// lastHB = oldHB ;
			// }else

			if (m_Ekg.IsPeakClear == 1) {
				lastHB = ((lastHB * 4) + HB) / 5;
				if (Math.abs(lastHB - HB) < 2)
					lastHB = HB;
				if (lastHB > (oldHB + 3))
					lastHB = oldHB + 3;
				else if (lastHB < (oldHB - 3))
					lastHB = oldHB - 3;
			} else {
				lastHB = ((lastHB * 5) + HB) / 6;
				if (Math.abs(lastHB - HB) < 2)
					lastHB = HB;
				if (lastHB > (oldHB + 2))
					lastHB = oldHB + 2;
				else if (lastHB < (oldHB - 2))
					lastHB = oldHB - 2;
			}

			oldHB = lastHB;
			//

		}

		return oldHB;

	}

	// ===============================================================================================//
	// == Function : EKG_process(double HBdoubleRaw, double BRdoubleRaw, unsigned
	// short int InitBit,
	// short int *BR_rate, short int *is_motion )
	// == Parameters: no
	// == HBdoubleRaw[] : Input Hreat raw data
	// == BRdoubleRaw[] : Input Breathe raw data
	// == InitBit : Input initial flag
	// == BR_rate[] : Input Breathe rate
	// == is_motion[] : Input motion value
	// ==
	// ===============================================================================================//

	public void EKG_Init() {
		int i;
		m_Ekg.InitEkgFilter();
		m_BreatheFilter.InitBrFilter();
		for (i = 0; i < size_rawbank; i++) {
			rawbank[i] = 0;
			rawbank_Br[i] = 0;
		}
		rawbank_idx = -1;
		motion = 0;
		FirstInit_HR = 0;
		EKG_init_value();

		// surh = 51;
		// surl =13;
	}

	public void EKG_init_value() {
		if (type == 0) {
			HB = 80;
			oldHB = 80;
			lastHB = 80;
		} else if (type == 1) {
			HB = 100;
			oldHB = 100;
			lastHB = 100;
		} else if (type == 2) {
			HB = 150;
			oldHB = 150;
			lastHB = 150;
		} else if (type == 3) {
			HB = 90;
			oldHB = 90;
			lastHB = 90;
		}
	}

	public void BR_Init() {
		int i;
		m_BreatheFilter.InitBrFilter();
		for (i = 0; i < size_rawbank; i++) {
			rawbank_Br[i] = 0;
		}
		rawbank_idx = -1;
		motion = 0;
		FirstInit_BR = 0;

		BR_init_value();
	}

	public void BR_init_value() {
		if (type == 0) {
			BR = 20;
			lastBR = 20;
			oldBR = 20;
		} else if (type == 1) {
			BR = 20;
			lastBR = 20;
			oldBR = 20;
		} else if (type == 2) {
			BR = 30;
			lastBR = 30;
			oldBR = 30;
		} else if (type == 3) {
			BR = 20;
			lastBR = 20;
			oldBR = 20;
		}
	}
}