## 2024-05-23 - Micro-optimizing Byte Array Construction
**Learning:** `System.arraycopy` vs Manual Loop vs Helper Method
In a microbenchmark, replacing manual loops with `System.arraycopy` yielded a ~3.6x speedup. However, a "zero-allocation" approach using a helper method `putInt` (manual bit shifting into array) was surprisingly *slower* (0.58x of original) than creating small temporary arrays and using `System.arraycopy`. This might be due to JIT intrinsics for `System.arraycopy` being highly optimized versus the overhead of method calls and array bounds checking in a non-inlined helper method in a tight loop.

## 2024-05-24 - Array Copying Optimization Surprises
**Learning:** `System.arraycopy` can be slower than a manual loop for appending very small arrays (1-2 bytes) to a larger array in a tight loop on some JVMs. However, `ByteArrayOutputStream` is vastly superior (10-16x faster) for incrementally building byte arrays, avoiding O(N²) allocation overhead completely.
**Action:** Prefer `ByteArrayOutputStream` over repeated array concatenation. Test `System.arraycopy` performance for small data before assuming it's faster.

## 2024-05-25 - Packet Construction Optimization
**Learning:** Repeatedly appending to byte arrays to build fixed-structure packets (header + payload) creates excessive garbage and is O(N) with high constant factors.
**Action:** For fixed-size or known-size packets, pre-allocate a single `byte[]` buffer and populate it using offsets and `System.arraycopy`. This avoids intermediate allocations entirely and is significantly faster (~4x speedup observed).
