package st.alr.homA.view;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import st.alr.homA.R;
import st.alr.homA.model.Control;
import st.alr.homA.services.ServiceMqtt;

public class ViewHolderControlSwitch extends RecyclerView.ViewHolder {
	private TextView mLabel1;
	private TextView mLabel2;
	private Switch mLabel3;

	public ViewHolderControlSwitch(View v) {
		super(v);
		mLabel1 = v.findViewById(R.id.switchDeviceName);
		mLabel2 = v.findViewById(R.id.switchControlName);
		mLabel3 = v.findViewById(R.id.switchControlValue);
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

	public Switch getLabel3() {
		return mLabel3;
	}

	public void setLabel3(Switch label) {
		mLabel3 = label;
	}

	public void setValue(String value) {
        mLabel3.setChecked(value.equals("1"));
        Log.v(this.toString(), "Setting switch " + mLabel2.getText()  + " to " + value.equals("1"));
	}

	public void setContent(Control c) {
		String text = c.getValue();
		setValue(text);
	}

	public void setInteractionListener(final Control c) {
		mLabel3.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String payload = c.getValue().equals("1") ? "0" : "1";
                String topic = c.getTopic();
                ServiceMqtt serviceMqtt = ServiceMqtt.getInstance();
				if (serviceMqtt != null) {
                    serviceMqtt.publish(topic, payload);
				}
			}
		});
	}

}

