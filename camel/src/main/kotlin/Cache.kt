import java.util.*

/**
 * @author Ewan
 */

interface Cache {
    val id: UUID
    fun exists(key: Key): Boolean
    fun get(key: Key): CachedOperation
    fun put(key: Key, operation: Operation<*,*>)
    data class Key(val term: Term, val context: Context)
}

class HashMapCache : Cache {

    override val id = UUID.randomUUID()!!

    private val cache = mutableMapOf<Cache.Key, CachedOperation>()

    override fun exists(key: Cache.Key) = cache.containsKey(key)

    override fun get(key: Cache.Key): CachedOperation {
        return cache[key] ?: throw NoSuchElementException("Key not found in cache")
    }

    override fun put(key: Cache.Key, operation: Operation<*,*>) {
        cache.put(key, CachedOperation(operation))
    }
}