package st.alr.homA;

import de.greenrobot.event.EventBus;

import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.Menu;

public class SettingsActivity extends PreferenceActivity {
	private static Preference serverPreference;
	private static SharedPreferences sharedPreferences;
	private SharedPreferences.OnSharedPreferenceChangeListener preferencesChangedListener;

	@Override
	protected void onDestroy() {
		sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferencesChangedListener);
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		App.activityPaused();
		super.onPause();
	}

	@Override
	protected void onResume() {
		App.activityActivated();
		super.onResume();
	}

	public void onEvent(Events.MqttConnectivityChanged event) {
		setServerPreferenceSummaryManually();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		EventBus.getDefault().register(this);

		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		getFragmentManager().beginTransaction().replace(android.R.id.content, new UserPreferencesFragment()).commit();

		preferencesChangedListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreference, String key) {
				if (key.equals("serverAddress"))
					setServerPreferenceSummary(sharedPreference.getString(key, ""));
			}
		};

	}

	public static class UserPreferencesFragment extends PreferenceFragment {

		@Override
		public void onCreate(Bundle savedInstanceState) {

			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preferences);
			serverPreference = findPreference("serverPreference");
			setServerPreferenceSummaryManually();

		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		return true;
	}

	@Override
	public boolean onIsMultiPane() {
		return false;
	}

	private static void setServerPreferenceSummaryManually() {
		setServerPreferenceSummary(sharedPreferences.getString("serverAddress", ""));
	}

	private static void setServerPreferenceSummary(String stringValue) {
		if (stringValue == null || stringValue.equals("")) {
			serverPreference.setSummary("No server set");
		} else {
			serverPreference.setSummary(App.getConnectionStateText());
		}
	}
}