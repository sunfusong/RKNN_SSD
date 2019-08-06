package com.rockchip.gpadc.ssddemo;

import android.content.res.AssetManager;
import android.graphics.RectF;
import android.nfc.Tag;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;

import static java.lang.System.arraycopy;

public class InferenceResult {

    OutputBuffer mOutputBuffer;
    ArrayList<Recognition> recognitions = null;
    private boolean mIsVaild = false;   //是否需要重新计算
    PostProcess mPostProcess = new PostProcess();

    /**
     * 初始化,将assert文件读取到内存
     * @param assetManager
     * @throws IOException
     */
    public void init(AssetManager assetManager) throws IOException {
        mOutputBuffer = new OutputBuffer();
        mPostProcess.init(assetManager);
    }


    /**
     * 分类器输出不为空,则停止推理
     */
    public void reset() {
        if (recognitions != null) {
            recognitions.clear();
            mIsVaild = true;
        }
    }

    /**
     * 拷贝输出缓存区
     * @param outputs
     */
    public synchronized void setResult(OutputBuffer outputs) {

        if (mOutputBuffer.mLocations == null) {
            mOutputBuffer.mLocations = outputs.mLocations.clone();
            mOutputBuffer.mClasses = outputs.mClasses.clone();
        } else {
            arraycopy(outputs.mLocations, 0, mOutputBuffer.mLocations, 0, outputs.mLocations.length);
            arraycopy(outputs.mClasses, 0, mOutputBuffer.mClasses, 0, outputs.mClasses.length);
        }
        mIsVaild = false;
    }


    /**
     * 判断是否需要重新推理
     * @return 分类器结果
     */
    public synchronized ArrayList<Recognition> getResult() {
        if (!mIsVaild) {
            mIsVaild = true;
            //mOutputBuffer是推理得出的结果
            recognitions = mPostProcess.postProcess(mOutputBuffer);
        }
        return recognitions;
    }

    /**
     * 定义输出缓存区
     * mLocations 输出位置
     * mClasses 输出分类
     */
    public static class OutputBuffer {
        public float[] mLocations;
        public float[] mClasses;
    }

    /**
     * An immutable result returned by a Classifier describing what was recognized.
     *  由描述已识别内容的分类器(CNN的全连接层)返回的不可变结果,计算每种分类的得分
     */
    public static class Recognition {
        /**
         * A unique identifier for what has been recognized. Specific to the class, not the instance of
         * the object.
         */
        private final int id;

        /**
         * Display name for the recognition.
         */
        private final String title;

        /**
         * A sortable score for how good the recognition is relative to others. Higher should be better.
         * 一个可排序的分数，表示相对于其他人的认可度有多高,越高越好。
         */
        private final Float confidence;

        /** Optional location within the source image for the location of the recognized object.
         * 源图像中可用于识别对象位置的可选位置
         * */
        private RectF location;

        public Recognition(
                final int id, final String title, final Float confidence, final RectF location) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
        }

        public int getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public Float getConfidence() {
            return confidence;
        }

        public RectF getLocation() {
            return new RectF(location);
        }

        public void setLocation(RectF location) {
            this.location = location;
        }

        @Override
        public String toString() {
            String resultString = "";

            resultString += "[" + id + "] ";

            if (title != null) {
                resultString += title + " ";
            }

            if (confidence != null) {
                resultString += String.format("(%.1f%%) ", confidence * 100.0f);
            }

            if (location != null) {
                resultString += location + " ";
            }

            return resultString.trim();
        }
    }
}
