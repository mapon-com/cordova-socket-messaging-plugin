# Cordova Socket.IO client plugin for Android

Plugin for Cordova to enable native Android background service of WebSocket messaging using Socket.IO.

The idea is simple, you just start and stop the service from Cordova JavaScript,
listen for incoming socket messages and send outgoing messages.

It works purely as a transport layer.
Incoming message contain 3 fields:

    String event - message type
    JSON data - main data object
    Boolean alert (optional) - flag for popup notification

Outgoing messages contain 2 fields:

    String event - message type
    JSON data - main data object

The background service will recover itself if the application is closed/killed.
It will reconnect to the socket and wake up the Cordova application if there is an incoming message.

Note: This is a client. It needs a server implementation. 

## Installation

Install via repo url directly, run:

    cordova plugin add https://github.com/mapon-com/cordova-socket-messaging-plugin.git

## Using the plugin

### Basic commands

Starting the service:

    socketService.startService(connectUrl);

Stopping the service:

    socketService.stopService();
    
### Listeners

Listeners listen for incoming messages in the background service.
If the application is in the foreground when the message comes in, the message is broadcasted to Cordova and recieved by the listener.

Adding a listener:

    socketservice.addEventListener(eventName, callback);
    
Removing the listener:

    socketservice.removeEventListener(eventName, callback);
    
### Sending a message

    socketservice.fireNativeEvent( 'outgoing.event',
        {
            event: event,
            data: data
        }
    );
    
### Wake-up functionality

If the application is in the background, or killed, when the message comes in, then the background service will show a popup notification.
Clicking the notification will start the application.
Once the application is started again, check that it was started by the background service:

    socketservice.hasParam(paramName);
    
And receive the incoming message:
    
    socketservice.getParam(paramName);

### Internationalization

It is possible to translate the popup notification texts.

    socketservice.injectLangArray(
        {
            'new_[event_type]': 'New message ...',
            'open': 'Open',
            'close': 'Close'
        }
    );

### Other notes
    
All the method calls support success and error callbacks. For example:

    socketservice.startService(
        connectUrl,
        function() { console.log('service started') },
        function() { console.log('something went wrong') }
    );
