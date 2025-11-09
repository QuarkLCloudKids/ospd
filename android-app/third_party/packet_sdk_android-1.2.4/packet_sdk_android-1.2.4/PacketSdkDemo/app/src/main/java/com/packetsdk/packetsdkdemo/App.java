package com.packetsdk.packetsdkdemo;

import android.app.Application;

import com.packet.sdk.PacketSdk;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        //init sdk with app key, replace with your own key
        PacketSdk.initialize(this, "Wusylh6m4LfEQSsz");
        //enable logging
        PacketSdk.setEnableLogging(true);
    }
}
