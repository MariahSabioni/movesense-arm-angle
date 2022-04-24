package com.example.movesense_app_minet_sabioni.RawData;

import com.example.movesense_app_minet_sabioni.Computations.Filters;

public class AccData {

    private double Acc_x, Acc_y, Acc_z;
    private double PreviousAcc_x, PreviousAcc_y, PreviousAcc_z;
    private double Acc_xFiltered, Acc_yFiltered, Acc_zFiltered, Alpha;
    private long Time;
    private double Gyro_x, Gyro_y, Gyro_z;

    public AccData(double acc_x, double acc_y, double acc_z, long time, double alpha) {
        this.Acc_x = acc_x;
        this.Acc_y = acc_y;
        this.Acc_z = acc_z;
        this.Time = time;
        this.Alpha = alpha;
    }

    public double getAcc_xFiltered() {
        return Acc_xFiltered;
    }

    public double getAcc_yFiltered() {
        return Acc_yFiltered;
    }

    public double getAcc_zFiltered() {
        return Acc_zFiltered;
    }

    public double getGyro_x() {
        return Gyro_x;
    }

    public void setGyro_x(double gyro_x) {
        Gyro_x = gyro_x;
    }

    public void setGyro_y(double gyro_y) {
        Gyro_y = gyro_y;
    }

    public void setGyro_z(double gyro_z) {
        Gyro_z = gyro_z;
    }

    public double getAcc_x() {
        return Acc_x;
    }

    public double getAcc_y() {
        return Acc_y;
    }

    public double getAcc_z() {
        return Acc_z;
    }

    public long getTime() {
        return Time;
    }

    public double getAlpha() {
        return Alpha;
    }

    public void setAcc_x(double acc_x) {
        Acc_x = acc_x;
    }

    public void setAcc_y(double acc_y) {
        Acc_y = acc_y;
    }

    public void setAcc_z(double acc_z) {
        Acc_z = acc_z;
    }

    public void setAlpha(double alpha) {
        Alpha = alpha;
    }

    public void setFilteredAcc(double filteredAcc_x, double filteredAcc_y, double filteredAcc_z) {
        Acc_xFiltered = filteredAcc_x;
        Acc_yFiltered = filteredAcc_y;
        Acc_zFiltered = filteredAcc_z;
    }

    public void setNotFilteredAcc(){
        Acc_xFiltered = Acc_x;
        Acc_yFiltered = Acc_y;
        Acc_zFiltered = Acc_z;
    }

    public static AccData filterAcc(AccData currentData, AccData previousData) {
        try {
            if (previousData != null) {
                //filter raw data
                double filteredAcc_x = Filters.emwaFilter(currentData.getAlpha(), previousData.getAcc_xFiltered(), currentData.getAcc_x());
                double filteredAcc_y = Filters.emwaFilter(currentData.getAlpha(), previousData.getAcc_yFiltered(), currentData.getAcc_y());
                double filteredAcc_z = Filters.emwaFilter(currentData.getAlpha(), previousData.getAcc_zFiltered(), currentData.getAcc_z());
                currentData.setFilteredAcc(filteredAcc_x,filteredAcc_y,filteredAcc_z);
                return currentData;
            }
            //no filtered data exist, save raw data instead
            currentData.setNotFilteredAcc();
            return currentData;
        } catch (NullPointerException e) {
            e.printStackTrace();
            currentData.setNotFilteredAcc();
            return currentData;
        }
    }

}
