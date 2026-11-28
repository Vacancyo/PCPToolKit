#ifndef BLACKBOX_SPEEDHOOK_H
#define BLACKBOX_SPEEDHOOK_H

#include <jni.h>

class SpeedHook {
public:
    static void init(JNIEnv *env);
    static void onLibraryLoaded(const char *name);
    static void setSpeed(float speed);
};

#endif
