package st.alr.homA.view;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import st.alr.homA.R;

public class ViewHolderRoom extends RecyclerView.ViewHolder {
	private TextView mLabel1;

	public ViewHolderRoom(View v) {
		super(v);
		mLabel1 = v.findViewById(R.id.title);
	}

	public TextView getLabel1() {
		return mLabel1;
	}

	public void setLabel1(TextView label1) {
		mLabel1 = label1;
	}

}

