package com.typesafe.play.redis

import javax.inject.{Inject, Provider}

import akka.stream.Materializer
import play.api.cache._
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
    new AsyncRedisCacheApi(new SyncRedisCacheApi(name, jedisPool, environment.classLoader), executionContext)
  }

  lazy val redisDefaultCacheApi: AsyncCacheApi = cacheApi(RedisModule.defaultCacheNameFromConfig(configuration))
}

class RedisModule extends Module {

  import scala.collection.JavaConverters._

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    val defaultCacheName = RedisModule.defaultCacheNameFromConfig(configuration)
    val bindCaches = configuration.underlying.getStringList("play.cache.bindCaches").asScala

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
        bind[JavaSyncCacheApi].qualifiedWith(namedCache).to(new NamedJavaCacheApiProvider(javaAsyncCacheApiKey)),
        bind[Cached].qualifiedWith(namedCache).to(new NamedCachedProvider(scalaAsyncCacheApiKey, bind[Materializer]))
      )
    }

    // bind unnamed caches to default named caches
    Seq(
      bind[SyncCacheApi].to(bind[SyncCacheApi].qualifiedWith(named(defaultCacheName))),
      bind[AsyncCacheApi].to(bind[AsyncCacheApi].qualifiedWith(named(defaultCacheName))),
      bind[JavaSyncCacheApi].to(bind[JavaSyncCacheApi].qualifiedWith(named(defaultCacheName))),
      bind[JavaAsyncCacheApi].to(bind[JavaAsyncCacheApi].qualifiedWith(named(defaultCacheName))),
      bind[JedisPool].toProvider[JedisPoolProvider]
    ) ++ bindCache(defaultCacheName) ++ bindCaches.flatMap(bindCache)
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
    new SyncRedisCacheApi(namespace, injector.instanceOf(client), classLoader)
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

class NamedCachedProvider(key: BindingKey[AsyncCacheApi], m: BindingKey[Materializer]) extends Provider[Cached] {
  @Inject private var injector: Injector = _
  lazy val get: Cached = {
    new Cached(injector.instanceOf(key))(injector.instanceOf(m))
  }
}