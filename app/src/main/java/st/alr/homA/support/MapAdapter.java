
package st.alr.homA.support;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Collection;

import st.alr.homA.R;
import st.alr.homA.model.Control;
import st.alr.homA.model.Device;
import st.alr.homA.model.Room;
import st.alr.homA.view.ViewHolderControlRange;
import st.alr.homA.view.ViewHolderControlSwitch;
import st.alr.homA.view.ViewHolderControlText;
import st.alr.homA.view.ViewHolderDevice;
import st.alr.homA.view.ViewHolderRoom;

public abstract class MapAdapter<K, T> extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    protected ValueSortedMap<K, T> mMap;
    protected LayoutInflater mInflater;
    private final Handler mUiThreadHandler;

    private final int ROOM = 0, DEVICE = 1,
            CONTROL_SWITCH = 2, CONTROL_RANGE = 3, CONTROL_TEXT = 4;
    
    public MapAdapter(Context context) {
        mMap = new ValueSortedMap<>();
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mUiThreadHandler = new Handler(context.getMainLooper());
    }

    @SuppressWarnings("unchecked")
    public synchronized void addItem(T object) {
        mMap.put((K) object.toString(), object);
        notifyDataSetChanged();
    }
    
    public synchronized void setMap(ValueSortedMap<K, T> map) {
        mMap = map;
        notifyDataSetChanged();
    }

    public synchronized void removeItem(T object) {
        mMap.remove(object.toString());
        notifyDataSetChanged();
     }

    public synchronized void clearItems() {
        mMap.clear();        
        notifyDataSetChanged();
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mMap.size();
    }
    
    // Returns the view type of the item at position for the purposes of view recycling.
    @Override
    public int getItemViewType(int position) {
        if (mMap.get(position) instanceof Room) {
            return ROOM;
        } else if (mMap.get(position) instanceof Device) {
            if (((Device) mMap.get(position)).isControl()) {
                String controlName = ((Device) mMap.get(position)).getName();
                Control control = ((Device) mMap.get(position)).getControls().get(controlName);
                if (control != null) {
                    if (control.getMeta("type", "text").equals("switch")) {
                        return CONTROL_SWITCH;
                    } else if (control.getMeta("type", "text").equals("range")) {
                        return CONTROL_RANGE;
                    } else {
                        return CONTROL_TEXT;
                    }
                }
            } else
                return DEVICE;
        }
        return -1;
    }

    /**
     * This method creates different RecyclerView. ViewHolder objects based on the item view type.
     *
     * @param viewGroup ViewGroup container for the item
     * @param viewType type of view to be inflated
     * @return viewHolder to be inflated
     */
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View view;
        RecyclerView.ViewHolder viewHolder = null;
        LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());

        switch (viewType) {
            case ROOM:
                view = inflater.inflate(R.layout.row_layout, viewGroup, false);
                viewHolder = new ViewHolderRoom(view);
                break;
            case DEVICE:
                view = inflater.inflate(R.layout.row_layout, viewGroup, false);
                viewHolder = new ViewHolderDevice(view);
                break;
            case CONTROL_SWITCH:
                view = inflater.inflate(R.layout.row_layout_device_switch, viewGroup, false);
                viewHolder = new ViewHolderControlSwitch(view);
                break;
            case CONTROL_RANGE:
                view = inflater.inflate(R.layout.row_layout_device_range, viewGroup, false);
                viewHolder = new ViewHolderControlRange(view);
                break;
            case CONTROL_TEXT:
                view = inflater.inflate(R.layout.row_layout_device_text, viewGroup, false);
                viewHolder = new ViewHolderControlText(view);
                break;
            default:
                //View v = inflater.inflate(android.R.layout.simple_list_item_1, viewGroup, false);
                //viewHolder = new RecyclerViewSimpleTextViewHolder(v);
            	viewHolder = null;
                break;
        }
        return viewHolder;
    }

    /**
     * This method internally calls onBindViewHolder(ViewHolder, int) to update the
     * RecyclerView.ViewHolder contents with the item at the given position
     * and also sets up some private fields to be used by RecyclerView.
     *
     * @param viewHolder The type of RecyclerView.ViewHolder to populate
     * @param position Item position in the viewgroup.
     */
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        switch (viewHolder.getItemViewType()) {
            case ROOM:
                ViewHolderRoom vhRoom = (ViewHolderRoom) viewHolder;
                configureViewHolderRoom(vhRoom, position);
                break;
            case DEVICE:
                ViewHolderDevice vhDevice = (ViewHolderDevice) viewHolder;
                configureViewHolderDevice(vhDevice, position);
                break;
            case CONTROL_SWITCH:
                ViewHolderControlSwitch vhControlSwitch = (ViewHolderControlSwitch) viewHolder;
                configureViewHolderControlSwitch(vhControlSwitch, position);
                break;
            case CONTROL_RANGE:
                ViewHolderControlRange vhControlRange = (ViewHolderControlRange) viewHolder;
                configureViewHolderControlRange(vhControlRange, position);
                break;
            case CONTROL_TEXT:
                ViewHolderControlText vhControlText = (ViewHolderControlText) viewHolder;
                configureViewHolderControlText(vhControlText, position);
                break;
            default:
                //RecyclerViewSimpleTextViewHolder vh = (RecyclerViewSimpleTextViewHolder) viewHolder;
                //configureDefaultViewHolder(vh, position);
                break;
        }
    }

    private void configureViewHolderRoom(ViewHolderRoom vh, int position) {
        String text = ((Room) mMap.get(position)).getId();
        if (text != null) {
            vh.getLabel1().setText(text);
        }
    }

    private void configureViewHolderDevice(ViewHolderDevice vh, int position) {
        String text = ((Device) mMap.get(position)).getName();
        if (text != null) {
            vh.getLabel1().setText(text);
        }
    }

    private void configureViewHolderControlSwitch(final ViewHolderControlSwitch vh, int position) {
        String controlName = ((Device) mMap.get(position)).getName();
        String deviceName = ((Device) mMap.get(position)).getDeviceName();
        if ((controlName != null) && (deviceName != null)){
            vh.getLabel1().setText(deviceName);
            vh.getLabel2().setText(controlName);

            Control c = ((Device) mMap.get(position)).getControls().get(controlName);
            c.setValueChangedObserverDevice(new ValueChangedObserver() {
                @Override
                public void onValueChange(final Object sender, Object value) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            vh.setContent((Control) sender);
                        };
                    });
                }
            });
            vh.setInteractionListener(c);
            vh.setContent(c);
        }
    }

    private void configureViewHolderControlRange(final ViewHolderControlRange vh, int position) {
        String controlName = ((Device) mMap.get(position)).getName();
        String deviceName = ((Device) mMap.get(position)).getDeviceName();
        if ((controlName != null) && (deviceName != null)){
            vh.getLabel1().setText(deviceName);
            vh.getLabel2().setText(controlName);

            Control c = ((Device) mMap.get(position)).getControls().get(controlName);
            c.setValueChangedObserverDevice(new ValueChangedObserver() {
                @Override
                public void onValueChange(final Object sender, Object value) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            vh.setContent((Control) sender);
                        };
                    });
                }
            });
            vh.setControl(c);
            vh.setInteractionListener(c);
            vh.setContent(c);
        }
    }

    private void configureViewHolderControlText(final ViewHolderControlText vh, int position) {
        String controlName = ((Device) mMap.get(position)).getName();
        String deviceName = ((Device) mMap.get(position)).getDeviceName();
        if ((controlName != null) && (deviceName != null)){
            vh.getLabel1().setText(deviceName);
            vh.getLabel2().setText(controlName);

            Control c = ((Device) mMap.get(position)).getControls().get(controlName);
            c.setValueChangedObserverDevice(new ValueChangedObserver() {
                @Override
                public void onValueChange(final Object sender, Object value) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            vh.setContent((Control) sender);
                        };
                    });
                }
            });
            vh.setInteractionListener(c);
            vh.setContent(c);
        }
    }

    public synchronized Object getItem(int position) {
        return mMap.get(position);
    }

    public synchronized Object getItem(String key) {
        return mMap.get(key);
    }

    @Override
    public synchronized long getItemId(int position) {
        return 0;
    }

    public synchronized void sortDataset() {
        mMap.sortDataset();
        notifyDataSetChanged();
    }

    public Collection<T> getMap() {
        return mMap.values();
    }

    private void runOnUiThread(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper())
            r.run();
        else
            mUiThreadHandler.post(r);
    }
}
