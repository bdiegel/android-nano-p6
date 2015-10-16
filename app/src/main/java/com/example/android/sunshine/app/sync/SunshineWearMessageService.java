package com.example.android.sunshine.app.sync;

import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class SunshineWearMessageService extends WearableListenerService {

    public final String LOG_TAG = SunshineWearMessageService.class.getSimpleName();

    private static final String WEATHER_REQUEST = "/sunshine/weather-request";

    public SunshineWearMessageService() {
    }

    @Override
    public void onMessageReceived( MessageEvent messageEvent )
    {
        super.onMessageReceived( messageEvent );

        Log.d(LOG_TAG, "Peer message: " + messageEvent.getPath());

        if ( messageEvent.getPath().equals( WEATHER_REQUEST ) )
        {
            SunshineSyncAdapter.syncImmediately(this);
        }
    }

}
