package im.dlg.contrib.repost

import akka.actor._
import im.dlg.contrib.repost.rss.RSS
import im.dlg.contrib.repost.twitter.Twitter

object RepostExtension extends ExtensionId[RepostExtensionImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): RepostExtensionImpl = RepostExtensionImpl()(system)
  override def lookup(): ExtensionId[_ <: Extension] = RepostExtension
}

final case class RepostExtensionImpl()(implicit system: ActorSystem) extends Extension {
  Twitter.start(system)
  RSS.start(system)
}
