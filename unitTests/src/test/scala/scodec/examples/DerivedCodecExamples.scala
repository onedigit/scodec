package scodec
package examples

import scodec.bits._
import codecs._
import implicits._

class DerivedCodecsExample extends CodecSuite {

  test("enums - explicit") {
    enum Color derives Codec { case Red, Green, Blue }
    val c = mappedEnum(uint8, Color.Red -> 10, Color.Green -> 20, Color.Blue -> 30)
    assertBitsEqual(c.encode(Color.Green).require, 0x14)
  }

  test("enums - derived") {
    enum Color derives Codec { case Red, Green, Blue }
    val c = Codec[Color]
    assertBitsEqual((c :: c :: c).encode(Color.Red, Color.Green, Color.Blue).require, 0x000102)
  }

  sealed trait Sprocket derives Codec
  case class Woozle(count: Int, strength: Int) extends Sprocket derives Codec
  case class Wocket(size: Int, inverted: Boolean) extends Sprocket
  case class Wootle(count: Int, data: BitVector) extends Sprocket

  case class Geiling(name: String, sprockets: Vector[Sprocket]) derives Codec

  sealed trait ColorT
  object ColorT {
    case object Red extends ColorT
    case object Yellow extends ColorT
    case object Green extends ColorT

    given Codec[ColorT] = mappedEnum(uint8, Red -> 0, Yellow -> 1, Green -> 2)
  }

  case class Point(x: Int, y: Int, z: Int)
  object Point {
    given Codec[Point] = (uint8 :: uint8 :: uint8).as[Point]
  }

  test("demonstrate deriving a codec for a case class") {
    // Codecs can be derived automatically for case classes where each component
    // type has an implicit codec available.
    //
    // In this example, Woozle is a product of two integers, and scodec.codecs.implicits._
    // is imported in this file, resulting an an implicit Codec[Int] being available.
    assertEquals(Codec[Woozle].encode(Woozle(1, 2)).require, hex"0000000100000002".bits)
  }

  test("demonstrate deriving a codec for a sealed class hierarchy") {
    // Codecs can be derived automatically for sealed class hierarchies where:
    //  - there is an implicit Discriminated[R, D] available where R is the root of
    //    the type hierarchy and D is the type of the discriminator
    //  - there is an implicit Discriminator[R, X, D] available for each subtype X of R
    //  - each subtype has an implicit codec (or can have one derived)
    //
    // In this example, Sprocket defines a Discriminated[Sprocket, Int] in its companion
    // and each subclass defines a Discriminator[Sprocket, X, Int] in their companions.
    assertEquals(Codec[Sprocket].encode(Wocket(3, true)).require, hex"0100000003ff".bits)
  }

  test("demonstrate nested derivations") {
    // Derived codecs can be based on other derived codecs.
    //
    // Geiling has a Vector[Sprocket] element. The scodec.codecs.implicits object
    // defines an implicit codec for Vector[A] when there's an implicitly available
    // Codec[A]. There's no manually defined `Codec[Sprocket]` but one is implicitly
    // derived.
    val ceil = Geiling("Ceil", Vector(Woozle(1, 2), Wocket(3, true)))
    val encoded = Codec[Geiling].encode(ceil).require
    assertEquals(encoded, hex"00000004 4365696c 00000002 000000000100000002 0100000003ff".bits)
    assertEquals(Codec[Geiling].decode(encoded).require.value, ceil)
  }

  test("demonstrate that derivation support does not interfere with manually authored implicit codecs in companions") {
    assertEquals(Codec[ColorT].encode(ColorT.Green).require, hex"02".bits)
    assertEquals(Codec[Point].encode(Point(1, 2, 3)).require.size, 24L)
  }
}
