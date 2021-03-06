package st.alr.homA;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.widget.ListAdapter;

import java.util.ArrayList;
import java.util.HashMap;

import de.greenrobot.event.EventBus;
import st.alr.homA.model.Device;
import st.alr.homA.model.Quickpublish;
import st.alr.homA.model.Room;
import st.alr.homA.services.ServiceBackgroundPublish;
import st.alr.homA.services.ServiceMqtt;
import st.alr.homA.support.Defaults;
import st.alr.homA.support.Events;
import st.alr.homA.support.RoomAdapter;

public class App extends Application {
    private final String LOG_TAG = App.class.getSimpleName();
    private static App instance;
    private NotificationCompat.Builder notificationBuilder;
    private SharedPreferences sharedPreferences;
    private RoomAdapter rooms;
    private HashMap<String, Device> devices;

    private NotificationManager notificationManager;
    private Handler uiThreadHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(LOG_TAG, "onCreate()");
        //TODO Bugsnag.register(this, Defaults.BUGSNAG_API_KEY);
        //TODO Bugsnag.setNotifyReleaseStages("production", "testing");
        instance = this;
        uiThreadHandler = new Handler(getMainLooper());

        devices = new HashMap<>();
        rooms = new RoomAdapter(this);

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.OnSharedPreferenceChangeListener preferencesChangedListener;
        preferencesChangedListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreference, String key) {
                if (key.equals(Defaults.SETTINGS_KEY_NOTIFICATION_ENABLED) ||
                        key.equals(Defaults.SETTINGS_KEY_QUICKPUBLISH_NOTIFICATION))
                    handleNotification();
            }
        };
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferencesChangedListener);
        handleNotification();
        EventBus.getDefault().register(this);
    }

    public Room getRoom(String id) {
        Log.v(LOG_TAG, "getRoom(): request for " + id + " in " + rooms.getMap().toString());
        return (Room) rooms.getItem(id);
    }

    public Room getRoom(int index) {
        return (Room) rooms.getItem(index);
    }

    public Integer getRoomCount() {
        return rooms.getCount();
    }

    public void addRoom(final Room room) {
        Runnable r = new Runnable() {
            
            @Override
            public void run() {
                rooms.addItem(room);
                EventBus.getDefault().post(new Events.RoomAdded(room));
            }
        };
        
        if (Looper.myLooper() == Looper.getMainLooper())
               r.run();
        else
            uiThreadHandler.post(r);
    }

    public void removeRoom(final Room room) {
        Runnable r  = new Runnable() {
           
            @Override
            public void run() {
                rooms.removeItem(room);
            }
        };
        
        if (Looper.myLooper() == Looper.getMainLooper())
            r.run();
        else
            uiThreadHandler.post(r);
    }

    public void removeAllRooms() {
        uiThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                rooms.clearItems();
            }
        });
    }

    public ListAdapter getRoomListAdapter() {
        return rooms;
    }

    public static Device getDevice(String id) {
        if (instance == null)
            return null;
        return instance.devices.get(id);
    }

    public static void addDevice(Device device) {
        if (instance != null)
            instance.devices.put(device.toString(), device);
    }

    public static App getInstance() {
        return instance;
    }

    public static Context getContext() {
        return getInstance();
    }

    public void onEventMainThread(Events.StateChanged.ServiceMqtt event) {
        if (event.getState() == Defaults.State.ServiceMqtt.DISCONNECTED_WAITINGFORINTERNET
                || event.getState() == Defaults.State.ServiceMqtt.DISCONNECTED_USERDISCONNECT
                || event.getState() == Defaults.State.ServiceMqtt.DISCONNECTED_DATADISABLED
                || event.getState() == Defaults.State.ServiceMqtt.DISCONNECTED) {
            removeAllRooms();
            devices.clear();
        }
        updateNotification();
    }

    /**
     * @category NOTIFICATION HANDLING
     */
    private void handleNotification() {
        Log.v(LOG_TAG, "handleNotification()");
        notificationManager.cancel(Defaults.NOTIFCATION_ID);

        if (sharedPreferences.getBoolean(Defaults.SETTINGS_KEY_NOTIFICATION_ENABLED, Defaults.VALUE_NOTIFICATION_ENABLED))
            createNotification();
    }

    private void createNotification() {
        notificationBuilder = new NotificationCompat.Builder(this);
        Intent resultIntent = new Intent(this, ActivityMain.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(ActivityMain.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        notificationBuilder.setContentIntent(resultPendingIntent);
        setNotificationQuickpublishes();
        updateNotification();
    }

    public void updateNotification() {
        notificationBuilder.setContentTitle(getResources().getString(R.string.appName));
        notificationBuilder
                .setSmallIcon(R.drawable.homamonochrome)
                .setOngoing(true)
                .setContentText(ServiceMqtt.getStateAsString())
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setWhen(0);
        notificationManager.notify(Defaults.NOTIFCATION_ID, notificationBuilder.build());
    }
    
    private void setNotificationQuickpublishes() {
        ArrayList<Quickpublish> qps = Quickpublish.fromPreferences(this, Defaults.SETTINGS_KEY_QUICKPUBLISH_NOTIFICATION);
        for (int i = 0; i < qps.size() && i < Defaults.NOTIFICATION_MAX_ACTIONS; i++) {
            Intent pubIntent = new Intent().setClass(this, ServiceBackgroundPublish.class);
            pubIntent.setAction("st.alr.homA.action.QUICKPUBLISH");
            pubIntent.putExtra("qp", "[" + qps.get(i).toJsonString() + "]");
            PendingIntent p = PendingIntent.getService(this, 0, pubIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            notificationBuilder.addAction(0, qps.get(i).getName(), p);
        }
    }

    public void cancelNotification() {
    	notificationManager.cancelAll();
    }

    @SuppressLint("HardwareIds")
    public static String getAndroidId() {
        return Secure.getString(instance.getContentResolver(), Secure.ANDROID_ID);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.v(LOG_TAG, "onLowMemory()");
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.v(LOG_TAG, "onTrimMemory()");
    }
}
