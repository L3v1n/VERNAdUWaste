package com.vern.vernaduwaste;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class NetworkStateManager {

    private static final String TAG = "NetworkStateManager";

    private static NetworkStateManager instance;
    private final MutableLiveData<Boolean> networkAvailable = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> wifiConnected = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> internetAccessible = new MutableLiveData<>(false);

    private final ConnectivityManager connectivityManager;

    private NetworkStateManager(Context context) {
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        registerNetworkCallback();
        // Force an initial network status update
        updateInitialNetworkStatus();
    }

    public static synchronized NetworkStateManager getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkStateManager(context.getApplicationContext());
        }
        return instance;
    }

    private void registerNetworkCallback() {
        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .build();

        connectivityManager.registerNetworkCallback(networkRequest, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Log.d(TAG, "Network available");
                updateNetworkStatus(network);
            }

            @Override
            public void onLost(Network network) {
                Log.d(TAG, "Network lost");
                networkAvailable.postValue(false);
                wifiConnected.postValue(false);
                internetAccessible.postValue(false);
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                Log.d(TAG, "Network capabilities changed");
                updateNetworkStatus(network);
            }
        });
    }

    private void updateInitialNetworkStatus() {
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork != null) {
            updateNetworkStatus(activeNetwork);
        } else {
            networkAvailable.postValue(false);
            wifiConnected.postValue(false);
            internetAccessible.postValue(false);
        }
    }

    private void updateNetworkStatus(Network network) {
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        if (capabilities != null) {
            boolean hasInternetCapability = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            boolean isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            boolean isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);

            networkAvailable.postValue(true);
            wifiConnected.postValue(isWifi);

            if (hasInternetCapability && isValidated) {
                internetAccessible.postValue(true);
                Log.d(TAG, "Internet access confirmed via NetworkCapabilities.");
            } else {
                // Perform an actual network check
                checkInternetAccess();
            }

            Log.d(TAG, "Network Type: " + (isWifi ? "Wi-Fi" : "Other"));
            Log.d(TAG, "Internet Accessible (Validated): " + isValidated);
        } else {
            networkAvailable.postValue(false);
            wifiConnected.postValue(false);
            internetAccessible.postValue(false);
        }
    }

    private void checkInternetAccess() {
        new Thread(() -> {
            try {
                HttpURLConnection urlConnection = (HttpURLConnection) new URL("https://clients3.google.com/generate_204").openConnection();
                urlConnection.setConnectTimeout(1000);
                urlConnection.setReadTimeout(1000);
                urlConnection.setUseCaches(false);
                urlConnection.getInputStream();
                int responseCode = urlConnection.getResponseCode();
                if (responseCode == 204) {
                    Log.d(TAG, "Internet access confirmed via HTTP request.");
                    internetAccessible.postValue(true);
                } else {
                    Log.d(TAG, "Internet access check failed. Response code: " + responseCode);
                    internetAccessible.postValue(false);
                }
                urlConnection.disconnect();
            } catch (IOException e) {
                Log.d(TAG, "Internet access check failed with exception: " + e.getMessage());
                internetAccessible.postValue(false);
            }
        }).start();
    }

    public LiveData<Boolean> getNetworkAvailable() {
        return networkAvailable;
    }

    public LiveData<Boolean> getWifiConnected() {
        return wifiConnected;
    }

    public LiveData<Boolean> getInternetAccessible() {
        return internetAccessible;
    }

    public boolean isNetworkAvailable() {
        Boolean isAvailable = networkAvailable.getValue();
        return isAvailable != null && isAvailable;
    }

    public boolean isWifiConnected() {
        Boolean isWifi = wifiConnected.getValue();
        return isWifi != null && isWifi;
    }

    public boolean isInternetAccessible() {
        Boolean isAccessible = internetAccessible.getValue();
        return isAccessible != null && isAccessible;
    }
}
