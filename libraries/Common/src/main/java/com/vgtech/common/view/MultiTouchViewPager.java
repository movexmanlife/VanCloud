package com.vgtech.common.view;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

/**
 * Created by vic on 2016/11/16.
 */

public class MultiTouchViewPager extends ViewPager {

    public MultiTouchViewPager(Context context) {
        super(context);
    }

    public MultiTouchViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private boolean mIsDisallowIntercept = false;

    @Override public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        mIsDisallowIntercept = disallowIntercept;
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
        if (ev.getPointerCount() > 1 && mIsDisallowIntercept) {
            requestDisallowInterceptTouchEvent(false);
            boolean handled = super.dispatchTouchEvent(ev);
            requestDisallowInterceptTouchEvent(true);
            return handled;
        } else {
            return super.dispatchTouchEvent(ev);
        }
    }
}
