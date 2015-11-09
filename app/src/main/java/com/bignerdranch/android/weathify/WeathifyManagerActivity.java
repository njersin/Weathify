package com.bignerdranch.android.weathify;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.location.Geocoder;
import android.location.Location;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerNotificationCallback;
import com.spotify.sdk.android.player.PlayerState;
import com.spotify.sdk.android.player.Spotify;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class WeathifyManagerActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        WeatherFragment.OnLaunchSpotifyPlayerListener,
        WeatherFragment.OnPauseTrackListener,
        WeatherFragment.OnNextTrackListener,
        WeatherFragment.OnResumeTrackListener,
        WeatherFragment.OnPreviousTrackListener,
        PlayerNotificationCallback,
        ConnectionStateCallback {

    private static final int REQUEST_CODE = 33;

    private static final String CLIENT_ID = "24f48422484c4489a3482c2a89df034f";
    private static final String REDIRECT_URI = "weathify-login://callback";

    private Player mSpotifyPlayer;

    protected static final String TAG = "WeathifyManagerActivity";
    public static final String USER_ADDRESS = "UserAddress";

    private GoogleApiClient mGoogleApiClient;
    private Location mUserCurrentLocation;
    private AddressResultReceiver mResultReceiver;
    private boolean mFetchAddress;
    private String mUserAddress;

    private SplashFragment mSplashFragment;

    private String mSpotifyPlaylistUser;
    private String mSpotifyPlaylistID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mResultReceiver = new AddressResultReceiver(new Handler());

        mFetchAddress = true;
        getGoogleApiClient();

        mSplashFragment =  new SplashFragment();
        FragmentManager manager = getFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();
        transaction.add(android.R.id.content, mSplashFragment);
        transaction.commit();
    }

    protected synchronized void getGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    public void resumeTrack() {
        mSpotifyPlayer.resume();
    }

    @Override
    public void pauseTrack() {
        mSpotifyPlayer.pause();
    }

    @Override
    public void previousTrack() {
        mSpotifyPlayer.skipToPrevious();
    }

    @Override
    public void nextTrack() { mSpotifyPlayer.skipToNext(); }

    @Override
    public void launchSpotifyPlayer(String currentCondition) {

        try {
            String spotifyPlaylists = getSpotifyPlaylists();
            JSONObject playlists = new JSONObject(spotifyPlaylists);
            JSONObject spotifyplaylist = playlists.getJSONObject("spotifyplaylist");
            JSONObject weatherPlaylist = spotifyplaylist.getJSONObject(currentCondition);

            mSpotifyPlaylistUser = weatherPlaylist.getString("user");
            mSpotifyPlaylistID = weatherPlaylist.getString("playlist");

            if (!mSpotifyPlaylistUser.isEmpty() & !mSpotifyPlaylistID.isEmpty()) {

                AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                        AuthenticationResponse.Type.TOKEN,
                        REDIRECT_URI);
                builder.setScopes(new String[]{"user-read-private", "streaming"});
                AuthenticationRequest request = builder.build();

                AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
            }

        } catch (JSONException e) {
            Log.e(TAG, "parsing error, check schema?", e);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                Config playerConfig = new Config(this, response.getAccessToken(), CLIENT_ID);
                mSpotifyPlayer = Spotify.getPlayer(playerConfig, this, new Player.InitializationObserver() {
                    @Override
                    public void onInitialized(Player player) {
                        mSpotifyPlayer.addConnectionStateCallback(WeathifyManagerActivity.this);
                        mSpotifyPlayer.addPlayerNotificationCallback(WeathifyManagerActivity.this);
                        mSpotifyPlayer.play("spotify:user:" + mSpotifyPlaylistUser + ":playlist:" + mSpotifyPlaylistID);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e("MainActivity", "Could not initialize player: " + throwable.getMessage());
                    }
                });
            }
        }
    }

    @Override
    public void onLoggedIn() {
        Log.d(TAG, "User logged in");
    }

    @Override
    public void onLoggedOut() {
        Log.d(TAG, "User logged out");
    }

    @Override
    public void onLoginFailed(Throwable error) {
        Log.d(TAG, "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Log.d(TAG, "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d(TAG, "Received connection message: " + message);
    }

    @Override
    public void onPlaybackEvent(EventType eventType, PlayerState playerState) {
        Log.d(TAG, "Playback event received: " + eventType.name());
    }

    @Override
    public void onPlaybackError(ErrorType errorType, String errorDetails) {
        Log.d(TAG, "Playback error received: " + errorType.name());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Spotify.destroyPlayer(this);
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        mUserCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mUserCurrentLocation != null) {
            if (!Geocoder.isPresent()) {
                Toast.makeText(this, R.string.no_geocoder_available, Toast.LENGTH_LONG).show();
                return;
            }

            if (mFetchAddress) {
                startIntentService();
            }

        }  else {
            Toast.makeText(this, R.string.no_location_detected, Toast.LENGTH_LONG).show();
            return;
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    protected void startIntentService() {
        Intent intent = new Intent(this, FetchAddressIntentService.class);
        intent.putExtra(GeocoderConstants.RECEIVER, mResultReceiver);
        intent.putExtra(GeocoderConstants.LOCATION_DATA_EXTRA, mUserCurrentLocation);
        startService(intent);
    }

    class AddressResultReceiver extends ResultReceiver {
        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            mUserAddress = resultData.getString(GeocoderConstants.RESULT_DATA_KEY);
            if (resultCode == GeocoderConstants.SUCCESS_RESULT) {

                FragmentManager manager = getFragmentManager();
                FragmentTransaction transaction = manager.beginTransaction();
                WeatherFragment weatherFragment = new WeatherFragment();

                Bundle arguments = new Bundle();
                arguments.putString(USER_ADDRESS, mUserAddress);
                weatherFragment.setArguments(arguments);

                transaction.addToBackStack(weatherFragment.getClass().getName());  //tag for transaction == fragment class name
                transaction.replace(android.R.id.content, weatherFragment);
                transaction.commit();

            } else {
                showToast("Could not find address...");
            }
        }
    }

    protected void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private String getSpotifyPlaylists() {
        InputStream playlistStream = getResources().openRawResource(R.raw.spotifyplaylists);
        BufferedReader playlistStreamReader = new BufferedReader(new InputStreamReader(playlistStream));

        try {

            int c;
            StringBuffer buffer = new StringBuffer();
            while ((c = playlistStreamReader.read()) != -1) {
                buffer.append((char)c);
            }

            String playlist = buffer.toString();
            return playlist;

        } catch (IOException e) {
            Log.e(TAG, "Error reading secret spotify playlists from raw resource file", e);
            return null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
