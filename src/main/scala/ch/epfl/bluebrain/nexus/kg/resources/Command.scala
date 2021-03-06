package ch.epfl.bluebrain.nexus.kg.resources

import java.time.Instant

import ch.epfl.bluebrain.nexus.iam.client.types.Identity
import ch.epfl.bluebrain.nexus.kg.resources.attachment.Attachment.BinaryAttributes
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import io.circe.Json

/**
  * Enumeration of resource command types.
  */
sealed trait Command extends Product with Serializable {

  /**
    * @return the resource identifier
    */
  def id: Id[ProjectRef]

  /**
    * @return the last known revision of the resource when this command was created
    */
  def rev: Long

  /**
    * @return the instant when this command was created
    */
  def instant: Instant

  /**
    * @return the identity which created this command
    */
  def identity: Identity
}

object Command {

  /**
    * An intent for resource creation.
    *
    * @param id       the resource identifier
    * @param rev      the last known revision of the resource when this command was created
    * @param schema   the schema that is used to constrain the resource
    * @param types    the collection of known resource types (asserted or inferred)
    * @param source   the source representation of the resource
    * @param instant  the instant when this command was created
    * @param identity the identity which created this command
    */
  final case class Create(
      id: Id[ProjectRef],
      rev: Long,
      schema: Ref,
      types: Set[AbsoluteIri],
      source: Json,
      instant: Instant,
      identity: Identity
  ) extends Command

  /**
    * An intent for resource update.
    *
    * @param id       the resource identifier
    * @param rev      the last known revision of the resource when this command was created
    * @param types    the collection of known resource types (asserted or inferred)
    * @param source   the new source representation of the resource
    * @param instant  the instant when this command was created
    * @param identity the identity which created this command
    */
  final case class Update(
      id: Id[ProjectRef],
      rev: Long,
      types: Set[AbsoluteIri],
      source: Json,
      instant: Instant,
      identity: Identity
  ) extends Command

  /**
    * An intent for resource deprecation.
    *
    * @param id       the resource identifier
    * @param rev      the last known revision of the resource when this command was created
    * @param instant  the instant when this command was created
    * @param identity the identity which created this command
    */
  final case class Deprecate(
      id: Id[ProjectRef],
      rev: Long,
      instant: Instant,
      identity: Identity
  ) extends Command

  /**
    * An intent to add a tag to a resource (revision aliasing).
    *
    * @param id        the resource identifier
    * @param rev       the last known revision of the resource when this command was created
    * @param targetRev the revision to be tagged with the provided ''tag''
    * @param tag       the tag's name
    * @param instant   the instant when this command was created
    * @param identity  the identity which created this command
    */
  final case class AddTag(
      id: Id[ProjectRef],
      rev: Long,
      targetRev: Long,
      tag: String,
      instant: Instant,
      identity: Identity
  ) extends Command

  /**
    * An intent to add an attachment to a resource.
    *
    * @param id       the resource identifier
    * @param rev      the last known revision of the resource when this command was created
    * @param value    the metadata of the attachment
    * @param instant  the instant when this event was recorded
    * @param identity the identity which generated this event
    */
  final case class AddAttachment(id: Id[ProjectRef],
                                 rev: Long,
                                 value: BinaryAttributes,
                                 instant: Instant,
                                 identity: Identity)
      extends Command

  /**
    * An intent to remove an attachment from a resource.
    *
    * @param id       the resource identifier
    * @param rev      the revision that this event generated
    * @param filename the filename of the attachment to remove
    * @param instant  the instant when this event was recorded
    * @param identity the identity which generated this event
    */
  final case class RemoveAttachment(id: Id[ProjectRef],
                                    rev: Long,
                                    filename: String,
                                    instant: Instant,
                                    identity: Identity)
      extends Command
}
