package com.github

import com.fasterxml.jackson.annotation._
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import net.dean.jraw.models.Submission

import scala.collection.mutable

package object redditalerts {

  object models {

    /**
      * Wraps submissions and bases hashmap/set membership on the post's unique id.
      * Ordering is based on created date
      *
      * @param submission Submission to wrap
      */
    class SubmissionWrapper(val submission: Submission) {
      override def hashCode(): Int = submission.getUniqueId.hashCode

      override def equals(obj: Any): Boolean = obj match {
        case obj: SubmissionWrapper => obj.submission.getUniqueId == submission.getUniqueId
        case obj: Submission => obj.getUniqueId == submission.getUniqueId
        case _ => false
      }
    }

    object SubmissionWrapper {
      implicit val orderingByDate: Ordering[SubmissionWrapper] =
        Ordering.by(wrapper => (wrapper.submission.getCreated, wrapper.submission.getUniqueId))
    }

    case class Alert(@JsonProperty("alert_method") alertMethod: String,
                     @JsonProperty("alert_msg") alertMsg: String,
                     @JsonProperty("submission") submission: SubmissionSubset)

    case class SubmissionSubset(@JsonProperty("title") title: String,
                                @JsonProperty("permalink") permalink: String) {

      @JsonIgnore
      var additionalProperties: mutable.Map[String, Any] = mutable.Map.empty

      @JsonAnySetter
      def setAdditionalProperty(name: String, value: AnyRef) {
        this.additionalProperties.put(name, value)
      }
    }

  }


  lazy val MAPPER: ObjectMapper = {
    val mapper = new ObjectMapper
    mapper.registerModule(DefaultScalaModule)
  }
}
