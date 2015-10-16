/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService implements GoogleApiClient.ConnectionCallbacks,
      DataApi.DataListener,
      GoogleApiClient.OnConnectionFailedListener {

    private static final String LOG_TAG = "SunshineWatchFace";

    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    /**
     * DataItem keys
     */
    private static final String PARAM_WEATHERID = "weather-id";
    private static final String PARAM_TEMP_LOW = "weather-low";
    private static final String PARAM_TEMP_HIGH = "weather-high";

    private static final String PATH = "/sunshine-weather-data";

    GoogleApiClient mGoogleApiClient;

    // Weather data
    private Bitmap mWeatherIcon;
    private Bitmap mWeatherIconAmbient;
    private static final char DEGREES = (char) 0x00B0;
    private String mWeatherHigh = "?" + DEGREES;
    private String mWeatherLow = "?" + DEGREES;
    private int mWeatherId;


    @Override
    public Engine onCreateEngine() {
        mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
              .addConnectionCallbacks(this)
              .addOnConnectionFailedListener(this)
              .addApi(Wearable.API)
              .build();
        mGoogleApiClient.connect();
        return new Engine();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        Log.i(LOG_TAG, "Sunshine Watch connected");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(LOG_TAG, "Sunshine Watch suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(LOG_TAG, "Sunshine Watch connection failed");
    }


    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        Log.i(LOG_TAG, "Sunshine Watch data events");

        for (DataEvent event : dataEventBuffer) {

            if (event.getType() == DataEvent.TYPE_CHANGED) {
                DataItem item = event.getDataItem();
                if (item.getUri().getPath().compareTo(PATH) == 0) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    mWeatherId = dataMap.getInt(PARAM_WEATHERID);
                    mWeatherIcon = BitmapFactory.decodeResource(
                          getResources(),
                          getIconResourceForWeatherCondition(mWeatherId));
                    mWeatherIconAmbient = toGrayscale(mWeatherIcon);
                    mWeatherHigh = dataMap.getString(PARAM_TEMP_HIGH);
                    mWeatherLow = dataMap.getString(PARAM_TEMP_LOW);
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
                invalidate();
            }
        };

        boolean mRegisteredTimeZoneReceiver = false;

        // Paint objects
        Paint mBackgroundPaint;
        Paint mTimePaint;
        Paint mMinPaint;
        Paint mSecPaint;
        Paint mHiTempPaint;
        Paint mLoTempPaint;
        Paint mDatePaint;

        // Colors
        int mBackgroundColor;
        int mBackgroundColorAmbient;
        int mTemperatureColor;

        boolean mAmbient;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        float mTimeYOffset;
        float mTimeXOffset;
        float mMinXOffset;
        float mDateYOffset;

        // Date and time
        Calendar mCalendar;
        Date mDate;
        SimpleDateFormat mDateFormat;
        String mDateFormatString = "EEE, d MMM";

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                  .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                  .setStatusBarGravity(Gravity.LEFT | Gravity.TOP)
                  .setHotwordIndicatorGravity(Gravity.CENTER)
                  .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                  .setShowSystemUiTime(false)
                  .build());

            Resources resources = SunshineWatchFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundColor = resources.getColor(R.color.digital_background);
            mBackgroundPaint.setColor(mBackgroundColor);
            mBackgroundColorAmbient = resources.getColor(R.color.digital_background_ambient);

            mTemperatureColor = resources.getColor(R.color.digital_temp_text);

            mTimePaint = createTextPaint(resources.getColor(R.color.digital_text));
            mMinPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mSecPaint = createTextPaint(resources.getColor(R.color.digital_sec_text));
            mHiTempPaint = createTextPaint(mTemperatureColor);
            mLoTempPaint = createTextPaint(mTemperatureColor);
            mDatePaint = createTextPaint(resources.getColor(R.color.digital_text));

            mLoTempPaint.setTextAlign(Paint.Align.RIGHT);
            mHiTempPaint.setTextAlign(Paint.Align.LEFT);
            mDatePaint.setTextAlign(Paint.Align.CENTER);

            mCalendar = Calendar.getInstance();
            mDate = new Date();

            // init weather icon
            mWeatherIcon = BitmapFactory.decodeResource(resources,  getIconResourceForWeatherCondition(mWeatherId));
            mWeatherIconAmbient = toGrayscale(mWeatherIcon);

            initFormats();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            Log.d(LOG_TAG, "onVisibilityChanged: " + visible);

            if (visible) {
                mGoogleApiClient.connect();
                Log.d(LOG_TAG, "mGoogleApiClient: " + mGoogleApiClient.isConnecting());

                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, SunshineWatchFace.this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void initFormats() {
            mDateFormat = new SimpleDateFormat(mDateFormatString, Locale.getDefault());
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();

            mTimeYOffset = resources.getDimension(isRound ? R.dimen.digital_time_y_offset_round : R.dimen.digital_time_y_offset);
            mDateYOffset = resources.getDimension(isRound ? R.dimen.digital_date_y_offset_round : R.dimen.digital_date_y_offset);

            float timeTextSize = resources.getDimension(isRound ? R.dimen.digital_time_text_size_round : R.dimen.digital_time_text_size);
            float minTextSize = resources.getDimension(isRound ? R.dimen.digital_min_text_size_round : R.dimen.digital_min_text_size);
            float secTextSize = resources.getDimension(isRound ? R.dimen.digital_min_text_size_round : R.dimen.digital_min_text_size);
            float tempTextSize = resources.getDimension(isRound ? R.dimen.digital_temp_text_size_round : R.dimen.digital_temp_text_size);
            float dateTextSize = resources.getDimension(isRound ? R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size);

            mTimePaint.setTextSize(timeTextSize);
            mMinPaint.setTextSize(minTextSize);
            mSecPaint.setTextSize(secTextSize);
            mHiTempPaint.setTextSize(tempTextSize);
            mLoTempPaint.setTextSize(tempTextSize);
            mDatePaint.setTextSize(dateTextSize);

            // calculate offsets to center time
            mTimeXOffset = (mTimePaint.measureText("12") + mMinPaint.measureText(String.format(" %02d", 55))) / 2;
            mMinXOffset = mTimePaint.measureText("12");
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimePaint.setAntiAlias(!inAmbientMode);
                    mHiTempPaint.setAntiAlias(!inAmbientMode);
                    mLoTempPaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                }

                if (inAmbientMode) {
                    mBackgroundPaint.setColor(mBackgroundColorAmbient);
                    mHiTempPaint.setColor(Color.WHITE);
                    mLoTempPaint.setColor(Color.WHITE);
                } else {
                    mBackgroundPaint.setColor(mBackgroundColor);
                    mHiTempPaint.setColor(mTemperatureColor);
                    mLoTempPaint.setColor(mTemperatureColor);
                }

                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            int centerX = bounds.width() >> 1;
            int centerY = bounds.height() >> 1;

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);

            // Format time:
            int hour = mCalendar.get(Calendar.HOUR);
            String hours = hour == 0 ? "12" : String.format("%02d", mCalendar.get(Calendar.HOUR_OF_DAY));
            String minutes = String.format(" %02d", mCalendar.get(Calendar.MINUTE));
            String seconds = String.format(" %02d", mCalendar.get(Calendar.SECOND));

            // Draw background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            // Time: display seconds in interactive mode
            canvas.drawText(hours, centerX - mTimeXOffset, mTimeYOffset, mTimePaint);
            canvas.drawText(minutes, centerX - mTimeXOffset + mMinXOffset, mTimeYOffset - 32, mMinPaint);
            if (!mAmbient) {
                canvas.drawText(seconds, centerX - mTimeXOffset + mMinXOffset, mTimeYOffset, mSecPaint);
            }

            // Weather Icon: center in face
            Bitmap weatherIcon = mAmbient ? mWeatherIconAmbient : mWeatherIcon;
            int cx = (bounds.width() - weatherIcon.getWidth()) >> 1;
            int cy = (bounds.height() - weatherIcon.getHeight()) >> 1;
            canvas.drawBitmap(weatherIcon, cx, cy, new Paint());

            // Weather Low and High: left and right of icon
            canvas.drawText(mWeatherLow, centerX - 60, centerY + 15, mLoTempPaint);
            canvas.drawText(mWeatherHigh, centerX + 60, centerY + 15, mHiTempPaint);

            // Date: bottom & centered
            canvas.drawText(mDateFormat.format(mDate).toUpperCase(), centerX, centerY + mDateYOffset, mDatePaint);
            canvas.drawText(mCalendar.get(Calendar.YEAR)+"", centerX, centerY + mDateYOffset + 35, mDatePaint);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                      - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    public static int getIconResourceForWeatherCondition(int weatherId) {
        // Based on weather code data found at:
        // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
        if (weatherId >= 200 && weatherId <= 232) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return R.drawable.ic_light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return R.drawable.ic_rain;
        } else if (weatherId == 511) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return R.drawable.ic_rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            return R.drawable.ic_fog;
        } else if (weatherId == 761 || weatherId == 781) {
            return R.drawable.ic_storm;
        } else if (weatherId == 800) {
            return R.drawable.ic_clear;
        } else if (weatherId == 801) {
            return R.drawable.ic_light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return R.drawable.ic_cloudy;
        } else {
            return R.mipmap.ic_launcher;
        }
        //return -1;
    }

    public Bitmap toGrayscale(Bitmap bmpOriginal)
    {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);

        Paint paint = new Paint();
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
        paint.setColorFilter(filter);

        c.drawBitmap(bmpOriginal, 0, 0, paint);

        return bmpGrayscale;
    }
}
