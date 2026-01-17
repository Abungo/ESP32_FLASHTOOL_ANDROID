## 2024-05-23 - Optimizing InputStream Reading
**Learning:** Byte-by-byte reading of files (`inputStream.read()`) is a significant performance bottleneck due to excessive method call overhead and potential system calls. This is especially critical in mobile apps when reading assets or firmware files.
**Action:** Always replace byte-by-byte reading loops with buffered reading using `byte[] buffer` (e.g., 4KB) to drastically reduce overhead.

## 2024-05-24 - Array Copying Optimization Surprises
**Learning:** `System.arraycopy` can be slower than a manual loop for appending very small arrays (1-2 bytes) to a larger array in a tight loop on some JVMs. However, `ByteArrayOutputStream` is vastly superior (10-16x faster) for incrementally building byte arrays, avoiding O(N²) allocation overhead completely.
**Action:** Prefer `ByteArrayOutputStream` over repeated array concatenation. Test `System.arraycopy` performance for small data before assuming it's faster.
