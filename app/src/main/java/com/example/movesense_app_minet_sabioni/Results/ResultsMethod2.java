package com.example.movesense_app_minet_sabioni.Results;

import com.example.movesense_app_minet_sabioni.Computations.Filters;
import com.example.movesense_app_minet_sabioni.RawData.AccData;

public class ResultsMethod2 {

    private AccData accData;
    private double DT, Beta;
    private double PitchAcc, CompPitch;

    public ResultsMethod2(AccData accData, double dT, double beta){
        this.accData = accData;
        this.PitchAcc = computePitchAcc();
        this.Beta = beta;
        this.DT = dT;
    }

    private double computePitchAcc(){
        double Acc_x = accData.getAcc_xFiltered();
        double Acc_y = accData.getAcc_yFiltered();
        double Acc_z = accData.getAcc_zFiltered();
        return 90+Math.atan(Acc_y/Math.sqrt(Math.pow(Acc_x,2)+Math.pow(Acc_z,2)))*180/Math.PI;//+90 to keep the same convention
    }

    public double getGyro_x(){
        return accData.getGyro_x();
    }

    public void setFilteredPitch(double filteredPitch){
        CompPitch = filteredPitch;
    }

    public void setNotFilteredPitch(){
        CompPitch = PitchAcc;
    }

    public double getPitchAcc() {
        return PitchAcc;
    }

    public double getCompPitch(){
        return CompPitch;
    }

    public double getBeta() {
        return Beta;
    }

    public double getDT() {
        return DT;
    }

    public double getTime(){
        return accData.getTime();
    }

    public static ResultsMethod2 filterPitch(ResultsMethod2 currentData, ResultsMethod2 previousData) {
        try {
            if (previousData != null) {
                //filter raw data
                double filteredPitch = Filters.complimentaryFilter(currentData.getGyro_x(),
                        currentData.getPitchAcc(),previousData.getCompPitch(),currentData.getDT(), currentData.getBeta());
                currentData.setFilteredPitch(filteredPitch);
                return currentData;
            }
            //no filtered data exist, save raw data instead
            currentData.setNotFilteredPitch();
            return currentData;
        } catch (NullPointerException e) {
            e.printStackTrace();
            currentData.setNotFilteredPitch();
            return currentData;
        }
    }

}
