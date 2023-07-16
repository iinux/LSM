package com.iinux.lsm;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final long WIFI_RESTART_DELAY = 6000; // 6 seconds

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request necessary permissions
        String[] permissions = {Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.INTERNET};
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showIPDialog();
            } else {
                Toast.makeText(this, "Permissions denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    void toastLong(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    // 在你的 Activity 中调用此方法来退出应用
    public void exitApp() {
        finishAffinity(); // 关闭当前 Activity 及其所有子 Activity
        System.exit(0); // 终止应用进程
    }

    private void showIPDialog() {
        // Get the IP address
        List<String> ipAddresses = getWifiIPAddresses();
        String message = TextUtils.join("\n", ipAddresses);

        // Create and show the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Local IP Address");
        builder.setMessage(message);
        builder.setPositiveButton("OK", null);
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                // checkAndRestartWifi();
                exitApp();
            }
        });
        builder.show();
    }

    public String getAllIPAddresses() {
        StringBuilder sb = new StringBuilder();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress()) {
                        String ipAddress = address.getHostAddress();
                        if (address.getAddress().length == 4) {
                            sb.append(ipAddress).append("\n");
                        } else {
                            if (address.getAddress().length == 16) {
                                assert ipAddress != null;
                                if (ipAddress.startsWith("2")) {
                                    sb.append(ipAddress).append("\n");
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            toast(e.getMessage());
        }
        return sb.toString();
    }

    private List<String> getWifiIPAddresses() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        List<String> ipAddresses = new ArrayList<>();

        if (wifiManager != null && connectivityManager != null) {
            Network[] networks = connectivityManager.getAllNetworks();
            for (Network network : networks) {
                if (connectivityManager.getNetworkInfo(network).getType() == ConnectivityManager.TYPE_WIFI) {
                    LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
                    if (linkProperties != null) {
                        List<LinkAddress> addresses = linkProperties.getLinkAddresses();
                        for (LinkAddress address : addresses) {
                            InetAddress inetAddress = address.getAddress();
                            if (!inetAddress.isLoopbackAddress()) {
                                if (inetAddress instanceof Inet6Address) {
                                    // IPv6 address
                                    ipAddresses.add(inetAddress.getHostAddress());
                                } else {
                                    // IPv4 address
                                    ipAddresses.add(inetAddress.getHostAddress());
                                }
                            }
                        }
                    }
                }
            }
        }

        return ipAddresses;
    }

    private void checkAndRestartWifi() {
        MainActivity self = this;
        // Check if only IPv4 address is available
        if (isIPv4Only()) {
            // Restart Wi-Fi after delay
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(self, "ready to restart", Toast.LENGTH_SHORT).show();
                    WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

                    // Disable and enable current Wi-Fi network
                    @SuppressLint("MissingPermission") List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
                    if (configuredNetworks != null) {
                        for (WifiConfiguration config : configuredNetworks) {
                            if (config.status == WifiConfiguration.Status.CURRENT) {
                                wifiManager.disableNetwork(config.networkId);
                                wifiManager.enableNetwork(config.networkId, true);
                                break;
                            }
                        }
                    }
                }
            }, WIFI_RESTART_DELAY);
        }
    }

    private boolean isIPv4Only() {
        // Check if only IPv4 address is available
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network[] networks = cm.getAllNetworks();
        for (Network network : networks) {
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                        && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)) {
                    return true;
                }
            }
        }
        return false;
    }
}


