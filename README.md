There are a few issues in this implementation that should be addressed before deploying it in a production system with the expected level of concurrency and throughput.

## 1. Expired entries are never removed

The cache checks the TTL during `get()`, but expired entries are not removed from the map. The code simply returns `null`:

```kotlin
fun get(key: K): V? {
    val entry = cache[key]
    if (entry != null) {
        if (System.currentTimeMillis() - entry.timestamp < ttlMs) {
            return entry.value
        }
    }
    return null
}
```

This means expired entries remain in the `ConcurrentHashMap` indefinitely. Over time, especially with hundreds of writes per second, the cache will accumulate stale entries and continue growing in memory even though those entries are no longer usable. This can lead to increased GC pressure and potentially memory exhaustion.

A simple improvement would be to remove expired entries when they are detected:

```kotlin
fun get(key: K): V? {
    val entry = cache[key] ?: return null
    val now = System.currentTimeMillis()

    if (now - entry.timestamp >= ttlMs) {
        cache.remove(key, entry) // safe conditional removal
        return null
    }

    return entry.value
}
```

The conditional `remove(key, entry)` is important because another thread may have updated the value after it was read.

---

## 2. `size()` does not reflect valid cache entries

Currently the method is implemented as:

```kotlin
fun size(): Int {
    return cache.size
}
```

Since expired entries are not removed, `size()` will include stale entries that are no longer valid. In production this can make monitoring misleading because the reported cache size will not represent the number of usable items.

---

## 3. Reliance on `System.currentTimeMillis()`

TTL checks are based on:

```kotlin
System.currentTimeMillis()
```

This method uses wall-clock time, which can change due to NTP adjustments or manual clock updates. If the system clock moves backwards or forwards, expiration behavior may become inconsistent.

For measuring elapsed time it is generally safer to use a monotonic time source such as `System.nanoTime()`:

```kotlin
data class CacheEntry<V>(val value: V, val createdAt: Long)

fun put(key: K, value: V) {
    cache[key] = CacheEntry(value, System.nanoTime())
}
```

---

## 4. Cache is unbounded

The cache has no capacity limit:

```kotlin
private val cache = ConcurrentHashMap<K, CacheEntry<V>>()
```

If the application receives many distinct keys, the map can grow indefinitely. Even with a TTL of one minute, a high write rate with mostly unique keys could result in very large memory usage.

Production caches typically include a maximum size and an eviction policy (e.g., LRU).

---

## 5. Cleanup only happens on reads

Expiration is only checked in `get()`. If a key is written once and never read again, the expired entry will stay in the cache forever.

In write-heavy workloads this can behave like a slow memory leak. A background cleanup task or eviction strategy would help address this.

---

## 6. Ambiguous `null` return value

The method signature is:

```kotlin
fun get(key: K): V?
```

If `V` is nullable, a `null` result could mean:

- the key does not exist
- the entry expired
- the stored value itself is `null`

This ambiguity can lead to incorrect assumptions by callers. One option is to disallow null values or return a wrapper type that clearly represents the cache result.

---

## Summary

The main concerns with this implementation are:

- expired entries are never removed  
- the cache is unbounded and may grow indefinitely  
- `size()` does not reflect the number of valid entries  
- expiration depends on wall-clock time  
- cleanup only occurs during reads  
- `null` results are ambiguous  

While `ConcurrentHashMap` ensures thread-safe access to the underlying map, the cache lacks several behaviors typically required in production systems, such as proper eviction, expiration cleanup, and reliable time handling. In practice, it would be safer either to extend this implementation to address these issues or to use a mature caching library such as **Caffeine**, which is designed to handle high concurrency and eviction efficiently.
