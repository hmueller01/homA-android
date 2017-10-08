
package st.alr.homA.model;

import st.alr.homA.support.DeviceAdapter;
import st.alr.homA.support.ValueSortedMap;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Class Room keeps all the devices in a room.
 */
public class Room implements Comparable<Room> {
    private String mId;
    private DeviceAdapter mDevices;
    private Handler mUiThreadHandler;

    public Room(Context context, String id) {
        mId = id;
        mUiThreadHandler = new Handler(context.getMainLooper());

        mDevices = new DeviceAdapter(context);
        mDevices.setMap(new ValueSortedMap<String, Device>());
    }

    public String getId() {
        return mId;
    }

    public Device getDevice(String id) {
        return (Device) mDevices.getItem(id);
    }
    
    public Device getDevice(int position) {
        return (Device) mDevices.getItem(position);
    }

    @Override
    public String toString() {
        return getId();
    }

    public void addDevice(final Device device) {
        final Room room = this;
        Runnable r  = new Runnable() {
            @Override
            public void run() {
                Log.v(this.toString(), "Adding " + device.getName() + " to room " + room.getId());
                mDevices.addItem(device);
            }
        };
        runOnUiThread(r);
    }

    public void removeDevice(final Device device) {
        final Room room = this;
        Runnable r  = new Runnable() {
            @Override
            public void run() {
                Log.v(this.toString(), "Removing " + device.getName() + " from room " + room.getId());
                mDevices.removeItem(device);
            }
        };
        runOnUiThread(r);
    }

    @Override
    public int compareTo(Room another) {
        return this.toString().compareToIgnoreCase(another.toString());
    }
    
    public int getDeviceCount(){
        return mDevices.getItemCount();
    }
    
    public DeviceAdapter getAdapter(){
        return mDevices;
    }

    private void runOnUiThread(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper())
            r.run();
        else
            mUiThreadHandler.post(r);
    }
}
