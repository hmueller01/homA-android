package st.alr.homA.view;

import st.alr.homA.R;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

public class ViewHolderDevice extends RecyclerView.ViewHolder {
	private TextView mLabel1;

	public ViewHolderDevice(View v) {
		super(v);
		mLabel1 = (TextView) v.findViewById(R.id.title);
	}

	public TextView getLabel1() {
		return mLabel1;
	}

	public void setLabel1(TextView label1) {
		mLabel1 = label1;
	}

}

