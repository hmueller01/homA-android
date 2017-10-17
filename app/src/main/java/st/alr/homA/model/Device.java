
package st.alr.homA.model;

import st.alr.homA.App;
import st.alr.homA.support.Defaults;
import st.alr.homA.support.ValueChangedObserver;
import st.alr.homA.support.ValueSortedMap;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

/**
 * Class Device keeps all the controls in a device.
 */
public class Device implements Comparable<Device> {
    private String mId;
    private String mName;
    // TODO: Mr - Test for control value in room view
    private String mDeviceId; // in case of new control value this holds mId of device
    private Room mRoom;
    private ValueSortedMap<String, Control> mControls;
    private ValueChangedObserver mControlAddedObserver;
    private Context mContext;
    private final Handler mUiThreadHandler;

    public Device(String id, Context context) {
        mId = id;
        mName = null;
        mDeviceId = null;
        mControls = new ValueSortedMap<>();
        mContext = context;
        mUiThreadHandler = new Handler(context.getMainLooper());
    }

    // TODO: Mr - Test for control value in room view
    // Used to view a control on the device list view
    public Device(String deviceId, String controlName, Context context) {
        this(controlName, context); // must be called at first, otherwise mDeviceId gets lost!
        mDeviceId = deviceId;
    }

    public Room getRoom() {
        return mRoom;
    }

    private void removeFromCurrentRoom() {
        if (mRoom != null) {
            mRoom.removeDevice(this);
            if (mRoom.getDeviceCount() == 0) {
                Log.v(toString(), "Room " + mRoom.getId() + " is empty, removing it");
                App.removeRoom(mRoom);
            }
        }
    }

    public void moveToRoom(final String roomname) {
        final Device device = this;
        Runnable r  = new Runnable() {
            @Override
            public void run() {
                if (mRoom != null && mRoom.getId().equals(roomname)) // Don't move if the device is already in the target mRoom. Also prevents https://github.com/binarybucks/homA/issues/47
                    return;

                String cleanedName = (roomname != null) && !roomname.equals("") ? roomname : Defaults.VALUE_ROOM_NAME;

                Room newRoom = App.getRoom(cleanedName);
                if (newRoom == null) {
                    newRoom = new Room(mContext, cleanedName);
                    App.addRoom(newRoom);
                }

                removeFromCurrentRoom();
                newRoom.addDevice(device);

                mRoom = newRoom;
            }
        };
        runOnUiThread(r);
    }

    public String getId() {
        return mId;
    }

    public String getName() {
        return (mName != null) && !mName.equals("") ? mName : mId;
    }

    public void setName(String name) {
        //mRoom.removeDevice(this);
        mName = name;
        //mRoom.addDevice(this);
    }

    // TODO: Mr - Test for control value in room view
    public boolean isControl() {
        return (mDeviceId != null);
    }

    public String getDeviceId() {
        if (isControl())
            return mDeviceId;
        else
            return mId;
    }

    public String getDeviceName() {
        if (isControl()) {
            Device device = App.getDevice(mDeviceId);
            if (device != null)
                return device.getName();
            else
                return "<device not found>";
        } else {
            return getName();
        }
    }

    public Control getControlWithId(String id) {
        return mControls.get(id);
    }

    public void addControl(Control control) {
        mControls.put(control.toString(), control);
        if (mControlAddedObserver != null) {
            mControlAddedObserver.onValueChange(this, control);
        }
    }

    public void setControlAddedObserver(ValueChangedObserver observer) {
        mControlAddedObserver = observer;
    }

    public void removeControlAddedObserver() {
        mControlAddedObserver = null;
    }

    public ValueSortedMap<String, Control> getControls() {
        return mControls;
    }

    @Override
    public String toString() {
        return mId;
    }

    @Override
    public int compareTo(@NonNull Device another) {
        return getName().compareToIgnoreCase(another.getName());
    }

    public void setMeta(String key, String value) {
        if (key.equals("room"))
            this.moveToRoom(value);
        else if (key.equals("name"))
            this.setName(value);
    }

    public void sortControls() {
        mControls.sortDataset();
    }

    private void runOnUiThread(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper())
            r.run();
        else
            mUiThreadHandler.post(r);
    }

}
