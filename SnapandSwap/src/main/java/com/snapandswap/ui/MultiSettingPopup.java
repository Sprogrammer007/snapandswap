package com.snapandswap.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.snapandswap.CameraSettings;
import com.snapandswap.ListPreference;
import com.snapandswap.PreferenceGroup;
import com.snapandswap.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Steve on 02/06/13.
 */
public class MultiSettingPopup extends AbstractSettingPopup implements
        AdapterView.OnItemClickListener{

    private static final String TAG ="MultiSettingPopup";

    private String[] mKeys;
    private int numKeys;
    private String[] mSettingTitles;
    private String[] mCurOption;
    private PreferenceGroup mPrefGroup;
    private ListPreference[] mPreference;
    private ArrayList<HashMap<String, String>> listItem;
    private MyAdapter listItemAdapter;
    private int mExposureIndex;
    private boolean mClickable = false;
    private Resources mRes;

    public MultiSettingPopup(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRes = context.getResources();
        mKeys = mRes.getStringArray(R.array.pref_camera_all_keys);
        numKeys = mKeys.length;
        mPreference = new ListPreference[numKeys];
        mSettingTitles = new String[numKeys];
        mCurOption = new String[numKeys];
        mExposureIndex = 4;
    }

    public boolean initialize(PreferenceGroup group) {
        mPrefGroup = group;
        if (mPrefGroup == null) return false;

        for (int i = 0; i < numKeys; i++) {
            mPreference[i] = mPrefGroup.findPreference(mKeys[i]);
            CharSequence[] entries = mPreference[i].getEntries();
            mSettingTitles[i] = mPreference[i].getTitle();
                if (mKeys[i].equals(CameraSettings.KEY_EXPOSURE)) {
                    mCurOption[i] = entries[mExposureIndex].toString();
                } else {
                    int index = mPreference[i].findIndexOfValue(
                            mPreference[i].getValue());
                    if (index != -1) {
                    mCurOption[i] = entries[index].toString();
                    } else {
                        Log.e(TAG, "Invalid preference value.");
                        mPreference[i].print();
                    }
                }
        }
        return mSettingTitles != null;
    }

    public void afterinit() {
        Context context = getContext();
        listItem = new ArrayList<HashMap<String, String>>();

        // Set title.
        mMultiTitle.setText("Settings");

        for (int i = 0; i < numKeys; i++) {
            HashMap<String, String> map = new HashMap<String, String>();
            map.put("title", mSettingTitles[i]);
            map.put("option", mCurOption[i]);
            listItem.add(map);
        }

        listItemAdapter = new MyAdapter(context, listItem,
                R.layout.setting_item,
                new String[] {"title", "option"},
                new int[] {R.id.texttitle, R.id.textselected});
        ((ListView) mMultiSettingList).setAdapter(listItemAdapter);
        ((ListView) mMultiSettingList).setOnItemClickListener(this);

        setSeletable(mClickable);

    }

    private class MyAdapter extends BaseAdapter {
        private int[] mTo;
        private String[] mFrom;

        private List<HashMap<String, String>> mData;
        private int[] mNotClickablePositions;
        private int mResource;
        private LayoutInflater mInflater;

        private MyAdapter (Context context, List<HashMap<String, String>> data,
                           int resource, String[] from, int[] to) {
            mData = data;
            mFrom = from;
            mTo = to;
            mResource = resource;
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mNotClickablePositions = new int[] {3, 4, 6};

        }


        @Override
        public int getCount() {
            return mData.size();
        }

        @Override
        public Object getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            View v = mInflater.inflate(mResource, parent, false);
            bindView(position, v);

            for (int i = 0; i < mNotClickablePositions.length ; i++) {
                if (mClickable && position == mNotClickablePositions[i]){
                    v.setOnClickListener(null);
                }
            }

            return v;
        }

        private void bindView(int position, View view) {
            final Map dataSet = mData.get(position);
            if (dataSet == null) {
                return;
            }

            final String[] from = mFrom;
            final int[] to = mTo;
            final int count = to.length;

            for (int i = 0; i < count; i++) {
                final View v = view.findViewById(to[i]);
                if (v != null) {
                    final Object data = dataSet.get(from[i]);
                    String text = data == null ? "" : data.toString();
                    if (text == null) {
                        text = "";
                    }
                    if (v instanceof TextView) {
                        setViewText((TextView) v, text);
                    } else {
                        throw new IllegalStateException(v.getClass().getName() + " is not a " +
                                " view that can be bounds by this SimpleAdapter");
                    }

                    for (int j = 0; j < mNotClickablePositions.length ; j++) {
                        if (mClickable && position == mNotClickablePositions[j]) {
                            switch (v.getId()) {
                                case R.id.texttitle:
                                ((TextView) v).setTextColor(Color.GRAY);
                                break;
                                case R.id.textselected:
                                ((TextView) v).setTextColor(mRes.getColor(R.color.holo_blue_light_disable));
                                break;
                            }
                        }
                    }
                }
            }
        }

        public void setViewText(TextView v, String text) {
            v.setText(text);
        }


        public void updateData(int position, HashMap<String, String> data){
            final HashMap<String, String> set = mData.set(position, data);
            if (!set.equals(data)) {
                notifyDataSetChanged();
            }
        }

        public void updateAllData(ArrayList<HashMap<String, String>> list) {
//            if (mData.equals(list)) return;
            mData = list;
            notifyDataSetChanged();
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

    }

    @Override
    public void setOnListPrefChangedListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public void reloadPreference() {

    }

    public void updateExposure(int value) {
        mExposureIndex = value;
    }
    public void updatePrefSelected(ListPreference pref){
        if (pref == null) return;
        int index = findIndexByPref(pref);
        if (index != -1) {
            CharSequence[] entries = pref.getEntries();
            int i = pref.findIndexOfValue(pref.getValue());
            HashMap<String, String> map = new HashMap<String, String>();
            map.put("title", mSettingTitles[index]);
            if (i != -1) {
                map.put("option", entries[i].toString());
            }
            listItemAdapter.updateData(index, map);
        }
    }

    public void updateAllSelected() {
        if (mPrefGroup == null) return;

        String[] options = new String[numKeys];

        for (int i = 0; i < numKeys; i++) {
            mPreference[i] = mPrefGroup.findPreference(mKeys[i]);
            CharSequence[] entries = mPreference[i].getEntries();
            mSettingTitles[i] = mPreference[i].getTitle();
            if (mKeys[i].equals(CameraSettings.KEY_EXPOSURE)) {
                options[i] = entries[mExposureIndex].toString();
            } else {
                Log.i(TAG, "find index update all  ");
                int index = mPreference[i].findIndexOfValue(
                        mPreference[i].getValue());
                if (index != -1) {
                    options[i] = entries[index].toString();
                } else {
                    Log.e(TAG, "Invalid preference value.");
                    mPreference[i].print();
                }
            }
        }
        ArrayList<HashMap<String, String>> list =
                new ArrayList<HashMap<String, String>>();

        for (int i = 0; i < numKeys; i++) {
            Log.i(TAG, mSettingTitles[i] + " " + options[i]);
            HashMap<String, String> map = new HashMap<String, String>();
            map.put("title", mSettingTitles[i]);
            map.put("option", options[i]);
            list.add(map);
        }
        listItemAdapter.updateAllData(list);
    }


    public void setSeletable(boolean s) {
        mClickable = s;
    }


    private int findIndexByPref(ListPreference pref) {
        final ListPreference[] prefs = mPreference;
        for (int i = 0; i < prefs.length; i++) {
            if (prefs[i] == pref) return i;
        }
        return  -1;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        ListPreference pref = mPreference[position];
        if (mListener != null) {
            mListener.onListPrefChanged(parent, view,position, id, pref);
        }
    }

}
