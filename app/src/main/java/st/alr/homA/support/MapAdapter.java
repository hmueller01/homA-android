
package st.alr.homA.support;

import java.util.Collection;

import st.alr.homA.R;
import st.alr.homA.model.Control;
import st.alr.homA.model.Device;
import st.alr.homA.view.ViewHolderDevice;
import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public abstract class MapAdapter<K, T> extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    protected ValueSortedMap<K, T> mMap;
    protected LayoutInflater mInflater;
    
    private final int DEVICE = 0, CONTROL = 1;
    
    public MapAdapter(Context context) {
        mMap = new ValueSortedMap<>();
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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
        if (mMap.get(position) instanceof String) {
            return DEVICE;
        } else if (mMap.get(position) instanceof Control) {
            return CONTROL;
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
        RecyclerView.ViewHolder viewHolder;
        LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());

        switch (viewType) {
            case DEVICE:
                View v1 = inflater.inflate(R.layout.row_layout, viewGroup, false);
                viewHolder = new ViewHolderDevice(v1);
                break;
            case CONTROL:
            	// TODO: Set different layout and viewholder
                View v2 = inflater.inflate(R.layout.row_layout, viewGroup, false);
                viewHolder = new ViewHolderDevice(v2);
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
            case DEVICE:
                ViewHolderDevice vh1 = (ViewHolderDevice) viewHolder;
                configureViewHolder1(vh1, position);
                break;
            case CONTROL:
            	/* TODO
                ViewHolder2 vh2 = (ViewHolderControl) viewHolder;
                configureViewHolder2(vh2, position);
                */
                break;
            default:
                //RecyclerViewSimpleTextViewHolder vh = (RecyclerViewSimpleTextViewHolder) viewHolder;
                //configureDefaultViewHolder(vh, position);
                break;
        }
    }

    private void configureViewHolder1(ViewHolderDevice vh1, int position) {
        String device = ((Device) mMap.get(position)).getName();
        if (device != null) {
            vh1.getLabel1().setText(device);
        }
    }

    /* TODO
    private void configureViewHolder2(ViewHolderControl vh2) {
        vh2.getImageView().setImageResource(R.drawable.sample_golden_gate);
    }
    */
    
    //@Override
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

    //public Collection<Object> getMap() {
    //    return (Collection<Object>) mMap.values();
    public Collection<T> getMap() {
        return mMap.values();
    }
}
