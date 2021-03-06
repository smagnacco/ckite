/**
 * Generated by Scrooge
 *   version: ?
 *   rev: ?
 *   built at: ?
 */
package the.walrus.ckite.rpc.thrift

import com.twitter.scrooge.{
  ThriftException, ThriftStruct, ThriftStructCodec3}
import org.apache.thrift.protocol._
import org.apache.thrift.transport.{TMemoryBuffer, TTransport}
import java.nio.ByteBuffer
import scala.collection.immutable.{Map => immutable$Map}
import scala.collection.mutable.{
  ArrayBuffer => mutable$ArrayBuffer, Buffer => mutable$Buffer,
  HashMap => mutable$HashMap, HashSet => mutable$HashSet}
import scala.collection.{Map, Set}


object JoinResponseST extends ThriftStructCodec3[JoinResponseST] {
  val Struct = new TStruct("JoinResponseST")
  val SuccessField = new TField("success", TType.BOOL, 1)
  val SuccessFieldManifest = implicitly[Manifest[Boolean]]

  /**
   * Checks that all required fields are non-null.
   */
  def validate(_item: JoinResponseST) {
  }

  override def encode(_item: JoinResponseST, _oproto: TProtocol) { _item.write(_oproto) }
  override def decode(_iprot: TProtocol): JoinResponseST = Immutable.decode(_iprot)

  def apply(
    success: Boolean
  ): JoinResponseST = new Immutable(
    success
  )

  def unapply(_item: JoinResponseST): Option[Boolean] = Some(_item.success)

  object Immutable extends ThriftStructCodec3[JoinResponseST] {
    override def encode(_item: JoinResponseST, _oproto: TProtocol) { _item.write(_oproto) }
    override def decode(_iprot: TProtocol): JoinResponseST = {
      var success: Boolean = false
      var _got_success = false
      var _done = false
      _iprot.readStructBegin()
      while (!_done) {
        val _field = _iprot.readFieldBegin()
        if (_field.`type` == TType.STOP) {
          _done = true
        } else {
          _field.id match {
            case 1 => { /* success */
              _field.`type` match {
                case TType.BOOL => {
                  success = {
                    _iprot.readBool()
                  }
                  _got_success = true
                }
                case _ => TProtocolUtil.skip(_iprot, _field.`type`)
              }
            }
            case _ =>
              TProtocolUtil.skip(_iprot, _field.`type`)
          }
          _iprot.readFieldEnd()
        }
      }
      _iprot.readStructEnd()
      if (!_got_success) throw new TProtocolException("Required field 'JoinResponseST' was not found in serialized data for struct JoinResponseST")
      new Immutable(
        success
      )
    }
  }

  /**
   * The default read-only implementation of JoinResponseST.  You typically should not need to
   * directly reference this class; instead, use the JoinResponseST.apply method to construct
   * new instances.
   */
  class Immutable(
    val success: Boolean
  ) extends JoinResponseST

  /**
   * This Proxy trait allows you to extend the JoinResponseST trait with additional state or
   * behavior and implement the read-only methods from JoinResponseST using an underlying
   * instance.
   */
  trait Proxy extends JoinResponseST {
    protected def _underlying_JoinResponseST: JoinResponseST
    override def success: Boolean = _underlying_JoinResponseST.success
  }
}

trait JoinResponseST extends ThriftStruct
  with Product1[Boolean]
  with java.io.Serializable
{
  import JoinResponseST._


  def success: Boolean

  def _1 = success


  /**
   * If the specified field is optional, it is set to None.  Otherwise, if the field is
   * known, it is reverted to its default value; if the field is unknown, it is subtracked
   * from the passthroughFields map, if present.
   */
  def unsetField(_fieldId: Short): JoinResponseST =
    _fieldId match {
      case 1 => copy(success = false)
      case _ => this
    }

  override def write(_oprot: TProtocol) {
    JoinResponseST.validate(this)
    _oprot.writeStructBegin(Struct)
    if (true) {
      val success_item = success
      _oprot.writeFieldBegin(SuccessField)
      _oprot.writeBool(success_item)
      _oprot.writeFieldEnd()
    }
    _oprot.writeFieldStop()
    _oprot.writeStructEnd()
  }

  def copy(
    success: Boolean = this.success
  ): JoinResponseST =
    new Immutable(
      success
    )

  override def canEqual(other: Any): Boolean = other.isInstanceOf[JoinResponseST]

  override def equals(other: Any): Boolean =
    _root_.scala.runtime.ScalaRunTime._equals(this, other)

  override def hashCode: Int = _root_.scala.runtime.ScalaRunTime._hashCode(this)

  override def toString: String = _root_.scala.runtime.ScalaRunTime._toString(this)


  override def productArity: Int = 1

  override def productElement(n: Int): Any = n match {
    case 0 => success
    case _ => throw new IndexOutOfBoundsException(n.toString)
  }

  override def productPrefix: String = "JoinResponseST"
}