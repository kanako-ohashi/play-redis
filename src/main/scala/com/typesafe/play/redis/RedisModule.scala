package com.typesafe.play.redis

import javax.inject.{Inject, Provider}

import play.api.cache._
import play.api.inject._
import play.api.{Configuration, Environment}
import play.cache.NamedCacheImpl
import redis.clients.jedis.JedisPool

import scala.concurrent.ExecutionContext

/**
 * Redis cache components for compile time injection
 */
trait RedisCacheComponents {
  def environment: Environment
  def configuration: Configuration
  def applicationLifecycle: ApplicationLifecycle

  lazy val jedisPool: JedisPool = new JedisPoolProvider(configuration, applicationLifecycle).get

  /**
   * Use this to create with the given name.
   */
  def cacheApi(name: String): SyncCacheApi = {
    new RedisCacheApi(name, jedisPool, environment.classLoader)
  }

  lazy val redisDefaultCacheApi: SyncCacheApi = cacheApi(RedisModule.defaultCacheNameFromConfig(configuration))
}

class RedisModule extends Module {

  import scala.collection.JavaConversions._

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    val ehcacheDisabled = configuration.getStringList("play.modules.disabled").fold(false)(x => x.contains("play.api.cache.EhCacheModule"))
    val defaultCacheName = RedisModule.defaultCacheNameFromConfig(configuration)
    val bindCaches = configuration.underlying.getStringList("play.cache.redis.bindCaches").toSeq

    // Creates a named cache qualifier
    def named(name: String): NamedCache = {
      new NamedCacheImpl(name)
    }

    // bind a cache with the given name
    def bindCache(name: String) = {
      val namedCache = named(name)
      val cacheApiKey = bind[SyncCacheApi].qualifiedWith(namedCache)
      val asyncCacheApiKey = bind[AsyncCacheApi].qualifiedWith(namedCache)
      Seq(
        cacheApiKey.to(new NamedRedisCacheApiProvider(name, bind[JedisPool], environment.classLoader)),
        asyncCacheApiKey.to(new NamedAsyncRedisCacheApiProvider(bind[SyncCacheApi].qualifiedWith(namedCache), bind[ExecutionContext]))//,
        // bind[JavaCacheApi].qualifiedWith(namedCache).to(new NamedJavaCacheApiProvider(cacheApiKey)),
        //bind[Cached].qualifiedWith(namedCache).to(new NamedCachedProvider(cacheApiKey))
      )
    }

    val defaultBindings = Seq(
      bind[JedisPool].toProvider[JedisPoolProvider]//,
      //bind[JavaCacheApi].to[DefaultJavaCacheApi]
    ) ++ bindCaches.flatMap(bindCache)

    // alias the default cache to the unqualified implementation only if the default cache is disabled as it already does this.
    if (ehcacheDisabled)
      Seq(
        bind[SyncCacheApi].to(bind[SyncCacheApi].qualifiedWith(named(defaultCacheName))),
        bind[AsyncCacheApi].to(bind[AsyncCacheApi].qualifiedWith(named(defaultCacheName)))
      ) ++ bindCache(defaultCacheName) ++ defaultBindings
    else
      defaultBindings
  }
}

object RedisModule {
  def defaultCacheNameFromConfig(configuration: Configuration): String = {
    configuration.underlying.getString("play.cache.defaultCache")
  }
}

class NamedRedisCacheApiProvider(namespace: String, client: BindingKey[JedisPool], classLoader: ClassLoader) extends Provider[SyncCacheApi] {
  @Inject private var injector: Injector = _
  lazy val get: SyncCacheApi = {
    new RedisCacheApi(namespace, injector.instanceOf(client), classLoader)
  }
}

class NamedAsyncRedisCacheApiProvider(cache: BindingKey[SyncCacheApi], ec: BindingKey[ExecutionContext]) extends Provider[AsyncCacheApi] {
  @Inject private var injector: Injector = _
  lazy val get: AsyncCacheApi = {
    new AsyncRedisCacheApi(injector.instanceOf(cache), injector.instanceOf(ec))
  }
}
