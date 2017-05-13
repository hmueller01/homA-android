package st.alr.homA.support;

import java.lang.ref.WeakReference;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public abstract class ServiceBindable extends Service {
    private final String TAG  = "ServiceBindable";
    protected boolean mStarted;
    protected ServiceBinder mBinder;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(this.TAG, "onCreate()");
        mBinder = new ServiceBinder(this);
    }
    
    abstract protected void onStartOnce();

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(this.TAG, "onBind()");
        if (!mStarted) {
            mStarted = true;
            onStartOnce();
        }
        return mBinder;
    }
    
    public class ServiceBinder extends Binder {
        private WeakReference<ServiceBindable> mService;

        public ServiceBinder(ServiceBindable serviceBindable) {
            mService = new WeakReference<ServiceBindable>(serviceBindable);
        }

        public ServiceBindable getService() {
            return mService.get();
        }
        
        public void close() {
            mService = null;
        }
    }
    
    @Override
    public void onDestroy() {
        Log.v(this.TAG, "onDestroy()");
        if (mBinder != null) {
            mBinder.close();
            mBinder = null;
        }
        super.onDestroy();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(this.TAG, "onStartCommand()");
        if (!mStarted) {
            mStarted = true;
            onStartOnce();
        }
        
        return Service.START_STICKY;
    }

}
