#ifndef IO_URING_READER_H
#define IO_URING_READER_H

#include "platform.h"

int read_offsets_io_uring(const char *path, const long *offsets, int num_offsets, int chunk_size, char *buf_out);

#endif // IO_URING_READER_H