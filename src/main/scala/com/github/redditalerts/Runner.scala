package com.github.redditalerts

import java.util.{Properties, UUID}

import net.dean.jraw.RedditClient
import net.dean.jraw.http.{OkHttpNetworkAdapter, UserAgent}
import net.dean.jraw.models.{Submission, SubredditSort, TimePeriod}
import net.dean.jraw.oauth.{Credentials, OAuthHelper}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.rogach.scallop.{ScallopConf, Subcommand}

import scala.collection.JavaConverters._
import scala.collection.immutable.SortedSet

object Runner {
  implicit class PropertyHelpers(p: Properties) {
    def update(keyValues: Iterable[(AnyRef, AnyRef)]): Unit = {
      keyValues.foldLeft(p) {
        case (props, (k, v)) =>
          props.put(k, v)
          props
      }
    }
  }

  def getSettings: Properties = {
    val props = new Properties()
    val source = Option(getClass.getResourceAsStream("/settings.properties"))
    source match {
      case Some(stream) => props.load(stream)
      case None => throw new IllegalStateException("settings.properties could not be read from classpath")
    }
    val overrides = System.getenv().asScala.filter(elm => props.containsKey(elm._1))
    props.update(overrides)
    props
  }

  class SubmissionWrapper(val submission: Submission) {
    override def hashCode(): Int = submission.getUniqueId.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case obj: SubmissionWrapper => obj.submission.getUniqueId == submission.getUniqueId
      case obj: Submission => obj.getUniqueId == submission.getUniqueId
      case _ => false
    }
  }

  object SubmissionWrapper {
    implicit val orderingByDate: Ordering[SubmissionWrapper] = Ordering.by(_.submission.getCreated)
  }

  def streamPosts(reddit: RedditClient, subreddit: String, seenBufferSize: Int = 100): Stream[Submission] = {
    def newPosts(): Iterator[SubmissionWrapper] = reddit.subreddit(subreddit)
      .posts()
      .sorting(SubredditSort.NEW)
      .timePeriod(TimePeriod.ALL)
      .limit(seenBufferSize)
      .build()
      .next()
      .iterator()
      .asScala
      .map(new SubmissionWrapper(_))

    val seen: Set[SubmissionWrapper] = BoundedSet[SubmissionWrapper](seenBufferSize) ++ newPosts()

    def streamPostsRec(reddit: RedditClient, seenBufferSize: Int, seen: Set[SubmissionWrapper]): Stream[Submission] = {
      val potentialNewSubmissions = SortedSet.empty[SubmissionWrapper] ++ newPosts()

      val newSubmissions = potentialNewSubmissions.diff(seen)
      println(s"Found ${newSubmissions.size} new submissions")
      (Stream.empty[Submission] ++ newSubmissions.map(_.submission)) #::: streamPostsRec(reddit, seenBufferSize, seen ++ newSubmissions)
    }

    streamPostsRec(reddit, seenBufferSize, seen)
  }

  class CliArgs(arguments: Seq[String]) extends ScallopConf(arguments) {
    val clientId = opt[String]("client-id", descr = "Reddit Client ID", required = true)
    val clientSecret = opt[String]("client-secret", descr = "Reddit Client Secret", required = true)
    val producer = new Subcommand("producer") {
      val subreddit = opt[String]("subreddit", default = Some("all"))
    }
    addSubcommand(producer)
    verify()
  }

  def main(args: Array[String]): Unit = {
    val conf = new CliArgs(args)
    conf.subcommand match {
      case Some(conf.producer) => producer(conf)
      case _ =>
    }
  }

  def producer(conf: CliArgs): Unit = {
    val agent = new UserAgent("bot", "com.github.reddit-alerts", "0.1.0", "alerting-bot")
    val networkAdapter = new OkHttpNetworkAdapter(agent)
    val creds = Credentials.userless(conf.clientId(), conf.clientSecret(), UUID.randomUUID())
    val reddit = OAuthHelper.automatic(networkAdapter, creds)
    reddit.setAutoRenew(true)

    val stream = streamPosts(reddit, conf.producer.subreddit())

    val producer = new KafkaProducer[String, Submission](getSettings)
    try {
      stream.map(new ProducerRecord[String, Submission]("reddit_topic", _))
        .foreach(producer.send)
    } finally {
      producer.close()
    }
  }
}
