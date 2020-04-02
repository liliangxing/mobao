package com.z.dragimageviewapplication.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.widget.ImageView;


/**
 * Created by Administrator on 14-5-8.
 */
public class DragImageView extends ImageView implements
        ScaleGestureDetector.OnScaleGestureListener {

    private boolean mOnce;
    //初始化时缩放的值
    private float mInitScale;
    //双击放大值到达的值
    private float mMidScale;
    //放大的最大值
    private float mMaxScale;

    private Matrix mScaleMatrix;

    //捕获用户多指触控时缩放的比例
    private ScaleGestureDetector mScaleGestureDetector;

    //记录上一次多点触控的数量
    private int mLastPointerCount;

    private float mLastX;
    private float mLastY;

    private int mTouchSlop;
    private boolean isCanDrag;

    private boolean isCheckLeftAndRight;
    private boolean isCheckTopAndBottom;

    /*********双击放大与缩小*********/
    private GestureDetector mGestureDetector;

    private boolean isAutoScale;


    private static final String TAG = "DragImageView";

    private float MAX_SCALE = 3f;//图片放大最大倍数
    private float MIN_SCALE = 0.5f;//图片缩小最小倍数
    private float NORMAL_SCALE=1f;//正常倍数，缩放效果为屏幕宽度

    private Matrix matrix = new Matrix();
    private Matrix savedMatrix = new Matrix();//
    private PointF start = new PointF();//记录单指触摸屏幕点

    private int screen_W, screen_H;// 可见屏幕的宽高度
    private float bitmap_W, bitmap_H;// 当前图片宽高
    private float beforeDistance, afterDistance;// 两触点距离

    private boolean isScaleRestore = false;// 是否需要缩放还原
    private boolean isNeedIntercept=false;//是否需要父类组件拦截处理

    private float scale_temp;// 缩放比例
    private float xCenterPoint;//缩放中心
    private float yCenterPoint;//缩放中心

    private float afterScale;//图片拖放后的比例
    private float xAfterCoordinate;//图片拖放后，左上顶点x坐标
    private float yAfterCoordinate;//图片拖放后，左上顶点y坐标

    private float beforeScale;//图片拖前的比例
    private float xBeforeCoordinate;

    private float beforeMatrixValues[] = new float[9];//图片移动前的矩阵
    private float afterMatrixValues[] = new float[9];
    private float saveMatrixValues[] = new float[9];

    private MODE mode = MODE.NONE;// 默认模式
    /**
     * 模式 NONE：无 DRAG：拖拽. ZOOM:缩放
     *
     */
    private enum MODE {
        NONE, DRAG, ZOOM
    };

    public DragImageView(Context context) {
        super(context);
    }

    public DragImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DragImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        // init
        mScaleMatrix = new Matrix();
        //setScaleType(ScaleType.MATRIX);
        //setOnTouchListener(this);
        //mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        //mScaleGestureDetector = new ScaleGestureDetector(context, this);
        mGestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {

                        if (isAutoScale) {
                            return true;
                        }

                        float x = e.getX();
                        float y = e.getY();

                        if (getScale() < mMidScale) {
                            postDelayed(new AutoScaleRunnable(mMidScale, x, y), 16);
                            isAutoScale = true;
                        } else {
                            postDelayed(new AutoScaleRunnable(mInitScale, x, y), 16);
                            isAutoScale = true;
                        }
                        return true;
                    }
                });
    }

    /**
     * 自动放大与缩小
     */
    private class AutoScaleRunnable implements Runnable {
        /**
         * 缩放的目标值
         */
        private float mTargetScale;
        // 缩放的中心点
        private float x;
        private float y;

        private final float BIGGER = 1.07f;
        private final float SMALL = 0.93f;

        private float tmpScale;

        /**
         * @param mTargetScale
         * @param x
         * @param y
         */
        public AutoScaleRunnable(float mTargetScale, float x, float y) {
            this.mTargetScale = mTargetScale;
            this.x = x;
            this.y = y;

            if (getScale() < mTargetScale) {
                tmpScale = BIGGER;
            }
            if (getScale() > mTargetScale) {
                tmpScale = SMALL;
            }
        }

        @Override
        public void run() {
            //进行缩放
            mScaleMatrix.postScale(tmpScale, tmpScale, x, y);
            checkBorderAndCenterWhenScale();
            setImageMatrix(mScaleMatrix);

            float currentScale = getScale();

            if ((tmpScale >1.0f && currentScale <mTargetScale) ||(tmpScale<1.0f &&currentScale>mTargetScale)) {
                //这个方法是重新调用run()方法
                isAutoScale = false;
                postDelayed(this, 16);
            }else{
                //设置为我们的目标值
                float scale = mTargetScale/currentScale;
                mScaleMatrix.postScale(scale, scale, x, y);
                checkBorderAndCenterWhenScale();
                setImageMatrix(mScaleMatrix);
                isAutoScale = false;
            }
        }
    }

   /* *//**
     * 获取ImageView加载完成的图片
     *//*
    @Override
    public void onGlobalLayout() {
        if (!mOnce) {
            // 得到控件的宽和高
            int width = getWidth();
            int height = getHeight();

            // 得到我们的图片，以及宽和高
            Drawable drawable = getDrawable();
            if (drawable == null) {
                return;
            }
            int dh = drawable.getIntrinsicHeight();
            int dw = drawable.getIntrinsicWidth();

            float scale = 1.0f;

            // 图片的宽度大于控件的宽度，图片的高度小于空间的高度，我们将其缩小
            if (dw > width && dh < height) {
                scale = width * 1.0f / dw;
            }

            // 图片的宽度小于控件的宽度，图片的高度大于空间的高度，我们将其缩小
            if (dh > height && dw < width) {
                scale = height * 1.0f / dh;
            }

            // 缩小值
            if (dw > width && dh > height) {
                scale = Math.min(width * 1.0f / dw, height * 1.0f / dh);
            }

            // 放大值
            if (dw < width && dh < height) {
                scale = Math.min(width * 1.0f / dw, height * 1.0f / dh);
            }

            *//**
             * 得到了初始化时缩放的比例
             *//*
            mInitScale = scale;
            mMaxScale = mInitScale * 4;
            mMidScale = mInitScale * 2;

            // 将图片移动至控件的中间
            int dx = getWidth() / 2 - dw / 2;
            int dy = getHeight() / 2 - dh / 2;

            mScaleMatrix.postTranslate(dx, dy);
            mScaleMatrix.postScale(mInitScale, mInitScale, width / 2,
                    height / 2);
            setImageMatrix(mScaleMatrix);

            mOnce = true;
        }
    }*/

    /**
     * 注册OnGlobalLayoutListener这个接口
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        //getViewTreeObserver().addOnGlobalLayoutListener(this);
    }

    /**
     * 取消OnGlobalLayoutListener这个接口
     */
    @SuppressWarnings("deprecation")
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        //getViewTreeObserver().removeGlobalOnLayoutListener(this);
    }

    /**
     * 获取当前图片的缩放值
     *
     * @return
     */
    public float getScale() {
        float[] values = new float[9];
        mScaleMatrix.getValues(values);
        return values[Matrix.MSCALE_X];
    }

    // 缩放区间时initScale maxScale
    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        float scale = getScale();
        float scaleFactor = detector.getScaleFactor();

        if (getDrawable() == null) {
            return true;
        }

        // 缩放范围的控制
        if ((scale < mMaxScale && scaleFactor > 1.0f)
                || (scale > mInitScale && scaleFactor < 1.0f)) {
            if (scale * scaleFactor < mInitScale) {
                scaleFactor = mInitScale / scale;
            }

            if (scale * scaleFactor > mMaxScale) {
                scale = mMaxScale / scale;
            }

            // 缩放
            mScaleMatrix.postScale(scaleFactor, scaleFactor,
                    detector.getFocusX(), detector.getFocusY());

            checkBorderAndCenterWhenScale();

            setImageMatrix(mScaleMatrix);
        }

        return true;
    }

    /**
     * 获得图片放大缩小以后的宽和高，以及left，right，top，bottom
     *
     * @return
     */
    private RectF getMatrixRectF() {
        Matrix matrix = mScaleMatrix;
        RectF rectF = new RectF();
        Drawable d = getDrawable();
        if (d != null) {
            rectF.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
            matrix.mapRect(rectF);
        }
        return rectF;
    }

    /**
     * 在缩放的时候进行边界以及我们的位置的控制
     */
    private void checkBorderAndCenterWhenScale() {
        RectF rectF = getMatrixRectF();
        float deltaX = 0;
        float deltaY = 0;

        int width = getWidth();
        int height = getHeight();

        // 缩放时进行边界检测，防止出现白边
        if (rectF.width() >= width) {
            if (rectF.left > 0) {
                deltaX = -rectF.left;
            }
            if (rectF.right < width) {
                deltaX = width - rectF.right;
            }
        }

        if (rectF.height() >= height) {
            if (rectF.top > 0) {
                deltaY = -rectF.top;
            }
            if (rectF.bottom < height) {
                deltaY = height - rectF.bottom;
            }
        }

        /**
         * 如果宽度或高度小于空间的宽或者高，则让其居中
         */
        if (rectF.width() < width) {
            deltaX = width / 2f - rectF.right + rectF.width() / 2f;
        }

        if (rectF.height() < height) {
            deltaY = height / 2f - rectF.bottom + rectF.height() / 2f;
        }

        mScaleMatrix.postTranslate(deltaX, deltaY);
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {

    }

   /* @Override
    public boolean onTouch(View v, MotionEvent event) {

        if (mGestureDetector.onTouchEvent(event)) {
            return true;
        }
        *//** 处理单点、多点触摸 **//*
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                onTouchDown(event);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                onPointerDown(event);
                break;
            case MotionEvent.ACTION_MOVE:
                //处理手指移动时的事件
                onTouchMove(event);

                if (isNeedIntercept) {
//                    返回false，让父类控件处理
                    isNeedIntercept=false;
                    return false;
                }

                break;
            case MotionEvent.ACTION_UP:
                mode = MODE.NONE;
                break;
            case MotionEvent.ACTION_POINTER_UP:
               *//* mode = MODE.NONE;
                *//**//** 执行缩放还原 **//**//*
                if (isScaleRestore) {
                    doScaleAnim();
                    isScaleRestore=false;
                }*//*
                break;
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }

        setImageMatrix(matrix);
        return true;

       *//* mScaleGestureDetector.onTouchEvent(event);

        float x = 0;
        float y = 0;
        // 拿到多点触控的数量
        int pointerCount = event.getPointerCount();
        for (int i = 0; i < pointerCount; i++) {
            x += event.getX(i);
            y += event.getY(i);
        }

        x /= pointerCount;
        y /= pointerCount;

        if (mLastPointerCount != pointerCount) {
            isCanDrag = false;
            mLastX = x;
            mLastY = y;
        }
        mLastPointerCount = pointerCount;
        RectF rectF = getMatrixRectF();
        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:
                if (rectF.width()>getWidth() +0.01|| rectF.height()>getHeight()+0.01) {
                    if(getParent() instanceof ViewPager)
                        getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (rectF.width()>getWidth()+0.01 || rectF.height()>getHeight()+0.01) {
                    if(getParent() instanceof ViewPager)
                        getParent().requestDisallowInterceptTouchEvent(true);
                }
                float dx = x - mLastX;
                float dy = y - mLastY;

                if (!isCanDrag) {
                    isCanDrag = isMoveAction(dx, dy);
                }

                if (isCanDrag) {
                    if (getDrawable() != null) {
                        isCheckLeftAndRight = isCheckTopAndBottom = true;
                        // 如果宽度小于控件宽度，不允许横向移动
                        if (rectF.width() < getWidth()) {
                            isCheckLeftAndRight = false;
                            dx = 0;
                        }
                        // 如果高度小于控件高度，不允许纵向移动
                        if (rectF.height() < getHeight()) {
                            isCheckTopAndBottom = false;
                            dy = 0;
                        }
                        mScaleMatrix.postTranslate(dx, dy);

                        checkBorderWhenTranslate();

                        setImageMatrix(mScaleMatrix);
                    }
                }
                mLastX = x;
                mLastY = y;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mLastPointerCount = 0;
                break;

            default:
                break;
        }
*//*
        //return true;
    }*/

    /**
     * 当移动时进行边界检查
     */
    private void checkBorderWhenTranslate() {
        RectF rectF = getMatrixRectF();
        float deltaX = 0;
        float deltaY = 0;

        int width = getWidth();
        int heigth = getHeight();

        if (rectF.top > 0 && isCheckTopAndBottom) {
            deltaY = -rectF.top;
        }
        if (rectF.bottom < heigth && isCheckTopAndBottom) {
            deltaY = heigth - rectF.bottom;
        }
        if (rectF.left > 0 && isCheckLeftAndRight) {
            deltaX = -rectF.left;
        }
        if (rectF.right < width && isCheckLeftAndRight) {
            deltaX = width - rectF.right;
        }
        mScaleMatrix.postTranslate(deltaX, deltaY);

    }

    /**
     * 判断是否是move
     *
     * @param dx
     * @param dy
     * @return
     */
    private boolean isMoveAction(float dx, float dy) {
        return Math.sqrt(dx * dx + dy * dy) > mTouchSlop;
    }

    /**
     * 设置显示图片
     * @param bm
     */
    public void setImageBitmap(Bitmap bm) {

        /** 获取图片宽高 **/
        bitmap_W = bm.getWidth();
        bitmap_H = bm.getHeight();

        //设置图片缩放比例
        if (screen_W > 0) {
            //一般比例，即缩放效果为屏幕大小
            NORMAL_SCALE =   bitmap_W / bitmap_W;
            MIN_SCALE = NORMAL_SCALE / 2;
            MAX_SCALE = NORMAL_SCALE * 3;
        }

        super.setImageBitmap(bm);
        initImgSize();
    }

    /**
     * 可见屏幕宽度 *
     */
    public void setScreen_W(int screen_W) {
        this.screen_W = screen_W;
        this.xCenterPoint = screen_W / 2;
    }

    /**
     * 可见屏幕高度 *
     */
    public void setScreen_H(int screen_H) {
        this.screen_H = screen_H;
        this.yCenterPoint = screen_H / 2;
    }

    /**
     * 初始化图片尺寸
     * 缩放至屏幕宽度，居中
     */
    private void initImgSize(){

        Matrix matrix = getImageMatrix();
        float matrixValue[] = new float[9];
        matrix.getValues(matrixValue);
        float scale = 1 / matrixValue[0] * NORMAL_SCALE;
        matrix.postScale(scale, scale);
        mInitScale = scale;
        mMaxScale = mInitScale * 4f;
        mMidScale = mInitScale * 1.5f;

        //缩放图片宽度至屏幕宽度
        matrix.getValues(matrixValue);

        float xCenterCoordinate = (screen_W - bitmap_W * scale) / 2;
        float yCenterCoordinate = (screen_H - bitmap_H * scale) / 2;

        float dx = xCenterCoordinate - matrixValue[2];
        float dy = yCenterCoordinate - matrixValue[5];

        //移动图片到屏幕中心
        matrix.postTranslate(dx, dy);

        setImageMatrix(matrix);
    }


    /**
     * touch 事件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mGestureDetector.onTouchEvent(event)) {
            return true;
        }
        /** 处理单点、多点触摸 **/
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                onTouchDown(event);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                onPointerDown(event);
                break;
            case MotionEvent.ACTION_MOVE:
                //处理手指移动时的事件
                onTouchMove(event);

                if (isNeedIntercept) {
//                    返回false，让父类控件处理
                    isNeedIntercept=false;
                    return false;
                }

                break;
            case MotionEvent.ACTION_UP:
                mode = MODE.NONE;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                mode = MODE.NONE;
                /** 执行缩放还原 **/
                /*if (isScaleRestore) {
                    doScaleAnim();
                    isScaleRestore=false;
                }*/
                break;
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }

        setImageMatrix(matrix);
        return true;
    }

    /**
     * 单指按下 *
     */
    void onTouchDown(MotionEvent event) {
        mode = MODE.DRAG;

        getParent().requestDisallowInterceptTouchEvent(true);
        matrix.set(getImageMatrix());
        savedMatrix.set(matrix);
        savedMatrix.getValues(saveMatrixValues);//保存移动前的数据到saveMatrixValues数组
        start.set(event.getX(), event.getY());
    }

    /**
     * 两个手指操作，缩放模式 *
     */
    void onPointerDown(MotionEvent event) {


        beforeDistance = getDistance(event);// 获取两点的距离
        //两只手指，且指间隙大于10f
        if (event.getPointerCount() == 2 && beforeDistance > 10f) {
            savedMatrix.set(matrix);
            savedMatrix.getValues(saveMatrixValues);//保存移动前的数据到saveMatrixValues数组
            mode = MODE.ZOOM;
        }
    }

    /**
     * 移动的处理 *
     */
    void onTouchMove(MotionEvent event) {

        matrix.getValues(beforeMatrixValues);
        beforeScale = beforeMatrixValues[0];//图片左上顶点x坐标
        xBeforeCoordinate = beforeMatrixValues[2];

        /** 处理拖动 **/
        if (mode == MODE.DRAG) {

//            在这里要进行判断处理，防止在drag时候越界
            //图片宽度超过屏幕宽度可以移动
            boolean isWidthBeyond=beforeScale * bitmap_W >= screen_W;
            //图片高度超过屏幕高度可以移动
            boolean isHeightBeyond=beforeScale * bitmap_H > screen_H;

            if (isWidthBeyond||isHeightBeyond) {

                float dx=event.getX() - start.x;
                float dy=event.getY()- start.y;

                matrix.set(savedMatrix);//还原拖动前的值，这里的移动值是相对值，不是绝对坐标值
                matrix.postTranslate(dx,dy);

            }
            getAfterMatrixValues();

            doDragBack();

            //左拖动，且处于超过屏幕左边缘
            boolean isLeftBeyond=(xAfterCoordinate >= 0 && xAfterCoordinate - xBeforeCoordinate >= 0);
            //右拖动，且处于超过屏幕右边缘
            boolean isRightBeyond=(bitmap_W * afterScale + xAfterCoordinate <= screen_W && xAfterCoordinate - xBeforeCoordinate < 0);

            if (isLeftBeyond ||isRightBeyond) {

                setImageMatrix(matrix);
                //调用父类控件进行touchEvent拦截，让父类控件处理该事件
                getParent().requestDisallowInterceptTouchEvent(false);
                isNeedIntercept=true;

            }

        }else if (mode == MODE.ZOOM) {
            /** 处理缩放 **/

            afterDistance = getDistance(event);// 获取两点的距离

            float gapLenght = afterDistance - beforeDistance;// 变化的长度

            if (Math.abs(gapLenght) > 5f) {

                scale_temp = afterDistance / beforeDistance;// 求的缩放的比例
                this.setScale(scale_temp);

            }
            matrix.getValues(afterMatrixValues);
        }
    }

    /**
     * 获取两点的距离 *
     */
    float getDistance(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);

        return FloatMath.sqrt(x * x + y * y);
    }

    /**
     * 获取矩阵变化后的矩阵值
     */
    void getAfterMatrixValues(){

        matrix.getValues(afterMatrixValues);

        afterScale = afterMatrixValues[0];
        xAfterCoordinate = afterMatrixValues[2];//图片左上顶点x坐标
        yAfterCoordinate = afterMatrixValues[5];//图片左上顶点y坐标

    }

    /**
     * 缩小还原大小，缩放至图片宽度
     */
    public void doScaleAnim() {
        if (afterScale < NORMAL_SCALE) {
            //放大1/afterScale倍
            float scale = 1 / afterScale * NORMAL_SCALE;
            matrix.postScale(scale, scale, xCenterPoint, yCenterPoint);
        }
    }

    /**
     * 处理缩放 *
     */
    void setScale(float scale) {

        boolean isCanScale = false;
        if (scale > NORMAL_SCALE && beforeScale <= MAX_SCALE) {
            // 放大
            isCanScale = true;
        }else if (scale < NORMAL_SCALE && beforeScale >= MIN_SCALE) {
            // 缩小
            isCanScale = true;
            isScaleRestore = true;
        }
        if (!isCanScale)
            return;

        matrix.set(savedMatrix);//还原放大前的值，这里的放大倍数是绝对值，不是相对值
        matrix.postScale(scale, scale, xCenterPoint, yCenterPoint);
        getAfterMatrixValues();
        doDragBack();

    }

    /**
     * 位置处理，图片超过边缘，则返回边缘，图片尺寸小于屏幕，则返回中间
     */
    public void doDragBack() {


        float imgWidth = bitmap_W * afterScale;//图片宽度=图片原始宽度x缩放倍数
        float imgHeight = bitmap_H * afterScale;//图片高度=图片原始高度度x缩放倍数

        if (mode == MODE.DRAG) {
            //在拖动模式下，如果图片大于屏幕，，不处理返回
            boolean isDeal = imgWidth >= screen_W || imgHeight >= screen_H;

            if (!isDeal) {
                return;
            }
        }


        boolean isDragBackHorizontal = false;//能否图片水平方向变变化
        boolean isDragBackVertical = false;//能否图片垂直方向变化


        float xCenterCoordinate = (screen_W - imgWidth) / 2;//图片x轴中心点
        float yCenterCoordinate = (screen_H - imgHeight) / 2;//图片y轴中心点

        float xEdgeCoordinate=xAfterCoordinate + imgWidth;//图片x轴边沿坐标
        float yEdgeCoordinate=yAfterCoordinate + imgHeight;//图片y轴边沿坐标

        float dx = 0;//水平方向调整距离
        float dy = 0;//垂直方向调整距离

        /** 水平进行判断 **/
        if (xAfterCoordinate > 0) {
            //是否图片右边越过左边屏幕
            dx = -xAfterCoordinate;
            isDragBackHorizontal = true;
        }else if (xEdgeCoordinate< screen_W) {
            //是否图片左边越过右边屏幕
            dx = screen_W - xEdgeCoordinate;
            isDragBackHorizontal = true;

        }

        //如果图片宽度小于屏幕宽度，返回中间
        if (imgWidth < screen_W) {
            dx = xCenterCoordinate - xAfterCoordinate;
            isDragBackHorizontal = true;
        }


        /** 垂直进行判断 **/
        if (yAfterCoordinate > 0) {
            //是否图片上面越过上边屏幕
            dy = -yAfterCoordinate;
            isDragBackVertical = true;
        }else if (yEdgeCoordinate < screen_H) {
            //是否图片下面越过下边屏幕
            dy = screen_H -yEdgeCoordinate;
            isDragBackVertical = true;

        }
        //如果图片高度小于屏幕高度，返回中间
        if (imgHeight < screen_H) {
            dy = yCenterCoordinate - yAfterCoordinate;
            isDragBackVertical = true;
        }
        if (isDragBackHorizontal || isDragBackVertical)
            matrix.postTranslate(dx, dy);
    }


}
