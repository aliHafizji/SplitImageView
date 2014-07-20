package com.alihafizji.example;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.alihafizji.splitimageview.SplitImageView;

import java.util.Random;


public class MainActivity extends Activity {

    private SplitImageView mSplitImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mSplitImageView = (SplitImageView)findViewById(R.id.masked_image_view);
        mSplitImageView.setSplitPercent(100);
        mSplitImageView.setAutomaticAnimationDuration(2000);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.fit_xy:
                mSplitImageView.setScaleType(SplitImageView.ScaleType.FIT_XY);
                break;
            case R.id.fit_start:
                mSplitImageView.setScaleType(SplitImageView.ScaleType.FIT_START);
                break;
            case R.id.fit_center:
                mSplitImageView.setScaleType(SplitImageView.ScaleType.FIT_CENTER);
                break;
            case R.id.fit_end:
                mSplitImageView.setScaleType(SplitImageView.ScaleType.FIT_END);
                break;
            case R.id.center:
                mSplitImageView.setScaleType(SplitImageView.ScaleType.CENTER);
                break;
            case R.id.center_crop:
                mSplitImageView.setScaleType(SplitImageView.ScaleType.CENTER_CROP);
                break;
            case R.id.center_inside:
                mSplitImageView.setScaleType(SplitImageView.ScaleType.CENTER_INSIDE);
                break;
            case R.id.add_color_filter:
                mSplitImageView.setColorFilter(Color.parseColor("#80FF0000"), PorterDuff.Mode.LIGHTEN);
                break;
            case R.id.remove_color_filter:
                mSplitImageView.clearColorFilter();
                break;
            case R.id.apply_random_percent:
                Random random = new Random();
                mSplitImageView.setSplitPercent(random.nextInt(100));
                break;
            case R.id.enable_snap_mask_to_bounds:
                mSplitImageView.setSnapToBounds(true);
                break;
            case R.id.disable_snap_mask_to_bounds:
                mSplitImageView.setSnapToBounds(false);
                break;
            case R.id.enable_touch_unveil:
                mSplitImageView.setUnveilOnTouch(true);
                break;
            case R.id.disable_touch_unveil:
                mSplitImageView.setUnveilOnTouch(false);
                break;
            case R.id.enable_automatic_animation:
                mSplitImageView.setEnableAutomaticAnimation(true);
                break;
            case R.id.disable_automatic_animation:
                mSplitImageView.setEnableAutomaticAnimation(false);
                break;
        }
        return true;
    }
}
