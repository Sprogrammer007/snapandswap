package com.snapandswap.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.snapandswap.CameraActivity;
import com.snapandswap.IconListPreference;
import com.snapandswap.ListPreference;
import com.snapandswap.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SingleSettingPopup extends AbstractSettingPopup implements
			 AdapterView.OnItemClickListener{

    private static final String TAG ="SingleSettingPopup";

	private ListPreference mPreference;

	public SingleSettingPopup(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

    private class SinglePrefAdapter extends SimpleAdapter {

		public SinglePrefAdapter(Context context,
				List<? extends Map<String, ?>> data, int resource,
						String[] from, int[] to) {
			super(context, data, resource, from, to);
		}

		@Override
		public void setViewImage(ImageView v, String value) {
			if ("".equals(value)) {
				// Some settings have no icons. Ex: exposure compensation.
				v.setVisibility(View.GONE);
			} else {
				super.setViewImage(v, value);
			}
		}

	}
	
	public void initialize(ListPreference preference) {
		mPreference = preference;
        Context context = getContext();
        CharSequence[] entries = mPreference.getEntries();
        int[] iconIds = null;
        if (preference instanceof IconListPreference) {
            iconIds = ((IconListPreference) mPreference).getImageIds();
            if (iconIds == null) {
                iconIds = ((IconListPreference) mPreference).getIconIds();
            }
        }
        // Set title.
        mTitle.setText(mPreference.getTitle());
		
        // Prepare the ListView.
        ArrayList<HashMap<String, Object>> listItem =
                new ArrayList<HashMap<String, Object>>();
        for(int i = 0; i < entries.length; ++i) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("text", entries[i].toString());
            if (iconIds != null) map.put("icon", iconIds[i]);
            listItem.add(map);
        }

		SimpleAdapter listItemAdapter = new SinglePrefAdapter(context, listItem,
				R.layout.setting_item_icon,
				new String[] {"text", "icon"},
				new int[] {R.id.text, R.id.image});
		((ListView) mSettingList).setAdapter(listItemAdapter);
		((ListView) mSettingList).setOnItemClickListener(this);
		
		 reloadPreference();
	}


	public void reloadPreference() {
		int index = mPreference.findIndexOfValue(mPreference.getValue());
		if (index != -1) {
			((ListView) mSettingList).setItemChecked(index, true);
		} else {
			Log.e(CameraActivity.TAG, "Invalid preference value.");
			mPreference.print();
		}
	}

    @Override
    public void setOnListPrefChangedListener(Listener listener) {
        mListener = listener;
    }

	@Override
	public void onItemClick(AdapterView<?> arg0, View view, int index, long id) {
		mPreference.setValueIndex(index);
		if (mListener != null) {
			mListener.onListPrefChanged(arg0, view, index, id, mPreference);
		}
	}

}
