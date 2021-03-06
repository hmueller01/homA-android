package st.alr.homA.services;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.MqttTopic;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedList;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import de.greenrobot.event.EventBus;
import st.alr.homA.ActivityPreferences;
import st.alr.homA.App;
import st.alr.homA.R;
import st.alr.homA.model.Control;
import st.alr.homA.model.Device;
import st.alr.homA.support.Defaults;
import st.alr.homA.support.Defaults.State;
import st.alr.homA.support.Events;
import st.alr.homA.support.MqttPublish;
import st.alr.homA.support.ServiceBindable;

public class ServiceMqtt extends ServiceBindable implements MqttCallback {
    private final String LOG_TAG = ServiceMqtt.class.getSimpleName();
    private static State.ServiceMqtt state = State.ServiceMqtt.INITIAL;

    private short keepAliveSeconds;
    private MqttClient mqttClient;
    private SharedPreferences sharedPreferences;
    private static ServiceMqtt instance;
    private Thread workerThread;
    private LinkedList<DeferredPublishable> deferredPublishables;
    private Exception error;
    private Handler pubHandler;

    private BroadcastReceiver netConnReceiver;
    private BroadcastReceiver pingSender;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        workerThread = null;
        error = null;
        changeState(Defaults.State.ServiceMqtt.INITIAL);
        keepAliveSeconds = 15 * 60;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        deferredPublishables = new LinkedList<>();
        EventBus.getDefault().register(this);

        HandlerThread pubThread = new HandlerThread("MQTTPUBTHREAD");
        pubThread.start();
        pubHandler = new Handler(pubThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        doStart(startId);
        String action = (intent == null) ? "intent is null!" : intent.getAction();
        Log.v(LOG_TAG, "onStartCommand: intent action: " + action);
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Start the MQTT service in a new thread.
     * @param startId Start Id, set to -1 to force start (after manual disconnect)
     */
    private void doStart(final int startId) {
        Thread thread1 = new Thread() {
            @Override
            public void run() {
                handleStart(startId);
                if (this == workerThread) // Clean up worker thread
                    workerThread = null;
            }

            @Override
            public void interrupt() {
                if (this == workerThread) // Clean up worker thread
                    workerThread = null;
                super.interrupt();
            }
        };
        thread1.start();
    }

    /**
     * Handle the start of the MQTT service.
     * @param startId Start Id, set to -1 to force start (after manual disconnect)
     */
    void handleStart(int startId) {
        Log.v(LOG_TAG, "handleStart");

        // Respect user's wish to stay disconnected. Overwrite with startId == -1 to reconnect manually afterwards
        if ((state == Defaults.State.ServiceMqtt.DISCONNECTED_USERDISCONNECT) && startId != -1) {
            Log.d(LOG_TAG, "handleStart: respecting user disconnect ");
            return;
        }

        if (isConnecting()) {
            Log.d(LOG_TAG, "handleStart: already connecting");
            return;
        }

        // Respect user's wish to not use data
        if (!isBackgroundDataEnabled()) {
            Log.e(LOG_TAG, "handleStart: !isBackgroundDataEnabled");
            changeState(Defaults.State.ServiceMqtt.DISCONNECTED_DATADISABLED);
            return;
        }

        // Don't do anything unless we're disconnected
        if (isDisconnected()) {
            Log.v(LOG_TAG, "handleStart: !isConnected");
            // Check if there is a data connection
            if (isOnline(true)) {
                if (connect()) {
                    Log.v(LOG_TAG, "handleStart: connect sucessfull");
                    onConnect();
                }
            } else {
                Log.e(LOG_TAG, "handleStart: !isOnline");
                changeState(Defaults.State.ServiceMqtt.DISCONNECTED_WAITINGFORINTERNET);
            }
        } else {
            Log.d(LOG_TAG, "handleStart: already connected");
        }
    }
    
    private boolean isDisconnected(){
        Log.v(LOG_TAG, "isDisconnected: " + state);
        return state == Defaults.State.ServiceMqtt.INITIAL 
                || state == Defaults.State.ServiceMqtt.DISCONNECTED 
                || state == Defaults.State.ServiceMqtt.DISCONNECTED_USERDISCONNECT 
                || state == Defaults.State.ServiceMqtt.DISCONNECTED_WAITINGFORINTERNET 
                || state == Defaults.State.ServiceMqtt.DISCONNECTED_ERROR;
    }

    
    /**
     * Init the MQTT client and set the callback for receiving messages.
     * @category CONNECTION HANDLING
     */
    private void initMqttClient() {
        Log.v(LOG_TAG, "initMqttClient");

        if (mqttClient != null) {
            return;
        }

        try {
            String brokerAddress = sharedPreferences.getString(Defaults.SETTINGS_KEY_BROKER_HOST,
                    Defaults.VALUE_BROKER_HOST);
            String brokerPort = sharedPreferences.getString(Defaults.SETTINGS_KEY_BROKER_PORT,
                    Defaults.VALUE_BROKER_PORT);
            if(brokerPort.equals(""))
                brokerPort = Defaults.VALUE_BROKER_PORT;
            String prefix = getBrokerSecurityMode() == Defaults.VALUE_BROKER_SECURITY_NONE ? "tcp" : "ssl";
            String serverURI = prefix + "://" + brokerAddress + ":" + brokerPort;
            Log.v(LOG_TAG, "initMqttClient: serverURI=" + serverURI);
            mqttClient = new MqttClient(serverURI, ActivityPreferences.getDeviceName(), null);
            mqttClient.setCallback(this);
        } catch (MqttException e) {
            // something went wrong!
            mqttClient = null;
            changeState(e);
        }
    }

    private int getBrokerSecurityMode() {
        return sharedPreferences.getInt(Defaults.SETTINGS_KEY_BROKER_SECURITY,
                Defaults.VALUE_BROKER_SECURITY_NONE);
    }

    //
    private javax.net.ssl.SSLSocketFactory getSSLSocketFactory() throws CertificateException,
            KeyStoreException, NoSuchAlgorithmException, IOException, KeyManagementException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        // From https://www.washington.edu/itconnect/security/ca/load-der.crt
        InputStream caInput = new BufferedInputStream(new FileInputStream(
                sharedPreferences.getString(Defaults.SETTINGS_KEY_BROKER_SECURITY_SSL_CA_PATH, "")));
        java.security.cert.Certificate ca;
        try {
            ca = cf.generateCertificate(caInput);
        } finally {
            caInput.close();
        }

        // Create a KeyStore containing our trusted CAs
        String keyStoreType = KeyStore.getDefaultType();
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        keyStore.load(null, null);
        keyStore.setCertificateEntry("ca", ca);

        // Create a TrustManager that trusts the CAs in our KeyStore
        String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
        tmf.init(keyStore);

        // Create an SSLContext that uses our TrustManager
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), null);

        return context.getSocketFactory();
    }

    private boolean connect() {
        Log.v(LOG_TAG, "connect");
        workerThread = Thread.currentThread(); // We connect, so we're the
                                               // worker thread
        error = null; // clear previous error on connect
        initMqttClient();

        try {
            changeState(Defaults.State.ServiceMqtt.CONNECTING);
            MqttConnectOptions options = new MqttConnectOptions();

            switch (ActivityPreferences.getBrokerAuthType()) {
                case Defaults.VALUE_BROKER_AUTH_ANONYMOUS:                    
                    break;

                case Defaults.VALUE_BROKER_AUTH_BROKERUSERNAME:
                    options.setPassword(sharedPreferences.getString(
                            Defaults.SETTINGS_KEY_BROKER_PASSWORD, "").toCharArray());

                    options.setUserName(ActivityPreferences.getBrokerUsername());
                    break;
            }

            if (getBrokerSecurityMode() == Defaults.VALUE_BROKER_SECURITY_SSL_CUSTOMCACRT)
                options.setSocketFactory(this.getSSLSocketFactory());

            //setWill(options);
            options.setKeepAliveInterval(keepAliveSeconds);
            options.setConnectionTimeout(10);
            options.setCleanSession(false);

            mqttClient.connect(options);

            Log.d(LOG_TAG, "connect: No error during connect");
            changeState(Defaults.State.ServiceMqtt.CONNECTED);

            return true;
        } catch (Exception e) { // Catch paho and socket factory exceptions
            Log.e(LOG_TAG, e.toString());
            changeState(e);
            return false;
        }
    }


    private void onConnect() {
        if (!isConnected())
            Log.e(LOG_TAG, "onConnect: !isConnected");
                
        // Establish observer to monitor wifi and radio connectivity 
        if (netConnReceiver == null) {
            netConnReceiver = new NetworkConnectionIntentReceiver();
            registerReceiver(netConnReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        }

        // Establish ping sender
        if (pingSender == null) {
            pingSender = new PingSender();
            registerReceiver(pingSender, new IntentFilter(Defaults.INTENT_ACTION_PUBLICH_PING));
        }
        
        scheduleNextPing();
        
        try {
            mqttClient.subscribe("/devices/+/meta/#", 0);
            mqttClient.subscribe("/devices/+/controls/+/meta/#", 0);
            mqttClient.subscribe("/devices/+/controls/+", 0);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /**
     * Disconnect from MQTT broker and set state.
     * @param fromUser True, if disconnect by user request
     */
    public void disconnect(boolean fromUser) {
        Log.v(LOG_TAG, "disconnect");
        
        if (isConnecting()) // throws MqttException.REASON_CODE_CONNECT_IN_PROGRESS when disconnecting while connect is in progress.
            return;
        
        if (fromUser)
            changeState(Defaults.State.ServiceMqtt.DISCONNECTED_USERDISCONNECT);
        else
            changeState(Defaults.State.ServiceMqtt.DISCONNECTED);

        try {
            if (netConnReceiver != null) {
                unregisterReceiver(netConnReceiver);
                netConnReceiver = null;
            }

            cancelNextPing();
            if (pingSender != null) {
                unregisterReceiver(pingSender);
                pingSender = null;
            }
        } catch (Exception eee) {
            Log.e(LOG_TAG, "disconnect: Unregister failed", eee);
        }

        try {
            if (isConnected())
                mqttClient.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mqttClient = null;

            if (workerThread != null) {
                workerThread.interrupt();
            }
        }
    }

    @SuppressLint("Wakelock")
    // Lint check derps with the wl.release() call.
    @Override
    public void connectionLost(Throwable t) {
        Log.e(LOG_TAG, "error: " + t.toString());
        // we protect against the phone switching off while we're doing this
        // by requesting a wake lock - we request the minimum possible wake
        // lock - just enough to keep the CPU running until we've finished
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MQTT");
        wl.acquire();

        if (!isOnline(true)) {
            changeState(Defaults.State.ServiceMqtt.DISCONNECTED_WAITINGFORINTERNET);
        } else {
            changeState(Defaults.State.ServiceMqtt.DISCONNECTED);
            scheduleNextPing();
        }
        wl.release();
    }

    public void reconnect() {
        disconnect(true);
        doStart(-1);
    }

    public void onEvent(Events.StateChanged.ServiceMqtt event) {
        if (event.getState() == Defaults.State.ServiceMqtt.CONNECTED)
            publishDeferrables();
    }

    private void changeState(Exception e) {
        error = e; 
        changeState(Defaults.State.ServiceMqtt.DISCONNECTED_ERROR, e);
    }

    private void changeState(Defaults.State.ServiceMqtt newState) {
        changeState(newState, null);
    }

    private void changeState(Defaults.State.ServiceMqtt newState, Exception e) {
        Log.d(LOG_TAG, "changeState: ServiceMqtt state changed to: " + newState);
        state = newState;
        EventBus.getDefault().postSticky(new Events.StateChanged.ServiceMqtt(newState, e));
    }

    private boolean isOnline(boolean shouldCheckIfOnWifi) {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();

        return netInfo != null
                && netInfo.isAvailable()
                && netInfo.isConnected();
    }

    public boolean isConnected() {
        return ((mqttClient != null) && (mqttClient.isConnected()));
    }
    
    public static boolean isErrorState(Defaults.State.ServiceMqtt state) {
        return state == Defaults.State.ServiceMqtt.DISCONNECTED_ERROR;
    }
    
    public boolean hasError() {
        return error != null;
    }

    public boolean isConnecting() {
        return (mqttClient != null) && state == Defaults.State.ServiceMqtt.CONNECTING;
    }

    private boolean isBackgroundDataEnabled() {
        return isOnline(false);
    }

    /**
     * @category MISC
     */
    public static ServiceMqtt getInstance() {
        return instance;
    }

    @Override
    public void onDestroy() {
        // disconnect immediately
        disconnect(false);

        changeState(Defaults.State.ServiceMqtt.DISCONNECTED);

        super.onDestroy();
    }

    public static Defaults.State.ServiceMqtt getState() {
        return state;
    }
    
    public static String getErrorMessage() {
        Exception e = getInstance().error;

        if (getInstance() != null && getInstance().hasError() && e.getCause() != null)
            return "Error: " + e.getCause().getLocalizedMessage();
        else
            return "Error: " + getInstance().getString(R.string.na);
    }
    
    public static String getStateAsString() {
        return Defaults.State.toString(state);
    }
    
    public static String stateAsString(Defaults.State.ServiceMqtt state) {
        return Defaults.State.toString(state);
    }

    private void deferPublish(final DeferredPublishable p) {
        p.wait(deferredPublishables, new Runnable() {

            @Override
            public void run() {
                deferredPublishables.remove(p);
                if (!p.isPublishing()) //might happen that the publish is in progress while the timeout occurs.
                    p.publishFailed();
            }
        });
    }

    public void publish(String topic, String payload) {
        publish(topic, payload, false, 0, 0, null, null);
    }

    public void publish(String topic, String payload, boolean retained) {
        publish(topic, payload, retained, 0, 0, null, null);
    }

    public void publish(final String topic, final String payload,
                        final boolean retained, final int qos, final int timeout,
                        final MqttPublish callback, final Object extra) {
        publish(new DeferredPublishable(topic, payload, retained, qos, timeout, callback, extra));
                
    }

    private void publish(final DeferredPublishable p) {
        pubHandler.post(new Runnable() {
            
            @Override
            public void run() {
            if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
                Log.e(LOG_TAG, "publish: PUB ON MAIN THREAD");
            }

            if (!isOnline(false) || !isConnected()) {
                Log.d(LOG_TAG, "publish: pub deferred");

                deferPublish(p);
                doStart(1);
                return;
            }

            try {
                p.publishing();
                mqttClient.getTopic(p.getTopic()).publish(p);
                p.publishSuccessfull();
            } catch (MqttException e) {
                Log.e(LOG_TAG, "publish: " + e.getMessage());
                e.printStackTrace();
                p.cancelWait();
                p.publishFailed();
            }
            }
        });
    }

    private void publishDeferrables() {        
        for (Iterator<DeferredPublishable> iter = deferredPublishables.iterator(); iter.hasNext(); ) {
            DeferredPublishable p = iter.next();
            iter.remove();
            publish(p);
        }
    }

    private class DeferredPublishable extends MqttMessage {
        private Handler timeoutHandler;
        private MqttPublish callback;
        private String topic;
        private int timeout = 0;
        private boolean isPublishing;
        private Object extra;
        
        public DeferredPublishable(String topic, String payload, boolean retained, int qos,
                int timeout, MqttPublish callback, Object extra) {
            
            super(payload.getBytes());
            this.setQos(qos);
            this.setRetained(retained);
            this.extra = extra;
            this.callback = callback;
            this.topic = topic;
            this.timeout = timeout;
        }

        public void publishFailed() {
            if (callback != null)
                callback.publishFailed(extra);
        }

        public void publishSuccessfull() {
            if (callback != null)
                callback.publishSuccessfull(extra);
            cancelWait();
        }

        public void publishing() {
            isPublishing = true;
            if (callback != null)
                callback.publishing(extra);
        }
        
        public boolean isPublishing(){
            return isPublishing;
        }

        public String getTopic() {
            return topic;
        }
        
        public void cancelWait() {
            if (timeoutHandler != null)
                this.timeoutHandler.removeCallbacksAndMessages(this);
        }

        public void wait(LinkedList<DeferredPublishable> queue, Runnable onRemove) {
            if (timeoutHandler != null) {
                Log.d(LOG_TAG, "wait: This DeferredPublishable already has a timeout set");
                return;
            }

            // No need signal waiting for timeouts of 0. The command will be
            // failed right away
            if (callback != null && timeout > 0)
                callback.publishWaiting(extra);

            queue.addLast(this);
            this.timeoutHandler = new Handler();
            this.timeoutHandler.postDelayed(onRemove, timeout * 1000);
        }
    }


    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {}

    @Override
    protected void onStartOnce() {}

    private class NetworkConnectionIntentReceiver extends BroadcastReceiver {
        @Override
        @SuppressLint("Wakelock")
        public void onReceive(Context ctx, Intent intent)
        {
            Log.v(LOG_TAG, "NetworkConnectionIntentReceiver: onReceive");
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MQTTitude");
            wl.acquire();

            if (isOnline(true) && !isConnected() && !isConnecting()) {
                Log.v(LOG_TAG, "NetworkConnectionIntentReceiver: onReceive: triggering doStart(-1)");
                // TODO: Why is this set to 1 and not to -1?
                doStart(1);
            }
            wl.release();
        }
    }

    public void messageArrived(String topic, MqttMessage message) throws Exception {
        // we protect against the phone switching off while we're doing this
        // by requesting a wake lock - we request the minimum possible wake
        // lock - just enough to keep the CPU running until we've finished
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MQTT");
        wl.acquire();

        //
        // I'm assuming that all messages I receive are being sent as strings
        // this is not an MQTT thing - just me making as assumption about what
        // data I will be receiving - your app doesn't have to send/receive
        // strings - anything that can be sent as bytes is valid
        // String messageBody = new String(payloadbytes);

        // inform the app (for times when the Activity UI is running) of the
        // received message so the app UI can be updated with the new data
        String payloadStr = new String(message.getPayload());
        Log.v(LOG_TAG, "messageArrived: topic=" + topic
                + ", message=" + payloadStr);

        String[] splitTopic = topic.split("/");

        // Ensure the device for the message exists
        String deviceId = splitTopic[2];
        Device device = App.getDevice(deviceId);
        if (device == null) {
            device = new Device(deviceId, this);
            App.addDevice(device);
            device.moveToRoom(Defaults.VALUE_ROOM_NAME);
        }

        // Topic parsing
        //  /devices/$uniqueDeviceId/controls/$deviceUniqueControlId/meta/type
        // 0/      1/              2/       3/                     4/   5/   6
        if (splitTopic[3].equals("controls")) {
            String controlName = splitTopic[4];
            Control control = device.getControlWithId(controlName);

            if (control == null) {
                control = new Control(controlName, device);
                device.addControl(control);
            }
            if (splitTopic.length == 5) { // Control value
                control.setValue(payloadStr);
            } else if (splitTopic.length == 7) { // Control meta
                control.setMeta(splitTopic[6], payloadStr);
            }
        } else if (splitTopic[3].equals("meta")) {
            device.setMeta(splitTopic[4], payloadStr); // Device Meta
        }

        // receiving this message will have kept the connection alive for us, so
        // we take advantage of this to postpone the next scheduled ping
        scheduleNextPing();

        // we're finished - if the phone is switched off, it's okay for the CPU
        // to sleep now
        wl.release();
    }    

    public class PingSender extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isOnline(true) && !isConnected() && !isConnecting()) {
                Log.v(LOG_TAG, "onReceive: ping: isOnline()=" + isOnline(true)  + ", isConnected()=" + isConnected());
                doStart(-1);
            } else if (!isOnline(true)) {
                Log.d(LOG_TAG, "onReceive: ping: Waiting for network to come online again");
            } else {            
                try {
                    ping();
                } catch (MqttException e) {
                    // if something goes wrong, it should result in
                    // connectionLost
                    // being called, so we will handle it there
                    Log.e(LOG_TAG, "onReceive: ping failed - MQTT exception", e);

                    // assume the client connection is broken - trash it
                    try {
                        mqttClient.disconnect();
                    } catch (MqttPersistenceException e1) {
                        Log.e(LOG_TAG, "onReceive: disconnect failed - persistence exception", e1);
                    } catch (MqttException e2)
                    {
                        Log.e(LOG_TAG, "onReceive: disconnect failed - mqtt exception", e2);
                    }

                    // reconnect
                    Log.w(LOG_TAG, "onReceive: MqttException=" + e);
                    doStart(-1);
                }
            }
            scheduleNextPing();
        }
    }
    
    private void scheduleNextPing() {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(
                Defaults.INTENT_ACTION_PUBLICH_PING), PendingIntent.FLAG_UPDATE_CURRENT);

        Calendar wakeUpTime = Calendar.getInstance();
        wakeUpTime.add(Calendar.SECOND, keepAliveSeconds);

        AlarmManager aMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
        aMgr.set(AlarmManager.RTC_WAKEUP, wakeUpTime.getTimeInMillis(), pendingIntent);
    }

    private void cancelNextPing() {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(
                Defaults.INTENT_ACTION_PUBLICH_PING), PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager aMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
        aMgr.cancel(pendingIntent);
    }

    private void ping() throws MqttException {
        MqttTopic topic = mqttClient.getTopic("$SYS/keepalive");
        MqttMessage message = new MqttMessage();
        message.setRetained(false);
        message.setQos(1);
        message.setPayload(new byte[] {
            0
        });

        try {
            topic.publish(message);
        } catch (org.eclipse.paho.client.mqttv3.MqttPersistenceException e) {
            e.printStackTrace();
        } catch (org.eclipse.paho.client.mqttv3.MqttException e) {
            throw new MqttException(e);
        }
    }

}
