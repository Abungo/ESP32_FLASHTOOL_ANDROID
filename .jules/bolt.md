## 2024-05-23 - Optimizing InputStream Reading
**Learning:** Byte-by-byte reading of files (`inputStream.read()`) is a significant performance bottleneck due to excessive method call overhead and potential system calls. This is especially critical in mobile apps when reading assets or firmware files.
**Action:** Always replace byte-by-byte reading loops with buffered reading using `byte[] buffer` (e.g., 4KB) to drastically reduce overhead.
