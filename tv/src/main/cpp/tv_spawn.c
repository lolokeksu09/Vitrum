#include <jni.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

/* Standalone TV module: Xray inherits only the existing VpnService TUN fd. */
JNIEXPORT jint JNICALL
Java_app_rayclient_tv_TvNativeProcess_spawn(
    JNIEnv *env, jclass ignored, jstring jexe, jstring jcfg,
    jstring jassets, jstring jlog, jint tun_fd
) {
    if (tun_fd < 0) return -1;
    const char *exe = (*env)->GetStringUTFChars(env, jexe, NULL);
    const char *cfg = (*env)->GetStringUTFChars(env, jcfg, NULL);
    const char *assets = (*env)->GetStringUTFChars(env, jassets, NULL);
    const char *log = (*env)->GetStringUTFChars(env, jlog, NULL);
    if (!exe || !cfg || !assets || !log) {
        if (exe) (*env)->ReleaseStringUTFChars(env, jexe, exe);
        if (cfg) (*env)->ReleaseStringUTFChars(env, jcfg, cfg);
        if (assets) (*env)->ReleaseStringUTFChars(env, jassets, assets);
        if (log) (*env)->ReleaseStringUTFChars(env, jlog, log);
        return -1;
    }
    pid_t pid = fork();
    if (pid == 0) {
        int flags = fcntl(tun_fd, F_GETFD);
        if (flags < 0 || fcntl(tun_fd, F_SETFD, flags & ~FD_CLOEXEC) < 0) {
            _exit(125);
        }
        char fd[16];
        snprintf(fd, sizeof(fd), "%d", tun_fd);
        setenv("XRAY_TUN_FD", fd, 1);
        setenv("xray.tun.fd", fd, 1);
        setenv("XRAY_LOCATION_ASSET", assets, 1);
        setenv("xray.location.asset", assets, 1);
        setenv("GOMEMLIMIT", "128MiB", 1);
        int log_fd = open(log, O_WRONLY | O_CREAT | O_APPEND, 0600);
        if (log_fd >= 0) {
            dup2(log_fd, STDOUT_FILENO);
            dup2(log_fd, STDERR_FILENO);
            if (log_fd > STDERR_FILENO) close(log_fd);
        }
        execl(exe, exe, "run", "-c", cfg, (char *)NULL);
        _exit(127);
    }
    (*env)->ReleaseStringUTFChars(env, jexe, exe);
    (*env)->ReleaseStringUTFChars(env, jcfg, cfg);
    (*env)->ReleaseStringUTFChars(env, jassets, assets);
    (*env)->ReleaseStringUTFChars(env, jlog, log);
    return (jint)pid;
}

JNIEXPORT void JNICALL
Java_app_rayclient_tv_TvNativeProcess_terminate(
    JNIEnv *env, jclass ignored, jint pid
) {
    if (pid > 0) kill((pid_t)pid, SIGTERM);
}

JNIEXPORT jboolean JNICALL
Java_app_rayclient_tv_TvNativeProcess_alive(
    JNIEnv *env, jclass ignored, jint pid
) {
    if (pid <= 0) return JNI_FALSE;
    int status;
    pid_t state = waitpid((pid_t)pid, &status, WNOHANG);
    return state == 0 ? JNI_TRUE : JNI_FALSE;
}
