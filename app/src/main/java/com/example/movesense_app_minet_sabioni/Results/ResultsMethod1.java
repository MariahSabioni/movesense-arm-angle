package com.example.movesense_app_minet_sabioni.Results;

import com.example.movesense_app_minet_sabioni.RawData.AccData;

public class ResultsMethod1 {

    private AccData accData;
    private double Elevation;

    public ResultsMethod1(AccData accData){
        this.accData = accData;
        this.Elevation = computeElevation();
    }

    private double computeElevation() {
        double Acc_x = accData.getAcc_xFiltered();
        double Acc_y = accData.getAcc_yFiltered();
        double Acc_z = accData.getAcc_zFiltered();
        //method 1
        return Math.acos(-Acc_y/Math.sqrt(Math.pow(Acc_x,2)+Math.pow(Acc_y,2)+Math.pow(Acc_z,2)))*180/Math.PI;
    }

    public double getElevation() {
        return Elevation;
    }

    public double getTime(){
        return accData.getTime();
    }

}

