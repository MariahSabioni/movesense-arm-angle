package com.example.movesense_app_minet_sabioni.Computations;

public class Filters {

    public static double emwaFilter(double alpha, double prevFilteredData, double rawData){
        return alpha*prevFilteredData+(1-alpha)*rawData;
    }

    public static double complimentaryFilter(double gx, double pitchAcc, double prevCompPitch, double dT, double a){
        return a*(prevCompPitch+dT*gx)+(1-a)*pitchAcc;
    }


}
