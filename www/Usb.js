/* global require, module, console, Promise, window */

var exec = require('cordova/exec');

var listener = null;

function resolveFile(fileObject) {
    return new Promise(function(resolve, reject) {
        function merge(file) {
            file.name = fileObject.name;
            // Cordova's file transfer plugin doesn't work well with cdvfiles, so we reset it back to the original URI
            file.localURL = fileObject.uri;
            resolve(file);
        }
        
        function getFile(dirEntry) {
            return dirEntry.file(merge, reject);
        }

        window.resolveLocalFileSystemURL(fileObject.uri, getFile, reject);
    });
}

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
    
    open: function(roots) {
        if (roots) {
            if (!Array.isArray(roots)) {
                roots = [roots];
            }
            
            for (var i = 0; i < roots.length; ++i) {
                var root = roots[i];
                if ("string" !== typeof root) {
                    throw new Error("Invalid root " + root + " supplied");
                }
            }
        }
        
        return new Promise(function(resolve, reject) {
            function resolveFiles(result) {
                return Promise.all(result.files.map(resolveFile)).then(function(files) {
                    result.files = files;
                    resolve(result);
                });
            }
            
            exec(resolveFiles, reject, "UsbDetection", "open", [roots]);
        });
    },
    
    select: function(options) {
        options = options || {};
        var mimeType = options.mimeType || "";
        
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "UsbDetection", "select", [mimeType]);
        });
    }
};

module.exports = Usb;