package com.github.redditalerts

import java.util

import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer


class JsonPojoDeserializer[T >: Null] extends Deserializer[T] {
  private var tClass: Class[T] = _

  @SuppressWarnings(Array("unchecked")) override def configure(props: util.Map[String, _], isKey: Boolean): Unit = {
    tClass = props.get("JsonPOJOClass").asInstanceOf[Class[T]]
  }

  override def deserialize(topic: String, bytes: Array[Byte]): T = {
    if (bytes == null) return null
    try {
      MAPPER.readValue(bytes, tClass)
    } catch {
      case e: Exception =>
        throw new SerializationException(e)
    }
  }

  override def close(): Unit = {
  }
}
