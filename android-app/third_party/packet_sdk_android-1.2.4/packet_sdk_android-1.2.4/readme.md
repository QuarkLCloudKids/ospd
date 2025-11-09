Integrated document address：https://docs.packetsdk.com/android/editor

# Installation
Copy packet_sdk_v***.aar to the libs directory of your project.
my-application
├── app
│   ├── libs
│   │   ├── packet_sdk_v***.aar

Set the libs path in the gradle file.

dependencies {
    implementation(files("libs/packet_sdk_v***.aar"))
}

...
# SDK setup
SDK requires internet connectivity to work. To allow your app to use internet connection add following permission to AndroidManifest.xml file.

<uses-permission android:name="android.permission.INTERNET" \>

Configure SDK settings right after initializing the SDK, but before calling the PacketSdk.start() function.

import android.app.Application;
import com.packet.sdk.PacketSdk;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        //init sdk with your key
        PacketSdk.initialize(this, "app_key");
        //enable logging
        PacketSdk.setEnableLogging(true);
    }
}

Declare your Application in manifest and set usesCleartextTraffic="true"

<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.debby.tunnel_sdk">

    <application
        android:name=".App"
        android:allowBackup="true"
        android:icon="@mipmap/sun"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/sun"
        android:supportsRtl="true"
        android:theme="@style/Theme.Tunnel_sdk"
        android:usesCleartextTraffic="true">
     
    </application>

# Start SDK
import android.app.Activity;
import com.packet.sdk.PacketSdk;

public class MainActivity extends Activity {

    @Override
    void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //start sdk
        PacketSdk.start();
    }
}

# Logging
Call PacketSdk.setEnableLogging(true) in order to enable logging to logcat.

# Status monitor
import android.app.Activity;
import com.packet.sdk.PacketSdk;

public class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PacketSdk.setStatusListener((code, msg) -> { 
            //status code and msg
        });
        // ...
    }
}
# Stop SDK
call PacketSdk.stop() in order to stop SDK.