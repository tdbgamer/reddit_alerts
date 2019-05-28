package com.github.redditalerts

import java.util.Properties

object PropertyImplicits {
  implicit class PropertyWrapper(private val p: Properties) extends AnyVal {
    def update(keyValues: Iterable[(AnyRef, AnyRef)]): Unit = {
      keyValues.foldLeft(p) {
        case (props, (k, v)) =>
          props.put(k, v)
          props
      }
    }
  }
}
