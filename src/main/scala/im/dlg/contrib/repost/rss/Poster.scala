package im.dlg.contrib.repost.rss

import java.net.URL
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset, ZonedDateTime}

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.{Sink, Source}
import im.dlg.api.rpc.messaging.ApiTextMessage
import im.dlg.concurrent.FutureExt
import im.dlg.contrib.repost.ChannelPoster
import im.dlg.server.model.Group
import org.apache.commons.lang3.StringEscapeUtils

import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.xml._

final case class RSSItem(pubDate: Instant, title: String, description: String, link: String)

object RSSItem {
  val dtFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")

  def parse(node: Node): Try[RSSItem] =
    Try(
        RSSItem(
          ZonedDateTime.parse((node \\ "pubDate").text, dtFormat).toInstant,
          (node \\ "title").text,
          (node \\ "description").text,
          (node \\ "link").text
        ))

  def parseItems(s: String): Try[Seq[RSSItem]] =
    for {
      xml ← Try(XML.loadString(s))
      items ← Success((xml \\ "channel" \\ "item").map(parse).collect {
        case Success(item) ⇒ item
      })
    } yield items
}

final case class Poster(channelId: Int, interval: FiniteDuration)(implicit val system: ActorSystem)
    extends ChannelPoster.Poster {
  private lazy val http = Http(system)

  implicit val o: Ordering[Instant] = new Ordering[Instant] {
    override def compare(x: Instant, y: Instant): Int = x.compareTo(y)
  }

  private def logFailure[T](msg: String)(t: Try[T]): Try[T] = {
    t match {
      case Failure(err) ⇒ log.error(err, msg)
      case _ ⇒
    }
    t
  }

  private def collectSuccess[T]: PartialFunction[Try[T], T] = {
    case Success(r) ⇒ r
  }

  private def now = ZonedDateTime.now(ZoneOffset.UTC).toInstant

  private def postFunc(info: Group)(lastSeen: Instant, items: Seq[RSSItem]) = {
    val toPost = items.filter(_.pubDate.isAfter(lastSeen))
    (if (toPost.nonEmpty)
       FutureExt
         .ftraverse(toPost) { item ⇒
           log.debug("Posting `{}` to channel {}", item.title, info.id)
           val body = StringEscapeUtils.unescapeHtml4(s"${item.title}\n${item.description}") + "\n" + item.link
           val message = ApiTextMessage(body, IndexedSeq.empty, None, IndexedSeq.empty, IndexedSeq.empty)
           post(info.creatorUserId, message)
         }
         .map { _ ⇒
           toPost.map(_.pubDate).max
         }
         .recover {
           case t: Throwable ⇒
             log.error(t, "Post error, skipping items")
             now
         } else {
       log.debug("Skipping {} items, because of date constraint", items.length)
       FastFuture.successful(now)
     }).map { nextDate ⇒
      log.debug("Last seen date moved from {} to {} after processing of {} items", lastSeen, nextDate, items.length)
      nextDate
    }
  }

  override protected def startStream(key: String, info: Group): Unit = {
    val staticReq = HttpRequest(uri = key)
    Source
      .tick(0.seconds, interval, ())
      .named(s"$key-rss-stream")
      .map(_ ⇒ staticReq → Instant.now.getEpochSecond)
      .via(http.cachedHostConnectionPool(new URL(key).getHost))
      .map(_._1)
      .map(logFailure(s"request to `$key` failed"))
      .collect(collectSuccess)
      .mapAsync(1)(_.entity.toStrict(15.seconds))
      .map(bs ⇒ RSSItem.parseItems(bs.data.utf8String))
      .map(logFailure("RSS parse failed"))
      .collect(collectSuccess)
      .foldAsync(now)(postFunc(info))
      .to(Sink.ignore)
      .run
  }
}

case class RSSConfig(interval: FiniteDuration, repost: Map[String, String])

object RSS extends ChannelPoster.Starter[RSSConfig] {
  override protected def name: String = "rss"

  override def startWithConfig(config: RSSConfig)(implicit system: ActorSystem): Unit =
    config.repost.foreach {
      case (chanId, link) ⇒
        Poster(chanId.toInt, config.interval)(system).start(link)
    }
}
