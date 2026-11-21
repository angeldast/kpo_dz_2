package pos.infrastructure

import kotlin.reflect.KClass

/**
 * Минимальный DI-контейнер: регистрация фабрик по типу (обычно по интерфейсу-порту),
 * ленивое создание одиночек, зависимости резолвятся друг через друга.
 */
class DiContainer {
    private val factories = mutableMapOf<KClass<*>, DiContainer.() -> Any>()
    private val singletons = mutableMapOf<KClass<*>, Any>()

    fun <T : Any> register(type: KClass<T>, factory: DiContainer.() -> T) {
        factories[type] = factory
    }

    inline fun <reified T : Any> singleton(noinline factory: DiContainer.() -> T) = register(T::class, factory)

    inline fun <reified T : Any> get(): T = resolve(T::class)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> resolve(type: KClass<T>): T {
        singletons[type]?.let { return it as T }
        val factory = factories[type] ?: error("В контейнере не зарегистрирован тип ${type.simpleName}")
        val instance = factory(this)
        singletons[type] = instance
        return instance as T
    }
}
