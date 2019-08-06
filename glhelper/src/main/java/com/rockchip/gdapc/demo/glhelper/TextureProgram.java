package com.rockchip.gdapc.demo.glhelper;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_TEXTURE0;
import static android.opengl.GLES20.GL_TRIANGLE_STRIP;
import static android.opengl.GLES20.glActiveTexture;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glDisableVertexAttribArray;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glGetAttribLocation;
import static android.opengl.GLES20.glGetUniformLocation;
import static android.opengl.GLES20.glVertexAttribPointer;

/**
 * Created by Randall on 2018/5/15
 *
 * Draw texture2D
 */

public class TextureProgram extends ShaderProgram {

    private static final float FULL_RECTANGLE_COORDS[] = {
            -1.0f, -1.0f,   // 0 bottom left
            1.0f, -1.0f,   // 1 bottom right
            -1.0f,  1.0f,   // 2 top left
            1.0f,  1.0f,   // 3 top right
    };

    //flip
    private static final float FULL_RECTANGLE_TEX_COORDS[] = {
            1.0f, 1.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 0.0f,
    };

    private static final float FULL_RECTANGLE_TEX_COORDS_SSD[] = {
            1.0f, 0.0f,
            0.0f, 0.0f,
            1.0f, 1.0f,
            0.0f, 1.0f,
    };

    private static final float FULL_RECTANGLE_TEX_COORDS_NOFLIP[] = {
            0.0f, 1.0f,     // 左下角
            1.0f, 1.0f,      //右下角
            0.0f, 0.0f,     //左上角
            1.0f, 0.0f,     //右上角
    };

    //分配内存空间,传入指定坐标数据
    private static final FloatBuffer FULL_RECTANGLE_BUF =
            GlUtil.createFloatBuffer(FULL_RECTANGLE_COORDS);
    private static final FloatBuffer FULL_RECTANGLE_TEX_BUF =
            GlUtil.createFloatBuffer(FULL_RECTANGLE_TEX_COORDS);
    private static final FloatBuffer FULL_RECTANGLE_TEX_SSD_BUF =
            GlUtil.createFloatBuffer(FULL_RECTANGLE_TEX_COORDS_SSD);
    private static final FloatBuffer FULL_RECTANGLE_TEX_NOFLIP_BUF =
            GlUtil.createFloatBuffer(FULL_RECTANGLE_TEX_COORDS_NOFLIP);

    protected final int mUniformTextureLocation;

    private final int aPositionLocation;
    private final int aTextureCoordinatesLocation;


    public TextureProgram(Context context, int vertexShader, int fragmentShader) {
        super(context, vertexShader, fragmentShader);

        mUniformTextureLocation = glGetUniformLocation(mProgram, "s_texture");

        aPositionLocation = glGetAttribLocation(mProgram, "a_Position");
        aTextureCoordinatesLocation = glGetAttribLocation(mProgram, "a_TextureCoordinates");
    }


    // Create texture program, the format of texture is GL_TEXTURE_EXTERNAL_OES

    /**
     * GLSL是一门专为图形开发设计的编程语言
     * 读取glsl文件
     * @param context
     */
    public TextureProgram(Context context) {
        this(context, R.raw.oestex_vertex, R.raw.oestex_fragment);
    }


    //draw a camera texture to  screen

    /**
     * 绘画纹理显示到屏幕上
     * @param texture
     */
    public void draw(int texture) {
        useProgram();

        glClear(0);//清理屏幕
        glActiveTexture(GL_TEXTURE0);//激活纹理单元
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture);//指定id绑定到纹理上

        glEnableVertexAttribArray(aPositionLocation);//启用aPositionLocation指定的通用顶点属性数组
        //定义通用顶点属性数据的数组(索引,组件个数,组件类型,转化数据,字节偏移量2*4,指定指向数组中第一个通用顶点属性的第一个组件的指针)
        glVertexAttribPointer(aPositionLocation, 2, GL_FLOAT, false, 8, FULL_RECTANGLE_BUF);

        glEnableVertexAttribArray(aTextureCoordinatesLocation);//启用aTextureCoordinatesLocation指定的通用顶点属性数组
        glVertexAttribPointer(aTextureCoordinatesLocation, 2, GL_FLOAT, false, 8, FULL_RECTANGLE_TEX_BUF);

        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);//绘制图形

        glDisableVertexAttribArray(aPositionLocation);//禁用通用顶点属性数组
        glDisableVertexAttribArray(aTextureCoordinatesLocation);

    }

    // convert a camera texture to a normal GL_TEXTURE_2D texture, and scale the size of texture to model input
    /**
     *将相机纹理转换为普通的GL_TEXTURE_2D纹理，并将纹理大小缩放为模型输入(输入层)
     */
    public void drawFeatureMap(int texture) {
        useProgram();

        glClear(0);//清理屏幕
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture);

        glEnableVertexAttribArray(aPositionLocation);
        glVertexAttribPointer(aPositionLocation, 2, GL_FLOAT, false, 8, FULL_RECTANGLE_BUF);

        glEnableVertexAttribArray(aTextureCoordinatesLocation);
        glVertexAttribPointer(aTextureCoordinatesLocation, 2, GL_FLOAT, false, 8, FULL_RECTANGLE_TEX_SSD_BUF);

        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

        glDisableVertexAttribArray(aPositionLocation);
        glDisableVertexAttribArray(aTextureCoordinatesLocation);

    }

    //draw a static image on the screen
    public void drawImage(int texture, int target) {
        useProgram();

        glClear(0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(target, texture);

        glEnableVertexAttribArray(aPositionLocation);
        glVertexAttribPointer(aPositionLocation, 2, GL_FLOAT, false, 8, FULL_RECTANGLE_BUF);

        glEnableVertexAttribArray(aTextureCoordinatesLocation);
        glVertexAttribPointer(aTextureCoordinatesLocation, 2, GL_FLOAT, false, 8, FULL_RECTANGLE_TEX_NOFLIP_BUF);

        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

        glDisableVertexAttribArray(aPositionLocation);
        glDisableVertexAttribArray(aTextureCoordinatesLocation);

    }


    /**
     * 对纹理进行初始化设置
     * @return
     */
    public static int createOESTextureObject() {
        int[] tex = new int[1];
        //创建一个纹理对象
        GLES20.glGenTextures(1, tex, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0]);

        //画面过大过小处理,设置纹理采样:MIN最近点采样 MAX线性采样
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);


        //画面超出边界处理,设置纹理拉伸:S T轴拉伸方式截取拉伸
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        //绑定纹理
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        return tex[0];
    }

    public static int createTextureObjectFromBitmap(Bitmap bitmap) {
        int[] texture=new int[1];
        if(bitmap!=null&&!bitmap.isRecycled()){
            GLES20.glGenTextures(1,texture,0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            //根据以上指定的参数，生成一个2D纹理
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            return texture[0];
        }
        return 0;
    }
}
