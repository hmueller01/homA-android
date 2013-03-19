
package st.alr.homA;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Vector;

import st.alr.homA.MqttService.MQTT_CONNECTIVITY;
import st.alr.homA.model.Control;
import st.alr.homA.model.Device;
import st.alr.homA.model.Room;
import st.alr.homA.support.DeviceMapAdapter;
import st.alr.homA.support.Events;
import st.alr.homA.support.Events.MqttConnectivityChanged;
import st.alr.homA.view.ControlView;
import de.greenrobot.event.EventBus;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;

public class MainActivity extends FragmentActivity {
    private RoomPagerAdapter roomPagerAdapter;
    private static ViewPager mViewPager;
    private static Room currentRoom;

    RelativeLayout disconnectedLayout;
    LinearLayout connectedLayout;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_settings:
                Intent intent1 = new Intent(this, SettingsActivity.class);
                startActivity(intent1);
                return true;
            case R.id.menu_nfc:
                Intent intent2 = new Intent(this, NfcWriteActivity.class);
                startActivity(intent2);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onEventMainThread(MqttConnectivityChanged event) {
        updateViewVisibility();
    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent service = new Intent(this, MqttService.class);
        startService(service);
    }

    private void updateViewVisibility() {
        if (MqttService.getConnectivity() == MQTT_CONNECTIVITY.CONNECTED) {
            connectedLayout.setVisibility(View.VISIBLE);
            disconnectedLayout.setVisibility(View.INVISIBLE);
        } else {
            connectedLayout.setVisibility(View.INVISIBLE);
            disconnectedLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateViewVisibility();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (NfcAdapter.getDefaultAdapter(this) == null
                || !NfcAdapter.getDefaultAdapter(this).isEnabled()) {
            menu.removeItem(R.id.menu_nfc);
        }

        return true;
    }
    
    

    /**
     * @category START
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        
        
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        disconnectedLayout = (RelativeLayout) findViewById(R.id.disconnectedLayout);
        connectedLayout = (LinearLayout) findViewById(R.id.connectedLayout);

        updateViewVisibility();

        roomPagerAdapter = new RoomPagerAdapter(this);
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(roomPagerAdapter);
        mViewPager.setOnPageChangeListener(new OnPageChangeListener() {
            @Override
            public void onPageSelected(int index) {
                currentRoom = App.getRoom(index);
            }

            @Override
            public void onPageScrollStateChanged(int arg0) {
            }

            @Override
            public void onPageScrolled(int arg0, float arg1, int arg2) {
            }
        });
        EventBus.getDefault().register(this);

    }



    public class RoomPagerAdapter extends PagerAdapter {
        private HashMap<String, DeviceMapAdapter> deviceMapAdapter;
        private Context context;
        private HashMap<String, View> views;
        
        public RoomPagerAdapter(Context context) {
         this.context=context;
         this.deviceMapAdapter = new HashMap<String, DeviceMapAdapter>();
         this.views = new HashMap<String, View>();

         EventBus.getDefault().register(this);
         notifyDataSetChanged();    
        }
        
        public int getItemPosition (Object object) {
            return POSITION_NONE; //TODO CHANGE
        }


        
        @Override
        public Object instantiateItem(ViewGroup container, int position) {
         final Room room = App.getRoom(position);
         View page = views.get(room.getId());

         if(page == null) {
           page = getLayoutInflater().inflate(R.layout.fragment_room, container, false);
           views.put(room.getId(), page);
           ListView lv = (ListView) page.findViewById(R.id.devices_list);

           DeviceMapAdapter m = new DeviceMapAdapter(context, room.getDevices());
           deviceMapAdapter.put(room.getId(), m);
           lv.setAdapter(m);
           m.notifyDataSetChanged();

           lv.setOnItemClickListener(new OnItemClickListener() {
               @Override
               public void onItemClick(AdapterView<?> parent, View view, int position, long it) {
                   
                   FragmentManager fm = getSupportFragmentManager();
                   FragmentTransaction ft = fm.beginTransaction();

                   fm.beginTransaction();
                   DeviceFragment d = DeviceFragment.newInstance(room.getId(), room.getDevices().values().toArray()[position].toString());
                   ft.add(d, "tag");
                   ft.commit();                  
               }
           });
         }

         container.addView(page);
         return room.getId();
        }
        
      @Override
      public CharSequence getPageTitle(int position) {
          return position<getCount() ? App.getRoom(position).getId().toUpperCase(Locale.ENGLISH) : "";
      }
        
        @Override
        public int getCount() {
         return App.getRoomCount();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return views.get(object).equals(view);
//            return view.equals(object);
        }
        
        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
         container.removeView(views.get(object));
         views.remove(object);
        }

        /**
         * @category EVENTS
         */
        public void onEventMainThread(Events.RoomAdded event) {
            Log.v(this.toString(), "Room added : " + event.getRoom().getId());
            notifyDataSetChanged();

        }

        public void onEventMainThread(Events.RoomsCleared event) {
             Log.v(this.toString(), "Rooms cleared");
            notifyDataSetChanged();
        }
            
        public void onEventMainThread(Events.RoomRemoved event) {
               Log.v(this.toString(), "Room removed: " + event.getRoom().getId());        
            notifyDataSetChanged();
        }        

        
        public void onEventMainThread(Events.DeviceAddedToRoom event) {
 Log.v(this.toString(), "DeviceAddedToRoom: " + event.getDevice().toString() + " " + event.getRoom().getId());
            DeviceMapAdapter m = deviceMapAdapter.get(event.getRoom().getId());
            if(m != null){
                m.addItem(event.getDevice());
                m.notifyDataSetChanged();
            }        }


        public void onEventMainThread(Events.DeviceRenamed event) {
Log.v(this.toString(), "DeviceRenamed: " + event.getDevice().toString());
            DeviceMapAdapter m = deviceMapAdapter.get(event.getDevice().getRoom().getId());
            if(m != null){
                m.sortDataset();
                m.notifyDataSetChanged();
            }
        }        


        public void onEventMainThread(Events.DeviceRemovedFromRoom event) {
 Log.v(this.toString(), "DeviceRemovedFromRoom: " + event.getDevice().toString() + " " + event.getRoom().toString());
            DeviceMapAdapter m = deviceMapAdapter.get(event.getRoom().getId());
            if(m != null){
                m.removeItem(event.getDevice());
                m.notifyDataSetChanged();
            }            
        }        
       
    }

    
    
    


//    public static class RoomsFragmentPagerAdapter extends  FragmentStatePagerAdapter {
//        public RoomsFragmentPagerAdapter(FragmentManager fm) {
//            super(fm);
//        }
//
//        @Override
//        public int getItemPosition(Object object) {
//            return POSITION_NONE;
//        }
//
//        @Override
//        public int getCount() {
//            return App.getRoomCount();
//        }
//        
//        
//
//        @Override
//        public Fragment getItem(int position) {
//            return null; //TODO 
//        }
//
//        @Override
//        public CharSequence getPageTitle(int position) {
//            return position<getCount() ? App.getRoom(position).getId().toUpperCase(Locale.ENGLISH) : "";
//        }
//    }
//    

    public static class DeviceFragment extends DialogFragment {
        Room room;
        Device device;

        static DeviceFragment newInstance(String roomId, String deviceId) {
            DeviceFragment f = new DeviceFragment();
            Bundle args = new Bundle();
            args.putString("roomId", roomId);
            args.putString("deviceId", deviceId);
            f.setArguments(args);
            return f;
        }
        
        public void onEventMainThread(MqttConnectivityChanged event) {
            if(event.getConnectivity() != MqttService.MQTT_CONNECTIVITY.CONNECTED) {
                Log.v(this.toString(), "Lost connection, closing currently open dialog");
                FragmentManager fragmentManager = getFragmentManager();
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                fragmentTransaction.remove(this);
                fragmentTransaction.commit();
            }            
        }
        
        public void onSaveInstanceState (Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putString("roomId", room.getId());
            outState.putString("deviceId", device.toString());
        }

        private boolean setArgs(Bundle savedInstanceState){
            Bundle b; 
            if(savedInstanceState != null)
                b = savedInstanceState;
            else
                b = getArguments();
            
            room = App.getRoom(b.getString("roomId"));
            if(room == null) {
                Log.e(this.toString(), "DeviceFragment for phantom room: "+ b.getString("roomId"));
                return false;  
            }
            
            device = room.getDevices().get(b.getString("deviceId"));
            
            return true;
        }
        
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {            
            LinearLayout outerLayout = new LinearLayout(this.getActivity());
            outerLayout.setOrientation(LinearLayout.VERTICAL);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            if(setArgs(savedInstanceState)) {
                EventBus.getDefault().register(this);
                        
                // Use the Builder class for convenient dialog construction
                builder.setTitle(device.getName());
    
                ScrollView sw = new ScrollView(this.getActivity());
    
                LinearLayout ll = new LinearLayout(this.getActivity());
                ll.setOrientation(LinearLayout.VERTICAL);
                ll.setPadding(16, 0, 16, 0);
                for (Control control : device.getControls().values()) {
                    ll.addView(getControlView(control).attachToControl(control).getLayout());
                }
    
                sw.addView(ll);
                outerLayout.addView(sw);
            }

            builder.setView(outerLayout);
            return builder.create();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = super.onCreateView(inflater, container, savedInstanceState);
            getDialog().setCanceledOnTouchOutside(true);
            return v;
        }

        
        public ControlView getControlView(Control control) {
            ControlView v = null;

            switch (control.getType()) {
                case SWITCH:
                    v = new st.alr.homA.view.SwitchControlView(getActivity());
                    break;
                case RANGE:
                    v = new st.alr.homA.view.RangeControlView(getActivity());
                    break;
                default:
                    v = new st.alr.homA.view.TextControlView(getActivity());
            }

            return v;
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            for (Control control : device.getControls().values()) {
                control.removeValueChangedObserver();
                
            }
            Log.v(this.toString(), "DeviceFragment: onDestroyView");
            EventBus.getDefault().unregister(this);

        }
        


    }
//
//    public static class RoomFragment extends Fragment {
//        Room room;
//        DeviceMapAdapter m;
//        
//        static RoomFragment newInstance(String id) {
//            Log.v("RoomFragment", "newInstance for: " + id);
//            RoomFragment f = new RoomFragment();
//            Bundle args = new Bundle();
//            args.putString("roomId", id);
//            f.setArguments(args);
//            return f;
//        }
//
//        
//        private boolean setArgs(Bundle savedInstanceState){
//            Bundle b; 
//            if(savedInstanceState != null)
//                b = savedInstanceState;
//            else
//                b = getArguments();
//            
//            room = App.getRoom(b.getString("roomId"));
//            
//            if(room == null) {
//                Log.e(this.toString(), "RoomFragment for phantom room: "+ b.getString("roomId"));
//                return false;  
//            }
//            return true;            
//        }
//  
//        @Override
//        public void onSaveInstanceState(Bundle outState) {
//            super.onSaveInstanceState(outState);
//            outState.putString("roomId", room.getId());
//        }
//
//        
//        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//            setArgs(savedInstanceState);
//            View v = inflater.inflate(R.layout.fragment_room, container, false);
//            Log.e(this.toString(), "onCreateView " + roomId);
//
//            if(room == null){
//                Log.e(this.toString(), "No room for id " + roomId);
//                return v;
//            }
//            
//            m = new DeviceMapAdapter(getActivity(), room.getDevices());
//            ListView lv = (ListView) v.findViewById(R.id.devices_list);
//            lv.setAdapter(m);
//            m.notifyDataSetChanged();
//
//            lv.setOnItemClickListener(new OnItemClickListener() {
//                @Override
//                public void onItemClick(AdapterView<?> parent, View view, int position, long it) {
//                    DeviceFragment d = DeviceFragment.newInstance(room.getId(), room.getDevices()
//                            .values().toArray()[position].toString());
//                    d.show(getFragmentManager(), "tag");
//                }
//            });
//            
//            
//            EventBus.getDefault().register(this);
//
//
//            return v;
//        }
//
//        
//        public void onEventMainThread(Events.DeviceAddedToRoom event) {
//            if(event.getRoom() != room) {
//                return;
//            }
//            Log.v(this.toString(), "DeviceAddedToRoom: " + event.getDevice().toString() + " "
//                    + event.getRoom().getId());
//            m.addItem(event.getDevice());
//            m.notifyDataSetChanged();
//        }
//
//        public void onEventMainThread(Events.DeviceRenamed event) {
//            if(event.getDevice().getRoom() != room) {
//                return;
//            }
//
//            Log.v(this.toString(), "DeviceRenamed: " + event.getDevice().toString());
//            m.sortDataset();
//            m.notifyDataSetChanged();
//
//        }
//
//        public void onEventMainThread(Events.DeviceRemovedFromRoom event) {
//            if(event.getRoom() != room) {
//                return;
//            }
//
//            Log.v(this.toString(), "DeviceRemovedFromRoom: " + event.getDevice().toString() + " "
//                    + event.getRoom().toString());
//            m.removeItem(event.getDevice());
//            m.notifyDataSetChanged();
//        }
//        
//        @Override
//        public void onDestroy(){
//            EventBus.getDefault().unregister(this);
//            super.onDestroy();
//        }
//    }
}
