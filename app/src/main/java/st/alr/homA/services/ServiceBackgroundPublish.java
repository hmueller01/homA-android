
package st.alr.homA.services;

import android.app.Service;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;

import de.greenrobot.event.EventBus;
import st.alr.homA.model.Quickpublish;
import st.alr.homA.support.Defaults;
import st.alr.homA.support.Events;

public class ServiceBackgroundPublish extends Service {
    private final String LOG_TAG = ServiceBackgroundPublish.class.getSimpleName();
    private boolean waitingForConnection = false;
    Runnable deferred;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(LOG_TAG, "Background publish service started ");
        EventBus.getDefault().register(this);
        startService(new Intent(this, ServiceMqtt.class));

        if (intent != null) {
            String action = intent.getAction();

            if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                    || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
                Log.v(LOG_TAG, "Background NFC publish");

                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                Ndef ndefTag = Ndef.get(tag);

                NdefMessage ndefMessage = ndefTag.getCachedNdefMessage();
                byte[] message;
                if (ndefMessage != null) {
                    NdefRecord[] ndefRecords = ndefMessage.getRecords();
                    if (ndefRecords.length > 0) {
                        message = ndefRecords[0].getPayload();

                        if (message != null) {
                            handleMessage(new String(message));
                        }
                    }
                }
            } else if (action.equals("st.alr.homA.action.QUICKPUBLISH")) {
                Log.v(LOG_TAG, "Background notification publish");
                handleMessage(intent.getStringExtra("qp"));
            } else {
                Log.d(LOG_TAG, "Unknown intent with action: " + action);
            }
        } else {
            Log.v(LOG_TAG, "NFC Service started without intent, abandon ship");
        }

        stopSelf();
        return super.onStartCommand(intent, flags, startId);
    }

    private void handleMessage(String quickpublishJson) {
        ArrayList<Quickpublish> qps = Quickpublish.fromJsonString(quickpublishJson);

        for (Quickpublish qp : qps) {
            Log.v(LOG_TAG, "Got Quickpublish from tag: " + qp.toString());
            ServiceMqtt.getInstance().publish(qp.getTopic(), qp.getPayload(), qp.isRetained(), 0, 20, null, null);
        }
    }

    public void onEvent(Events.StateChanged.ServiceMqtt event) {
        if (waitingForConnection && event.getState() == Defaults.State.ServiceMqtt.CONNECTED) {
            if (deferred != null) {
                waitingForConnection = false;
                deferred.run();
            }
        }
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.v(LOG_TAG, "onDestroy()");
        EventBus.getDefault().unregister(this);

        super.onDestroy();
    }

}
