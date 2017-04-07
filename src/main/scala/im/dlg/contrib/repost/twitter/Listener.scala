package im.dlg.contrib.repost.twitter

import akka.actor._
import im.dlg.util.log.AnyLogging
import twitter4j._

import scala.util.control.NonFatal

private final case class Listener(actor: ActorRef)(
    implicit val system: ActorSystem)
    extends StatusListener
    with AnyLogging {
  override def onStallWarning(warning: StallWarning): Unit =
    log.warning("onStallWarning: {}", warning)
  override def onDeletionNotice(
      statusDeletionNotice: StatusDeletionNotice): Unit =
    log.debug("onDeletionNotice: {}", statusDeletionNotice)
  override def onScrubGeo(userId: Long, upToStatusId: Long): Unit = {}
  override def onStatus(status: Status): Unit = actor ! TwitterStatus(status)
  override def onTrackLimitationNotice(numberOfLimitedStatuses: Int): Unit =
    log.debug("onTrackLimitationNotice: {}", numberOfLimitedStatuses)
  override def onException(ex: Exception): Unit = ex match {
    case NonFatal(t) ⇒
    case _ ⇒
      log.error(ex, "TwitterStream failed")
      actor ! akka.actor.Status.Failure(ex)
  }
}

//private final case class Listener(actor: ActorRef) extends UserStreamListener {
//  override def onFriendList(friendIds: Array[Long]): Unit = {}
//  override def onUserListUnsubscription(subscriber: User, listOwner: User, list: UserList): Unit = {}
//  override def onBlock(source: User, blockedUser: User): Unit = {}
//  override def onUserListSubscription(subscriber: User, listOwner: User, list: UserList): Unit = {}
//  override def onFollow(source: User, followedUser: User): Unit = {}
//  override def onUserListMemberAddition(addedMember: User, listOwner: User, list: UserList): Unit = {}
//  override def onDirectMessage(directMessage: DirectMessage): Unit = {}
//  override def onUnblock(source: User, unblockedUser: User): Unit = {}
//  override def onUserListUpdate(listOwner: User, list: UserList): Unit = {}
//  override def onUserProfileUpdate(updatedUser: User): Unit = {}
//  override def onUserListMemberDeletion(deletedMember: User, listOwner: User, list: UserList): Unit = {}
//  override def onDeletionNotice(directMessageId: Long, userId: Long): Unit = {}
//  override def onFavorite(source: User, target: User, favoritedStatus: Status): Unit = {}
//  override def onUnfavorite(source: User, target: User, unfavoritedStatus: Status): Unit = {}
//  override def onUserListDeletion(listOwner: User, list: UserList): Unit = {}
//  override def onUserListCreation(listOwner: User, list: UserList): Unit = {}
//  override def onStallWarning(warning: StallWarning): Unit = {}
//  override def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice): Unit = {}
//  override def onScrubGeo(userId: Long, upToStatusId: Long): Unit = {}
//  override def onStatus(status: Status): Unit = {
//    println(s"\n\nSTATUS = ${status.getText}")
//    actor ! TwitterStatus(status)
//  }
//  override def onTrackLimitationNotice(numberOfLimitedStatuses: Int): Unit = {}
//  override def onException(ex: Exception): Unit = {}
//}
