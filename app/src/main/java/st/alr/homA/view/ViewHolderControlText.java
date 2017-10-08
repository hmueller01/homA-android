package st.alr.homA.view;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import st.alr.homA.R;
import st.alr.homA.model.Control;

public class ViewHolderControlText extends RecyclerView.ViewHolder {
	private TextView mLabel1;
	private TextView mLabel2;
	private TextView mLabel3;

	public ViewHolderControlText(View v) {
		super(v);
		mLabel1 = v.findViewById(R.id.textDeviceName);
		mLabel2 = v.findViewById(R.id.textControlName);
		mLabel3 = v.findViewById(R.id.textControlValue);
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

	public TextView getLabel3() {
		return mLabel3;
	}

	public void setLabel3(TextView label) {
		mLabel3 = label;
	}

    public void setValue(String value) {
        mLabel3.setText(value);
    }

	public void setContent(Control c) {
		String text = c.getValue() + c.getMeta("unit", "");
		setValue(text);
	}

    public void setInteractionListener(final Control c) {
    }

}

