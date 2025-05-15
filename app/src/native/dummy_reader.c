#include "io_uring_reader.h"

#if !HAS_IO_URING
int read_offsets_io_uring(const char *path, const long *offsets, int num_offsets, int chunk_size, char *buf_out)
{
  return -42; // Not supported
}
#endif