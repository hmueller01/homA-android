package st.alr.homA.view;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import st.alr.homA.R;
import st.alr.homA.model.Control;
import st.alr.homA.services.ServiceMqtt;

public class ViewHolderControlRange extends RecyclerView.ViewHolder {
	private TextView mLabel1;
	private TextView mLabel2;
	private SeekBar mLabel3;

	public ViewHolderControlRange(View v) {
		super(v);
		mLabel1 = v.findViewById(R.id.rangeDeviceName);
		mLabel2 = v.findViewById(R.id.rangeControlName);
		mLabel3 = v.findViewById(R.id.rangeControlValue);
	}

	public TextView getLabel1() {
		return mLabel1;
	}

	public void setLabel1(TextView label) {
		mLabel1 = label;
	}

	public TextView getLabel2() {
		return mLabel2;
	}

	public void setLabel2(TextView label) {
		mLabel2 = label;
	}

	public SeekBar getLabel3() {
		return mLabel3;
	}

	public void setLabel3(SeekBar label) {
		mLabel3 = label;
	}

    public void setControl(Control control) {
        int max;
        try {
            max = Integer.parseInt(control.getMeta("max", "255"));
        } catch (NumberFormatException e) {
            max = 255;
        }
        mLabel3.setMax(max);
        Log.v(this.toString(), "Setting seekbar max to " + max);
    }

    public void setValue(int intValue) {
		mLabel3.setProgress(intValue);
		Log.v(this.toString(), "Setting seekbar " + mLabel2.getText()  + " to " + intValue);
	}

	public void setContent(Control c) {
		String value = c.getValue();
        int intValue;
        try {
            intValue = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            try {
                intValue = Math.round(Float.parseFloat(value));
            } catch (NumberFormatException f) {
                intValue = 0; // Value is not an int nor a float. Let's quit guessing.
            }
        }
		setValue(intValue);
	}

	public void setInteractionListener(final Control c) {
        mLabel3.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    ServiceMqtt serviceMqtt = ServiceMqtt.getInstance();
                    if (serviceMqtt != null) {
                        String payload = Integer.toString(progress);
                        String topic = c.getTopic();
                        serviceMqtt.publish(topic, payload);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar arg0) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar arg0) {
            }
        });
	}

}

