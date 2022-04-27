package rescala.extra.encrdt.encrypted.deltabased

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromArray}
import com.google.crypto.tink.Aead
import kofre.causality.CausalContext

case class EncryptedDeltaGroup(stateCiphertext: Array[Byte], serialDottedVersionVector: Array[Byte])(
    implicit dotSetJsonCodec: JsonValueCodec[CausalContext]
) {
  lazy val dottedVersionVector: CausalContext = readFromArray(serialDottedVersionVector)

  def decrypt[T](aead: Aead)(implicit tJsonCodec: JsonValueCodec[T]): DecryptedDeltaGroup[T] = {
    val plainText           = aead.decrypt(stateCiphertext, serialDottedVersionVector)
    val state               = readFromArray[T](plainText)
    val dottedVersionVector = readFromArray[CausalContext](serialDottedVersionVector)
    DecryptedDeltaGroup(state, dottedVersionVector)
  }
}