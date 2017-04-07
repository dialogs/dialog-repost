package im.dlg.contrib.repost.twitter

import akka.actor._
import im.dlg.contrib.repost.ChannelPoster

case class TwitterConfig(consumerKey: String,
                         consumerSecret: String,
                         token: String,
                         tokenSecret: String,
                         repost: Map[String, String])

object Twitter extends ChannelPoster.Starter[TwitterConfig] {
  override protected def name: String = "twitter"

  override def startWithConfig(config: TwitterConfig)(
      implicit system: ActorSystem): Unit = {
    TwitterHub(config).start()
  }
}
