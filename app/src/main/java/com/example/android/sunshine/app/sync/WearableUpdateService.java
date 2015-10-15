package com.example.android.sunshine.app.sync;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.Date;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * helper methods.
 */
public class WearableUpdateService extends IntentService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static final String LOG_TAG = WearableUpdateService.class.getSimpleName();

    // IntentService can perform
    private static final String ACTION_SEND_WEATHER_UPDATE = "com.example.android.sunshine.app.sync.action.SEND_WEATHER_UPDATE";

    // Params
    private static final String EXTRA_PARAM_WEATHERID = "com.example.android.sunshine.app.sync.extra.WEATHERID";
    private static final String EXTRA_PARAM_TEMP_LOW = "com.example.android.sunshine.app.sync.extra.TEMP_LOW";
    private static final String EXTRA_PARAM_TEMP_HIGH = "com.example.android.sunshine.app.sync.extra.TEMP_HIGH";

    private static final String PATH = "/weather";

    private GoogleApiClient mGoogleApiClient;
    private PutDataMapRequest mRequestMap;

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionSendWeatherUpdate(Context context, int weatherId, double tempLow, double tempHigh) {
        Log.d(LOG_TAG, "Starting service");

        // format temperatures to user preference
        String minTemp = Utility.formatTemperature(context, tempLow);
        String maxTemp = Utility.formatTemperature(context, tempHigh);

        Intent intent = new Intent(context, WearableUpdateService.class);
        intent.setAction(ACTION_SEND_WEATHER_UPDATE);
        intent.putExtra(EXTRA_PARAM_WEATHERID, weatherId);
        intent.putExtra(EXTRA_PARAM_TEMP_LOW, minTemp);
        intent.putExtra(EXTRA_PARAM_TEMP_HIGH, maxTemp);
        context.startService(intent);
    }

    public WearableUpdateService() {
        super("WearableUpdateService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(LOG_TAG, "handle intent");

        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_SEND_WEATHER_UPDATE.equals(action)) {
                final int weatherId = intent.getIntExtra(EXTRA_PARAM_WEATHERID, 0);
                final String tempLow = intent.getStringExtra(EXTRA_PARAM_TEMP_LOW);
                final String tempHigh = intent.getStringExtra(EXTRA_PARAM_TEMP_HIGH);

                mRequestMap = PutDataMapRequest.create(PATH);
                mRequestMap.getDataMap().putInt(EXTRA_PARAM_WEATHERID, weatherId);
                mRequestMap.getDataMap().putString(EXTRA_PARAM_TEMP_HIGH, tempHigh);
                mRequestMap.getDataMap().putString(EXTRA_PARAM_TEMP_LOW, tempLow);
                mRequestMap.getDataMap().putLong("time", new Date().getTime()); // timestamp

                mGoogleApiClient = new GoogleApiClient.Builder(WearableUpdateService.this)
                      .addConnectionCallbacks(this)
                      .addOnConnectionFailedListener(this)
                      .addApi(Wearable.API)
                      .build();

                mGoogleApiClient.connect();

                updateWearable();
            }
        }
    }

    /**
     * Handle action in the provided background thread with the provided parameters.
     */
    private void updateWearable() {
        Log.d(LOG_TAG, "updateWearable");

        // Start weather data update task asynchronously
//        SendWeatherUpdateTask task = new SendWeatherUpdateTask();
//        task.execute();

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(LOG_TAG, "Put weather DataItem");
                PendingResult<DataApi.DataItemResult> result =
                      Wearable.DataApi.putDataItem(mGoogleApiClient, mRequestMap.asPutDataRequest());
                Log.d(LOG_TAG, "result= " + result);

            }
        });
        thread.start();
    }

    @Override
    public void onDestroy() {
        mGoogleApiClient.disconnect();
        super.onDestroy();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(LOG_TAG, "onConnected");

        updateWearable();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

//    private class SendWeatherUpdateTask extends AsyncTask {
//        @Override
//        protected Object doInBackground(Object[] params) {
//            Log.d(LOG_TAG, "doInBackground");
//            try {
//                Log.d(LOG_TAG, "mRequestMap: " + mRequestMap.toString());
//
//                Wearable.DataApi.putDataItem(mGoogleApiClient, mRequestMap.asPutDataRequest())
//                      .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
//
//                          public void onResult(DataApi.DataItemResult result) {
//                              // this callback represents local success (not whether received by wearable)
//                              Log.d(LOG_TAG, "onResult callback: " + result.getStatus());
//                              if (result.getStatus().isSuccess()) {
//                                  Log.d(LOG_TAG, "Put DataItem succeeded");
//                              }
//                          }
//                      });
//
//            } catch (Exception e) {
//                Log.d(LOG_TAG, "Task failed: " + e);
//            }
//
//            return null;
//        }
//    }
}
