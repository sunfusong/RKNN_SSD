/**
 * Created by Randall on 2018/10/15
 * <p>
 * RKNN inference Camera Demo
 */

package com.rockchip.gpadc.ssddemo;

import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.widget.ImageView;

import com.rockchip.gdapc.demo.glhelper.TextureProgram;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static android.content.Context.MODE_PRIVATE;
import static android.opengl.GLES20.GL_FRAMEBUFFER;
import static android.opengl.GLES20.glBindFramebuffer;
import static android.opengl.GLES20.glFinish;
import static android.opengl.GLES20.glViewport;
import static com.rockchip.gpadc.ssddemo.PostProcess.INPUT_SIZE;
import static java.lang.Thread.sleep;

import com.rockchip.gpadc.ssddemo.InferenceResult.Recognition;

/**
 * 定义渲染器
 */
public class CameraSurfaceRender implements GLSurfaceView.Renderer {
    public static final String TAG = "ssd";
    private String mModelName = "ssd.rknn";

    private Camera mCamera;

    private SurfaceTexture mSurfaceTexture;

    private TextureProgram mTextureProgram;     // Draw texture2D (include camera texture (GL_TEXTURE_EXTERNAL_OES) and normal GL_TEXTURE_2D texture)
    //    private LineProgram mLineProgram;           // Draw detection result
    private GLSurfaceView mGLSurfaceView;
    private int mOESTextureId = -1;    //camera texture ID

    // for inference
    private InferenceWrapper mInferenceWrapper;
    private String fileDirPath;     // file dir to store model cache
    private ImageBufferQueue mImageBufferQueue;    // intermedia between camera thread and  inference thread
    private InferenceResult mInferenceResult = new InferenceResult();  // detection result
    private int mWidth;    //surface width
    private int mHeight;    //surface height
    private Handler mMainHandler;   // ui thread handle,  update fps
    private Object cameraLock = new Object();
    private volatile boolean mStopInference = false;


    public CameraSurfaceRender(GLSurfaceView glSurfaceView, Handler handler) {
        mGLSurfaceView = glSurfaceView;
        mMainHandler = handler;
        fileDirPath = mGLSurfaceView.getContext().getCacheDir().getAbsolutePath();


        Log.e("CameraSurfaceRender", "fileDirPath: " + fileDirPath.toString());

        createFile(mModelName, R.raw.ssd);

        try {
            mInferenceResult.init(mGLSurfaceView.getContext().getAssets());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        startCamera();//打开摄像头
        startTrack();//开始追踪
    }

    /**
     * 渲染窗口大小发生改变或者屏幕方法发生变化时候回调
     *
     * @param gl
     * @param width
     * @param height
     */
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    private void startTrack() {
        mInferenceResult.reset();//判断分类器是否为空,是否继续推理
        mImageBufferQueue = new ImageBufferQueue(3, INPUT_SIZE, INPUT_SIZE);//创建纹理并绑定到帧缓冲区
        mOESTextureId = TextureProgram.createOESTextureObject();//对纹理进行初始化设置,mOESTextureId代表纹理id
        mSurfaceTexture = new SurfaceTexture(mOESTextureId);//根据外部纹理ID创建surfaceTexture
        mTextureProgram = new TextureProgram(mGLSurfaceView.getContext());
//        mLineProgram = new LineProgram(mGLSurfaceView.getContext());

        //监听surfaceTexture的每帧数据
        mSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            //在MainActivity中设置GLsurfaceView渲染为被动,当有数据来的时候,进入该方法中执行requestRender,完成新数据渲染
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                mGLSurfaceView.requestRender();
            }
        });

        try {
            mCamera.setPreviewTexture(mSurfaceTexture);//设置预览输出,将相机的每一帧存到mSurfaceTexture
            mCamera.startPreview();//开始预览
        } catch (IOException e) {
            e.printStackTrace();
        }

        mStopInference = false;
        //开辟子线程,计算fps,模型推理outputs
        mInferenceThread = new Thread(mInferenceRunnable);
        mInferenceThread.start();
    }


    /**
     * 执行绘制工作
     * 将相机纹理显示到屏幕和转为模型输入
     *系统自动定时调用(20ms)
     * @param gl
     */
    @Override
    public void onDrawFrame(GL10 gl) {

        if (mStopInference) {
            return;
        }

        ImageBufferQueue.ImageBuffer imageBuffer = mImageBufferQueue.getFreeBuffer();//获取纹理

        if (imageBuffer == null) {
            return;
        }

        // render to offscreen
        glBindFramebuffer(GL_FRAMEBUFFER, imageBuffer.mFramebuffer);//激活当前的帧缓冲区对象
        glViewport(0, 0, imageBuffer.mWidth, imageBuffer.mHeight);//设置视口(0,0)为左下坐标,宽高
        mTextureProgram.drawFeatureMap(mOESTextureId);//将相片纹理从缓存区取出,转为二维纹理,作为模型输入
        glFinish();//阻止直到所有GL命令执行完成(在完成所有先前调用的GL命令的效果之前，glFinish不会返回)
        mImageBufferQueue.postBuffer(imageBuffer);//设置纹理准备完成状态,与开辟线程中的getReadyBuffer对应

        // main screen
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, mWidth, mHeight);
        mTextureProgram.draw(mOESTextureId);//取出纹理,转为二维纹理

//        mLineProgram.draw(recognitions);

        //将SurfaceTexture对象所关联的OpenGLES中纹理对象的内容将被更新为Image Stream中最新的图片,即上面的二维纹理
        mSurfaceTexture.updateTexImage();

        // update main screen
        // draw track result
        updateMainUI(1, 0);//绘制方框
    }

    /**
     * 对handle子线程进行标记
     *
     * @param type message what
     * @param data message obj
     */
    private void updateMainUI(int type, Object data) {
        Message msg = mMainHandler.obtainMessage();
        msg.what = type;
        msg.obj = data;
        mMainHandler.sendMessage(msg);
    }

    public ArrayList<Recognition> getTrackResult() {
        return mInferenceResult.getResult();//进行ssd检测算法,NMS算法
    }

    public void onPause() {
        stopCamera();
        stopTrack();

    }

    public void onResume() {
        startCamera();
    }

    private void stopTrack() {

        mStopInference = true;
        try {
            mInferenceThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (mSurfaceTexture != null) {
            int[] t = {mOESTextureId};
            GLES20.glDeleteTextures(1, t, 0);

            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }

        if (mTextureProgram != null) {
            mTextureProgram.release();
            mTextureProgram = null;
        }

//        if (mLineProgram != null) {
//            mLineProgram.release();
//            mLineProgram = null;
//        }

        if (mImageBufferQueue != null) {
            mImageBufferQueue.release();
            mImageBufferQueue = null;
        }
    }

    /**
     * 判断是否有摄像头
     * 打开后置摄像头
     * 设置预览参数
     */
    private void startCamera() {
        if (mCamera != null) {
            return;
        }

        //synchronized修饰代码块,当在多线程访问时,需要获取自定义锁才可以执行.cameraLock为自定义锁
        synchronized (cameraLock) {
            Camera.CameraInfo camInfo = new Camera.CameraInfo();

            int numCameras = Camera.getNumberOfCameras();//获取摄像头数量,判断是否存在前置摄像头;返回值为>=0;摄像头序号为numCameras -1.一般0为后置 1为前置
            for (int i = 0; i < numCameras; i++) {
                Camera.getCameraInfo(i, camInfo);
                //if (camInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                mCamera = Camera.open(i);//打开摄像头
                break;

                //}
            }

            if (mCamera == null) {
                throw new RuntimeException("Unable to open camera");
            }

            Camera.Parameters camParams = mCamera.getParameters();//获取相机参数

            List<Camera.Size> sizes = camParams.getSupportedPreviewSizes();//获取相机支持的分辨率
            for (int i = 0; i < sizes.size(); i++) {
                Camera.Size size = sizes.get(i);
                Log.v(TAG, "Camera Supported Preview Size = " + size.width + "x" + size.height);
            }


            camParams.setPreviewSize(640, 480);//对预览分辨率进行设置
            camParams.setRecordingHint(true);//提高MediaRecorder录制摄像头视频性能的

            mCamera.setParameters(camParams);


            if (mSurfaceTexture != null) {
                try {
                    mCamera.setPreviewTexture(mSurfaceTexture);//设置
                } catch (IOException e) {
                    e.printStackTrace();
                }

                mCamera.startPreview();//开始预览
            }
        }
    }

    /**
     * 关闭预览
     * 释放相机
     */
    private void stopCamera() {
        if (mCamera == null)
            return;

        synchronized (cameraLock) {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }

        Log.i(TAG, "stopped camera");
    }

    private Thread mInferenceThread;
    /**
     * fps的计算
     */
    private Runnable mInferenceRunnable = new Runnable() {
        public void run() {

            int count = 0;
            long oldTime = System.currentTimeMillis();//当前计算机时间与GMT所差的毫秒数
            long currentTime;

            String paramPath = fileDirPath + "/" + mModelName;

            //inputSize 输入图像大小 channel 图像通道 numResult 结果数量 numClasses SSD分类数 modelPath 模型路径 ssd.rknn
            mInferenceWrapper = new InferenceWrapper(INPUT_SIZE, PostProcess.INPUT_CHANNEL,
                    PostProcess.NUM_RESULTS, PostProcess.NUM_CLASSES, paramPath);//读取ssd.rknn模型文件,进行rknn_init初始化.

            while (!mStopInference) {//第一次开辟线程该值为false
                ImageBufferQueue.ImageBuffer buffer = mImageBufferQueue.getReadyBuffer();//获取待处理的数据,即纹理id

                if (buffer == null) {
                    try {
                        sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }

                InferenceResult.OutputBuffer outputs = mInferenceWrapper.run(buffer.mTextureId);//输入纹理id,并进行推理

                mInferenceResult.setResult(outputs);//拷贝输出缓存区到OutputBuffer mOutputBuffer,同时读取assets下的文件

                mImageBufferQueue.releaseBuffer(buffer);//释放缓存

                //固定帧数时间法:fps = frameNum / elapsedTime; 计算固定帧数使用的时间,可求出帧率。
                // 要避免动作不流畅的最低是30
                if (++count >= 30) {
                    currentTime = System.currentTimeMillis();

                    float fps = count * 1000.f / (currentTime - oldTime);

                    //Log.d(TAG, "current fps = " + fps);

                    oldTime = currentTime;
                    count = 0;
                    updateMainUI(0, fps);//刷新fps

                }

            }

            mInferenceWrapper.deinit();//卸载rknn模型.销毁context
            mInferenceWrapper = null;
        }
    };

    /**
     * 将apk下的raw的ssd.rknn文件复制到filePath路径下.
     * @param fileName
     * @param id
     */
    private void createFile(String fileName, int id) {
        String filePath = fileDirPath + "/" + fileName;
        try {
            File dir = new File(fileDirPath);

            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 目录存在，则将apk中raw中的需要的文档复制到该目录下
            File file = new File(filePath);

            if (!file.exists() || isFirstRun()) {
                // 通过raw得到数据资源
                InputStream ins = mGLSurfaceView.getContext().getResources().openRawResource(id);
                FileOutputStream fos = new FileOutputStream(file);
                byte[] buffer = new byte[8192];
                int count = 0;

                while ((count = ins.read(buffer)) > 0) {
                    fos.write(buffer, 0, count);
                }

                fos.close();
                ins.close();

                Log.d(TAG, "Create " + filePath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 确保文件只复制一次
     * @return
     */
    private boolean isFirstRun() {
        SharedPreferences sharedPreferences = mGLSurfaceView.getContext()
                .getSharedPreferences("setting", MODE_PRIVATE);
        boolean isFirstRun = sharedPreferences.getBoolean("isFirstRun", true);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (isFirstRun) {
            editor.putBoolean("isFirstRun", false);
            editor.commit();
        }

        return isFirstRun;
    }
}
