import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.stream.ActorMaterializer
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import spray.json.{JsValue, JsonFormat, _}
import DefaultJsonProtocol._

import scala.concurrent.{Future, Await, ExecutionContext}
import ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import scala.concurrent.duration._

/*  
 *  Para parsear un JSON en scala la forma más fácil de hacerlo es 
 *  armar case clases que le peguen a cada campo (no hace falta utilizarlos todos)
 *  en este caso creamos uno para la página en sí (que contiene el número y una lista
 *  de películas); y otro para la película (que contiene el título, autor, etc.)
 *  Además hay que crear un protocolo de unmarshalling para poder utilizar estas 
 *  cases clases en este caso es MovieDBProtocol
 */ 

case class Movie(title: String, vote_average: Int, id: Int, overview: String)
case class Page[Movie](page: Int, results: List[Movie])

object MovieDBProtocol extends DefaultJsonProtocol {
  implicit def movieFormat = jsonFormat4(Movie)
  implicit def pageFormat[Movie: JsonFormat] = jsonFormat2(Page.apply[Movie])
}

class MovieDataFormatter extends Actor {
  import MovieDBProtocol._

  def receive = {
    case movieData: String => {

      var page = movieData.parseJson.convertTo[Page[Movie]]

      println(s"Página: ${page.page}")
      println("--------------------------------------")

      for(movie <- page.results) {
        println(s"--- ID: ${movie.id} | Título: ${movie.title} | Puntuación: ${movie.vote_average}")
        println("--------------------------------------")
      }

    }
  }
}

// TODO: Refactorizar a un RequestsDispatcher que maneje los requests a la API, estos actores solo deberían 
//       setear el endpoint y el método junto con los parámetros
//

class MovieFinder(system: ActorSystem, val apiKey: String, var formatter: ActorRef) extends Actor {

  def receive = {
    case query:String => {

      implicit val actSystem = system; // Para el Http()

      val queryString = Some(s"api_key=${apiKey}&language=es-LA&page=1&include_adult=false&query=${query}")
      val movieDBUri = Uri.from(scheme = "http", host = "api.themoviedb.org", path = "/3/search/movie", queryString = queryString)
      val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = movieDBUri))

      responseFuture.onComplete {

        case Success(res) => {
          println(s"Response for key=${query} with code=${res.status.intValue()}")

          val stringFuture: Future[String] = Unmarshal(res).to[String]

          formatter ! Await.result(stringFuture, 5 seconds) 

        }
        case Failure(_)   => sys.error("Ocurrio un error al esperar la respuesta de la API")
      }
    }
    case _ => println("Buscador recibio una query que no es del tipo string<")
  }

}


class MovieRecommender(system: ActorSystem, val apiKey: String, var formatter: ActorRef) extends Actor {

  def receive = {
    case movieID:String => {

      implicit val actSystem = system; // Para el Http()

      val path = "/3/movie/%s/recommendations".format(movieID)

      val queryString = Some(s"api_key=${apiKey}&language=es-LA&page=1")
      val movieDBUri = Uri.from(scheme = "http", host = "api.themoviedb.org", path = path, queryString = queryString)
      val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = movieDBUri))

      responseFuture.onComplete {

        case Success(res) => {
          println(s"Response for endpoint=${path} with code=${res.status.intValue()}")

          val stringFuture: Future[String] = Unmarshal(res).to[String]

          println(s"Recommendations for ID: ${movieID}")
          formatter ! Await.result(stringFuture, 5 seconds) 

        }
        case Failure(_)   => sys.error("Ocurrio un error al esperar la respuesta de la API")
      }
    }
    case _ => println("Buscador recibio una query que no es del tipo string<")
  }
}


object Client {
  def main(args: Array[String]): Unit = {

    val system = ActorSystem()
    val APIKey = "0f4c286aea338ef131e2ed9b2b522856"

    val dataFormatter = system.actorOf(Props[MovieDataFormatter], "formatter")
    val movieFinder = system.actorOf(Props(classOf[MovieFinder], system, APIKey, dataFormatter), "finder")
    val recommender = system.actorOf(Props(classOf[MovieRecommender], system, APIKey, dataFormatter), "recommender")

    movieFinder ! "robot"
    recommender ! "2048"
//    movieFinder ! "alien"
  }
}
