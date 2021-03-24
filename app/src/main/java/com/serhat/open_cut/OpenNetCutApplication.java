package com.serhat.open_cut;

import android.app.Application;

public class OpenNetCutApplication extends Application {
    public void onCreate(){
        super.onCreate();
        try {
            System.init(this);
        }catch (Exception e){
            System.errorLogging(e);

        }
    }
}