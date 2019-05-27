package com.github.redditalerts

import java.util

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Serializer

class JsonPojoSerializer[T] extends Serializer[T] {
  final private val objectMapper = new ObjectMapper

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

  override def serialize(topic: String, data: T): Array[Byte] = {
    if (data == null) return null
    try
      objectMapper.writeValueAsBytes(data)
    catch {
      case e: Exception =>
        throw new SerializationException("Error serializing JSON message", e)
    }
  }

  override def close(): Unit = {
  }

}
