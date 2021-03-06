package com.github.redditalerts

import java.time.Duration
import java.util
import java.util.{Properties, UUID}

import com.github.redditalerts.PropertyImplicits._
import com.github.redditalerts.models.{Alert, SubmissionWrapper}
import com.typesafe.scalalogging.LazyLogging
import javax.mail.internet.{InternetAddress, MimeMessage}
import javax.mail.{Authenticator, Message, MessagingException, PasswordAuthentication, Session, Transport}
import net.dean.jraw.RedditClient
import net.dean.jraw.http.{OkHttpNetworkAdapter, UserAgent}
import net.dean.jraw.models.{Submission, SubredditSort, TimePeriod}
import net.dean.jraw.oauth.{Credentials, OAuthHelper}
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.rogach.scallop.{ScallopConf, Subcommand}

import scala.collection.JavaConverters._
import scala.collection.immutable.SortedSet


object Runner extends LazyLogging {
  val DELETED_POSTS_TOLERANCE: Int = 10

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

  def streamPosts(reddit: RedditClient, subreddit: String, seenBufferSize: Int = 100): Stream[Iterable[Submission]] = {
    def newPosts(): Iterator[SubmissionWrapper] = reddit.subreddit(subreddit)
      .posts()
      .sorting(SubredditSort.NEW)
      .timePeriod(TimePeriod.ALL)
      .limit(seenBufferSize)
      .build()
      .next()
      .iterator()
      .asScala
      .map(SubmissionWrapper(_))

    val seen: BoundedSet[SubmissionWrapper] = BoundedSet[SubmissionWrapper](seenBufferSize + DELETED_POSTS_TOLERANCE) ++ newPosts()

    def streamPostsRec(reddit: RedditClient, seenBufferSize: Int, seen: BoundedSet[SubmissionWrapper]): Stream[Iterable[Submission]] = {
      val potentialNewSubmissions = BoundedSet[SubmissionWrapper](seenBufferSize + DELETED_POSTS_TOLERANCE) ++ newPosts()

      val newSubmissions = potentialNewSubmissions.diff(seen)
      println(s"Found ${newSubmissions.size} new submissions")
      newSubmissions.map(_.submission) #::
        streamPostsRec(reddit, seenBufferSize, ((seen | newSubmissions) & potentialNewSubmissions) | seen.take(DELETED_POSTS_TOLERANCE))
    }

    streamPostsRec(reddit, seenBufferSize, seen)
  }

  class CliArgs(arguments: Seq[String]) extends ScallopConf(arguments) {
    val clientId = opt[String]("client-id", descr = "Reddit Client ID", required = true)
    val clientSecret = opt[String]("client-secret", descr = "Reddit Client Secret", required = true)
    val producer = new Subcommand("producer") {
      val subreddit = opt[String]("subreddit", default = Some("all"))
    }
    val emailAlerter = new Subcommand("email-alerter") {
      val smtpHost = opt[String]("smtp-host", 'h', descr = "SMTP Hostname", required = true)
      val smtpPort = opt[String]("smtp-port", 'p', descr = "SMTP Port", default = Some("465"))
      val smtpUser = opt[String]("smtp-user", 'u', descr = "SMTP Username", required = true)
      val smtpPass = opt[String]("smtp-pass", 'P', descr = "SMTP Password", required = true)
      val fromAddress = opt[String]("from-address", descr = "From address", required = true)
      val toAddresses = opt[String]("to-addresses", descr = "Comma separated email addresses to alert", required = true)
    }
    addSubcommand(producer)
    addSubcommand(emailAlerter)
    verify()
  }

  def main(args: Array[String]): Unit = {
    val conf = new CliArgs(args)
    conf.subcommand match {
      case Some(conf.producer) => producer(conf)
      case Some(conf.emailAlerter) => emailAlerter(conf)
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
      stream.flatMap(_.map(new ProducerRecord[String, Submission]("reddit_topic", _)))
        .foreach(producer.send)
    } finally {
      producer.close()
    }
  }

  def emailAlerter(conf: CliArgs): Unit = {
    val props = new Properties()
    props.setProperty("mail.smtp.host", conf.emailAlerter.smtpHost())
    props.setProperty("mail.smtp.port", conf.emailAlerter.smtpPort())
    props.setProperty("mail.smtp.auth", "true")
    val session = Session.getDefaultInstance(props, new Authenticator {
      override def getPasswordAuthentication: PasswordAuthentication =
        new PasswordAuthentication(conf.emailAlerter.smtpUser(), conf.emailAlerter.smtpPass())
    })

    val kafkaProps = getSettings
    kafkaProps.update(Map("JsonPOJOClass" -> classOf[Alert]))
    val consumer = new KafkaConsumer[String, Alert](kafkaProps)
    consumer.subscribe(util.Arrays.asList("alerts"))
    try {
      while (true) {
        val records = consumer.poll(Duration.ofMillis(100))
        records.asScala.foreach { record =>
          val alert = record.value()
          logger.info(s"Alerting $alert")
          try {
            val message = new MimeMessage(session)
            message.setFrom(new InternetAddress(conf.emailAlerter.fromAddress()))
            message.setRecipients(Message.RecipientType.TO, conf.emailAlerter.toAddresses())
            message.setSubject("Reddit Alert!")
            message.setText(s"${alert.alertMsg}\n${alert.submission.title}\n"
              + s"https://reddit.com${alert.submission.permalink}")
            Transport.send(message)
          } catch {
            case e: MessagingException => logger.error("Failed to send email", e)
            case e: Throwable => logger.error("Unexpected exception", e)
          }
        }
      }
    } finally {
      consumer.close()
    }
  }
}
