package st.alr.homA;

import java.util.HashMap;

import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.MqttTopic;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

enum ConnectingState {
	DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING
}

public class App extends Application implements MqttCallback {
	private MqttClient mqttClient;
	private HashMap<String, Device> devices;
	private Handler uiThreadHandler;
	private ConnectingState state;
	private final Object mLock = new Object();

	RoomsHashMapAdapter roomsAdapter;

	@Override
	public void onCreate() {
		devices = new HashMap<String, Device>();
		roomsAdapter = new RoomsHashMapAdapter(this);

		uiThreadHandler = new Handler();

		Log.v(toString(), "Creating application wide instances");
		super.onCreate();

		bootstrapAndConnectMqtt();
	}

	public void bootstrapAndConnectMqtt() {
		try {

			Log.e(toString(), Thread.currentThread().getName());
			if (Thread.currentThread().getName().equals("main")) {
				new Thread(new Runnable() {
					@Override
					public void run() {
						bootstrapAndConnectMqtt();
					}
				}).start();
				return;
			}

			if ((mqttClient != null) && mqttClient.isConnected()) {
				broadcastConnectionStateChanged(ConnectingState.DISCONNECTING);
				mqttClient.disconnect();
				broadcastConnectionStateChanged(ConnectingState.DISCONNECTED);

			}
			broadcastConnectionStateChanged(ConnectingState.CONNECTING);

			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

			mqttClient = new MqttClient("tcp://" + prefs.getString("serverAddress", "") + ":" + prefs.getString("serverPort", "1883"), MqttClient.generateClientId(), null);
			mqttClient.setCallback(this);

			connectMqtt();

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void connectMqtt() {
		try {

			mqttClient.connect();
			broadcastConnectionStateChanged(ConnectingState.CONNECTED);

			mqttClient.subscribe("/devices/+/controls/+/type", 0);
			mqttClient.subscribe("/devices/+/controls/+", 0);
			mqttClient.subscribe("/devices/+/meta/#", 0);

		} catch (MqttException e) {
			Log.e(toString(), "MqttException: " + e.getMessage()); 
			broadcastConnectionStateChanged(ConnectingState.DISCONNECTED);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// When the connection is lost after a connection has been established
	// successfully, this will retry to reconnect
	@Override
	public void connectionLost(Throwable cause) {
		Log.e(toString(), "Mqtt connection lost. Cause: " + cause);
		broadcastConnectionStateChanged(ConnectingState.DISCONNECTED);

		while (!mqttClient.isConnected()) {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
			}
			bootstrapAndConnectMqtt();
		}
	}

	@Override
	public void deliveryComplete(MqttDeliveryToken token) {
		Log.v(toString(), "Mqtt QOS delivery complete. Token: " + token);
	}

	// public ControlsHashMapAdapter getDevicesAdapter() {
	// return devicesAdapter;
	// }

	@Override
	public void messageArrived(MqttTopic topic, MqttMessage message) throws MqttException {

		String payloadStr = new String(message.getPayload());
		String topicStr = topic.getName();

		final String text = topic.getName() + ":" + new String(message.getPayload()) + "\n";
		Log.v(toString(), "Received: " + text);

		String[] splitTopic = topicStr.split("/");

		// Ensure the device for the message exists
		String deviceId = splitTopic[2];
		Device device = devices.get(deviceId);
		if (device == null) {
			device = new Device(deviceId, this);
			addDevice(device);
			device.moveToRoom(this.getString(R.string.defaultRoomName));

		}

		// Topic parsing
		if (splitTopic[3].equals("controls")) {
			String controlName = splitTopic[4];
			Control control = device.getControlWithId(controlName);

			if (control == null) {
				control = new Control(this, controlName, topicStr.replace("/type", ""), device);
				device.addControl(control);

			}

			if (splitTopic.length < 6) { // Control value
				control.setValue(payloadStr);
			} else { // Control type
				control.setType(payloadStr);
				Log.v(toString(), "type set to: " + payloadStr);
			}
		} else if (splitTopic[3].equals("meta")) {
			if (splitTopic[4].equals("room")) { // Device Room
				device.moveToRoom(payloadStr);

			} else if (splitTopic[4].equals("name")) { // Device name
				device.setName(payloadStr);
			}
		}

	}

	public void addDevice(Device device) {
		devices.put(device.getId(), device);
		Log.v(toString(), "Device '" + device.getId() + "' added, new count is: " + devices.size());
	}

	public void removeDevice(Device device) {
		devices.remove(device.getId());
		Log.v(toString(), "Device '" + device.getId() + "'  removed, new count is: " + devices.size());

	}

	public void addRoom(Room room) {

//		uiThreadHandler.post(new Runnable() {
//	        private Room object = room;
//			@Override
//			public void run() {
		synchronized (mLock) {

				roomsAdapter.add(room);
		}
				//			}});
	}

	public void removeRoom(Room room) {
//		final Room r = room; 
//		uiThreadHandler.post(new Runnable() {
//			@Override
//			public void run() {
//
		synchronized (mLock) {

		roomsAdapter.remove(room);
		}
//			}});

	}

	public Room getRoom(String id) {
		return (Room)(roomsAdapter.getRoom(id));
	}
	
//	public HashMap<String, Room> getRooms() {
//		return rooms;
//	}
	public Device getDevice(String id) {
		return devices.get(id);
	}
	
	public HashMap<String, Device> getDevices() {
		return devices;
	}

	public RoomsHashMapAdapter getRoomsAdapter() {
		return roomsAdapter;
	}

	public Handler getUiThreadHandler() {
		return uiThreadHandler;
	}

	public void publishMqtt(String topicStr, String value) {
		MqttTopic t = mqttClient.getTopic(topicStr + "/on");

		MqttMessage message = new MqttMessage(value.getBytes());
		message.setQos(0);

		// Publish the message
		// String time = new Timestamp(System.currentTimeMillis()).toString();
		// log("Publishing at: "+time+ " to topic \""+topicName+"\" qos "+qos);
		try {
			MqttDeliveryToken token = t.publish(message);
		} catch (MqttPersistenceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MqttException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// mqttSocket.publish(topic+"/on", value, 0, true);

	}

	private void broadcastConnectionStateChanged(ConnectingState status) {
		Log.e(toString(), "sending broadcast");
		state = status;
		Intent i = new Intent("st.alr.homA.mqttConnectivityChanged").putExtra("status", status);
		this.sendBroadcast(i);

	}

	public ConnectingState getState() {
		return state;
	}

	public boolean isConnected() {
		return (mqttClient != null) && mqttClient.isConnected();
	}
}
