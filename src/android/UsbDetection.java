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
import org.json.JSONObject;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
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
    private static final int REQUEST_OPEN_DOCUMENT = 348;

    private CallbackContext updateCallback;
    private BroadcastReceiver usbReceiver;
    private BroadcastReceiver mediaReceiver;

    private CallbackContext openCallback;
    private CallbackContext selectCallback;

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
            discoverAndOpenCard(data, callbackContext);
            return true;
        } else if (action.equals("select")) {
            selectCallback = callbackContext;

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            if (data.length() > 0) {
                String type = data.getString(0);
                if (type != null && type.length() > 0) {
                    LOG.v(TAG, "Setting open document type to " + type);
                    intent.setType(type);
                }
            }

            cordova.startActivityForResult(this, intent, REQUEST_OPEN_DOCUMENT);
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
                    if (resultCode == Activity.RESULT_OK) {
                        String path = data.getDataString();
                        LOG.v(TAG, "Found card root " + path);
                        discoverAndReturnUploadableFiles(path);
                    } else {
                        LOG.v(TAG, "Card root failed");
                        PluginResult result = new PluginResult(PluginResult.Status.ERROR);

                        openCallback.sendPluginResult(result);
                        openCallback = null;
                    }
                }
                break;
            case REQUEST_OPEN_DOCUMENT:
                if (selectCallback != null) {
                    PluginResult result = null;

                    if (resultCode != Activity.RESULT_OK) {
                        LOG.v(TAG, "Document selection failed");
                        result = new PluginResult(PluginResult.Status.ERROR);
                    } else {
                        try {
                            JSONArray files = new JSONArray();
                            Context context = getContext();

                            ClipData fileData = data.getClipData();
                            if (fileData == null) {
                                Uri uri = data.getData();
                                String fileName = Helpers.getFileName(context, uri);
                                LOG.v(TAG, "User selected one file: " + uri.toString() + ", " + fileName);
                                files.put(constructFileObject(uri, fileName));
                            } else {
                                int fileCount = fileData.getItemCount();
                                LOG.v(TAG, "User selected " + fileCount + " file(s)");

                                for (int i = 0; i < fileCount; ++i) {
                                    ClipData.Item item = fileData.getItemAt(i);
                                    Uri uri = item.getUri();
                                    String fileName = Helpers.getFileName(context, uri);
                                    LOG.v(TAG, "User selected " + uri.toString() + ", " + fileName);
                                    files.put(constructFileObject(uri, fileName));
                                }
                            }

                            result = new PluginResult(PluginResult.Status.OK, files);
                        } catch (JSONException e) {
                            LOG.e(TAG, "Document selection failed", e);
                            result = new PluginResult(PluginResult.Status.ERROR);
                        }
                    }

                    selectCallback.sendPluginResult(result);
                    selectCallback = null;
                }
        }
    }

    private void discoverAndOpenCard(JSONArray data, CallbackContext callbackContext) {
        openCallback = callbackContext;
        boolean needsDiscovery = true;

        if (data.length() > 0) {
            try {
                JSONArray existingRoots = data.getJSONArray(0);
                if (existingRoots != null && existingRoots.length() > 0) {
                    String rootDir = Helpers.findValidRoot(getContext(), existingRoots);
                    if (rootDir != null) {
                        needsDiscovery = false;
                        LOG.v(TAG, "Discovered existing root, discovering and returning files from" + rootDir);
                        discoverAndReturnUploadableFiles(rootDir);
                    }
                }
            } catch (Exception e) {
                LOG.e(TAG, "Error while trying to open existing roots", e);
            }
        }

        if (needsDiscovery) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            cordova.startActivityForResult(this, intent, REQUEST_FIND_CARD);
        }
    }

    private void discoverAndReturnUploadableFiles(final String rootDir) {
        if (openCallback != null) {
            final CallbackContext thisOpenCallback = openCallback;
            openCallback = null;

            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    PluginResult result = null;

                    try {
                        JSONArray files = Helpers.discoverUploadFiles(getContext(), rootDir);

                        JSONObject uploadResult = new JSONObject();
                        uploadResult.put("root", rootDir);
                        uploadResult.put("files", files);

                        result = new PluginResult(PluginResult.Status.OK, uploadResult);
                    } catch (Exception e) {
                        LOG.e(TAG, "Exception while discovering uploadable files", e);
                        result = new PluginResult(PluginResult.Status.ERROR);
                    }

                    thisOpenCallback.sendPluginResult(result);
                }
            });
        }
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

    private JSONObject constructFileObject(Uri file, String fileName) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("uri", file.toString());
        obj.put("name", fileName);
        return obj;
    }
}
