package org.excession.usbDetection;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import org.apache.cordova.LOG;

import java.util.Collection;
import java.util.Iterator;

public class Helpers {
    private static final String TAG = "UsbDetection";

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

}