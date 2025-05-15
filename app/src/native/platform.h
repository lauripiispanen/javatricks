#ifndef PLATFORM_H
#define PLATFORM_H

#if defined(__linux__)
  #define HAS_IO_URING 1
#else
  #define HAS_IO_URING 0
#endif

#endif // PLATFORM_H
