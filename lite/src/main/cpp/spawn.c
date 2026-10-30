#include <jni.h>
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/wait.h>

/* Android 6 ARMv7: child inherits the TUN fd, not a public socket. */
JNIEXPORT jint JNICALL Java_app_rayclient_lite_Native_spawn(JNIEnv *e, jclass clazz,
        jstring exeJ, jstring cfgJ, jstring assetsJ, jstring logJ, jint fd) {
    const char *exe = (*e)->GetStringUTFChars(e, exeJ, 0);
    const char *cfg = (*e)->GetStringUTFChars(e, cfgJ, 0);
    const char *assets = (*e)->GetStringUTFChars(e, assetsJ, 0);
    const char *log = (*e)->GetStringUTFChars(e, logJ, 0);
    if (!exe || !cfg || !assets || !log) {
        if (exe) (*e)->ReleaseStringUTFChars(e, exeJ, exe);
        if (cfg) (*e)->ReleaseStringUTFChars(e, cfgJ, cfg);
        if (assets) (*e)->ReleaseStringUTFChars(e, assetsJ, assets);
        if (log) (*e)->ReleaseStringUTFChars(e, logJ, log);
        return -1;
    }
    pid_t pid = fork();
    if (pid == 0) {
        if (fd >= 0) {
            int flags = fcntl(fd, F_GETFD);
            if (flags >= 0) fcntl(fd, F_SETFD, flags & ~FD_CLOEXEC);
            char value[24]; snprintf(value, sizeof value, "%d", fd);
            setenv("XRAY_TUN_FD", value, 1);
            setenv("xray.tun.fd", value, 1);
        }
        setenv("XRAY_LOCATION_ASSET", assets, 1);
        setenv("xray.location.asset", assets, 1);
        setenv("GOMAXPROCS", "2", 1);
        setenv("GOMEMLIMIT", "160MiB", 1);
        int out = open(log, O_CREAT | O_APPEND | O_WRONLY, 0600);
        if (out >= 0) {
            dup2(out, STDOUT_FILENO);
            dup2(out, STDERR_FILENO);
            close(out);
        }
        execl(exe, exe, "run", "-c", cfg, (char*)0);
        _exit(127);
    }
    (*e)->ReleaseStringUTFChars(e, exeJ, exe);
    (*e)->ReleaseStringUTFChars(e, cfgJ, cfg);
    (*e)->ReleaseStringUTFChars(e, assetsJ, assets);
    (*e)->ReleaseStringUTFChars(e, logJ, log);
    return pid;
}
JNIEXPORT void JNICALL Java_app_rayclient_lite_Native_kill(JNIEnv *e, jclass clazz, jint pid) {
    if (pid > 0) {
        kill(pid, SIGTERM);
        usleep(300000);
        kill(pid, SIGKILL);
        int stat; waitpid(pid, &stat, WNOHANG);
    }
}
JNIEXPORT jboolean JNICALL Java_app_rayclient_lite_Native_alive(JNIEnv *e, jclass clazz, jint pid) {
    if (pid <= 0) return JNI_FALSE;
    int stat; pid_t done = waitpid(pid, &stat, WNOHANG);
    return done == 0 ? JNI_TRUE : JNI_FALSE;
}
