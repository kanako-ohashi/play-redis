package com.typesafe.play.redis

import javax.inject.{Inject, Provider}

import play.api.cache.{AsyncCacheApi, Cached, NamedCache, SyncCacheApi}
import play.api.inject._
import play.api.{Configuration, Environment}
import play.cache.{DefaultAsyncCacheApi, DefaultSyncCacheApi, NamedCacheImpl, AsyncCacheApi => JavaAsyncCacheApi, SyncCacheApi => JavaSyncCacheApi}
import redis.clients.jedis.JedisPool

import scala.concurrent.ExecutionContext

/**
 * Redis cache components for compile time injection
 */
trait RedisCacheComponents {
  def environment: Environment
  def configuration: Configuration
  def applicationLifecycle: ApplicationLifecycle
  def executionContext: ExecutionContext

  lazy val jedisPool: JedisPool = new JedisPoolProvider(configuration, applicationLifecycle).get

  /**
   * Use this to create with the given name.
   */
  def cacheApi(name: String): AsyncCacheApi = {
    new AsyncRedisCacheApi(new RedisCacheApi(name, jedisPool, environment.classLoader), executionContext)
  }

  lazy val redisDefaultCacheApi: AsyncCacheApi = cacheApi(RedisModule.defaultCacheNameFromConfig(configuration))
}

class RedisModule extends Module {

  import scala.collection.JavaConverters._

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    val ehcacheDisabled = if(configuration.underlying.hasPath("play.modules.disabled")){
      configuration.underlying.getStringList("play.modules.disabled").contains("play.api.cache.EhCacheModule")
    } else false
    val defaultCacheName = RedisModule.defaultCacheNameFromConfig(configuration)
    val bindCaches = configuration.underlying.getStringList("play.cache.redis.bindCaches").asScala

    // Creates a named cache qualifier
    def named(name: String): NamedCache = {
      new NamedCacheImpl(name)
    }

    // bind a cache with the given name
    def bindCache(name: String) = {
      val namedCache = named(name)
      val scalaSyncCacheApiKey = bind[SyncCacheApi].qualifiedWith(namedCache)
      val scalaAsyncCacheApiKey = bind[AsyncCacheApi].qualifiedWith(namedCache)
      val javaAsyncCacheApiKey = bind[JavaAsyncCacheApi].qualifiedWith(namedCache)
      Seq(
        scalaSyncCacheApiKey.to(new NamedScalaCacheApiProvider(name, bind[JedisPool], environment.classLoader)),
        scalaAsyncCacheApiKey.to(new NamedScalaAsyncCacheApiProvider(scalaSyncCacheApiKey, bind[ExecutionContext])),
        javaAsyncCacheApiKey.to(new NamedJavaAsyncCacheApiProvider(scalaAsyncCacheApiKey)),
        bind[JavaSyncCacheApi].qualifiedWith(namedCache).to(new NamedJavaCacheApiProvider(javaAsyncCacheApiKey))//,
        //bind[Cached].qualifiedWith(namedCache).to(new NamedCachedProvider(asyncCacheApiKey)) // TODO
        // TODO Define old CacheApi
      )
    }

    val defaultBindings = Seq(
      bind[JedisPool].toProvider[JedisPoolProvider]//,
      //bind[JavaCacheApi].to[DefaultJavaCacheApi] // TODO
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

class NamedScalaCacheApiProvider(namespace: String, client: BindingKey[JedisPool], classLoader: ClassLoader) extends Provider[SyncCacheApi] {
  @Inject private var injector: Injector = _
  lazy val get: SyncCacheApi = {
    new RedisCacheApi(namespace, injector.instanceOf(client), classLoader)
  }
}

class NamedScalaAsyncCacheApiProvider(cache: BindingKey[SyncCacheApi], ec: BindingKey[ExecutionContext]) extends Provider[AsyncCacheApi] {
  @Inject private var injector: Injector = _
  lazy val get: AsyncCacheApi = {
    new AsyncRedisCacheApi(injector.instanceOf(cache), injector.instanceOf(ec))
  }
}

class NamedJavaCacheApiProvider(key: BindingKey[JavaAsyncCacheApi]) extends Provider[JavaSyncCacheApi] {
  @Inject private var injector: Injector = _
  lazy val get: JavaSyncCacheApi = {
    new DefaultSyncCacheApi(injector.instanceOf(key))
  }
}

class NamedJavaAsyncCacheApiProvider(key: BindingKey[AsyncCacheApi]) extends Provider[JavaAsyncCacheApi] {
  @Inject private var injector: Injector = _
  lazy val get: JavaAsyncCacheApi = {
    new DefaultAsyncCacheApi(injector.instanceOf(key))
  }
}

//class NamedCachedProvider(key: BindingKey[AsyncCacheApi]) extends Provider[Cached] {
//  @Inject private var injector: Injector = _
//  lazy val get: Cached = {
//    new Cached(injector.instanceOf(key))
//  }
//}