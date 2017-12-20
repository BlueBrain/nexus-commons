package ch.epfl.bluebrain.nexus.commons.service.directives

import akka.http.scaladsl.marshalling.{Marshaller, ToResponseMarshaller}
import akka.http.scaladsl.model._
import ch.epfl.bluebrain.nexus.commons.http.{ContextUri, RdfMediaTypes}
import ch.epfl.bluebrain.nexus.commons.http.JsonOps._
import io.circe.Encoder

/**
  * Directive to marshall StatusFrom instances into HTTP responses.
  */
trait ErrorDirectives {

  /**
    * Implicitly derives a [[akka.http.scaladsl.marshalling.ToResponseMarshaller]] for any type ''A'' based on
    * implicitly available [[StatusFrom]] and [[io.circe.Encoder]] instances.
    *
    * @tparam A the generic type for which the marshaller is derived
    * @return a ''ToResponseMarshaller'' that will generate a JSON response using the status provided by ''StatusFrom''
    *         and the entity using the JSON representation provided by ''Encoder''
    */
  final implicit def jsonMarshallerFromStatusAndEncoder[A: StatusFrom: Encoder]: ToResponseMarshaller[A] =
    Marshaller.withFixedContentType(ContentTypes.`application/json`) { value =>
      HttpResponse(status = implicitly[StatusFrom[A]].apply(value),
                   entity = HttpEntity(ContentTypes.`application/json`, implicitly[Encoder[A]].apply(value).noSpaces))
    }

  /**
    * Implicitly derives a [[akka.http.scaladsl.marshalling.ToResponseMarshaller]] for any type ''A'' based on
    * implicitly available [[StatusFrom]] and [[io.circe.Encoder]] instances.
    *
    * @tparam A the generic type for which the marshaller is derived
    * @return a ''ToResponseMarshaller'' that will generate a JSON-LD response using the status provided by
    *         ''StatusFrom'' and the entity using the JSON representation provided by ''Encoder'', with
    *         an appropriate ''context'' injected.
    */
  final implicit def jsonLdMarshallerFromStatusAndEncoder[A](
      implicit
      statusFrom: StatusFrom[A],
      encoder: Encoder[A],
      context: ContextUri
  ): ToResponseMarshaller[A] =
    Marshaller.withFixedContentType(RdfMediaTypes.`application/ld+json`) { value =>
      HttpResponse(status = statusFrom(value),
                   entity = HttpEntity(RdfMediaTypes.`application/ld+json`,
                                       encoder.mapJson(_.addContext(context)).apply(value).noSpaces))
    }
}

object ErrorDirectives extends ErrorDirectives
