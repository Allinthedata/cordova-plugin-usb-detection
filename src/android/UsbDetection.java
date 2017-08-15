package org.excession.usbDetection;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.LOG;
import org.apache.cordova.file.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.content.ContentResolver;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.provider.DocumentFile;

import java.io.File;

public class UsbDetection extends CordovaPlugin {
    private static final String TAG = "UsbDetection";
    private static final int REQUEST_FIND_CARD = 763;

    private CallbackContext updateCallback;
    private BroadcastReceiver usbReceiver;
    private BroadcastReceiver mediaReceiver;

    private CallbackContext openCallback;

    @Override
    public void initialize(final CordovaInterface cordova, CordovaWebView webView){
        LOG.setLogLevel("VERBOSE");
        LOG.v(TAG, "UsbDetection: initialization");
        super.initialize(cordova, webView);
    }

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
        LOG.v(TAG, "USB Action" + action);
        if (action.equals("getStatus")) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, isUsbAvailable());
            callbackContext.sendPluginResult(result);
            return true;
        } else if (action.equals("listen")) {
            updateCallback = callbackContext;
            PluginResult result = new PluginResult(PluginResult.Status.OK, isUsbAvailable());
            result.setKeepCallback(true);
            updateCallback.sendPluginResult(result);
            return true;
        } else if (action.equals("open")) {
            openCallback = callbackContext;
            cordova.setActivityResultCallback(this);

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            cordova.startActivityForResult(this, intent, REQUEST_FIND_CARD);
            return true;
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        LOG.v(TAG, "Activity result", requestCode);
        switch (requestCode) {
            case REQUEST_FIND_CARD:
                if (openCallback != null) {
                    PluginResult result = null;

                    if (resultCode == Activity.RESULT_OK) {
                        String path = data.getDataString();
                        LOG.v(TAG, "Found card root " + path);
                        result = new PluginResult(PluginResult.Status.OK, path);
                    } else {
                        LOG.v(TAG, "Card root failed");
                        result = new PluginResult(PluginResult.Status.ERROR);
                    }

                    openCallback.sendPluginResult(result);
                    openCallback = null;
                }
        }
    }

    private File getSdCardDirectory() {
        String externalStorageDirectory = Environment.getExternalStorageDirectory().getAbsolutePath();

        LOG.v(TAG, "External storage directory is " + externalStorageDirectory);

        File[] storageDirs = new File("/storage/").listFiles();
        File sdDir = null;

        for (int i = 0; i < storageDirs.length && sdDir == null; ++i) {
            File dir = storageDirs[i];
            LOG.v(TAG, "Checking directory " + dir.getAbsolutePath());
            if (!dir.getAbsolutePath().equals(externalStorageDirectory) && dir.isDirectory() && dir.canRead()) {
                LOG.v(TAG, "This is the absolute path");
                sdDir = dir;
            }
        }

        return sdDir;
    }

    private boolean isSdPresent() {
        return getSdCardDirectory() != null;
    }

    private boolean isUsbAvailable() {
        return Helpers.hasMassStorageDevice(getContext());
    }

    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        initBroadcastReceiver();
    }

    public void onPause(boolean multitasking) {
        super.onPause(multitasking);

        if (usbReceiver != null) {
            LOG.v(TAG, "Destroying usb receiver");
            getContext().unregisterReceiver(usbReceiver);
            usbReceiver = null;
        }

        if (mediaReceiver != null) {
            LOG.v(TAG, "Destroying media receiver");
            getContext().unregisterReceiver(mediaReceiver);
            mediaReceiver = null;
        }
    }

    private void initBroadcastReceiver() {
        LOG.v(TAG, "Initialising broadcast receiver");

        usbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                LOG.d(TAG, "USB broadcast intent " + action);

                if (updateCallback != null) {
                    PluginResult result = new PluginResult(PluginResult.Status.OK, isUsbAvailable());
                    result.setKeepCallback(true);
                    updateCallback.sendPluginResult(result);
                }
            }
        };

        mediaReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                LOG.d(TAG, "Media broadcast intent " + action);

                if (updateCallback != null) {
                    PluginResult result = new PluginResult(PluginResult.Status.OK, isUsbAvailable());
                    result.setKeepCallback(true);
                    updateCallback.sendPluginResult(result);
                }
            }
        };

        IntentFilter mediaFilter = new IntentFilter();
        mediaFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        mediaFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        mediaFilter.addAction(Intent.ACTION_MEDIA_SHARED);
        mediaFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        mediaFilter.addDataScheme(ContentResolver.SCHEME_CONTENT);

        IntentFilter otgFilter = new IntentFilter();
        otgFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        otgFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);

        Context context = getContext();
        context.registerReceiver(mediaReceiver, mediaFilter);
        context.registerReceiver(usbReceiver, otgFilter);
    }

    private Context getContext() {
        return cordova.getActivity().getApplicationContext();
    }
}
