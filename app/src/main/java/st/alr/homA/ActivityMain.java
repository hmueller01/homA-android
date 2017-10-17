
package st.alr.homA;

import android.Manifest;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;

import de.greenrobot.event.EventBus;
import st.alr.homA.model.Control;
import st.alr.homA.model.Device;
import st.alr.homA.model.Room;
import st.alr.homA.services.ServiceBackgroundPublish;
import st.alr.homA.services.ServiceMqtt;
import st.alr.homA.support.Defaults;
import st.alr.homA.support.Events;
import st.alr.homA.support.ItemClickSupport;
import st.alr.homA.support.ValueSortedMap;
import st.alr.homA.view.ControlView;
import st.alr.homA.view.ControlViewRange;
import st.alr.homA.view.ControlViewSwitch;
import st.alr.homA.view.ControlViewText;

public class ActivityMain extends FragmentActivity {
    private final String LOG_TAG = ActivityMain.class.getSimpleName();
    private DrawerLayout mDrawerLayout;
    private RecyclerView mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;   
    RelativeLayout mDisconnectedLayout;
    LinearLayout mConnectedLayout;
    private CharSequence mTitle;

    @Override
    protected void onStart() {
        super.onStart();
        Log.v(LOG_TAG, "onStart()");

        Intent service = new Intent(this, ServiceMqtt.class);
        startService(service);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(LOG_TAG, "onCreate()");

        setContentView(R.layout.activity_main);

        mDrawerLayout = findViewById(R.id.drawer_layout);
        mDrawerList = findViewById(R.id.left_drawer);

        mDisconnectedLayout = findViewById(R.id.disconnectedLayout);
        mConnectedLayout = findViewById(R.id.connectedLayout);

        updateViewVisibility();
        setActionbarTitleAppName();

        // Set the adapter for the list view
        mDrawerList.setAdapter(App.getRoomListAdapter());
        // Set layout manager to position the items
        mDrawerList.setLayoutManager(new LinearLayoutManager(this));
        ItemClickSupport.addTo(mDrawerList).setOnItemClickListener(
                new ItemClickSupport.OnItemClickListener() {
                    @Override
                    public void onItemClicked(RecyclerView recyclerView, int position, View v) {
                        selectRoom(App.getRoom(position));
                        mDrawerLayout.closeDrawer(mDrawerList);
                    }
                }
        );
        mDrawerToggle = new ActionBarDrawerToggle(this, /* host Activity */
                mDrawerLayout,      /* DrawerLayout object */
                //R.drawable.ic_navigation_drawer,  /* nav drawer icon to replace 'Up' caret */
                //new Toolbar(this),  /* The toolbar to use if you have an independent Toolbar */
                R.string.na,  /* "open drawer" description */
                R.string.na  /* "close drawer" description */) {
            /** Called when a drawer has settled in a completely closed state. */
            @Override
            public void onDrawerClosed(View view) {
                setActionbarTitle();
            }

            /** Called when a drawer has settled in a completely open state. */
            @Override
            public void onDrawerOpened(View drawerView) {
                setActionbarTitleAppName();
            }
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerToggle.setDrawerIndicatorEnabled(true);
        mDrawerToggle.syncState();
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }

        Room selected =  App.getRoom(PreferenceManager.getDefaultSharedPreferences(this).getString("selectedRoomId", ""));
        if (selected != null)
            selectRoom(selected);

        // check if we have permission to read from external storage
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)	!=
                PackageManager.PERMISSION_GRANTED) {
            // permission is not granted, ask for permission and wait for
            // callback method onRequestPermissionsResult gets the result of the request.
            // PERMISSION_REQUEST_READ_EXTERNAL_STORAGE is an app-defined int constant.
            // requires API 16
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    Defaults.PERMISSION_REQUEST_READ_EXTERNAL_STORAGE);
        }

        EventBus.getDefault().register(this);
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
//        if (NfcAdapter.getDefaultAdapter(this) == null
//                || !NfcAdapter.getDefaultAdapter(this).isEnabled()) {
//            menu.removeItem(R.id.menu_nfc);
//        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent i;
        int itemId = item.getItemId();

        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        // Handle your other action bar items...
        if (itemId == R.id.menu_settings) {
            i = new Intent(this, ActivityPreferences.class);
            startActivity(i);
            return true;
        } else if (itemId == R.id.menu_exit) {
            finishAffinity(); // requires API 16
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    public void onEventMainThread(Events.StateChanged.ServiceMqtt event) {
        updateViewVisibility();
    }

    public void onEventMainThread(Events.RoomAdded e) {
        if (e.getRoom().getId().equals(PreferenceManager.getDefaultSharedPreferences(this).getString("selectedRoomId", ""))) {
            selectRoom(e.getRoom());
        } else {
            Log.v(LOG_TAG, "selected "+ PreferenceManager.getDefaultSharedPreferences(this).getString("selectedRoomId", ""));
            Log.v(LOG_TAG, "room " +e.getRoom().getId());
        }
    }

    @Override
    protected void onDestroy() {
        Log.v(LOG_TAG, "onDestroy()");
        // disconnect from MQTT broker, if app is terminated
        stopService(new Intent(this, ServiceMqtt.class));
        stopService(new Intent(this, ServiceBackgroundPublish.class));

        super.onDestroy();
    }

    private void updateViewVisibility() {
        if (ServiceMqtt.getState() == Defaults.State.ServiceMqtt.CONNECTED) {
            Log.v(LOG_TAG, "Showing connected layout");
            mConnectedLayout.setVisibility(View.VISIBLE);
            mDisconnectedLayout.setVisibility(View.INVISIBLE);
            setActionbarTitle();
        } else {
            Log.v(LOG_TAG, "Showing disconnected layout");
            mConnectedLayout.setVisibility(View.INVISIBLE);
            mDisconnectedLayout.setVisibility(View.VISIBLE);
            setActionbarTitleAppName();
        }
    }

    protected void setActionbarTitleAppName() {
        String appName = getString(R.string.appName);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            String abTitle = (String) actionBar.getTitle();
            if (abTitle != null && !abTitle.equals(appName))
                mTitle = abTitle;
            actionBar.setTitle(appName);
        }
    }

    protected void setActionbarTitle(String t){
        Log.v(LOG_TAG, "setActionbarTitle() with parameter to " + t);
        ActionBar actionBar = getActionBar();
        if (actionBar != null)
            actionBar.setTitle(t);
        mTitle = t;
    }
    
    protected void setActionbarTitle() {
        Log.v(LOG_TAG, "setActionbarTitle() to " + mTitle);
        if (mTitle != null)
            setActionbarTitle(mTitle.toString());
        else 
            setActionbarTitleAppName();
    }

    private void selectRoom(Room r) {
        Log.v(LOG_TAG, "selecting " + r.getId());
        Fragment f = RoomFragment.newInstance(r);
        
        // Insert the fragment by replacing any existing fragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                       .replace(R.id.content_frame, f)
                       .commit();
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString("selectedRoomId", r.getId()).commit();
        Log.v(LOG_TAG, "selected2 "+ PreferenceManager.getDefaultSharedPreferences(this).getString("selectedRoomId", ""));

        setActionbarTitle(r.getId());
    }

    /** ***************************************************************************
     * This Fragment class lists/shows all the devices in a room.
     */
    public static class RoomFragment extends Fragment {
        private final String LOG_TAG = RoomFragment.class.getSimpleName();
        private Room mRoom;

        public static RoomFragment newInstance(Room r) {
            RoomFragment f = new RoomFragment();
            Bundle args = new Bundle();
            args.putString("roomId", r.getId());
            f.setArguments(args);

            return f;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mRoom = App.getRoom(getArguments().getString("roomId"));
            if (mRoom == null) {
                getActivity().getSupportFragmentManager().beginTransaction().remove(this).commit();
                Log.e(LOG_TAG, "Clearing fragment for removed room");
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.fragment_room, container, false);
            if (mRoom == null)
                return v;

            RecyclerView listView = v.findViewById(R.id.devices_list);
            listView.setAdapter(mRoom.getAdapter());
            // Set layout manager to position the items
            listView.setLayoutManager(new LinearLayoutManager(getContext()));
            RecyclerView.ItemDecoration itemDecoration = new
                    DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL);
            listView.addItemDecoration(itemDecoration);
            ItemClickSupport.addTo(listView).setOnItemClickListener(
                    new ItemClickSupport.OnItemClickListener() {
                        @Override
                        public void onItemClicked(RecyclerView recyclerView, int position, View v) {
                            FragmentManager fm = getFragmentManager();
                            FragmentTransaction ft = fm.beginTransaction();
                            DeviceFragment df = DeviceFragment.newInstance(mRoom.getId(), mRoom.getDevice(position).getDeviceId());
                            ft.add(df, "tag");
                            ft.commit();
                        }
                    }
            );
            return v;
        }
    }


    /** ***************************************************************************
     * This DialogFragment class lists/shows all the controls of a device
     * using an AlertDialog.
     */
    public static class DeviceFragment extends DialogFragment {
        private final String LOG_TAG = DeviceFragment.class.getSimpleName();
        private Room mRoom;
        private Device mDevice;

        static DeviceFragment newInstance(String roomId, String deviceId) {
            DeviceFragment f = new DeviceFragment();
            Bundle args = new Bundle();
            args.putString("roomId", roomId);
            args.putString("deviceId", deviceId);
            f.setArguments(args);
            return f;
        }

        public void onEventMainThread(Events.StateChanged.ServiceMqtt event) {
            if (event.getState() != Defaults.State.ServiceMqtt.CONNECTED) {
                Log.v(LOG_TAG, "Lost connection, closing currently open dialog");
                FragmentManager fragmentManager = getFragmentManager();
                FragmentTransaction fragmentTransaction = fragmentManager
                        .beginTransaction();
                fragmentTransaction.remove(this);
                fragmentTransaction.commit();
            }
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            // After long times of inactivity with an open dialog, Android might
            // decide to swap out the room or device
            // In this case there is nothing left that we can save.
            // Restoring the fragment from the bundle will likely return nothing
            // (see setArgs).
            if (mDevice != null && mRoom != null) {
                outState.putString("roomId", mRoom.getId());
                outState.putString("deviceId", mDevice.toString());
            }
        }

        private boolean setArgs(Bundle savedInstanceState) {
            Bundle b = savedInstanceState;

            if (b == null)
                b = getArguments();
/*TODO: Mr - Test for control value in room view
            mRoom = App.getRoom(b.getString("roomId"));
            if (mRoom == null) {
                Log.e(LOG_TAG, "DeviceFragment for phantom room " + b.getString("roomId"));
                return false;
            }
            mDevice = mRoom.getDevice(b.getString("deviceId"));
            if (mDevice == null) {
                Log.e(LOG_TAG, "DeviceFragment " + b.getString("deviceId") + " not found in room " + b.getString("roomId"));
                return false;
            }
            return true;
*/
            mRoom = App.getRoom(b.getString("roomId"));
            if (mRoom != null) {
                mDevice = mRoom.getDevice(b.getString("deviceId"));
                if (mDevice != null)
                    return true;
            }
            // search device in all rooms, if device is in another room than the control
            for (int i = 0; i < App.getRoomCount(); i++) {
                mRoom = App.getRoom(i);
                mDevice = mRoom.getDevice(b.getString("deviceId"));
                if (mDevice != null)
                    return true;
            }

            return false;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            LinearLayout outerLayout = new LinearLayout(getActivity());
            outerLayout.setOrientation(LinearLayout.VERTICAL);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            if (setArgs(savedInstanceState)) {
                EventBus.getDefault().register(this);

                // Use the Builder class for convenient dialog construction
                builder.setTitle(mDevice.getName());

                ScrollView sw = new ScrollView(getActivity());
                LinearLayout ll = new LinearLayout(getActivity());
                ll.setOrientation(LinearLayout.VERTICAL);
                ll.setPadding(16, 0, 16, 0);
                for (Control control : mDevice.getControls().values()) {
                    ll.addView(getControlView(control).attachToControl(control).getLayout());
                }
                sw.addView(ll);
                outerLayout.addView(sw);
            }
            builder.setView(outerLayout);
            return builder.create();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View v = super.onCreateView(inflater, container, savedInstanceState);
            getDialog().setCanceledOnTouchOutside(true);
            return v;
        }

        public ControlView getControlView(Control control) {
            ControlView v;

            if (control.getMeta("type", "text").equals("switch")) {
                v = new ControlViewSwitch(getActivity());
            } else if (control.getMeta("type", "text").equals("range")) {
                v = new ControlViewRange(getActivity());
            } else {
                v = new ControlViewText(getActivity());
            }
            return v;
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            Log.v(LOG_TAG, "onDestroyView()");

            ValueSortedMap<String, Control> controls;

            if ((mDevice != null) && ((controls = mDevice.getControls()) != null))
                for (Control control : controls.values())
                    control.removeValueChangedObserver();

            EventBus.getDefault().unregister(this);
        }
    }
}
