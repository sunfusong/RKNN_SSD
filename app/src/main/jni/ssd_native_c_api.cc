
#include <jni.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <pthread.h>
#include <sys/syscall.h>


#include <sched.h>

#include "ssd_native_c_api.h"
#include "ssd_image.h"
#include "direct_texture.h"


/**
 * 将传入的字符串转为字节数组
 * @param env
 * @param jstr
 * @return
 */
static char *jstringToChar(JNIEnv *env, jstring jstr) {
    char *rtn = NULL;//字符指针
    jclass clsstring = env->FindClass("java/lang/String");//找到String类
    jstring strencode = env->NewStringUTF("utf-8");//指定UTF-8字符集
    jmethodID mid = env->GetMethodID(clsstring, "getBytes", "(Ljava/lang/String;)[B");//获取getBytes方法
    jbyteArray barr = (jbyteArray) env->CallObjectMethod(jstr, mid, strencode);//将str字符串转为字节数组
    jsize alen = env->GetArrayLength(barr);//获取字节数组bar长度
    jbyte *ba = env->GetByteArrayElements(barr,
                                          JNI_FALSE);//获取barr字节数组的指针,JNI_FALSE表示临时原始数组指针 JNI_TRUE表示临时缓存区数组指针

    if (alen > 0) {
        rtn = new char[alen + 1];
        memcpy(rtn, ba, alen);//由ba指向地址为起始地址的连续alen个字节的数据复制到以rtn指向地址为起始地址的空间内
        rtn[alen] = 0;
    }
    env->ReleaseByteArrayElements(barr, ba, 0);//释放数组
    return rtn;
}


//JNIEXPORT和JNICALL是JNI的关键字,表示此函数要被JNI调用
JNIEXPORT jint JNICALL Java_com_rockchip_gpadc_ssddemo_InferenceWrapper_init
        (JNIEnv *env, jobject obj, jint inputSize, jint channel, jint numResult, jint numClasses,
         jstring modelPath) {
    char *mModelPath = jstringToChar(env, modelPath);
    ssd_image::create(inputSize, channel, numResult, numClasses, mModelPath);//rknn_init

    return 0;
}

JNIEXPORT void JNICALL Java_com_rockchip_gpadc_ssddemo_InferenceWrapper_native_1deinit
        (JNIEnv *env, jobject obj) {
    ssd_image::destroy();//卸载rknn模型,销毁context以及相关资源

}

JNIEXPORT jint JNICALL Java_com_rockchip_gpadc_ssddemo_InferenceWrapper_native_1run___3B_3F_3F
        (JNIEnv *env, jclass obj, jbyteArray in, jfloatArray out0, jfloatArray out1) {


    jboolean inputCopy = JNI_FALSE;
    jbyte *const inData = env->GetByteArrayElements(in, &inputCopy);//获取指向in的指针,只读

    jboolean outputCopy = JNI_FALSE;

    jfloat *const y0 = env->GetFloatArrayElements(out0, &outputCopy);
    jfloat *const y1 = env->GetFloatArrayElements(out1, &outputCopy);

    ssd_image::run_ssd((char *) inData, (float *) y0, (float *) y1);//进行一次推理

    env->ReleaseByteArrayElements(in, inData, JNI_ABORT);//释放资源
    env->ReleaseFloatArrayElements(out0, y0, 0);
    env->ReleaseFloatArrayElements(out1, y1, 0);


    return 0;
}

JNIEXPORT jint JNICALL Java_com_rockchip_gpadc_ssddemo_InferenceWrapper_native_1run__I_3F_3F
        (JNIEnv *env, jclass obj, jint texId, jfloatArray out0, jfloatArray out1) {

    jboolean outputCopy = JNI_FALSE;

    jfloat *const y0 = env->GetFloatArrayElements(out0, &outputCopy);
    jfloat *const y1 = env->GetFloatArrayElements(out1, &outputCopy);

    ssd_image::run_ssd((int) texId, (float *) y0, (float *) y1);//纹理 推理

    env->ReleaseFloatArrayElements(out0, y0, 0);
    env->ReleaseFloatArrayElements(out1, y1, 0);
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_rockchip_gpadc_ssddemo_InferenceWrapper_native_1create_1direct_1texture
        (JNIEnv *env, jclass obj, jint width, jint height, jint fmt) {
    return (jint) gDirectTexture.createDirectTexture((int) width, (int) height, (int) fmt);//生成纹理
}

JNIEXPORT jboolean JNICALL
Java_com_rockchip_gpadc_ssddemo_InferenceWrapper_native_1delete_1direct_1texture
        (JNIEnv *env, jclass obj, jint texId) {
    return (jboolean) gDirectTexture.deleteDirectTexture((int) texId);//删除纹理
}

