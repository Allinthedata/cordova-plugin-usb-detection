package org.excession.usbDetection;

import android.content.Context;
import android.database.Cursor;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.support.v4.provider.DocumentFile;

import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Iterator;

public class Helpers {
    private static final String TAG = "UsbDetection";

    private static final String DOT_UNDERSCORE_FILE = "%2F._";

    private static final int SCSI_SUBCLASS = 6;
    private static final int BULK_PROTOCOL = 80;

    public static boolean hasMassStorageDevice(Context context) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        boolean hasMassStorage = false;

        Collection<UsbDevice> devices = usbManager.getDeviceList().values();
        for (Iterator<UsbDevice> it = devices.iterator(); it.hasNext() && !hasMassStorage; ) {
            UsbDevice device = it.next();

            int interfaceCount = device.getInterfaceCount();
            for (int i = 0; i < interfaceCount; i++) {
                UsbInterface usbInterface = device.getInterface(i);

                if (usbInterface.getInterfaceClass() != UsbConstants.USB_CLASS_MASS_STORAGE
                        || usbInterface.getInterfaceSubclass() != SCSI_SUBCLASS
                        || usbInterface.getInterfaceProtocol() != BULK_PROTOCOL) {
                    continue;
                }

                // Every mass storage device has exactly two endpoints
                // One IN and one OUT endpoint
                int endpointCount = usbInterface.getEndpointCount();
                if (endpointCount == 2) {
                    UsbEndpoint outEndpoint = null;
                    UsbEndpoint inEndpoint = null;

                    for (int j = 0; j < endpointCount; j++) {
                        UsbEndpoint endpoint = usbInterface.getEndpoint(j);

                        if(endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                            if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                                outEndpoint = endpoint;
                            } else {
                                inEndpoint = endpoint;
                            }
                        }
                    }

                    if (outEndpoint != null || inEndpoint != null) {
                        hasMassStorage = true;
                    }
                }
            }
        }

        return hasMassStorage;
    }

    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    public static String findValidRoot(Context context, JSONArray existingRoots) throws JSONException {
        String validRoot = null;

        for (int i = 0; i < existingRoots.length(); ++i) {
            String root = existingRoots.getString(i);
            DocumentFile rootFile = DocumentFile.fromTreeUri(context, Uri.parse(root));

            LOG.v(TAG, "Checking root " + root + ", exists: " + rootFile.exists() + ", isDirectory: " + rootFile.isDirectory());

            if (rootFile.exists() && rootFile.isDirectory()) {
                validRoot = root;
            }
        }

        return validRoot;
    }

    public static JSONArray discoverUploadFiles(Context context, String rootDir) throws JSONException {
        JSONArray images = new JSONArray();

        DocumentFile root = DocumentFile.fromTreeUri(context, Uri.parse(rootDir));
        discoverUploadFilesInDir(context, root, images);

        return images;
    }

    private static void discoverUploadFilesInDir(Context context, DocumentFile dir, JSONArray images) throws JSONException {
        LOG.v(TAG, "Discovering upload files in " + dir.getName());

        for (DocumentFile file : dir.listFiles()) {
            if (file.exists()) {
                if (file.isDirectory()) {
                    discoverUploadFilesInDir(context, file, images);
                } else if (isUploadFile(file)) {
                    Uri fileUri = file.getUri();
                    LOG.v(TAG, "Discovered upload file " + fileUri.toString());
                    JSONObject obj = new JSONObject();
                    obj.put("uri", fileUri.toString());
                    obj.put("name", getFileName(context, fileUri));

                    images.put(obj);
                }
            }
        }
    }

    private static boolean isUploadFile(DocumentFile file) {
        if (!file.getType().equals("image/jpeg")) {
            return false;
        }

        // Make sure that this isn't a ._ file from OSX
        Uri fileUri = file.getUri();
        return fileUri.toString().indexOf(DOT_UNDERSCORE_FILE) == -1;
    }
}