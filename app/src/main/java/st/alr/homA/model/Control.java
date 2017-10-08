
package st.alr.homA.model;

import android.support.annotation.NonNull;

import java.util.HashMap;

import st.alr.homA.support.ValueChangedObserver;

/**
 * Class Control keeps all the data of a control like id, device, topic, value and meta data.
 */
public class Control implements Comparable<Control> {
    private ValueChangedObserver mObserver;
    private ValueChangedObserver mObserverDevice; // TODO: Mr - Test for control value in room view
    private Device mDevice;
    private String mValue;
    private String mTopic;
    private String mId;
    private HashMap<String, String> mMeta;

    public Control(String id, Device device) {
        mId = id;
        mValue = "0";
        mTopic = "/devices/"+ device.getDeviceId() + "/controls/" + id + "/on";
        mDevice = device;
        mMeta = new HashMap<>();
    }

    public String getValue() {
        return mValue;
    }

    public void setValue(String value) {
        mValue = value;
        if (mObserver != null) {
            mObserver.onValueChange(this, value);
        }
        // TODO: Mr - Test for control value in room view
        if (mObserverDevice != null) {
            mObserverDevice.onValueChange(this, value);
        }
    }

    public String getMeta(String key, String def) {
        String s = mMeta.get(key);
        return (s != null && (!s.equals(""))) ? s : def;
    }

    public HashMap<String, String> getMeta() {
        return mMeta;
    }

    public void setMeta(String key, String value) {
        if (!value.equals(""))
            mMeta.put(key, value);
        else
            mMeta.remove(key);
        if (key.equals("order")) {
            mDevice.sortControls();
        }
    }

    public String getTopic() {
        return mTopic;
    }

    // Returns a friendly name shown in the user interface. For now this is the id
    public String getName() {
        return mId;
    }

    @Override
    public String toString() {
        return mId;
    }

    public void setValueChangedObserver(ValueChangedObserver observer) {
        this.mObserver = observer;
    }

    // TODO: Mr - Test for control value in room view
    public void setValueChangedObserverDevice(ValueChangedObserver observer) {
        this.mObserverDevice = observer;
    }

    public void removeValueChangedObserver() {
        mObserver = null;
    }

    // TODO: Mr - Test for control value in room view
    public void removeValueChangedObserverDevice() {
        mObserverDevice = null;
    }

    public Integer getOrder() {
        try {
            return Integer.parseInt(this.getMeta("order", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public int compareTo(@NonNull Control other) {
        int result = 0;
        
        if (getOrder() > other.getOrder()) {
            result = 1;
        } else if (getOrder() < other.getOrder()) {
            result = -1;
        }
        return result;
    }

}
