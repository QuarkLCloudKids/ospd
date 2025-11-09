package com.packetsdk.packetsdkdemo;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.packet.sdk.PacketSdk;
import android.util.Log;


public class MainActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i("PacketSDKDemo", "Setting status listener and starting SDK...");
        PacketSdk.setStatusListener((code, msg) -> {
            Log.i("PacketSDKDemo", "PacketSdk status code=" + code + ", msg=" + msg);
        });
        //start sdk
        PacketSdk.start();
    }
}