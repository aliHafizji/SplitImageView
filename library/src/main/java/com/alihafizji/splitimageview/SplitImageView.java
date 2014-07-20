package com.alihafizji.splitimageview;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Created by kauserali on 17/07/14.
 */
public class SplitImageView extends View {

    private static final String TAG = "SplitImageView";
    private static final int DEFAULT_SPLIT_PERCENT = 50;
    private static final int SNAP_MARGIN_PERCENTAGE = 15;
    private static final int DEFAULT_AUTOMATIC_ANIMATION_DURATION = 600;

    // settable by the client
    private Uri mBackgroundUri, mForegroundUri;
    private int mBackgroundResId = 0, mForegroundResId = 0;
    private Matrix mMatrix;
    private ScaleType mScaleType;


    // these are applied to the drawable
    private ColorFilter mColorFilter;
    private int mAlpha = 255;
    private int mViewAlphaScale = 256;
    private boolean mColorMod = false;

    private Drawable mForegroundDrawable = null, mBackgroundDrawable = null;
    private int mMaxDrawableWidth;
    private int mMaxDrawableHeight;

    private Matrix mDrawMatrix = null;

    // Avoid allocations...
    private RectF mTempSrc = new RectF();
    private RectF mTempDst = new RectF();

    private boolean mCropToPadding, mHaveFrame, mUnveilOnTouch, mSnapToBounds;

    private int mSplitPercent;
    private Path mSplitDrawPath;
    private Paint mDebugDrawPaint;
    private boolean mEnableDebugDraw;
    private boolean mIsAnimating;

    private boolean mEnableAutomaticAnimation;
    private AnimatorSet mAutomaticAnimationAnimatorSet;
    private int mAutomaticAnimationDuration;
    private GestureDetector mGestureDetector;

    private static final ScaleType[] sScaleTypeArray = {
            ScaleType.MATRIX,
            ScaleType.FIT_XY,
            ScaleType.FIT_START,
            ScaleType.FIT_CENTER,
            ScaleType.FIT_END,
            ScaleType.CENTER,
            ScaleType.CENTER_CROP,
            ScaleType.CENTER_INSIDE
    };

    public SplitImageView(Context context) {
        super(context);
        initImageView();
    }

    public SplitImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SplitImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initImageView();

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.SplitImageView, defStyle, 0);

        mSplitPercent = a.getInt(R.styleable.SplitImageView_splitPercent, DEFAULT_SPLIT_PERCENT);
        if (mSplitPercent < 0 || mSplitPercent > 100) {
            throw new IllegalArgumentException("Split percent should be between 0 and 100 and not:" + mSplitPercent);
        }

        mUnveilOnTouch = a.getBoolean(R.styleable.SplitImageView_unveilOnTouch, true);
        mSnapToBounds = a.getBoolean(R.styleable.SplitImageView_snapToBounds, true);

        Drawable backgroundDrawable = a.getDrawable(R.styleable.SplitImageView_backgroundSrc);
        if (backgroundDrawable != null) {
            setBackgroundImageDrawable(backgroundDrawable);
        }

        Drawable foregroundDrawable = a.getDrawable(R.styleable.SplitImageView_foregroundSrc);
        if (foregroundDrawable != null) {
            setForegroundImageDrawable(foregroundDrawable);
        }

        int index = a.getInt(R.styleable.SplitImageView_scaleType, -1);
        if (index >= 0) {
            setScaleType(sScaleTypeArray[index]);
        }

        int tint = a.getInt(R.styleable.SplitImageView_tint, 0);
        if (tint != 0) {
            setColorFilter(tint);
        }

        int alpha = a.getInt(R.styleable.SplitImageView_drawableAlpha, 255);
        if (alpha != 255) {
            setAlpha(alpha);
        }

        mCropToPadding = a.getBoolean(R.styleable.SplitImageView_cropToPadding, false);
        mAutomaticAnimationDuration = DEFAULT_AUTOMATIC_ANIMATION_DURATION;
        a.recycle();
    }

    public boolean isSnapToBounds() {
        return mSnapToBounds;
    }

    /**
     * This will enable or disable snap to bounds.
     *
     * Enabling this will snap the split between images to the bounds of the image.
     *
     * @param snapToBounds
     */
    public void setSnapToBounds(boolean snapToBounds) {
        mSnapToBounds = snapToBounds;
    }

    /**
     * Controls how the image should be resized or moved to match the size
     * of this SplitImageView.
     *
     * @param scaleType The desired scaling mode.
     *
     */
    public void setScaleType(ScaleType scaleType) {
        if (scaleType == null) {
            throw new NullPointerException();
        }

        if (mScaleType != scaleType) {
            mScaleType = scaleType;
            configureBounds();

            requestLayout();
            invalidate();
        }
    }

    /**
     * Return the current scale type in use by this SplitImageView.
     */
    public ScaleType getScaleType() {
        return mScaleType;
    }

    /** Return the view's background drawable, or null if no drawable has been
     assigned.
     */
    public Drawable getBackgroundDrawable() {
        return mBackgroundDrawable;
    }

    /** Return the view's foreground drawable, or null if no drawable has been
     assigned.
     */
    public Drawable getForegroundDrawable() {
        return mForegroundDrawable;
    }

    /**
     * Sets a drawable as the background content of this SplitImageView.
     *
     * This is not the same as calling setBackground() on the view. This corresponds to the
     * resource that will be drawn below the foreground resource. This will be revealed when the
     * foreground resource is unveiled
     */
    public void setBackgroundImageDrawable(Drawable drawable) {
        if (mBackgroundDrawable != drawable) {
            mBackgroundResId = 0;
            mBackgroundUri = null;

            final int oldWidth = mMaxDrawableWidth;
            final int oldHeight = mMaxDrawableHeight;

            updateDrawable(drawable, false);

            if (mMaxDrawableWidth > oldWidth || mMaxDrawableHeight > oldHeight) {
                requestLayout();
            }
            invalidate();

            createPathForSplitPercent(mSplitPercent);
            initGestureRecognizer();

            if (mEnableAutomaticAnimation) {
                startAutomaticAnimation();
            }
        }
    }

    /**
     * Sets a drawable as the foreground content of this SplitImageView.
     *
     * This is the image that will be used to unmask the background image
     */
    public void setForegroundImageDrawable(Drawable drawable) {
        if (mForegroundDrawable != drawable) {
            mForegroundResId = 0;
            mForegroundUri = null;

            final int oldWidth = mMaxDrawableWidth;
            final int oldHeight = mMaxDrawableHeight;

            updateDrawable(drawable, true);

            if (mMaxDrawableWidth > oldWidth || mMaxDrawableHeight > oldHeight) {
                requestLayout();
            }
            invalidate();

            createPathForSplitPercent(mSplitPercent);
            initGestureRecognizer();

            if (mEnableAutomaticAnimation) {
                startAutomaticAnimation();
            }
        }
    }

    /**
     * Sets the background content of this SplitImageView to the specified Uri.
     *
     * <p class="note">This does Bitmap reading and decoding on the UI
     * thread, which can cause a latency hiccup.  If that's a concern,
     * consider using {@link #setBackgroundImageDrawable(android.graphics.drawable.Drawable)} or
     * {@link #setBackgroundImageBitmap(android.graphics.Bitmap)} and
     * {@link android.graphics.BitmapFactory} instead.</p>
     *
     * @param uri The Uri of an image
     */
    public void setBackgroundImageURI(Uri uri) {
        if (mBackgroundResId != 0 ||
                (mBackgroundUri != uri &&
                        (uri == null || mBackgroundUri == null || !uri.equals(mBackgroundUri)))) {
            updateDrawable(null, false);
            mBackgroundResId = 0;
            mBackgroundUri = uri;

            final int oldWidth = mMaxDrawableWidth;
            final int oldHeight = mMaxDrawableHeight;

            resolveUri(false);

            if (mMaxDrawableWidth > oldWidth || mMaxDrawableHeight > oldHeight) {
                requestLayout();
            }
            invalidate();

            createPathForSplitPercent(mSplitPercent);
            initGestureRecognizer();

            if (mEnableAutomaticAnimation) {
                startAutomaticAnimation();
            }
        }
    }

    /**
     * Sets the foreground content of this SplitImageView to the specified Uri.
     *
     * <p class="note">This does Bitmap reading and decoding on the UI
     * thread, which can cause a latency hiccup.  If that's a concern,
     * consider using {@link #setForegroundImageDrawable(android.graphics.drawable.Drawable)}  or
     * {@link #setForegroundImageBitmap(android.graphics.Bitmap)}  and
     * {@link android.graphics.BitmapFactory} instead.</p>
     *
     * @param uri The Uri of an image
     */
    public void setForegroundImageURI(Uri uri) {
        if (mForegroundResId != 0 ||
                (mForegroundUri != uri &&
                        (uri == null || mForegroundUri == null || !uri.equals(mForegroundUri)))) {
            updateDrawable(null, true);
            mForegroundResId = 0;
            mForegroundUri = uri;

            final int oldWidth = mMaxDrawableWidth;
            final int oldHeight = mMaxDrawableHeight;

            resolveUri(true);

            if (mMaxDrawableWidth > oldWidth || mMaxDrawableHeight > oldHeight) {
                requestLayout();
            }
            invalidate();

            createPathForSplitPercent(mSplitPercent);
            initGestureRecognizer();

            if (mEnableAutomaticAnimation) {
                startAutomaticAnimation();
            }
        }
    }

    /**
     * Sets a Bitmap as the background content of this SplitImageView.
     *
     * @param bm The bitmap to set
     */
    public void setBackgroundImageBitmap(Bitmap bm) {
        // if this is used frequently, may handle bitmaps explicitly
        // to reduce the intermediate drawable object
        setBackgroundImageDrawable(new BitmapDrawable(getContext().getResources(), bm));
    }

    /**
     * Sets a Bitmap as the foreground content of this SplitImageView.
     *
     * @param bm The bitmap to set
     */
    public void setForegroundImageBitmap(Bitmap bm) {
        // if this is used frequently, may handle bitmaps explicitly
        // to reduce the intermediate drawable object
        setForegroundImageDrawable(new BitmapDrawable(getContext().getResources(), bm));
    }

    /** Return the view's optional matrix. This is applied to the
     view's drawables when it is drawn. If there is no matrix,
     this method will return an identity matrix.
     Do not change this matrix in place but make a copy.
     If you want a different matrix applied to the drawable,
     be sure to call setImageMatrix().
     */
    public Matrix getImageMatrix() {
        if (mDrawMatrix == null) {
            return new Matrix();
        }
        return mDrawMatrix;
    }

    public void setImageMatrix(Matrix matrix) {
        // collaps null and identity to just null
        if (matrix != null && matrix.isIdentity()) {
            matrix = null;
        }

        // don't invalidate unless we're actually changing our matrix
        if (matrix == null && !mMatrix.isIdentity() ||
                matrix != null && !mMatrix.equals(matrix)) {
            mMatrix.set(matrix);
            configureBounds();
            invalidate();
        }
    }

    /**
     * Return whether this SplitImageView crops to padding.
     *
     * @return whether this SplitImageView crops to padding
     *
     */
    public boolean getCropToPadding() {
        return mCropToPadding;
    }

    /**
     * Sets whether this SplitImageView will crop to padding.
     *
     * @param cropToPadding whether this SplitImageView will crop to padding
     *
     * @see #getCropToPadding()
     *
     */
    public void setCropToPadding(boolean cropToPadding) {
        if (mCropToPadding != cropToPadding) {
            mCropToPadding = cropToPadding;
            requestLayout();
            invalidate();
        }
    }

    /**
     * Returns the active color filter for this SplitImageView.
     *
     * @return the active color filter for this SplitImageView
     *
     * @see #setColorFilter(android.graphics.ColorFilter)
     */
    public ColorFilter getColorFilter() {
        return mColorFilter;
    }

    /**
     * Set a tinting option for the image.
     *
     * @param color Color tint to apply.
     * @param mode How to apply the color.  The standard mode is
     * {@link android.graphics.PorterDuff.Mode#SRC_ATOP}
     *
     */
    public final void setColorFilter(int color, PorterDuff.Mode mode) {
        setColorFilter(new PorterDuffColorFilter(color, mode));
    }

    public final void setColorFilter(int color) {
        setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
    }

    public void setColorFilter(ColorFilter cf) {
        if (mColorFilter != cf) {
            mColorFilter = cf;
            mColorMod = true;
            applyColorMod();
            invalidate();
        }
    }

    public final void clearColorFilter() {
        setColorFilter(null);
    }

    public boolean isDebugDrawEnabled() {
        return mEnableDebugDraw;
    }

    /**
     * This method will enable debug draw. It will draw a red border around the area that is
     * masked/split.
     *
     * @param enableDebugDraw
     */
    public void setDebugDraw(boolean enableDebugDraw) {
        mEnableDebugDraw = enableDebugDraw;
    }

    public boolean isEnableAutomaticAnimation() {
        return mEnableAutomaticAnimation;
    }

    /**
     * Will enable automatic animation. For this to work the foreground drawable & background
     * drawable will have to be set. Also when enabled touch unveil will not work.
     * @param enable
     */
    public void setEnableAutomaticAnimation(boolean enable) {
        mEnableAutomaticAnimation = enable;
        initGestureRecognizer();
        if (mEnableAutomaticAnimation) {
            startAutomaticAnimation();
        } else {
            stopAutomaticAnimation();
        }
    }

    /**
     * This method will set the percentage of split that is applied to the Images.
     * Lesser the value more the amount that is reveled.
     * By default this is 50%.
     *
     * @param percent
     */
    public void setSplitPercent(int percent) {
        if (percent >= 0 && percent <= 100) {
            mSplitPercent = percent;
            createPathForSplitPercent(mSplitPercent);
        } else {
            Log.e(TAG, "Split percentage should be between 0 and 100.");
        }
    }

    /**
     * Returns the alpha that will be applied to the drawables of this SplitImageView.
     *
     * @return the alpha that will be applied to the drawable of this SplitImageView
     *
     * @see #setImageAlpha(int)
     */
    public int getImageAlpha() {
        return mAlpha;
    }

    /**
     * Sets the alpha value that should be applied to the image.
     *
     * @param alpha the alpha value that should be applied to the image
     *
     * @see #getImageAlpha()
     */
    public void setImageAlpha(int alpha) {
        setAlpha(alpha);
    }

    public boolean unveilOnTouch() {
        return mUnveilOnTouch;
    }

    /**
     * Enables the touch to unveil feature
     * @param unveilOnTouch
     */
    public void setUnveilOnTouch(boolean unveilOnTouch) {
        mUnveilOnTouch = unveilOnTouch;
        initGestureRecognizer();
    }

    public int getAutomaticAnimationDuration() {
        return mAutomaticAnimationDuration;
    }

    /**
     * Set the duration of the automatic animation.
     * If the automatic animation has already started this won't take effect.
     * Call this before calling @setEnableAutomaticAnimation
     * @param automaticAnimationDuration
     */
    public void setAutomaticAnimationDuration(int automaticAnimationDuration) {
        mAutomaticAnimationDuration = automaticAnimationDuration;
    }

    /**
     * Sets the alpha value that should be applied to the image.
     *
     * @param alpha the alpha value that should be applied to the image
     *
     * @deprecated use #setImageAlpha(int) instead
     */
    @Deprecated
    public void setAlpha(int alpha) {
        alpha &= 0xFF;          // keep it legal
        if (mAlpha != alpha) {
            mAlpha = alpha;
            mColorMod = true;
            applyColorMod();
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mHaveFrame = true;
        configureBounds();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        resolveUris();
        int w;
        int h;

        if (mForegroundDrawable == null && mBackgroundDrawable == null) {
            // If no drawable, its intrinsic size is 0.
            mMaxDrawableWidth = -1;
            mMaxDrawableHeight = -1;
            w = h = 0;
        } else {
            w = mMaxDrawableWidth;
            h = mMaxDrawableHeight;
            if (w <= 0) w = 1;
            if (h <= 0) h = 1;
        }

        int pleft = getPaddingLeft();
        int pright = getPaddingRight();
        int ptop = getPaddingTop();
        int pbottom = getPaddingBottom();

        int widthSize;
        int heightSize;

        w += pleft + pright;
        h += ptop + pbottom;

        w = Math.max(w, getSuggestedMinimumWidth());
        h = Math.max(h, getSuggestedMinimumHeight());

        widthSize = resolveSizeAndState(w, widthMeasureSpec, 0);
        heightSize = resolveSizeAndState(h, heightMeasureSpec, 0);

        setMeasuredDimension(widthSize, heightSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mForegroundDrawable == null && mBackgroundDrawable == null) {
            return; // couldn't resolve the URI
        }

        if (mMaxDrawableWidth == 0 || mMaxDrawableHeight == 0) {
            return;     // nothing to draw (empty bounds)
        }

        if (mDrawMatrix == null && getPaddingTop() == 0 && getPaddingLeft() == 0) {
            if (mBackgroundDrawable != null) {
                mBackgroundDrawable.draw(canvas);
            }

            if (mSplitDrawPath != null) {
                canvas.clipPath(mSplitDrawPath);
            }

            if (mForegroundDrawable != null) {
                mForegroundDrawable.draw(canvas);
            }

            if (mEnableDebugDraw && mSplitDrawPath != null) {
                canvas.drawPath(mSplitDrawPath, mDebugDrawPaint);
            }
        } else {
            int saveCount = canvas.getSaveCount();
            canvas.save();

            if (mCropToPadding) {
                final int scrollX = getScrollX();
                final int scrollY = getScrollY();
                canvas.clipRect(scrollX + getPaddingLeft(), scrollY + getPaddingTop(),
                        scrollX + getRight() - getLeft() - getPaddingRight(),
                        scrollY + getBottom() - getTop() - getPaddingBottom());
            }

            canvas.translate(getPaddingLeft(), getPaddingTop());

            if (mDrawMatrix != null) {
                canvas.concat(mDrawMatrix);
            }
            if (mBackgroundDrawable != null) {
                mBackgroundDrawable.draw(canvas);
            }

            /**
             * Add a clipping path
             */
            if (mSplitDrawPath != null) {
                canvas.clipPath(mSplitDrawPath);
            }

            if (mForegroundDrawable != null) {
                mForegroundDrawable.draw(canvas);
            }

            if (mEnableDebugDraw && mSplitDrawPath != null) {
                canvas.drawPath(mSplitDrawPath, mDebugDrawPaint);
            }
            canvas.restoreToCount(saveCount);
        }
    }

    @Override
    public void invalidateDrawable(Drawable dr) {
        if (dr == mForegroundDrawable || dr == mBackgroundDrawable) {
            /* we invalidate the whole view in this case because it's very
             * hard to know where the drawable actually is. This is made
             * complicated because of the offsets and transformations that
             * can be applied. In theory we could get the drawable's bounds
             * and run them through the transformation and offsets, but this
             * is probably not worth the effort.
             */
            invalidate();
        } else {
            super.invalidateDrawable(dr);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mBackgroundDrawable != null) {
            mBackgroundDrawable.setVisible(getVisibility() == VISIBLE, false);
        }
        if (mForegroundDrawable != null) {
            mForegroundDrawable.setVisible(getVisibility() == VISIBLE, false);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mBackgroundDrawable != null) {
            mBackgroundDrawable.setVisible(false, false);
        }
        if (mForegroundDrawable != null) {
            mForegroundDrawable.setVisible(false, false);
        }
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(SplitImageView.class.getName());
    }

    @Override
    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
        super.onPopulateAccessibilityEvent(event);
        CharSequence contentDescription = getContentDescription();
        if (!TextUtils.isEmpty(contentDescription)) {
            event.getText().add(contentDescription);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(SplitImageView.class.getName());
    }

    @Override
    protected boolean verifyDrawable(Drawable dr) {
        return mForegroundDrawable == dr || mBackgroundDrawable == dr || super.verifyDrawable(dr);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mGestureDetector != null) {
            mGestureDetector.onTouchEvent(event);
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            snapSplitToBounds();
        }
        return mUnveilOnTouch;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        if (!mEnableAutomaticAnimation) {
            Parcelable parcelable = super.onSaveInstanceState();
            SavedState savedState = new SavedState(parcelable);
            savedState.splitPercent = mSplitPercent;
            return savedState;
        }
        return super.onSaveInstanceState();
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof  SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState savedState = (SavedState)state;
        super.onRestoreInstanceState(savedState.getSuperState());
        if (mSplitPercent != savedState.splitPercent) {
            mSplitPercent = savedState.splitPercent;
            createPathForSplitPercent(mSplitPercent);
        }
    }

    private void initImageView() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        mMatrix     = new Matrix();
        mScaleType  = ScaleType.FIT_CENTER;

        mDebugDrawPaint = new Paint();
        mDebugDrawPaint.setColor(Color.RED);
        mDebugDrawPaint.setStrokeWidth(5);
        mDebugDrawPaint.setStyle(Paint.Style.STROKE);
    }

    private void resolveUris() {
        resolveUri(false);
        resolveUri(true);
    }

    private void resolveUri(boolean forForegroundContent) {
        Drawable drawable = forForegroundContent ? mForegroundDrawable : mBackgroundDrawable;
        if (drawable != null) {
            return;
        }

        Resources rsrc = getResources();
        if (rsrc == null) {
            return;
        }

        Drawable d = null;

        int resource = forForegroundContent ? mForegroundResId : mBackgroundResId;
        Uri uri = forForegroundContent ? mForegroundUri : mBackgroundUri;
        if (resource != 0) {
            try {
                d = rsrc.getDrawable(resource);
            } catch (Exception e) {
                Log.w(TAG, "Unable to find resource: " + resource, e);
                // Don't try again.

                if (forForegroundContent) {
                    mForegroundUri = null;
                } else {
                    mBackgroundUri = null;
                }
            }
        } else if (uri != null) {
            String scheme = uri.getScheme();
            if (ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)) {
                try {
                    // Load drawable through Resources, to get the source density information
                    OpenResourceIdResult r = getResourceId(uri);
                    d = r.r.getDrawable(r.id);
                } catch (Exception e) {
                    Log.w(TAG, "Unable to open content: " + uri, e);
                }
            } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)
                    || ContentResolver.SCHEME_FILE.equals(scheme)) {
                InputStream stream = null;
                try {
                    stream = getContext().getContentResolver().openInputStream(uri);
                    d = Drawable.createFromStream(stream, null);
                } catch (Exception e) {
                    Log.w(TAG, "Unable to open content: " + uri, e);
                } finally {
                    if (stream != null) {
                        try {
                            stream.close();
                        } catch (IOException e) {
                            Log.w(TAG, "Unable to close content: " + uri, e);
                        }
                    }
                }
            } else {
                d = Drawable.createFromPath(uri.toString());
            }

            if (d == null) {
                System.out.println("resolveUri failed on bad bitmap uri: " + uri);
                // Don't try again.
                if (forForegroundContent) {
                    mForegroundUri = null;
                } else {
                    mBackgroundUri = null;
                }
            }
        } else {
            return;
        }

        updateDrawable(d, forForegroundContent);
    }

    private void updateDrawable(Drawable d, boolean isForegroundDrawable) {
        Drawable drawable = isForegroundDrawable ? mForegroundDrawable : mBackgroundDrawable;
        if (drawable != null) {
            drawable.setCallback(null);
            unscheduleDrawable(drawable);
        }

        if (isForegroundDrawable) {
            mForegroundDrawable = d;
        } else {
            mBackgroundDrawable = d;
        }

        if (d != null) {
            d.setCallback(this);
            d.setVisible(getVisibility() == VISIBLE, true);
            mMaxDrawableWidth = Math.max(d.getIntrinsicWidth(), mMaxDrawableWidth);
            mMaxDrawableHeight = Math.max(d.getIntrinsicHeight(), mMaxDrawableHeight);
            applyColorMod();
            configureBounds();
        }
    }

    private void configureBounds() {
        if ((mForegroundDrawable == null && mBackgroundDrawable == null) || !mHaveFrame) {
            return;
        }

        int dwidth = mMaxDrawableWidth;
        int dheight = mMaxDrawableHeight;

        int vwidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int vheight = getHeight() - getPaddingTop() - getPaddingBottom();

        boolean fits = (dwidth < 0 || vwidth == dwidth) &&
                (dheight < 0 || vheight == dheight);

        if (dwidth <= 0 || dheight <= 0 || ScaleType.FIT_XY == mScaleType) {

            if (mBackgroundDrawable != null) {
                mBackgroundDrawable.setBounds(0, 0, vwidth, vheight);
            }
            if (mForegroundDrawable != null) {
                mForegroundDrawable.setBounds(0, 0, vwidth, vheight);
            }
            mDrawMatrix = null;
        } else {

            if (mForegroundDrawable != null) {
                mForegroundDrawable.setBounds(0, 0, dwidth, dheight);
            }

            if (mBackgroundDrawable != null) {
                mBackgroundDrawable.setBounds(0, 0, dwidth, dheight);
            }

            if (ScaleType.MATRIX == mScaleType) {
                if (mMatrix.isIdentity()) {
                    mDrawMatrix = null;
                } else {
                    mDrawMatrix = mMatrix;
                }
            } else if (fits) {
                mDrawMatrix = null;
            } else if (ScaleType.CENTER == mScaleType) {
                mDrawMatrix = mMatrix;
                mDrawMatrix.setTranslate((int) ((vwidth - dwidth) * 0.5f + 0.5f),
                        (int) ((vheight - dheight) * 0.5f + 0.5f));
            } else if (ScaleType.CENTER_CROP == mScaleType) {
                mDrawMatrix = mMatrix;

                float scale;
                float dx = 0, dy = 0;

                if (dwidth * vheight > vwidth * dheight) {
                    scale = (float) vheight / (float) dheight;
                    dx = (vwidth - dwidth * scale) * 0.5f;
                } else {
                    scale = (float) vwidth / (float) dwidth;
                    dy = (vheight - dheight * scale) * 0.5f;
                }

                mDrawMatrix.setScale(scale, scale);
                mDrawMatrix.postTranslate((int) (dx + 0.5f), (int) (dy + 0.5f));
            } else if (ScaleType.CENTER_INSIDE == mScaleType) {
                mDrawMatrix = mMatrix;
                float scale;
                float dx;
                float dy;

                if (dwidth <= vwidth && dheight <= vheight) {
                    scale = 1.0f;
                } else {
                    scale = Math.min((float) vwidth / (float) dwidth,
                            (float) vheight / (float) dheight);
                }

                dx = (int) ((vwidth - dwidth * scale) * 0.5f + 0.5f);
                dy = (int) ((vheight - dheight * scale) * 0.5f + 0.5f);

                mDrawMatrix.setScale(scale, scale);
                mDrawMatrix.postTranslate(dx, dy);
            } else {
                mTempSrc.set(0, 0, dwidth, dheight);
                mTempDst.set(0, 0, vwidth, vheight);

                mDrawMatrix = mMatrix;
                mDrawMatrix.setRectToRect(mTempSrc, mTempDst, scaleTypeToScaleToFit(mScaleType));
            }
        }
    }

    private void createPathForSplitPercent(int splitPercent) {

       if (hasForegroundContent() && hasBackgroundContent() && mMaxDrawableHeight > 0
               && mMaxDrawableWidth > 0) {
           int width = getScaleType() == ScaleType.FIT_XY ? getWidth() : mMaxDrawableWidth;
           int height = getScaleType() == ScaleType.FIT_XY ? getHeight() : mMaxDrawableHeight;

           float aspectRatio = width/(height * 1.0f);
           int max = Math.max(width, height);
           float offset = -max + (max * 2) * (splitPercent/100.0f);

           mSplitDrawPath = new Path();
           mSplitDrawPath.moveTo(0, height);
           mSplitDrawPath.lineTo(0, -offset);
           mSplitDrawPath.lineTo(width + offset * aspectRatio, height);
           mSplitDrawPath.close();
       } else {
           mSplitDrawPath = null;
       }
       invalidate();
    }

    private boolean hasForegroundContent() {
        return mForegroundDrawable != null || mForegroundUri != null || mForegroundResId != 0;
    }

    private boolean hasBackgroundContent() {
        return mBackgroundDrawable != null || mBackgroundUri != null || mBackgroundResId != 0;
    }

    private void applyColorMod() {
        // Only mutate and apply when modifications have occurred. This should
        // not reset the mColorMod flag, since these filters need to be
        // re-applied if the Drawable is changed.
        if (mColorMod) {
            if (mBackgroundDrawable != null) {
                mBackgroundDrawable = mBackgroundDrawable.mutate();
                mBackgroundDrawable.setColorFilter(mColorFilter);
                mBackgroundDrawable.setAlpha(mAlpha * mViewAlphaScale >> 8);
            }

            if (mForegroundDrawable != null) {
                mForegroundDrawable = mForegroundDrawable.mutate();
                mForegroundDrawable.setColorFilter(mColorFilter);
                mForegroundDrawable.setAlpha(mAlpha * mViewAlphaScale >> 8);
            }
        }
    }

    private void snapSplitToBounds() {
        if (mUnveilOnTouch && mSnapToBounds) {
            if (mSplitPercent >= 100 - SNAP_MARGIN_PERCENTAGE || mSplitPercent <= SNAP_MARGIN_PERCENTAGE) {
                animateSplitPercent(mSplitPercent <= SNAP_MARGIN_PERCENTAGE ? 0 : 100);
            }
        }
    }

    private void animateSplitPercent(int toPercent) {
        animateSplitPercent(mSplitPercent, toPercent);
    }

    private void animateSplitPercent(int fromPercent, int toPercent) {
        if (!mIsAnimating && fromPercent >=0 && fromPercent <= 100 && toPercent >=0
                && toPercent <= 100) {
            ValueAnimator valueAnimator = ValueAnimator.ofInt(fromPercent, toPercent);
            valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    setSplitPercent((Integer) animation.getAnimatedValue());
                }
            });
            valueAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mIsAnimating = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mIsAnimating = false;
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    mIsAnimating = false;
                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            valueAnimator.setDuration((300 * Math.abs(toPercent - fromPercent))/SNAP_MARGIN_PERCENTAGE);
            valueAnimator.start();
        } else {
            Log.e(TAG, "Error in animateSplitPercent, fromPercent and toPercent should be between 0 - 100");
        }
    }

    private static final Matrix.ScaleToFit[] sS2FArray = {
            Matrix.ScaleToFit.FILL,
            Matrix.ScaleToFit.START,
            Matrix.ScaleToFit.CENTER,
            Matrix.ScaleToFit.END
    };

    private static Matrix.ScaleToFit scaleTypeToScaleToFit(ScaleType st)  {
        // ScaleToFit enum to their corresponding Matrix.ScaleToFit values
        return sS2FArray[st.nativeInt - 1];
    }

    private OpenResourceIdResult getResourceId(Uri uri) throws FileNotFoundException {
        String authority = uri.getAuthority();
        Resources r;
        if (TextUtils.isEmpty(authority)) {
            throw new FileNotFoundException("No authority: " + uri);
        } else {
            try {
                r = getContext().getPackageManager().getResourcesForApplication(authority);
            } catch (PackageManager.NameNotFoundException ex) {
                throw new FileNotFoundException("No package found for authority: " + uri);
            }
        }
        List<String> path = uri.getPathSegments();
        if (path == null) {
            throw new FileNotFoundException("No path: " + uri);
        }
        int len = path.size();
        int id;
        if (len == 1) {
            try {
                id = Integer.parseInt(path.get(0));
            } catch (NumberFormatException e) {
                throw new FileNotFoundException("Single path segment is not a resource ID: " + uri);
            }
        } else if (len == 2) {
            id = r.getIdentifier(path.get(1), path.get(0), authority);
        } else {
            throw new FileNotFoundException("More than two path segments: " + uri);
        }
        if (id == 0) {
            throw new FileNotFoundException("No resource found for: " + uri);
        }
        OpenResourceIdResult res = new OpenResourceIdResult();
        res.r = r;
        res.id = id;
        return res;
    }

    private void initGestureRecognizer() {
        if (mUnveilOnTouch && !mEnableAutomaticAnimation && hasForegroundContent() && hasBackgroundContent() && mGestureDetector == null) {
            mGestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                    if (mIsAnimating || mEnableAutomaticAnimation) {
                        return false;
                    }
                    float locationX = e2.getX();
                    float locationY = e2.getY();

                    double distance = Math.sqrt(locationX * locationX + Math.pow(getHeight() - locationY, 2));
                    double maxDistance = Math.sqrt(getWidth() * getWidth() + getHeight() * getHeight());

                    double fraction = distance/maxDistance;
                    int fractionPercent = (int) (fraction * 100);

                    setSplitPercent(fractionPercent);
                    Log.i(TAG, "Changing split percent:" + fractionPercent);
                    return true;
                }
            });
        } else {
            mGestureDetector = null;
        }
    }

    private void startAutomaticAnimation() {
        if (mEnableAutomaticAnimation && hasForegroundContent() && hasBackgroundContent()) {

            ValueAnimator currentSplitPercentToMax = ValueAnimator.ofInt(mSplitPercent, 100);
            currentSplitPercentToMax.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    setSplitPercent((Integer) animation.getAnimatedValue());
                }
            });
            currentSplitPercentToMax.setDuration(((100 - mSplitPercent) * mAutomaticAnimationDuration)/100);

            ValueAnimator toMin = ValueAnimator.ofInt(100, 0);
            toMin.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    if (mEnableAutomaticAnimation) {
                        setSplitPercent((Integer) animation.getAnimatedValue());
                    }
                }
            });
            toMin.setDuration(mAutomaticAnimationDuration);

            ValueAnimator toMax = ValueAnimator.ofInt(0, 100);
            toMax.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    if (mEnableAutomaticAnimation) {
                        setSplitPercent((Integer) animation.getAnimatedValue());
                    }
                }
            });
            toMax.setDuration(mAutomaticAnimationDuration);

            currentSplitPercentToMax.start();
            currentSplitPercentToMax.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mAutomaticAnimationAnimatorSet.start();
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            mAutomaticAnimationAnimatorSet = new AnimatorSet();
            mAutomaticAnimationAnimatorSet.playSequentially(toMin, toMax);

            mAutomaticAnimationAnimatorSet.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mEnableAutomaticAnimation) {
                        mAutomaticAnimationAnimatorSet.start();
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    mEnableAutomaticAnimation = false;
                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
        }
    }

    private void stopAutomaticAnimation() {
        if (mEnableAutomaticAnimation) {
            mEnableAutomaticAnimation = false;
            if (mAutomaticAnimationAnimatorSet != null) {
                mAutomaticAnimationAnimatorSet.cancel();
            }
            initGestureRecognizer();
        }
    }

    /**
     * A resource identified by the {@link android.content.res.Resources} that contains it, and a resource id.
     *
     */
    private class OpenResourceIdResult {
        public Resources r;
        public int id;
    }

    /**
     * Options for scaling the bounds of both images to the bounds of this view.
     */
    public enum ScaleType {
        /**
         * Scale using the image matrix when drawing. The image matrix can be set using
         * <code>setImageMatrix</code>
         *
         * From XML, use this syntax:
         * <code>namespace:scaleType="matrix"</code>.
         */
        MATRIX      (0),
        /**
         * Scale the image using {@link android.graphics.Matrix.ScaleToFit#FILL}.
         * From XML, use this syntax: <code>namespace:scaleType="fitXY"</code>.
         */
        FIT_XY      (1),
        /**
         * Scale the image using {@link android.graphics.Matrix.ScaleToFit#START}.
         * From XML, use this syntax: <code>namespace:scaleType="fitStart"</code>.
         */
        FIT_START   (2),
        /**
         * Scale the image using {@link android.graphics.Matrix.ScaleToFit#CENTER}.
         * From XML, use this syntax:
         * <code>namespace:scaleType="fitCenter"</code>.
         */
        FIT_CENTER  (3),
        /**
         * Scale the image using {@link android.graphics.Matrix.ScaleToFit#END}.
         * From XML, use this syntax: <code>namespace:scaleType="fitEnd"</code>.
         */
        FIT_END     (4),
        /**
         * Center the image in the view, but perform no scaling.
         * From XML, use this syntax: <code>android:scaleType="center"</code>.
         */
        CENTER      (5),
        /**
         * Scale the image uniformly (maintain the image's aspect ratio) so
         * that both dimensions (width and height) of the image will be equal
         * to or larger than the corresponding dimension of the view
         * (minus padding). The image is then centered in the view.
         * From XML, use this syntax: <code>namespace:scaleType="centerCrop"</code>.
         */
        CENTER_CROP (6),
        /**
         * Scale the image uniformly (maintain the image's aspect ratio) so
         * that both dimensions (width and height) of the image will be equal
         * to or less than the corresponding dimension of the view
         * (minus padding). The image is then centered in the view.
         * From XML, use this syntax: <code>namespace:scaleType="centerInside"</code>.
         */
        CENTER_INSIDE (7);

        ScaleType(int ni) {
            nativeInt = ni;
        }
        final int nativeInt;
    }

    static class SavedState extends BaseSavedState {

        int splitPercent;

        public SavedState(Parcel source) {
            super(source);
            splitPercent = source.readInt();
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(splitPercent);
        }

        //required field that makes Parcelables from a Parcel
        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}
