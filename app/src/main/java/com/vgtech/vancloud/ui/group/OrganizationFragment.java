package com.vgtech.vancloud.ui.group;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.android.volley.VolleyError;
import com.vgtech.common.PrfUtils;
import com.vgtech.common.api.JsonDataFactory;
import com.vgtech.common.api.Organization;
import com.vgtech.common.api.RootData;
import com.vgtech.common.network.NetworkManager;
import com.vgtech.common.network.NetworkPath;
import com.vgtech.common.network.android.HttpListener;
import com.vgtech.vancloud.R;
import com.vgtech.vancloud.VanCloudApplication;
import com.vgtech.common.utils.TenantPresenter;
import com.vgtech.vancloud.ui.adapter.OrganizationAdapter;
import com.vgtech.vantop.network.UrlAddr;
import com.vgtech.vantop.utils.VanTopActivityUtils;
import com.vgtech.vantop.utils.VanTopUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by vic on 2016/10/10.
 */
public class OrganizationFragment extends Fragment implements Animation.AnimationListener, AdapterView.OnItemClickListener, View.OnClickListener, OrganizationAdapterLister, AbsListView.OnScrollListener {
    private static final int CALLBACK_ORGS = 1;
    private ViewFlipper mCategoryFlipper;
    private Animation mNextInAnimation;
    private Animation mNextOutAnimation;
    private Animation mPreviousInAnimation;
    private Animation mPreviousOutAnimation;
    private View mCategoryWait;
    private OrganizationAdapter mCategoryAdapter;
    private ListView mCategoryListView;
    private LinearLayout mLuncherLayout;
    private HorizontalScrollView mLuncherScrollView;

    public OrganizationAdapter getCategoryAdapter() {
        return mCategoryAdapter;
    }

    private NetworkManager mNetworkManager;
    private boolean mIndex;

    private boolean mCreateGroup;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.organization_fragment, null);
        if (getActivity() instanceof CreateWorkGroupActivity) {
            mCreateGroup = true;
        }
        Bundle bundle = getArguments();
        if (bundle != null)
            mIndex = bundle.getBoolean("index");
        mNetworkManager = ((VanCloudApplication) getActivity().getApplication()).getNetworkManager();
        mCategoryHistory = new ArrayList<CategoryInfo>();
        mLuncherLayout = (LinearLayout) view.findViewById(R.id.luncher_layout);
        mLuncherScrollView = (HorizontalScrollView) view.findViewById(R.id.luncher_scrollview);
        mCategoryFlipper = (ViewFlipper) view.findViewById(R.id.viewFlipper);
        mNextInAnimation = AnimationUtils.loadAnimation(getActivity(),
                R.anim.in_righttoleft);
        mNextOutAnimation = AnimationUtils.loadAnimation(getActivity(),
                R.anim.out_righttoleft);
        mNextOutAnimation.setAnimationListener(this);
        mPreviousInAnimation = AnimationUtils.loadAnimation(getActivity(),
                R.anim.in_lefttoright);
        mPreviousOutAnimation = AnimationUtils.loadAnimation(getActivity(),
                R.anim.out_lefttoright);
        mPreviousOutAnimation.setAnimationListener(this);
        ListView categoryListView = (ListView) view.findViewById(R.id.category_list);
        setupCategoryListView(inflater, categoryListView);
        String url = VanTopUtils.generatorUrl(getActivity(), UrlAddr.URL_ORGS);
        Uri uri = Uri.parse(url);
        updateCategories(uri, mCategoryListView, mCategoryAdapter);
        setCatTitle(null);
        return view;
    }

    private OrganizationSelectedListener selectedListener;

    public void setSelectedChangeListener(OrganizationSelectedListener selectedListener) {
        this.selectedListener = selectedListener;
    }

    private OrganizationBackListener backListener;

    public void setOrganizationBackListener(OrganizationBackListener backListener) {
        this.backListener = backListener;
    }

    private void setupCategoryListView(LayoutInflater inflater,
                                       ListView listView) {
        mCategoryWait = inflater.inflate(R.layout.progress_black, null);
        mCategoryWait.findViewById(R.id.btn_retry).setVisibility(View.GONE);
        listView.addFooterView(mCategoryWait);
        mCategoryAdapter = new OrganizationAdapter(getActivity(), selectedListener);
        mCategoryListView = listView;
        listView.setAdapter(mCategoryAdapter);
        listView.setOnItemClickListener(this);
        listView.setOnScrollListener(this);
    }

    private ArrayList<CategoryInfo> mCategoryHistory;
    private Uri mCurrentCategories;
    private Uri mUpdateUri;

    private boolean updateCategories(Uri uri, ListView oldListView,
                                     OrganizationAdapter oldAdapter) {
        if (uri == null
                || (mCurrentCategories != null && uri
                .compareTo(mCurrentCategories) == 0)) {
            return false;
        }

        mNetworkManager.cancle(mCategoryDataCallback);
        String cat = uri.getQueryParameter("code");
        if (TextUtils.isEmpty(cat)) {
            mCategoryHistory.clear();
        } else if (oldAdapter != null && !oldAdapter.isEmpty()) {
            // save status
            Parcelable state = oldListView.onSaveInstanceState();
            mCategoryHistory.add(new CategoryInfo(mCurrentCategories,
                    mCurrentCategories.getQueryParameter("lable") == null ? "root" : mCurrentCategories.getQueryParameter("lable"), state, oldAdapter));
        }
        // clean data
        mCategoryAdapter.clear();
        mCategoryWait.setVisibility(View.VISIBLE);
        mCurrentCategories = uri;
        mUpdateUri = mCurrentCategories;
        mCategoryWait.postDelayed(new Runnable() {
            @Override
            public void run() {
                Map<String, String> params = new HashMap<String, String>();
                params.put("loginUserCode", PrfUtils.getStaff_no(getActivity()));
                NetworkPath path = new NetworkPath(mUpdateUri.toString(), params, getActivity(), true);
                mNetworkManager.load(CALLBACK_ORGS, path, mCategoryDataCallback);
            }
        }, 300);
        showProgress(mCategoryWait, true);
        return true;
    }

    private boolean mHasData;
    private boolean mSafe;
    private HttpListener<String> mCategoryDataCallback = new HttpListener<String>() {
        @Override
        public void dataLoaded(int callbackId, NetworkPath path, RootData rootData) {

            mSafe = VanTopActivityUtils.prehandleNetworkData(getActivity(), this, callbackId, path, rootData, true);
            if (!mSafe) {

                showProgress(mCategoryWait, false);
                return;
            }
            switch (callbackId) {
                case CALLBACK_ORGS: {
                    JSONObject jsonObject = rootData.getJson();
                    try {
                        Uri uri = Uri.parse(path.getUrl());
                        String code = uri.getQueryParameter("code");
                        String level = uri.getQueryParameter("level");
                        String pcodes = uri.getQueryParameter("pcodes");
                        if (mCurrentCategories != null) {
                            String cur_code = mCurrentCategories.getQueryParameter("code");
                            String cur_level = mCurrentCategories.getQueryParameter("level");
                            String cur_pcodes = mCurrentCategories.getQueryParameter("pcodes");
                            String netParams = "" + code + level + pcodes;
                            String curParams = "" + cur_code + cur_level + cur_pcodes;
                            if (!netParams.equals(curParams))
                                return;
                        }
                        if (selectedListener != null && selectedListener.getSelectMode() == OrganizationSelectedListener.SELECT_MULTI) {
                            Organization all = new Organization("", "all", code + pcodes + level);
                            if (!mCategoryAdapter.getData().contains(all))
                                mCategoryAdapter.addDataAndNorify(all);
                        }
                        if (jsonObject.has("nodes") && jsonObject.get("nodes") instanceof JSONArray) {
                            JSONArray nodeArray = jsonObject.getJSONArray("nodes");
                            List<Organization> list = JsonDataFactory.getDataArray(Organization.class, nodeArray);
                            boolean end = false;
                            for (Organization ori : list) {
                                if (!TextUtils.isEmpty(code) && code.equals(ori.code) && !TextUtils.isEmpty(level) && level.equals(ori.level) && !TextUtils.isEmpty(pcodes) && pcodes.equals(ori.pcodes)) {
                                    end = true;
                                    break;
                                }
                            }
                            if (!end) {
                                mCategoryAdapter.addAllData(list);
                                mCategoryAdapter.notifyDataSetChanged();
                            }
                        }
                        if (jsonObject.has("staffs") && jsonObject.get("staffs") instanceof JSONArray) {
                            JSONArray nodeArray = jsonObject.getJSONArray("staffs");
                            List<Organization> list = JsonDataFactory.getDataArray(Organization.class, nodeArray);
                            mCategoryAdapter.addAllData(list);
                            mCategoryAdapter.notifyDataSetChanged();
                        }
                        if (jsonObject.has("hasStaffs") && jsonObject.get("hasStaffs") instanceof Boolean) {
                            mHasData = jsonObject.getBoolean("hasStaffs");
                        }
                        if (!mHasData) {
                            mCategoryListView.removeFooterView(mCategoryWait);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
        }

        @Override
        public void onErrorResponse(VolleyError error) {

        }

        @Override
        public void onResponse(String response) {

        }
    };

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mNetworkManager != null && mCategoryDataCallback != null)
            mNetworkManager.cancle(mCategoryDataCallback);
        mNetworkManager.cancle(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.tv_lable_index:
                backListener.onBack();
                break;
            case R.id.tv_lable:
                int index = (int) v.getTag();
                int count = mLuncherLayout.getChildCount();
                if (index == 0 && count == 1)
                    return;
                previousCategories(index);
                for (int i = count - 1; i > index; i--) {
                    mLuncherLayout.removeViewAt(i);
                }
                View childView = mLuncherLayout.getChildAt(index);
                TextView childLableTv = (TextView) childView.findViewById(R.id.tv_lable);
                if (!(mCreateGroup&&index==0))
                    childLableTv.setTextColor(getResources().getColor(R.color.text_black));
                mLuncherScrollView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mLuncherScrollView.fullScroll(HorizontalScrollView.FOCUS_RIGHT);
                    }
                }, 100);
                break;
            default:
                break;
        }
    }

    private void setCatTitle(Organization organization) {
        if (mLuncherLayout.getChildCount() == 0) {
            View view = getActivity().getLayoutInflater().inflate(R.layout.luncher_text, null);
            if (mIndex) {
                View indexTv = view.findViewById(R.id.tv_lable_index);
                indexTv.setVisibility(View.VISIBLE);
                indexTv.setOnClickListener(this);
            } else {
                view.findViewById(R.id.ic_arrow).setVisibility(View.GONE);
            }

            TextView lableTv = (TextView) view.findViewById(R.id.tv_lable);
            lableTv.setTag(0);
//            if (mCreateGroup)
//                lableTv.setTextColor(getResources().getColor(R.color.bg_title));
            lableTv.setText(TenantPresenter.getCurrentTenant(getActivity()).tenant_name);
            mLuncherLayout.addView(view);
            mLuncherScrollView.setVisibility(View.VISIBLE);
        }
        if (organization != null) {
            View view = getActivity().getLayoutInflater().inflate(R.layout.luncher_text, null);
            TextView lableTv = (TextView) view.findViewById(R.id.tv_lable);
            lableTv.setText(organization.label);
            lableTv.setTag(mLuncherLayout.getChildCount());
            mLuncherLayout.addView(view);
            for (int i = 0; i < mLuncherLayout.getChildCount() - 1; i++) {
                View childVIew = mLuncherLayout.getChildAt(i);
                TextView childLableTv = (TextView) childVIew.findViewById(R.id.tv_lable);
                childLableTv.setTextColor(getResources().getColor(R.color.bg_title));
                childLableTv.setOnClickListener(this);
            }
            mLuncherScrollView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mLuncherScrollView.fullScroll(HorizontalScrollView.FOCUS_RIGHT);
                }
            }, 100);
        }

    }

    @Override
    public void onAnimationStart(Animation animation) {

    }

    @Override
    public void onAnimationEnd(Animation animation) {
        mCategoryFlipper.post(new Runnable() {
            @Override
            public void run() {
                if (mCategoryFlipper.getChildCount() > 1) {
                    mCategoryFlipper.setInAnimation(null);
                    mCategoryFlipper.setOutAnimation(null);
                    mCategoryFlipper.removeViewAt(0);
                }
            }

        });
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        boolean mInScroll = scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE;
        boolean flag = false;
        if (mCurrentCategories != null) {
            if (!mCategoryAdapter.getData().isEmpty()) {
                Organization organization = mCategoryAdapter.getData().get(mCategoryAdapter.getCount() - 1);
                String lastStaffNo = mCurrentCategories.getQueryParameter("lastStaffNo");
                if (organization.isUser() && organization.staff_no.equals(lastStaffNo))
                    flag = true;
            }
        }
        if (!flag && mSafe && mHasData
                && view.getLastVisiblePosition() >= (view.getCount() - 2)) {
            showProgress(mCategoryWait, true);
            Organization organization = mCategoryAdapter.getData().get(mCategoryAdapter.getCount() - 1);
            mCategoryWait.setVisibility(View.VISIBLE);
            String url = VanTopUtils.generatorUrl(getActivity(), UrlAddr.URL_ORGS_STAFFS);
            Uri uri = Uri.parse(url)
                    .buildUpon()
                    .appendQueryParameter("level", mCurrentCategories.getQueryParameter("level"))
                    .appendQueryParameter("code", mCurrentCategories.getQueryParameter("code"))
                    .appendQueryParameter("pcodes", mCurrentCategories.getQueryParameter("pcodes"))
                    .appendQueryParameter("lastStaffNo", organization.staff_no)
                    .build();
            appendListData(uri);
        }
    }

    protected void appendListData(Uri uri) {
        if (uri == null)
            return;
        mCurrentCategories = uri;
        Map<String, String> params = new HashMap<String, String>();
        params.put("loginUserCode", PrfUtils.getStaff_no(getActivity()));
        NetworkPath path = new NetworkPath(mCurrentCategories.toString(), params, getActivity(), true);
        mNetworkManager.load(CALLBACK_ORGS, path, mCategoryDataCallback);
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

    }

    public static class CategoryInfo {
        public final Uri cat;
        public final CharSequence title;
        public final Parcelable state;
        public final OrganizationAdapter adapter;

        public CategoryInfo(Uri cat, CharSequence title, Parcelable state,
                            OrganizationAdapter adapter) {
            super();
            this.cat = cat;
            this.title = title;
            this.state = state;
            this.adapter = adapter;
        }
    }

    @Override
    public void onAnimationRepeat(Animation animation) {

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

        Object object = parent.getItemAtPosition(position);
        if (object instanceof Organization) {
            OrganizationSearchListener createWorkGroupActivity = (OrganizationSearchListener) getActivity();
            createWorkGroupActivity.closeSearch();
            Organization organization = (Organization) object;
            if (organization.isUser() || "all".equals(organization.code)) {
                if (selectedListener.contains(organization)) {
                    selectedListener.remove(organization);
                    Organization allOrganization = mCategoryAdapter.getItem(0);
                    if ("all".equals(allOrganization.code)) {
                        selectedListener.remove(allOrganization);
                    }
                    if ("all".equals(organization.code)) {
                        selectedListener.remove(mCategoryAdapter.getData());
                    }
                } else {
                    selectedListener.add(organization);
                    if ("all".equals(organization.code)) {
                        selectedListener.add(mCategoryAdapter.getData());
                    }
                }
                mCategoryAdapter.notifyDataSetChanged();
            } else {
                if (!selectedListener.contains(organization)) {
                    nextCategories(organization);
                }
            }
        }
    }

    private void nextCategories(Organization item) {
        // Uri oldUri = mCurrentCategories;
        ListView oldView = mCategoryListView;
        OrganizationAdapter oldAdapter = mCategoryAdapter;
        View oldCategoryWait = mCategoryWait;

        final LayoutInflater inflater = getActivity().getLayoutInflater();
        ViewFlipper viewFlipper = mCategoryFlipper;

        ListView layout = (ListView) inflater.inflate(R.layout.organization_list,
                null);
        setupCategoryListView(inflater, layout);
        String url = VanTopUtils.generatorUrl(getActivity(), UrlAddr.URL_ORGS);
        Uri uri = Uri.parse(url)
                .buildUpon()
                .appendQueryParameter("level", item.level)
                .appendQueryParameter("code", item.code)
                .appendQueryParameter("lable", item.label)
                .appendQueryParameter("pcodes", item.pcodes)
                .build();
        boolean updated = updateCategories(uri, oldView, oldAdapter);
        if (updated) {
            setCatTitle(item);
            viewFlipper.addView(layout);
            viewFlipper.setInAnimation(mNextInAnimation);
            viewFlipper.setOutAnimation(mNextOutAnimation);
            viewFlipper.showNext();
        } else {
            mCategoryListView = oldView;
            mCategoryAdapter = oldAdapter;
            mCategoryWait = oldCategoryWait;
        }
    }

    private void previousCategories(int index) {
        final LayoutInflater inflater = getActivity().getLayoutInflater();
        ViewFlipper viewFlipper = mCategoryFlipper;

        ListView layout = (ListView) inflater.inflate(R.layout.organization_list,
                null);
        setupCategoryListView(inflater, layout);
        final int size = mCategoryHistory.size();
        if (size > 0) {
            for (int i = mCategoryHistory.size() - 1; i > index; i--) {
                mCategoryHistory.remove(i);
            }
            CategoryInfo info = mCategoryHistory.remove(index);
            //  setCatTitle(info.title);
            mCategoryAdapter = info.adapter;
            mCurrentCategories = info.cat;
            layout.setAdapter(info.adapter);
            mCategoryListView.removeFooterView(mCategoryWait);
            layout.onRestoreInstanceState(info.state);
        } else {
            String url = VanTopUtils.generatorUrl(getActivity(), UrlAddr.URL_ORGS);
            Uri uri = Uri.parse(url);
            updateCategories(uri, null, null);
            //  setCatTitle(getText(R.string.app_name));
        }

        viewFlipper.addView(layout);
        viewFlipper.setInAnimation(mPreviousInAnimation);
        viewFlipper.setOutAnimation(mPreviousOutAnimation);
        viewFlipper.showNext();
    }

    protected void showProgress(View progressLayout, boolean show) {

        if (progressLayout == null) {
            return;
        }
        if (progressLayout.getVisibility() != View.VISIBLE) {
            progressLayout.setVisibility(View.VISIBLE);
        }
        if (show) {
            progressLayout.findViewById(R.id.error_footer).setVisibility(
                    View.GONE);
            progressLayout.findViewById(R.id.progressBar).setVisibility(
                    View.VISIBLE);
        } else {
            progressLayout.findViewById(R.id.error_footer).setVisibility(
                    View.VISIBLE);
            progressLayout.findViewById(R.id.progressBar).setVisibility(
                    View.GONE);
        }
    }
}
