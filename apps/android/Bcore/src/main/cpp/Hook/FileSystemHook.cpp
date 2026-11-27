#include "FileSystemHook.h"
#include "Dobby/dobby.h"
#include "Log.h"
#include "IO.h"
#include "xdl.h"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

typedef int (*OpenFn)(const char *pathname, int flags, ...);
typedef FILE *(*FopenFn)(const char *pathname, const char *mode);
typedef FILE *(*FreopenFn)(const char *pathname, const char *mode, FILE *stream);
typedef int (*StatFn)(const char *pathname, struct stat *statbuf);
typedef DIR *(*OpendirFn)(const char *name);
typedef int (*MkdirFn)(const char *pathname, mode_t mode);
typedef int (*UnlinkFn)(const char *pathname);
typedef int (*RemoveFn)(const char *pathname);
typedef int (*RenameFn)(const char *oldpath, const char *newpath);
typedef ssize_t (*ReadlinkFn)(const char *pathname, char *buf, size_t bufsiz);
typedef int (*ChmodFn)(const char *pathname, mode_t mode);
typedef int (*AccessFn)(const char *pathname, int mode);

static OpenFn orig_open = nullptr;
static OpenFn orig_open64 = nullptr;
static FopenFn orig_fopen = nullptr;
static FreopenFn orig_freopen = nullptr;
static StatFn orig_stat = nullptr;
static StatFn orig_lstat = nullptr;
static OpendirFn orig_opendir = nullptr;
static MkdirFn orig_mkdir = nullptr;
static UnlinkFn orig_unlink = nullptr;
static RemoveFn orig_remove = nullptr;
static RenameFn orig_rename = nullptr;
static ReadlinkFn orig_readlink = nullptr;
static ChmodFn orig_chmod = nullptr;
static AccessFn orig_access = nullptr;

static bool isBlockedPath(const char *pathname) {
    return pathname != nullptr &&
           (strstr(pathname, "resource-cache") ||
            strstr(pathname, "@idmap") ||
            strstr(pathname, ".frro") ||
            strstr(pathname, "systemui") ||
            strstr(pathname, "data@resource-cache@"));
}

static const char *redirectPath(const char *pathname) {
    if (pathname == nullptr) {
        return nullptr;
    }
    const char *redirected = IO::redirectPath(pathname);
    if (redirected != nullptr && redirected != pathname) {
        ALOGD("FileSystemHook: redirect %s -> %s", pathname, redirected);
    }
    return redirected;
}

static void releaseRedirectedPath(const char *original, const char *redirected) {
    if (redirected != nullptr && redirected != original) {
        free(const_cast<char *>(redirected));
    }
}

static bool openNeedsMode(int flags) {
    if ((flags & O_CREAT) != 0) {
        return true;
    }
#ifdef O_TMPFILE
    if ((flags & O_TMPFILE) == O_TMPFILE) {
        return true;
    }
#endif
    return false;
}

int new_open(const char *pathname, int flags, ...) {
    if (orig_open == nullptr) {
        errno = ENOSYS;
        return -1;
    }
    if (isBlockedPath(pathname)) {
        errno = ENOENT;
        return -1;
    }

    va_list args;
    va_start(args, flags);
    mode_t mode = 0;
    if (openNeedsMode(flags)) {
        mode = va_arg(args, mode_t);
    }
    va_end(args);

    const char *redirected = redirectPath(pathname);
    int result = openNeedsMode(flags)
                 ? orig_open(redirected, flags, mode)
                 : orig_open(redirected, flags);
    releaseRedirectedPath(pathname, redirected);
    return result;
}

int new_open64(const char *pathname, int flags, ...) {
    if (orig_open64 == nullptr) {
        errno = ENOSYS;
        return -1;
    }
    if (isBlockedPath(pathname)) {
        errno = ENOENT;
        return -1;
    }

    va_list args;
    va_start(args, flags);
    mode_t mode = 0;
    if (openNeedsMode(flags)) {
        mode = va_arg(args, mode_t);
    }
    va_end(args);

    const char *redirected = redirectPath(pathname);
    int result = openNeedsMode(flags)
                 ? orig_open64(redirected, flags, mode)
                 : orig_open64(redirected, flags);
    releaseRedirectedPath(pathname, redirected);
    return result;
}

FILE *new_fopen(const char *pathname, const char *mode) {
    if (orig_fopen == nullptr) {
        errno = ENOSYS;
        return nullptr;
    }
    if (isBlockedPath(pathname)) {
        errno = ENOENT;
        return nullptr;
    }
    const char *redirected = redirectPath(pathname);
    FILE *result = orig_fopen(redirected, mode);
    releaseRedirectedPath(pathname, redirected);
    return result;
}

FILE *new_freopen(const char *pathname, const char *mode, FILE *stream) {
    if (orig_freopen == nullptr) {
        errno = ENOSYS;
        return nullptr;
    }
    if (isBlockedPath(pathname)) {
        errno = ENOENT;
        return nullptr;
    }
    const char *redirected = redirectPath(pathname);
    FILE *result = orig_freopen(redirected, mode, stream);
    releaseRedirectedPath(pathname, redirected);
    return result;
}

int new_stat(const char *pathname, struct stat *statbuf) {
    if (orig_stat == nullptr) {
        errno = ENOSYS;
        return -1;
    }
    if (isBlockedPath(pathname)) {
        errno = ENOENT;
        return -1;
    }
    const char *redirected = redirectPath(pathname);
    int result = orig_stat(redirected, statbuf);
    releaseRedirectedPath(pathname, redirected);
    return result;
}

int new_lstat(const char *pathname, struct stat *statbuf) {
    if (orig_lstat == nullptr) {
        errno = ENOSYS;
        return -1;
    }
    if (isBlockedPath(pathname)) {
        errno = ENOENT;
        return -1;
    }
    const char *redirected = redirectPath(pathname);
    int result = orig_lstat(redirected, statbuf);
    releaseRedirectedPath(pathname, redirected);
    return result;
}

DIR *new_opendir(const char *name) {
    if (orig_opendir == nullptr) {
        errno = ENOSYS;
        return nullptr;
    }
    if (isBlockedPath(name)) {
        errno = ENOENT;
        return nullptr;
    }
    const char *redirected = redirectPath(name);
    DIR *result = orig_opendir(redirected);
    releaseRedirectedPath(name, redirected);
    return result;
}

int new_mkdir(const char *pathname, mode_t mode) {
    if (orig_mkdir == nullptr) {
        errno = ENOSYS;
        return -1;
    }
    if (isBlockedPath(pathname)) {
        errno = ENOENT;
        return -1;
    }
    const char *redirected = redirectPath(pathname);
    int result = orig_mkdir(redirected, mode);
    releaseRedirectedPath(pathname, redirected);
    return result;
}

int new_unlink(const char *pathname) {
    if (orig_unlink == nullptr) {
        errno = ENOSYS;
        return -1;
    }
    if (isBlockedPath(pathname)) {
        errno = ENOENT;
        return -1;
    }
    const char *redirected = redirectPath(pathname);
    int result = orig_unlink(redirected);
    releaseRedirectedPath(pathname, redirected);
    return result;
}

int new_remove(const char *pathname) {
    if (orig_remove == nullptr) {
        errno = ENOSYS;
        return -1;
    }
    if (isBlockedPath(pathname)) {
        errno = ENOENT;
        return -1;
    }
    const char *redirected = redirectPath(pathname);
    int result = orig_remove(redirected);
    releaseRedirectedPath(pathname, redirected);
    return result;
}

int new_rename(const char *oldpath, const char *newpath) {
    if (orig_rename == nullptr) {
        errno = ENOSYS;
        return -1;
    }
    if (isBlockedPath(oldpath) || isBlockedPath(newpath)) {
        errno = ENOENT;
        return -1;
    }
    const char *redirectedOld = redirectPath(oldpath);
    const char *redirectedNew = redirectPath(newpath);
    int result = orig_rename(redirectedOld, redirectedNew);
    releaseRedirectedPath(oldpath, redirectedOld);
    releaseRedirectedPath(newpath, redirectedNew);
    return result;
}

ssize_t new_readlink(const char *pathname, char *buf, size_t bufsiz) {
    if (orig_readlink == nullptr) {
        errno = ENOSYS;
        return -1;
    }
    if (isBlockedPath(pathname)) {
        errno = ENOENT;
        return -1;
    }
    const char *redirected = redirectPath(pathname);
    ssize_t result = orig_readlink(redirected, buf, bufsiz);
    releaseRedirectedPath(pathname, redirected);
    return result;
}

int new_chmod(const char *pathname, mode_t mode) {
    if (orig_chmod == nullptr) {
        errno = ENOSYS;
        return -1;
    }
    if (isBlockedPath(pathname)) {
        errno = ENOENT;
        return -1;
    }
    const char *redirected = redirectPath(pathname);
    int result = orig_chmod(redirected, mode);
    releaseRedirectedPath(pathname, redirected);
    return result;
}

int new_access(const char *pathname, int mode) {
    if (orig_access == nullptr) {
        errno = ENOSYS;
        return -1;
    }
    if (isBlockedPath(pathname)) {
        errno = ENOENT;
        return -1;
    }
    const char *redirected = redirectPath(pathname);
    int result = orig_access(redirected, mode);
    releaseRedirectedPath(pathname, redirected);
    return result;
}

static bool hookSymbol(void *handle,
                       const char *name,
                       void *replacement,
                       void **origin) {
    void *target = xdl_sym(handle, name, nullptr);
    if (target == nullptr) {
        ALOGE("FileSystemHook: Symbol %s not found", name);
        return false;
    }
    if (DobbyHook(target, replacement, origin) != 0) {
        ALOGE("FileSystemHook: Failed to hook %s", name);
        return false;
    }
    ALOGD("FileSystemHook: Hooked %s at %p", name, target);
    return true;
}

void FileSystemHook::init() {
    ALOGD("FileSystemHook: Initializing file system hooks");

    void *handle = xdl_open("libc.so", XDL_DEFAULT);
    if (handle == nullptr) {
        ALOGE("FileSystemHook: Failed to open libc.so");
        return;
    }

    void *openTarget = xdl_sym(handle, "open", nullptr);
    void *open64Target = xdl_sym(handle, "open64", nullptr);
    if (openTarget != nullptr) {
        if (DobbyHook(openTarget, (void *) new_open, (void **) &orig_open) == 0) {
            ALOGD("FileSystemHook: Hooked open at %p", openTarget);
        } else {
            ALOGE("FileSystemHook: Failed to hook open");
        }
    }
    if (open64Target != nullptr && open64Target != openTarget) {
        if (DobbyHook(open64Target, (void *) new_open64, (void **) &orig_open64) == 0) {
            ALOGD("FileSystemHook: Hooked open64 at %p", open64Target);
        } else {
            ALOGE("FileSystemHook: Failed to hook open64");
        }
    } else if (open64Target != nullptr) {
        orig_open64 = orig_open;
    }

    hookSymbol(handle, "fopen", (void *) new_fopen, (void **) &orig_fopen);
    hookSymbol(handle, "freopen", (void *) new_freopen, (void **) &orig_freopen);
    hookSymbol(handle, "stat", (void *) new_stat, (void **) &orig_stat);
    hookSymbol(handle, "lstat", (void *) new_lstat, (void **) &orig_lstat);
    hookSymbol(handle, "opendir", (void *) new_opendir, (void **) &orig_opendir);
    hookSymbol(handle, "mkdir", (void *) new_mkdir, (void **) &orig_mkdir);
    hookSymbol(handle, "unlink", (void *) new_unlink, (void **) &orig_unlink);
    hookSymbol(handle, "remove", (void *) new_remove, (void **) &orig_remove);
    hookSymbol(handle, "rename", (void *) new_rename, (void **) &orig_rename);
    hookSymbol(handle, "readlink", (void *) new_readlink, (void **) &orig_readlink);
    hookSymbol(handle, "chmod", (void *) new_chmod, (void **) &orig_chmod);
    hookSymbol(handle, "access", (void *) new_access, (void **) &orig_access);

    xdl_close(handle);
}
