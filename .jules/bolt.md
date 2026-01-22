## 2024-05-23 - Micro-optimizing Byte Array Construction
**Learning:** `System.arraycopy` vs Manual Loop vs Helper Method
In a microbenchmark, replacing manual loops with `System.arraycopy` yielded a ~3.6x speedup. However, a "zero-allocation" approach using a helper method `putInt` (manual bit shifting into array) was surprisingly *slower* (0.58x of original) than creating small temporary arrays and using `System.arraycopy`. This might be due to JIT intrinsics for `System.arraycopy` being highly optimized versus the overhead of method calls and array bounds checking in a non-inlined helper method in a tight loop.

**Action:** Prefer `System.arraycopy` for array manipulation in Java. Be wary of "zero-allocation" micro-optimizations involving manual byte packing without benchmarking, as JIT optimizations on standard patterns often outperform custom logic.
