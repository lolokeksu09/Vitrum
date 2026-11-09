#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/wait.h>

/* Запускает xray как дочерний процесс, сохраняя открытым TUN-дескриптор. */
JNIEXPORT jint JNICALL Java_app_rayclient_Native_spawn(JNIEnv *env, jclass c,
        jstring jexe, jstring jcfg, jstring jassets, jstring jlog, jint fd) {
    const char *exe = (*env)->GetStringUTFChars(env, jexe, 0);
    const char *cfg = (*env)->GetStringUTFChars(env, jcfg, 0);
    const char *assets = (*env)->GetStringUTFChars(env, jassets, 0);
    const char *log = (*env)->GetStringUTFChars(env, jlog, 0);
    pid_t pid = fork();
    if (pid == 0) {
        if (fd >= 0) {
            int fl = fcntl(fd, F_GETFD);
            if (fl >= 0) fcntl(fd, F_SETFD, fl & ~FD_CLOEXEC);
            char num[16];
            snprintf(num, sizeof num, "%d", fd);
            setenv("xray.tun.fd", num, 1);
            setenv("XRAY_TUN_FD", num, 1);
        }
        setenv("xray.location.asset", assets, 1);
        setenv("XRAY_LOCATION_ASSET", assets, 1);
        int lf = open(log, O_WRONLY | O_CREAT | O_APPEND, 0600);
        if (lf >= 0) { dup2(lf, 1); dup2(lf, 2); }
        execl(exe, exe, "run", "-c", cfg, (char *)NULL);
        _exit(127);
    }
    (*env)->ReleaseStringUTFChars(env, jexe, exe);
    (*env)->ReleaseStringUTFChars(env, jcfg, cfg);
    (*env)->ReleaseStringUTFChars(env, jassets, assets);
    (*env)->ReleaseStringUTFChars(env, jlog, log);
    return (jint)pid;
}

JNIEXPORT void JNICALL Java_app_rayclient_Native_kill(JNIEnv *env, jclass c, jint pid) {
    if (pid > 0) { kill(pid, SIGTERM); usleep(300000); kill(pid, SIGKILL); int st; waitpid(pid, &st, WNOHANG); }
}

JNIEXPORT jboolean JNICALL Java_app_rayclient_Native_alive(JNIEnv *env, jclass c, jint pid) {
    if (pid <= 0) return JNI_FALSE;
    int st; pid_t r = waitpid(pid, &st, WNOHANG);
    return r == 0 ? JNI_TRUE : JNI_FALSE;
}
