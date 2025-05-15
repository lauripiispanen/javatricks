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

// Read data at multiple offsets using io_uring with optimized performance
int read_offsets_io_uring(const char *path, const long *offsets, int num_offsets, int chunk_size, char *buf_out) {
    struct io_uring ring;
    struct io_uring_sqe *sqe;
    struct io_uring_cqe **cqes;
    int fd, i, ret, commas = 0;
    int fixed_files[1];
    
    // Initialize io_uring with queue depth equal to number of offsets
    ret = io_uring_queue_init(num_offsets, &ring, IORING_SETUP_SQPOLL);
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
    
    // Register the file descriptor
    fixed_files[0] = fd;
    ret = io_uring_register_files(&ring, fixed_files, 1);
    if (ret < 0) {
        fprintf(stderr, "io_uring_register_files failed: %d\n", ret);
        close(fd);
        io_uring_queue_exit(&ring);
        return -1;
    }
    
    // Register the buffer
    ret = io_uring_register_buffers(&ring, 
                                  (struct iovec[1]){
                                      { .iov_base = buf_out, .iov_len = chunk_size * num_offsets }
                                  }, 1);
    if (ret < 0) {
        fprintf(stderr, "io_uring_register_buffers failed: %d\n", ret);
        close(fd);
        io_uring_queue_exit(&ring);
        return -1;
    }
    
    // Prepare read operations for each offset using fixed buffers and files
    for (i = 0; i < num_offsets; i++) {
        sqe = io_uring_get_sqe(&ring);
        if (!sqe) {
            fprintf(stderr, "io_uring_get_sqe failed\n");
            close(fd);
            io_uring_queue_exit(&ring);
            return -1;
        }
        
        // Use IORING_OP_READ_FIXED with the registered buffer and file
        io_uring_prep_read_fixed(sqe, 0, buf_out + (i * chunk_size), chunk_size, offsets[i], 0);
        sqe->flags |= IOSQE_FIXED_FILE; // Use registered file
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
    
    // Allocate space for the completion queue entries
    cqes = malloc(num_offsets * sizeof(struct io_uring_cqe *));
    if (!cqes) {
        fprintf(stderr, "malloc failed\n");
        close(fd);
        io_uring_queue_exit(&ring);
        return -1;
    }
    
    // Use batch processing for completions
    int head, processed = 0;
    int count;
    
    // Submit and wait for completions
    ret = io_uring_submit_and_wait(&ring, num_offsets);
    if (ret < 0) {
        fprintf(stderr, "io_uring_submit_and_wait failed: %d\n", ret);
        free(cqes);
        close(fd);
        io_uring_queue_exit(&ring);
        return -1;
    }
    
    // Process completions in batches
    while (processed < num_offsets) {
        count = io_uring_peek_batch_cqe(&ring, cqes, num_offsets - processed);
        if (count <= 0) {
            // No completions yet, wait for more
            ret = io_uring_wait_cqe(&ring, &cqes[0]);
            if (ret < 0) {
                fprintf(stderr, "io_uring_wait_cqe failed: %d\n", ret);
                free(cqes);
                close(fd);
                io_uring_queue_exit(&ring);
                return -1;
            }
            count = 1;
        }
        
        // Process each completion in the batch
        for (i = 0; i < count; i++) {
            struct io_uring_cqe *cqe = cqes[i];
            
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
        
        processed += count;
    }
    
    // Clean up
    free(cqes);
    io_uring_unregister_buffers(&ring);
    io_uring_unregister_files(&ring);
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
