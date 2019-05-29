package com.github.redditalerts

import scala.collection.immutable.{Set, SortedSet}
import scala.collection.{GenTraversableOnce, SetLike}

class BoundedSet[T: Ordering](set: SortedSet[T], maxSize: Option[Int] = None) extends Set[T] with SetLike[T, BoundedSet[T]] {
  override def contains(elem: T): Boolean = set.contains(elem)

  override def empty: BoundedSet[T] = new BoundedSet[T](SortedSet.empty[T], None)

  override def +(elem: T): BoundedSet[T] = maxSize match {
    case Some(size) => new BoundedSet((set + elem).takeRight(size), maxSize)
    case None => new BoundedSet(set + elem, maxSize)
  }

  override def ++(elems: GenTraversableOnce[T]): BoundedSet[T] = maxSize match {
    case Some(size) => new BoundedSet((set ++ elems).takeRight(size), maxSize)
    case None => new BoundedSet(set ++ elems, maxSize)
  }

  override def -(elem: T): BoundedSet[T] = new BoundedSet(set - elem, maxSize)

  override def iterator: Iterator[T] = set.iterator

  def setMaxSize(maxSize: Int) = new BoundedSet[T](set, Some(maxSize))
}

object BoundedSet {
  def apply[T: Ordering](maxSize: Int): BoundedSet[T] = new BoundedSet(SortedSet.empty[T], Some(maxSize))
}

