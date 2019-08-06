package com.rockchip.gpadc.ssddemo;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.ArrayList;

import static com.rockchip.gpadc.ssddemo.CameraSurfaceRender.TAG;
import com.rockchip.gpadc.ssddemo.InferenceResult.Recognition;

/**
 * Created by Randall on 2018/10/15
 */
public class MainActivity extends Activity {

    private GLSurfaceView mGLSurfaceView;
    private CameraSurfaceRender mRender;
    private TextView mFpsNum1;
    private TextView mFpsNum2;
    private TextView mFpsNum3;
    private TextView mFpsNum4;
    private ImageView mTrackResultView;
    private Bitmap mTrackResultBitmap = null;
    private Canvas mTrackResultCanvas = null;
    private Paint mTrackResultPaint = null;
    private Paint mTrackResultTextPaint = null;

    private PorterDuffXfermode mPorterDuffXfermodeClear;
    private PorterDuffXfermode mPorterDuffXfermodeSRC;

    // UI线程，用于更新处理结果
    private Handler mHandler = new Handler()
    {
        public void handleMessage(Message msg)
        {
            //looper按照顺序不断从MessageQueue取出message,而message.what用来标记哪个子线程发送的.updateMainUI(int type, Object data)
            if (msg.what == 0) {
                float fps = (float) msg.obj;

                Log.e("Ma4inActivity", "fps: "+fps);//fps: 25.020851


                DecimalFormat decimalFormat = new DecimalFormat("00.00");
                String fpsStr = decimalFormat.format(fps);

                Log.e("MainActivity", "fpsStr: "+fpsStr);//fpsStr: 25.02

                mFpsNum1.setText(String.valueOf(fpsStr.charAt(0)));
                mFpsNum2.setText(String.valueOf(fpsStr.charAt(1)));
                mFpsNum3.setText(String.valueOf(fpsStr.charAt(3)));
                mFpsNum4.setText(String.valueOf(fpsStr.charAt(4)));
            } else {
                showTrackSelectResults();//msg.what为1
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);//通过让屏幕保持不暗不关闭

        mFpsNum1 = (TextView) findViewById(R.id.fps_num1);
        mFpsNum2 = (TextView) findViewById(R.id.fps_num2);
        mFpsNum3 = (TextView) findViewById(R.id.fps_num3);
        mFpsNum4 = (TextView) findViewById(R.id.fps_num4);
        mTrackResultView = (ImageView) findViewById(R.id.canvasView);

        //对GLSurfaceView进行初始化
        mGLSurfaceView = (GLSurfaceView) findViewById(R.id.gl_surface_view);
        mGLSurfaceView.setEGLContextClientVersion(3);//OpenGLES:3.0
        mRender = new CameraSurfaceRender(mGLSurfaceView, mHandler);//定义渲染器
        mGLSurfaceView.setRenderer(mRender);//将渲染器设置到GLSurfaceView中
        mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);//渲染方式:被动,只有在创建和调用requestRender()时才会刷新
    }

    /**
     * sp值转化为px值.保证文字大小不变
     * @param spValue sp值
     * @return
     */
    public static int sp2px(float spValue) {
        Resources r = Resources.getSystem();
        final float scale = r.getDisplayMetrics().scaledDensity;
        return (int) (spValue * scale + 0.5f);
    }

    /**
     * 绘制矩形
     */
    private void showTrackSelectResults() {

        //todo
        // if we use mRender's resolution, draw paint is slow.
        int width = 640; //mRender.getWidth()
        int height = 480; //mRender.getHeight()

        if (mTrackResultBitmap == null) {


            mTrackResultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            mTrackResultCanvas = new Canvas(mTrackResultBitmap);

            //用于画线
            mTrackResultPaint = new Paint();
            mTrackResultPaint.setColor(0xff06ebff);//设置画笔颜色
            mTrackResultPaint.setStrokeJoin(Paint.Join.ROUND);//拐角风格
            mTrackResultPaint.setStrokeCap(Paint.Cap.ROUND);//圆角效果
            mTrackResultPaint.setStrokeWidth(4);//描边宽度
            mTrackResultPaint.setStyle(Paint.Style.STROKE);//描边效果
            mTrackResultPaint.setTextAlign(Paint.Align.LEFT);//对齐方式
            mTrackResultPaint.setTextSize(sp2px(10));//字体大小
            mTrackResultPaint.setTypeface(Typeface.SANS_SERIF);//设置Typeface对象
            mTrackResultPaint.setFakeBoldText(false);//是否是粗体

            //用于文字
            mTrackResultTextPaint = new Paint();
            mTrackResultTextPaint.setColor(0xff06ebff);
            mTrackResultTextPaint.setStrokeWidth(2);
            mTrackResultTextPaint.setTextAlign(Paint.Align.LEFT);
            mTrackResultTextPaint.setTextSize(sp2px(12));
            mTrackResultTextPaint.setTypeface(Typeface.SANS_SERIF);
            mTrackResultTextPaint.setFakeBoldText(false);


            mPorterDuffXfermodeClear = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);//完全透明显示
            mPorterDuffXfermodeSRC = new PorterDuffXfermode(PorterDuff.Mode.SRC);//显示S层(后绘制的),先绘制的是目标图DST
        }

        // clear canvas
        mTrackResultPaint.setXfermode(mPorterDuffXfermodeClear);//设置图层混淆模式
        mTrackResultCanvas.drawPaint(mTrackResultPaint);//绘制线条
        mTrackResultPaint.setXfermode(mPorterDuffXfermodeSRC);//显示后绘制的方框,隐藏上绘制的

        //detect result
        // todo, mRender.getTrackResult() is slow, you can move it to a new thread
        ArrayList<Recognition> recognitions = mRender.getTrackResult();//进行SSD,NMS算法

        //分类器返回的结果,size表示有多少个default box
        for (int i=0; i<recognitions.size(); ++i) {
            InferenceResult.Recognition rego = recognitions.get(i);
            RectF detection = rego.getLocation();

            detection.left *= width;//A*=B-->A=A*B
            detection.right *= width;
            detection.top *= height;
            detection.bottom *= height;

            //Log.d(TAG, rego.toString());

            mTrackResultCanvas.drawRect(detection, mTrackResultPaint);//绘制矩形(左边,画笔paint)
            mTrackResultCanvas.drawText(rego.getTitle(), detection.left+5, detection.bottom-5
                    , mTrackResultTextPaint);//绘制文本(文字,坐标,画笔),文字显示在矩形中的左下角
        }
        mTrackResultView.setScaleType(ImageView.ScaleType.FIT_XY);//设置图片填满整个imageView
        mTrackResultView.setImageBitmap(mTrackResultBitmap);//显示bitmap
    }

    @Override
    protected void onPause() {
        super.onPause();
        mRender.onPause();
        mGLSurfaceView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mRender.onResume();
        mGLSurfaceView.onResume();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        System.exit(0);
    }
}
