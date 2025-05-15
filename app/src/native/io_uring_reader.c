#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <errno.h>
#include "io_uring_reader.h"
#include <jni.h>
#include "fi_lauripiispanen_benchmarks_io_IoUringBridge.h"

#if HAS_IO_URING
#include <liburing.h>

// Read data at multiple offsets using io_uring
int read_offsets_io_uring(const char *path, const long *offsets, int num_offsets, int chunk_size, char *buf_out) {
    struct io_uring ring;
    struct io_uring_sqe *sqe;
    struct io_uring_cqe *cqe;
    int fd, i, ret, commas = 0;
    
    // Initialize io_uring with queue depth equal to number of offsets
    ret = io_uring_queue_init(num_offsets, &ring, 0);
    if (ret < 0) {
        fprintf(stderr, "io_uring_queue_init failed: %d\n", ret);
        return -1;
    }
    
    // Open the file
    fd = open(path, O_RDONLY);
    if (fd < 0) {
        fprintf(stderr, "open failed: %s\n", strerror(errno));
        io_uring_queue_exit(&ring);
        return -1;
    }
    
    // Prepare read operations for each offset
    for (i = 0; i < num_offsets; i++) {
        sqe = io_uring_get_sqe(&ring);
        if (!sqe) {
            fprintf(stderr, "io_uring_get_sqe failed\n");
            close(fd);
            io_uring_queue_exit(&ring);
            return -1;
        }
        
        // Set up the read operation at the specified offset
        io_uring_prep_read(sqe, fd, buf_out + (i * chunk_size), chunk_size, offsets[i]);
        sqe->user_data = i; // Store the index for later identification
    }
    
    // Submit all read operations at once
    ret = io_uring_submit(&ring);
    if (ret < 0) {
        fprintf(stderr, "io_uring_submit failed: %d\n", ret);
        close(fd);
        io_uring_queue_exit(&ring);
        return -1;
    }
    
    // Process all completions
    for (i = 0; i < num_offsets; i++) {
        ret = io_uring_wait_cqe(&ring, &cqe);
        if (ret < 0) {
            fprintf(stderr, "io_uring_wait_cqe failed: %d\n", ret);
            close(fd);
            io_uring_queue_exit(&ring);
            return -1;
        }
        
        // Check the result of the operation
        if (cqe->res < 0) {
            fprintf(stderr, "read failed: %s\n", strerror(-cqe->res));
            io_uring_cqe_seen(&ring, cqe);
            continue;
        }
        
        // Process the read data - count commas just like the Java benchmarks
        int index = cqe->user_data;
        int bytes_read = cqe->res;
        char *buf_ptr = buf_out + (index * chunk_size);
        
        for (int j = 0; j < bytes_read; j++) {
            if (buf_ptr[j] == ',') {
                commas++;
            }
        }
        
        io_uring_cqe_seen(&ring, cqe);
    }
    
    // Clean up
    close(fd);
    io_uring_queue_exit(&ring);
    
    return commas;
}
#endif

// JNI implementation
JNIEXPORT jint JNICALL Java_fi_lauripiispanen_benchmarks_io_IoUringBridge_readOffsets
  (JNIEnv *env, jclass cls, jstring jfilePath, jlongArray joffsets, jint chunkSize, jbyteArray jbuffer) {
    
#if HAS_IO_URING
    const char *filePath = (*env)->GetStringUTFChars(env, jfilePath, NULL);
    
    // Get the offset array
    jsize num_offsets = (*env)->GetArrayLength(env, joffsets);
    jlong *offsets = (*env)->GetLongArrayElements(env, joffsets, NULL);
    
    // Get the buffer
    jsize buffer_size = (*env)->GetArrayLength(env, jbuffer);
    jbyte *buffer = (*env)->GetByteArrayElements(env, jbuffer, NULL);
    
    // Call the native function
    int result = read_offsets_io_uring(filePath, (const long*)offsets, (int)num_offsets, (int)chunkSize, (char*)buffer);
    
    // Release resources
    (*env)->ReleaseStringUTFChars(env, jfilePath, filePath);
    (*env)->ReleaseLongArrayElements(env, joffsets, offsets, 0);
    (*env)->ReleaseByteArrayElements(env, jbuffer, buffer, 0);
    
    return result;
#else
    // Not supported on this platform
    return -42;
#endif
}
