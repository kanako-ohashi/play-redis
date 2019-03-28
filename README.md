# Play Redis Plugin [![Build Status](https://travis-ci.org/bizreach/play-redis.svg?branch=master)](https://travis-ci.org/bizreach/play-redis)

A fork of the former official (but not maintained now) [redis plugin](https://github.com/playframework/play-plugins/tree/master/redis) for Play Framework.

This plugin provides support for [Redis](https://redis.io/) using the best Java driver [Jedis](https://github.com/xetorthio/jedis).

## Versions

|Plugin version  |Play version   |
|----------------|---------------|
|2.7.0           |2.7.x          |
|2.6.1           |2.6.x          |
|2.5.1           |2.5.x          |

## How to install

Add `"jp.co.bizreach" %% "play-modules-redis" % "(plugin version)"` to your dependencies.

## Features

### Configurations

- Point to your Redis server using configuration settings  `redis.host`, `redis.port`,  `redis.password` and `redis.database` (defaults: `localhost`, `6379`, `null` and `0`)
- Alternatively, specify a URI-based configuration using `redis.uri` (for example: `redis.uri="redis://user:password@localhost:6379"`).
- Set the timeout in milliseconds using `redis.timeout` (default is 2000).
- Configure any aspect of the connection pool. See [the documentation for commons-pool2 `GenericObjectPoolConfig`](https://commons.apache.org/proper/commons-pool/apidocs/org/apache/commons/pool2/impl/GenericObjectPoolConfig.html), the underlying pool implementation, for more information on each setting.
  - redis.pool.maxIdle
  - redis.pool.minIdle
  - redis.pool.maxTotal
  - redis.pool.maxWaitMillis
  - redis.pool.testOnBorrow
  - redis.pool.testOnReturn
  - redis.pool.testWhileIdle
  - redis.pool.timeBetweenEvictionRunsMillis
  - redis.pool.numTestsPerEvictionRun
  - redis.pool.minEvictableIdleTimeMillis
  - redis.pool.softMinEvictableIdleTimeMillis
  - redis.pool.lifo
  - redis.pool.blockWhenExhausted

### Allows direct access to Jedis

Because the underlying Jedis Pool was injected for the cache module to use, you can just inject the Jedis Pool yourself, something like this:

```scala
//scala
import javax.inject.Inject
import redis.clients.jedis.JedisPool

import play.api.mvc._

class TryIt @Inject()(jedisPool: JedisPool, cc: ControllerComponents) extends AbstractController(cc) {
  ...
}
```

```java
//java
import javax.inject.Inject;
import redis.clients.jedis.JedisPool;

import play.mvc.*;

public class TryIt extends Controller {

   //The JedisPool will be injected for you from the module
   @Inject JedisPool jedisPool;

   ...
}
```

This plugin also supports compile time DI via RedisCacheComponents. Mix this in with your custom application loader just like you would if you were using EhCacheComponents from the reference cache module.

## Accessing different caches

### Play 2.7 2.6

This plugin supports NamedCaches through key namespacing on a single Jedis pool. To add additional namespaces besides the default (play), the configuration would look like such:

```scala
play.cache.bindCaches = ["db-cache", "user-cache", "session-cache"]
```

### Play 2.5

The default cache module (EhCache) will be used for all non-named cache UNLESS this module (RedisModule) is the only cache module that was loaded. If this module is the only cache module being loaded, it will work as expected on named and non-named cache. To disable the default cache module so that this Redis Module can be the default cache you must put this in your configuration:

```scala
play.modules.disabled = ["play.api.cache.EhCacheModule"]
```

This plugin supports play 2.5 NamedCaches through key namespacing on a single Jedis pool. To add additional namespaces besides the default (play), the configuration would look like such:

```scala
play.cache.redis.bindCaches = ["db-cache", "user-cache", "session-cache"]
```

## Licence

This software is licensed under the Apache 2 license, quoted below.

Copyright 2018 BizReach (http://www.bizreach.co.jp/).  
Copyright 2012 Typesafe (http://www.typesafe.com).

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
