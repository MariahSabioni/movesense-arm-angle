package com.example.movesense_app_minet_sabioni.Computations;

import android.content.Context;
import android.os.Environment;

import com.example.movesense_app_minet_sabioni.Results.ResultsMethod1;
import com.example.movesense_app_minet_sabioni.Results.ResultsMethod2;
import com.opencsv.CSVWriter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CSVWriterForResults {

    private List<String[]> resultsStringList = new ArrayList<String[]>();

    public CSVWriterForResults() {
        this.resultsStringList.add(new String[]{"Time", "Elevation Angle"});
    }

    public void convertResults2String_1(ResultsMethod1 res){
        double angle = res.getElevation();
        String angleString = String.valueOf(angle);
        double time = res.getTime();
        String timeString = String.valueOf(time);
        this.resultsStringList.add(new String[]{timeString,angleString});
    }

    public void convertResults2String_2(ResultsMethod2 res){
        double angle = res.getCompPitch();
        String angleString = String.valueOf(angle);
        double time = res.getTime();
        String timeString = String.valueOf(time);
        this.resultsStringList.add(new String[]{timeString,angleString});
    }

    public void writeToCSV(Context context, String fileName) throws IOException {
        String csv = (Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/"+fileName+".csv");
        CSVWriter writer = new CSVWriter(new FileWriter(csv, true));

        writer.writeAll(this.resultsStringList);
        writer.close();

    }
}
