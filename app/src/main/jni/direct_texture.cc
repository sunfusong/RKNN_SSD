// Create By randall.zhuo@rock-chips.com
// 2018/10/30

#include <dlfcn.h>
#include <stdio.h>

#include "direct_texture.h"

#include <android/log.h>
#include <cstdlib>
#include <string.h>
#include <cstdint>
#include <ctime>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "dtexture", ##__VA_ARGS__);
#define LOGE(...) __android_log_print(ANDROID_LOG_INFO, "dtexture", ##__VA_ARGS__);


DirectTexture  gDirectTexture;

DirectTexture::DirectTexture() {


}

DirectTexture::~DirectTexture() {

}

/**
 * 生成并绑定纹理
 * @param texWidth
 * @param texHeight
 * @param format
 * @return
 */
int DirectTexture::createDirectTexture(uint32_t texWidth, uint32_t texHeight, int format) {
    uint32_t texFormat = glColorFmtToHalFmt(format);//支持的色彩模式

    if (texFormat == -1) {
        return -1;
    }

    EGLDisplay dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);//EGL的api获取显示设备的句柄

    if (dpy == EGL_NO_DISPLAY) {
        LOGI("eglGetDisplay returned EGL_NO_DISPLAY.\n");
        return -2;
    }

    _DirectTexture *dt = new _DirectTexture();//纹理结构体指针

    dt->texId = -1;
    dt->locked = false;
    dt->pixels = NULL;
    dt->data = NULL;
    dt->texWidth = texWidth;
    dt->texHeight = texHeight;
    dt->format = format;
    dt->bytePerPixel = getBytePerPixel(format);
    dt->img = EGL_NO_IMAGE_KHR;

    AHardwareBuffer_Desc usage;//原生硬件缓冲区api分配缓冲区
    usage.format = texFormat;
    usage.height = texHeight;
    usage.width = texWidth;
    usage.layers = 1;
    usage.rfu0 = 0;
    usage.rfu1 = 0;
    usage.stride = -1;
    usage.usage = AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN | AHARDWAREBUFFER_USAGE_CPU_WRITE_NEVER
				  | AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT;

    AHardwareBuffer_allocate(&usage, &dt->textureBuffer);//分配和传递Desc匹配的缓冲区

    AHardwareBuffer_Desc usage1;
    AHardwareBuffer_describe(dt->textureBuffer, &usage1);

    dt->stride = usage1.stride;

    if (dt->stride != texWidth) {
        dt->data = (char *)malloc(texWidth * texHeight * dt->bytePerPixel);
    }

    dt->clientBuffer = eglGetNativeClientBufferANDROID(dt->textureBuffer);

    dt->img = eglCreateImageKHR(dpy, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, dt->clientBuffer, 0);

    if (dt->img == EGL_NO_IMAGE_KHR) {
        goto error;
    }

    glGenTextures(1, &dt->texId);//生成纹理 1:纹理个数 &dt->texId储存纹理索引的第一个元素指针
    //glBindTexture(GL_TEXTURE_EXTERNAL_OES, dt->texId);
    glBindTexture(GL_TEXTURE_2D, dt->texId);
    glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, (GLeglImageOES)dt->img);

    dtList.push_back(dt);//添加到最后以为

    return (int)(dt->texId);
    error:

    if (dt) {

        if (dt->texId != (GLuint)-1) {
            glDeleteTextures(1, &dt->texId);
        }

        delete dt;
        dt = nullptr;
    }

    return -3;
}


bool DirectTexture::deleteDirectTexture(int texId) {
    std::list<_DirectTexture *>::iterator iter;

    for(iter = dtList.begin(); iter != dtList.end(); iter++)
    {
        _DirectTexture *dt = (_DirectTexture *)*iter;

        if ((dt == nullptr) || (dt->texId != (GLuint)texId)) {
            continue;
        }

        if (dt->locked) {
            AHardwareBuffer_unlock(dt->textureBuffer, nullptr);

            dt->pixels = nullptr;
            dt->locked = false;
        }

        if (dt->data != nullptr) {
            free(dt->data);
        }


        if (dt->texId != (GLuint)-1) {
            glDeleteTextures(1, &dt->texId);
        }

        if (dt->img != EGL_NO_IMAGE_KHR) {
            eglDestroyImageKHR(eglGetDisplay(EGL_DEFAULT_DISPLAY), dt->img);
        }

        dt->textureBuffer = nullptr;//释放纹理缓冲区
        delete dt;

        dtList.erase(iter);//erase使iter迭代器失效
        break;
    }

    return true;
}

/**
 * 通过纹理id得到像素
 * @param texId
 * @return
 */
char *DirectTexture::requireBufferByTexId(int texId) {//返回字符型指针,重写DirectTexture的方法requireBufferByTexId
    _DirectTexture *dt =  getDirectTexture(texId);//dt指向纹理结构体地址

    if (dt == nullptr) {
        LOGE("Invalid texture id:%d\n", texId);
        return nullptr;
    }

    if (dt->locked) {
        if (dt->texWidth != dt->stride) {
            return dt->data;
        }

        return dt->pixels;
    }

    int err = AHardwareBuffer_lock(dt->textureBuffer, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN, -1, nullptr, (void**)(&dt->pixels));//???
    if (err != 0) {
        LOGE("Get buffer failed: %d\n", err);
        return nullptr;
    }

    dt->locked = true;

    if (dt->texWidth != dt->stride) {
        for (int i=0; i< dt->texHeight; ++i) {
            memcpy(dt->data + i * dt->texWidth * dt->bytePerPixel, dt->pixels + i * dt->stride * dt->bytePerPixel, dt->texWidth * dt->bytePerPixel);
        }

        return dt->data;
    }

    return (char *)dt->pixels;
}

bool DirectTexture::releaseBufferByTexId(int texId) {
    _DirectTexture *dt =  getDirectTexture(texId);

    if (dt == nullptr) {
        LOGE("Invalid texture id:%d\n", texId);
        return false;
    }

    if (!dt->locked) {
        return true;
    }

    AHardwareBuffer_unlock(dt->textureBuffer, nullptr);

    dt->pixels = NULL;
    dt->locked = false;

    return true;
}

/**
 * 像素格式
 * @param fmt 色彩模式类型
 * @return
 */
uint32_t DirectTexture::glColorFmtToHalFmt(int fmt) {
    switch(fmt) {
        case GL_RGB:
            return AHARDWAREBUFFER_FORMAT_R8G8B8_UNORM;
        case GL_RGBA:
            return AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
        default:
            LOGE("Unsupport fmt:%d\n", fmt);
            break;
    }

    return -1;
}

int DirectTexture::getBytePerPixel(int fmt) {
    switch(fmt) {
        case GL_RGB:
            return 3;
        case GL_RGBA:
            return 4;
        default:
            LOGE("Unsupport fmt:%d\n", fmt);
            break;
    }

    return 0;
}

_DirectTexture *DirectTexture::getDirectTexture(int texId) {//返回_DirectTexture结构体的指针变量 重写DirectTexture的getDirectTexture方法
    std::list<_DirectTexture *>::iterator iter;//iter为迭代器,可访问元素;该双向链表存储的是_DirectTexture结构体变量
    // dtList是该双向链表的一个对象

    for(iter = dtList.begin(); iter != dtList.end(); iter++)
    {
        _DirectTexture *dt =  (_DirectTexture *)*iter;//结构体指针dt 指向 双向链表的位置为*iter的结构体变量的地址

        if ((dt == NULL) || (dt->texId != (GLuint)texId)) {//GLuint是opengl的数据类型之一,表示无符号四字节整形
            continue;
        }

        return dt;
    }

    return NULL;
}
