package com.github.chet.datadog

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshal}
import akka.stream.ActorMaterializer
import cats.data.EitherT
import com.github.chet.datadog.DatadogClient.{DatadogMonitor, MonitorDetails, MonitorId}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Encoder}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait ClientHelper {
  implicit val ec: ExecutionContext
  implicit val mt: ActorMaterializer
  implicit val system: ActorSystem

  def post[T: FromEntityUnmarshaller](uri: Uri): EitherT[Future, Throwable, T] = {
    EitherT(Http().singleRequest(HttpRequest(uri = uri)).flatMap(processResponse[T]))
  }

  def post[T: FromEntityUnmarshaller, P: ToEntityMarshaller](uri: Uri, payload: P): EitherT[Future, Throwable, T] = {
    import akka.http.scaladsl.marshalling.Marshal

    val r = Marshal(payload).to[RequestEntity].flatMap{ entity =>
      Http().singleRequest(HttpRequest(uri = uri, entity = entity)).flatMap(processResponse[T])
    }

    EitherT(r)
  }

  def get[T: FromEntityUnmarshaller](uri: Uri): EitherT[Future, Throwable, T] = {
    EitherT(Http().singleRequest(HttpRequest(uri = uri)).flatMap(processResponse[T]))
  }

  def processResponse[T: FromEntityUnmarshaller](response: HttpResponse): Future[Either[Throwable, T]] = {
    response.toStrict(1.minute)
      .flatMap(r => Unmarshal(r.entity).to[T].map(Right.apply)
        .recover{ case e => Left(e)})
  }
}

case class DatadogClient(apiKey: String, appKey: String)
                        (implicit val system: ActorSystem,
                         val ec: ExecutionContext,
                         val mt: ActorMaterializer) extends FailFastCirceSupport with ClientHelper {

  val baseUrl: String = "https://app.datadoghq.com/api/v1"
  val keyArgs: Map[String, String] = Map("api_key" -> apiKey, "application_key" -> appKey)

  def muteMonitor(id: MonitorId): EitherT[Future, Throwable, DatadogMonitor] = {
    val url = Uri(baseUrl + s"/monitor/${id.value}/mute").withQuery(Query(keyArgs))
    post[DatadogMonitor](url)
  }

  def unMuteMonitor(id: MonitorId): EitherT[Future, Throwable, DatadogMonitor] = {
    val url = Uri(baseUrl + s"/monitor/${id.value}/unmute").withQuery(Query(keyArgs))
    post[DatadogMonitor](url)
  }

  def getMonitor(id: Long): EitherT[Future, Throwable, MonitorDetails] =
    get[MonitorDetails](Uri(baseUrl + s"/monitor/$id").withQuery(Query(keyArgs)))

  def filterMonitors(name: Option[String] = None,
                     tags: List[String] = List.empty,
                     monitorTags: List[String] = List.empty): EitherT[Future, Throwable, List[DatadogMonitor]] = {
    import cats.syntax.list._

    val query = monitorTags.toNel.map(tags => "monitor_tags" -> tags.toList.mkString(",")) ++
      tags.toNel.map(tags => "tags" -> tags.toList.mkString(",")) ++
      name.map(n => "name" -> n) ++ keyArgs

    val url = Uri(baseUrl + "/monitor").withQuery(Query(query.toMap))

    get[List[DatadogMonitor]](url)
  }

}

object DatadogClient{
  sealed trait MonitorType
  object MonitorType {
    //scalastyle:off
    case object `query alert` extends MonitorType
    case object `metric alert` extends MonitorType
    case object `service check` extends MonitorType
    case object `event alert` extends MonitorType
    case object composite extends MonitorType
    //scalastyle:on
  }

  case class MonitorId(value: Long) extends AnyVal

  case class MonitorDetails(id: MonitorId, name: String, message: String, query: String, `type`: MonitorType)


  case class DatadogMonitor(id: Option[MonitorId] = None,
                            `type`: MonitorType,
                            query: String,
                            name: String,
                            message: String,
                            tags: List[String])

  implicit val monitorIdEncoder: Encoder[MonitorId] =
    io.circe.generic.extras.semiauto.deriveUnwrappedEncoder[MonitorId]

  implicit val monitorIdDecoder: Decoder[MonitorId] =
    io.circe.generic.extras.semiauto.deriveUnwrappedDecoder[MonitorId]

  implicit val monitorTypeEncoder: Encoder[MonitorType] =
    io.circe.generic.extras.semiauto.deriveEnumerationEncoder[MonitorType]

  implicit val monitorTypeDecoder: Decoder[MonitorType] =
    io.circe.generic.extras.semiauto.deriveEnumerationDecoder[MonitorType]

  import io.circe.derivation._

  implicit val monitorDetailsDecoder: Decoder[MonitorDetails] = deriveDecoder
  implicit val datadogMonitorDecoder: Decoder[DatadogMonitor] = deriveDecoder

  implicit val datadogMonitorEncoder: Encoder[DatadogMonitor] = deriveEncoder

}
