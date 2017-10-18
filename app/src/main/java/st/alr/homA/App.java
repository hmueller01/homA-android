package st.alr.homA;

import android.app.Application;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

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
    private final static String LOG_TAG = App.class.getSimpleName();
    private static App mInstance;
    private Handler mUiThreadHandler;
    private RoomAdapter mRooms;
    private HashMap<String, Device> mDevices;
    private NotificationCompat.Builder mNotificationBuilder;
    private NotificationManager mNotificationManager;
    private SharedPreferences mSharedPreferences;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(LOG_TAG, "onCreate()");
        //Bugsnag.register(this, Defaults.BUGSNAG_API_KEY);
        //Bugsnag.setNotifyReleaseStages("production", "testing");
        mInstance = this;
        mUiThreadHandler = new Handler(getMainLooper());

        mRooms = new RoomAdapter(this);
        mDevices = new HashMap<>();

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        handleNotification();
        EventBus.getDefault().register(this);
    }

    public static App getInstance() {
        return mInstance;
    }

    public static Context getContext() {
        return mInstance.getApplicationContext();
    }

    public static Room getRoom(String id) {
        Log.v(LOG_TAG, "getRoom(): request for " + id + " in " + mInstance.mRooms.getMap().toString());
        return (Room) mInstance.mRooms.getItem(id);
    }

    public static Room getRoom(int index) {
        return (Room) mInstance.mRooms.getItem(index);
    }

    public static Integer getRoomCount() {
        return mInstance.mRooms.getItemCount();
    }

    public static void addRoom(final Room room) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                mInstance.mRooms.addItem(room);
                EventBus.getDefault().post(new Events.RoomAdded(room));
            }
        };
        mInstance.runOnUiThread(r);
    }

    public static void removeRoom(final Room room) {
        Runnable r  = new Runnable() {
            @Override
            public void run() {
                mInstance.mRooms.removeItem(room);
            }
        };
        mInstance.runOnUiThread(r);
    }

    public static void removeAllRooms() {
        Runnable r  = new Runnable() {
            @Override
            public void run() {
                mInstance.mRooms.clearItems();
            }
        };
        mInstance.runOnUiThread(r);
    }

    public static RoomAdapter getRoomListAdapter() {
        return mInstance.mRooms;
    }

    public static Device getDevice(String id) {
        return mInstance.mDevices.get(id);
    }

    public static void addDevice(Device device) {
        mInstance.mDevices.put(device.toString(), device);
    }

    public void onEventMainThread(Events.StateChanged.ServiceMqtt event) {
        if (event.getState() == Defaults.State.ServiceMqtt.DISCONNECTED_WAITINGFORINTERNET
                || event.getState() == Defaults.State.ServiceMqtt.DISCONNECTED_USERDISCONNECT
                || event.getState() == Defaults.State.ServiceMqtt.DISCONNECTED_DATADISABLED
                || event.getState() == Defaults.State.ServiceMqtt.DISCONNECTED) {
            removeAllRooms();
            mDevices.clear();
        }
        updateNotification();
    }

    /**
     * NOTIFICATION HANDLING
     */
    public static void handleNotification() {
        mInstance.mNotificationManager.cancel(Defaults.NOTIFCATION_ID);

        if (mInstance.mSharedPreferences.getBoolean(Defaults.SETTINGS_KEY_NOTIFICATION_ENABLED, Defaults.VALUE_NOTIFICATION_ENABLED))
            mInstance.createNotification();
    }

    private void createNotification() {
        mNotificationBuilder = new NotificationCompat.Builder(this, Defaults.NOTIFCATION_CHANNEL_ID);
        Intent resultIntent = new Intent(this, ActivityMain.class);
        // Just use the same intent filters as Android uses when it launches the app
        // Thanks to https://stackoverflow.com/questions/5502427/resume-application-and-stack-from-notification
        resultIntent.setAction(Intent.ACTION_MAIN);
        resultIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        resultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(ActivityMain.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        mNotificationBuilder.setContentIntent(resultPendingIntent);
        setNotificationQuickpublishes();
        updateNotification();
    }

    private void updateNotification() {
        mNotificationBuilder.setContentTitle(getResources().getString(R.string.appName));
        mNotificationBuilder
                .setSmallIcon(R.drawable.homamonochrome)
                .setOngoing(true)
                .setContentText(ServiceMqtt.getStateAsString())
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setWhen(0);
        mNotificationManager.notify(Defaults.NOTIFCATION_ID, mNotificationBuilder.build());
    }

    private void setNotificationQuickpublishes() {
        ArrayList<Quickpublish> qps = Quickpublish.fromPreferences(this, Defaults.SETTINGS_KEY_QUICKPUBLISH_NOTIFICATION);
        for (int i = 0; i < qps.size() && i < Defaults.NOTIFICATION_MAX_ACTIONS; i++) {
            Intent pubIntent = new Intent().setClass(this, ServiceBackgroundPublish.class);
            pubIntent.setAction("st.alr.homA.action.QUICKPUBLISH");
            pubIntent.putExtra("qp", "[" + qps.get(i).toJsonString() + "]");
            PendingIntent p = PendingIntent.getService(this, 0, pubIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            mNotificationBuilder.addAction(0, qps.get(i).getName(), p);
        }
    }

    public static void cancelNotification() {
        mInstance.mNotificationManager.cancelAll();
    }

    private void runOnUiThread(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper())
            r.run();
        else
            mUiThreadHandler.post(r);
    }

}
