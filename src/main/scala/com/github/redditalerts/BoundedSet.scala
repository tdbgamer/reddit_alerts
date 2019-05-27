package com.github.redditalerts

import scala.collection.GenTraversableOnce
import scala.collection.immutable.{Set, SortedSet}

class BoundedSet[T](set: SortedSet[T], maxSize: Int) extends Set[T] {
  override def contains(elem: T): Boolean = set.contains(elem)

  override def +(elem: T): Set[T] = new BoundedSet((set + elem).takeRight(maxSize), maxSize)

  override def ++(elems: GenTraversableOnce[T]): Set[T] = new BoundedSet((set ++ elems).takeRight(maxSize), maxSize)

  override def -(elem: T): Set[T] = new BoundedSet(set - elem, maxSize)

  override def iterator: Iterator[T] = set.iterator
}

object BoundedSet {
  def apply[T: Ordering](maxSize: Int): BoundedSet[T] = new BoundedSet(SortedSet.empty[T], maxSize)
}

