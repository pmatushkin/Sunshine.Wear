package com.example.android.sunshine.app.wearable;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

public class WearableIntentService extends IntentService
    implements GoogleApiClient.ConnectionCallbacks
{

    private static final String TAG = "WearableIntentService";

    private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
    };
    // these indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_SHORT_DESC = 1;
    private static final int INDEX_MAX_TEMP = 2;
    private static final int INDEX_MIN_TEMP = 3;

    private GoogleApiClient mGoogleApiClient;

    private static final String REQ_PATH = "/weather";
    private static final String KEY_WEATHER_ID = "com.example.key.weather_id";
    private static final String KEY_TEMP_MAX = "com.example.key.max_temp";
    private static final String KEY_TEMP_MIN = "com.example.key.min_temp";

    private int mWeatherId;
//    private int mWeatherArtResourceId;
    private double mMaxTemp;
    private double mMinTemp;

    public WearableIntentService() {
        super("WearableIntentService");
    }

    /**
     * This method is invoked on the worker thread with a request to process.
     * Only one Intent is processed at a time, but the processing happens on a
     * worker thread that runs independently from other application logic.
     * So, if this code takes a long time, it will hold up other requests to
     * the same IntentService, but it will not hold up anything else.
     * When all requests have been handled, the IntentService stops itself,
     * so you should not call {@link #stopSelf}.
     *
     * @param intent The value passed to {@link
     *               Context#startService(Intent)}.
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        // Get today's data from the ContentProvider
        String location = Utility.getPreferredLocation(this);
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                location, System.currentTimeMillis());
        Cursor data = getContentResolver().query(weatherForLocationUri, FORECAST_COLUMNS, null,
                null, WeatherContract.WeatherEntry.COLUMN_DATE + " ASC");
        if (data == null) {
            return;
        }
        if (!data.moveToFirst()) {
            data.close();
            return;
        }

        // Extract the weather data from the Cursor
        mWeatherId = data.getInt(INDEX_WEATHER_ID);
        mMaxTemp = data.getDouble(INDEX_MAX_TEMP);
        mMinTemp = data.getDouble(INDEX_MIN_TEMP);
        data.close();

        // create or connect a Google API client
        if (null == mGoogleApiClient) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .build();
        }
        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        // create and send a request to update the weather on wearable
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(REQ_PATH);

        putDataMapRequest.getDataMap().putInt(KEY_WEATHER_ID, mWeatherId);
        putDataMapRequest.getDataMap().putDouble(KEY_TEMP_MAX, mMaxTemp);
        putDataMapRequest.getDataMap().putDouble(KEY_TEMP_MIN, mMinTemp);

        PutDataRequest request = putDataMapRequest.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                                       @Override
                                       public void onResult(DataApi.DataItemResult dataItemResult) {
                                           if (dataItemResult.getStatus().isSuccess()) {
                                               Log.v(TAG, "Successfully sent");
                                           } else {
                                               Log.v(TAG, "Failed to send");
                                           }

                                           mGoogleApiClient.disconnect();
                                       }
                                   }
                );
    }

    @Override
    public void onConnectionSuspended(int i) {

    }
}
