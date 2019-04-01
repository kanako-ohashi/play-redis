package com.typesafe.play.redis

import java.io._
import java.nio.charset.StandardCharsets
import javax.inject.{Inject, Singleton}

import biz.source_code.base64Coder.Base64Coder
import play.api.Logger
import play.api.cache.SyncCacheApi
import redis.clients.jedis.{Jedis, JedisPool}

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

@Singleton
class SyncRedisCacheApi @Inject()(val namespace: String, jedisPool: JedisPool, classLoader: ClassLoader) extends SyncCacheApi {

  private val logger = Logger(getClass)
  private val namespacedKey: (String => String) = { x => s"$namespace::$x" }

  override def get[T](userKey: String)(implicit ct: ClassTag[T]): Option[T] = {
    logger.trace(s"Reading key ${namespacedKey(userKey)}")

    try {
      val rawData = withJedisClient { client => client.get(namespacedKey(userKey)) }
      rawData match {
        case null =>
          None
        case _ =>
          val data = rawData.split("-").toSeq
          val bytes = Base64Coder.decode(data.last)
          data.head match {
            case "oos" => Some(withObjectInputStream(bytes)(_.readObject().asInstanceOf[T]))
            case "string" => Some(withDataInputStream(bytes)(_.readUTF().asInstanceOf[T]))
            case "text" => Some(withDataInputStream(bytes){ in =>
              val bytes = new Array[Byte](in.readInt())
              in.read(bytes)
              new String(bytes, "UTF-8").asInstanceOf[T]
            })
            case "int" => Some(withDataInputStream(bytes)(_.readInt().asInstanceOf[T]))
            case "long" => Some(withDataInputStream(bytes)(_.readLong().asInstanceOf[T]))
            case "boolean" => Some(withDataInputStream(bytes)(_.readBoolean().asInstanceOf[T]))
            case _ => throw new IOException(s"was not able to recognize the type of serialized value. The type was ${data.head} ")
          }
      }
    } catch {
      case ex: Exception =>
        logger.warn("could not deserialize key:" + namespacedKey(userKey), ex)
        None
    }
  }

  override def getOrElseUpdate[A](userKey: String, expiration: Duration)(orElse: => A)(implicit evidence$1: ClassTag[A]): A = {
    get[A](userKey).getOrElse {
      val value = orElse
      set(userKey, value, expiration)
      value
    }
  }

  override def remove(userKey: String): Unit = withJedisClient(_.del(namespacedKey(userKey)))

  override def set(userKey: String, value: Any, expiration: Duration): Unit = {
    val expirationInSec = if (expiration == Duration.Inf) 0 else expiration.toSeconds.toInt
    val key = namespacedKey(userKey)

    var oos: ObjectOutputStream = null
    var dos: DataOutputStream = null
    try {
      val baos = new ByteArrayOutputStream()
      val prefix = value match {
        case x: String =>
          val bytes = x.getBytes(StandardCharsets.UTF_8)
          dos = new DataOutputStream(baos)
          if(bytes.length <= 65535){
            dos.writeUTF(x)
            "string"
          } else {
            dos.writeInt(bytes.length)
            dos.write(bytes)
            "text"
          }
        case x: Int =>
          dos = new DataOutputStream(baos)
          dos.writeInt(x)
          "int"
        case x: Long =>
          dos = new DataOutputStream(baos)
          dos.writeLong(x)
          "long"
        case x: Boolean =>
          dos = new DataOutputStream(baos)
          dos.writeBoolean(x)
          "boolean"
        case x: Serializable =>
          oos = new ObjectOutputStream(baos)
          oos.writeObject(x)
          oos.flush()
          "oos"
        case _ =>
          throw new IOException("could not serialize: " + value.toString)
      }

      val redisV = prefix + "-" + new String(Base64Coder.encode(baos.toByteArray))
      logger.trace(s"Setting key $key to $redisV")

      withJedisClient { client =>
        client.set(key, redisV)
        if (expirationInSec != 0) client.expire(key, expirationInSec)
      }
    } catch {
      case ex: IOException =>
        logger.warn("could not serialize key:" + key + " and value:" + value.toString + " ex:" + ex.toString)
    } finally {
      if (oos != null) oos.close()
      if (dos != null) dos.close()
    }
  }

  private class ClassLoaderObjectInputStream(stream: InputStream) extends ObjectInputStream(stream) {
    override protected def resolveClass(desc: ObjectStreamClass) = {
      Class.forName(desc.getName, false, classLoader)
    }
  }

  private def withDataInputStream[T](bytes: Array[Byte])(f: DataInputStream => T): T = {
    val dis = new DataInputStream(new ByteArrayInputStream(bytes))
    try f(dis) finally dis.close()
  }

  private def withObjectInputStream[T](bytes: Array[Byte])(f: ObjectInputStream => T): T = {
    val ois = new ClassLoaderObjectInputStream(new ByteArrayInputStream(bytes))
    try f(ois) finally ois.close()
  }

  private def withJedisClient[T](f: Jedis => T): T = {
    val client = jedisPool.getResource
    try {
      f(client)
    } finally {
      client.close()
    }
  }

}
