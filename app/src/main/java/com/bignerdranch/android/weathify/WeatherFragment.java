package com.bignerdranch.android.weathify;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class WeatherFragment extends Fragment {

    private String mUserAddress;
    private TextView mCityText;
    private TextView mTemperatureText;
    private TextView mWeatherText;
    private String mCurrentCondition;
    private TextView mWindText;
    private TextView mFeelsLikeText;

    private Button mPreviousButton;
    private Button mPlayButton;
    private Button mPauseButton;
    private Button mNextButton;

    private Button mLaunchSpotifyPlayerButton;

    private static final String TAG = "WeatherManager";

    OnPreviousTrackListener mOnPreviousTrackListener;
    OnResumeTrackListener mOnResumeTrackListener;
    OnNextTrackListener mOnNextTrackListener;
    OnPauseTrackListener mOnPauseTrackListener;
    OnLaunchSpotifyPlayerListener mOnLaunchSpotifyPlayerListener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mOnLaunchSpotifyPlayerListener = (OnLaunchSpotifyPlayerListener) activity;
            mOnNextTrackListener = (OnNextTrackListener) activity;
            mOnPreviousTrackListener = (OnPreviousTrackListener) activity;
            mOnPauseTrackListener = (OnPauseTrackListener) activity;
            mOnResumeTrackListener = (OnResumeTrackListener) activity;
        } catch (ClassCastException cce) {
            throw new ClassCastException(activity.toString() + " must implement WeatherFragment listeners");
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        Log.i(TAG, "onSaveInstanceState");
        savedInstanceState.putString("city", mUserAddress);
        savedInstanceState.putString("temperature", mTemperatureText.getText().toString());
        savedInstanceState.putString("weather", mWeatherText.getText().toString());
        savedInstanceState.putString("wind", mWindText.getText().toString());
        savedInstanceState.putString("feelslike", mFeelsLikeText.getText().toString());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.weather_fragment, container, false);

        String key = getKeyFromRawResource();
        mUserAddress = getArguments().getString(WeathifyManagerActivity.USER_ADDRESS);

        mCityText = (TextView) view.findViewById(R.id.city_text);
        mCityText.setText(mUserAddress);

        mTemperatureText = (TextView) view.findViewById(R.id.temperature_text);
        mWeatherText = (TextView) view.findViewById(R.id.weather_text);
        mWindText = (TextView) view.findViewById(R.id.wind_text);
        mFeelsLikeText = (TextView) view.findViewById(R.id.feels_like_text);

        if (savedInstanceState != null) {
            mUserAddress = savedInstanceState.getString("city", "");
            mTemperatureText.setText(savedInstanceState.getString("temperature", ""));
            mWeatherText.setText(savedInstanceState.getString("weather", ""));
            mWindText.setText(savedInstanceState.getString("wind", ""));
            mFeelsLikeText.setText(savedInstanceState.getString("feelslike", ""));
        } else {
            String city = "";
            String state = "";
            String[] addressParts = mUserAddress.split("\n");

            String cityState = "";
            if (addressParts.length > 1) {
                cityState = addressParts[1];
            } else {
                cityState = addressParts[0];
            }

            int commaIndex = cityState.indexOf(",");
            if (commaIndex > -1) {
                city = cityState.substring(0, commaIndex).trim();
                state = cityState.substring(commaIndex + 1, commaIndex + 4).trim();
            }

            if (!city.isEmpty() & !state.isEmpty()) {
                String wuTempUrl = "http://api.wunderground.com/api/"+key+"/conditions/q/"+state+"/"+city+".json";
                new fetchWeatherDataFromWeatherUnderground().execute(wuTempUrl);
            }
        }

        mPreviousButton = (Button) view.findViewById(R.id.previous_button);
        mPreviousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOnPreviousTrackListener.previousTrack();
            }
        });

        mPauseButton = (Button) view.findViewById(R.id.pause_button);
        mPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOnPauseTrackListener.pauseTrack();
            }
        });

        mPlayButton = (Button) view.findViewById(R.id.play_button);
        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOnResumeTrackListener.resumeTrack();
            }
        });

        mNextButton = (Button) view.findViewById(R.id.next_button);
        mNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOnNextTrackListener.nextTrack();
            }
        });

        mLaunchSpotifyPlayerButton = (Button) view.findViewById(R.id.spotify_playlist_button);
        mLaunchSpotifyPlayerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mOnLaunchSpotifyPlayerListener.launchSpotifyPlayer(mCurrentCondition);
            }
        });

        return view;
    }

    interface OnLaunchSpotifyPlayerListener {
        void launchSpotifyPlayer(String currentCondition);
    }

    interface OnPauseTrackListener {
        void pauseTrack();
    }

    interface OnResumeTrackListener {
        void resumeTrack();
    }

    interface OnPreviousTrackListener {
        void previousTrack();
    }

    interface OnNextTrackListener {
        void nextTrack();
    }

    private String getKeyFromRawResource() {
        InputStream keyStream = getResources().openRawResource(R.raw.key);
        BufferedReader keyStreamReader = new BufferedReader(new InputStreamReader(keyStream));

        try {
            String key = keyStreamReader.readLine();
            return key;
        } catch (IOException e) {
            Log.e(TAG, "Error reading secret key from raw resource file", e);
            return null;
        }
    }

    class fetchWeatherDataFromWeatherUnderground extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String...urls){

            String responseString = null;
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                InputStream responseStream = new BufferedInputStream(connection.getInputStream());
                InputStreamReader streamReader = new InputStreamReader(responseStream);

                int c;
                StringBuffer buffer = new StringBuffer();
                while ((c = streamReader.read()) != -1) {
                    buffer.append((char)c);
                }

                responseString = buffer.toString();
                Log.i("WEATHER", "String is" + responseString);

            } catch (Exception e) {
                Log.e(TAG, "Error fetching weather data, see exception for details:", e);
            }
            return responseString;
        }

        @Override
        protected void onPostExecute(String result) {

            if (result != null) {
                try {
                    Log.i(TAG, result);
                    JSONObject response = new JSONObject(result);
                    JSONObject currentConditions = response.getJSONObject("current_observation");

                    mTemperatureText.setText(currentConditions.getString("temperature_string"));

                    mCurrentCondition = currentConditions.getString("weather");
                    mWeatherText.setText(mCurrentCondition);

                    mWindText.setText(currentConditions.getString("wind_string"));
                    mFeelsLikeText.setText(currentConditions.getString("feelslike_string"));

                } catch (JSONException e) {
                    Log.e(TAG, "parsing error, check schema?", e);
                    mTemperatureText.setText("Error fetching temperature for " + mUserAddress);

                }
            } else {
                Log.e(TAG, "Result was null, check doInBackground for errors");
                mTemperatureText.setText("Error fetching temperature for " + mUserAddress);
            }
        }
    }
}
