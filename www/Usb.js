/* global require, module, console, Promise */

var exec = require('cordova/exec');

var listener = null;

var Usb = {
    status: function() {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "UsbDetection", "getStatus", []);
        });
    },
    
    listen: function(callback) {
        listener = callback;
        
        var onUpdate = function(update) {
            console.log("USB Update", update);
            listener(update);
        };
        
        exec(onUpdate, null, "UsbDetection", "listen", []);
    },
    
    open: function() {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "UsbDetection", "open", []);
        });
    }
};

module.exports = Usb;