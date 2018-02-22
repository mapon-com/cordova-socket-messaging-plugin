var exec = require('cordova/exec');
var channel = require('cordova/channel');

var socketservice = {
    injectLangArray: function (arg0, success, error) {
        return exec(success, error, "SocketService", "injectLangArray", [arg0]);
    },
    startService: function (arg0, success, error) {
        return exec(success, error, "SocketService", "startService", [arg0]);
    },
    stopService: function (arg0, success, error) {
        return exec(success, error, "SocketService", "stopService", [arg0]);
    },
    disableAlerts: function (arg0, success, error) {
        return exec(success, error, "SocketService", "disableAlerts", [arg0]);
    },
    hasParam: function (arg0, success, error) {
        return exec(success, error, "SocketService", "hasParam", [arg0]);
    },
    getParam: function (arg0, success, error) {
        return exec(success, error, "SocketService", "getParam", [arg0]);
    },
    _channels: {},
    createEvent: function(type, data) {
        var event = document.createEvent('Event');
        event.initEvent(type, false, false);
        if (data) {
            for (var i in data) {
                if (data.hasOwnProperty(i)) {
                    event[i] = data[i];
                }
            }
        }
        return event;
    },
    fireNativeEvent: function (eventname, data, success, error) {
        exec(success, error, "BroadcastService", "fireNativeEvent", [ eventname, data ]);
    },
    fireEvent: function (type, data) {
        var event = this.createEvent( type, data );
        if (event && (event.type in this._channels)) {
            this._channels[event.type].fire(event);
        }
    },
    addEventListener: function (eventname,f) {
        if (!(eventname in this._channels)) {
            var me = this;
            exec( function() {
                me._channels[eventname] = channel.create(eventname);
                me._channels[eventname].subscribe(f);
            }, function(err)  {
                console.log( "ERROR addEventListener: " + err)
            }, "BroadcastService", "addEventListener", [ eventname ]);
        }
        else {
            this._channels[eventname].subscribe(f);
        }
    },
    removeEventListener: function(eventname, f) {
        if (eventname in this._channels) {
            var me = this;
            exec( function() {
                me._channels[eventname].unsubscribe(f);
                delete me._channels[eventname];
            }, function(err)  {
                console.log( "ERROR removeEventListener: " + err)
            }, "BroadcastService", "removeEventListener", [ eventname ]);
        }
    }
};

module.exports = socketservice;
