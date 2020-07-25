package twitter

import scala.util.{Failure, Success}
import scala.concurrent.{Future, ExecutionContext, Await}
import ExecutionContext.Implicits.global

import com.danielasfregola.twitter4s.TwitterRestClient
import com.danielasfregola.twitter4s.TwitterStreamingClient
import com.danielasfregola.twitter4s.entities.{Tweet, AccessToken, ConsumerToken, Event, DirectMessageEventList}

import akka.actor.{ Actor, ActorRef, ActorSystem, Props }


case class GetMentions()

class TwitterClient extends Actor {

    val CONSUMER_KEY = "Smqf5X2hPt5brcKfG2MipHXfx"
    val CONSUMER_SECRET = "nngdpENb7QTXQnyJ0gleth8bYTGQGtsa6zBKDb9J7cZfHsaJR0"

    val ACCESS_KEY = "1285759165869236226-XAgIZcuzvkjuLn9pmQ7AZJyCWmKVx0"
    val ACCESS_TOKEN = "snq5qU19DUW5xOfYB891YuUI6AA2FZxeyIGj5Iq9PvXa0"

    val consumerToken = ConsumerToken(key = CONSUMER_KEY, secret = CONSUMER_SECRET)
    val accessToken = AccessToken(key = ACCESS_KEY, secret = ACCESS_TOKEN)

    val restClient = TwitterRestClient(consumerToken, accessToken)
    //La idea es que haya registro de los tweets ya mandados, pero esto se ejecuta cada vez que se hace reStart
    var oldMentions:List[_] = List()

    //val entryManager = system.actorOf(Props(classOf[EntryManager]), "entryManager")

    def receive = {
      case GetMentions() => {
        var newMentions:List[_] = List()
        var mentions = restClient.mentionsTimeline()
        mentions.onComplete {
          case Success(ratedData) => {
            for (tweetMention <- ratedData.data) {
              var user = tweetMention.user.get
              var mentionid = tweetMention.id
              if(oldMentions.contains(tweetMention) == false){
                println(s"Te mencionó el usuario ${user.screen_name} con el mensaje '${tweetMention.text}'")
                newMentions :: List(tweetMention)
              }
            }
            //entryManager ! newMentions
            oldMentions :: newMentions
          }

          case Failure(e) => println(e)
        }
      }
      case _ => println("No recibi nada")

    }

}