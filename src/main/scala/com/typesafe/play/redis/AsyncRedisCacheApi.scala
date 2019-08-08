package com.typesafe.play.redis

import akka.Done
import javax.inject.Inject
import play.api.cache.{AsyncCacheApi, SyncCacheApi}

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class AsyncRedisCacheApi @Inject()(cache: SyncCacheApi, ec: ExecutionContext) extends AsyncCacheApi {

  override def set(key: String, value: Any, expiration: Duration): Future[Done] = {
    Future {
      cache.set(key, value, expiration)
      Done
    }(ec)
  }

  override def remove(key: String): Future[Done] = {
    Future {
      cache.remove(key)
      Done
    }(ec)
  }

  override def getOrElseUpdate[A](key: String, expiration: Duration)(orElse: => Future[A])(implicit evidence$1: ClassTag[A]): Future[A] =
      cache.getOrElseUpdate(key, expiration)(orElse)

  override def get[T](key: String)(implicit evidence$2: ClassTag[T]): Future[Option[T]] = {
    Future {
      cache.get[T](key)
    }(ec)
  }

  override def removeAll(): Future[Done] = ???

}
