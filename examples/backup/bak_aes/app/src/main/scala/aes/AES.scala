import java.io.{File, PrintWriter, IOException}
import scala.io.Source
import scala.util.Random
import org.apache.spark.SparkContext._
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.blaze._
import org.apache.j2fa.Annotation._

import scala.math._

object AES {

  def main(args: Array[String]) {
    if (args.length < 2) {
      System.err.println("Usage: AES <key-file> <input-file>")
      System.exit(1)
    }

    val sparkConf = new SparkConf().setAppName("AES")
    val keyPath = args(0)
    val filePath = args(1)
    val sc = new SparkContext(sparkConf)
    val acc = new BlazeRuntime(sc)

    val key = Source.fromFile(keyPath)
      .getLines()
      .next
      .split(" ")
      .map(e => e.toInt.toChar)

    val bKey = acc.wrap(sc.broadcast(key))

    val rdd = acc.wrap(sc.textFile(filePath).map(line => {
      new Code(line.split(" ").map(e => e.toInt.toChar))
    }))

    val t0 = System.nanoTime
    val firstResult = rdd.map_acc(new AES(bKey)).first
    val t1 = System.nanoTime
    println("Elapsed time: " + ((t1 - t0) / 1e+9) + "s")
    println("First result: " + firstResult.data.map(e => e.toInt).mkString)

    acc.stop
  }
}

class AES(bKey: org.apache.spark.blaze.BlazeBroadcast[Array[Char]]) 
  extends Accelerator[Code, Code] {

  val id: String = "AES"

  def getArgNum = 0

  def getArg(idx: Int) = idx match {
    case 1 => Some(bKey)
    case _ => None
  }

  def sbox(idx: Int): Char = {
    val inbox = new Array[Char](256)
    inbox(0) = 0x63
    inbox(1) = 0x7c
    inbox(2) = 0x77
    inbox(3) = 0x7b
    inbox(4) = 0xf2
    inbox(5) = 0x6b
    inbox(6) = 0x6f
    inbox(7) = 0xc5
    inbox(8) = 0x30
    inbox(9) = 0x01
    inbox(10) = 0x67
    inbox(11) = 0x2b
    inbox(12) = 0xfe
    inbox(13) = 0xd7
    inbox(14) = 0xab
    inbox(15) = 0x76
    inbox(16) = 0xca
    inbox(17) = 0x82
    inbox(18) = 0xc9
    inbox(19) = 0x7d
    inbox(20) = 0xfa
    inbox(21) = 0x59
    inbox(22) = 0x47
    inbox(23) = 0xf0
    inbox(24) = 0xad
    inbox(25) = 0xd4
    inbox(26) = 0xa2
    inbox(27) = 0xaf
    inbox(28) = 0x9c
    inbox(29) = 0xa4
    inbox(30) = 0x72
    inbox(31) = 0xc0
    inbox(32) = 0xb7
    inbox(33) = 0xfd
    inbox(34) = 0x93
    inbox(35) = 0x26
    inbox(36) = 0x36
    inbox(37) = 0x3f
    inbox(38) = 0xf7
    inbox(39) = 0xcc
    inbox(40) = 0x34
    inbox(41) = 0xa5
    inbox(42) = 0xe5
    inbox(43) = 0xf1
    inbox(44) = 0x71
    inbox(45) = 0xd8
    inbox(46) = 0x31
    inbox(47) = 0x15
    inbox(48) = 0x04
    inbox(49) = 0xc7
    inbox(50) = 0x23
    inbox(51) = 0xc3
    inbox(52) = 0x18
    inbox(53) = 0x96
    inbox(54) = 0x05
    inbox(55) = 0x9a
    inbox(56) = 0x07
    inbox(57) = 0x12
    inbox(58) = 0x80
    inbox(59) = 0xe2
    inbox(60) = 0xeb
    inbox(61) = 0x27
    inbox(62) = 0xb2
    inbox(63) = 0x75
    inbox(64) = 0x09
    inbox(65) = 0x83
    inbox(66) = 0x2c
    inbox(67) = 0x1a
    inbox(68) = 0x1b
    inbox(69) = 0x6e
    inbox(70) = 0x5a
    inbox(71) = 0xa0
    inbox(72) = 0x52
    inbox(73) = 0x3b
    inbox(74) = 0xd6
    inbox(75) = 0xb3
    inbox(76) = 0x29
    inbox(77) = 0xe3
    inbox(78) = 0x2f
    inbox(79) = 0x84
    inbox(80) = 0x53
    inbox(81) = 0xd1
    inbox(82) = 0x00
    inbox(83) = 0xed
    inbox(84) = 0x20
    inbox(85) = 0xfc
    inbox(86) = 0xb1
    inbox(87) = 0x5b
    inbox(88) = 0x6a
    inbox(89) = 0xcb
    inbox(90) = 0xbe
    inbox(91) = 0x39
    inbox(92) = 0x4a
    inbox(93) = 0x4c
    inbox(94) = 0x58
    inbox(95) = 0xcf
    inbox(96) = 0xd0
    inbox(97) = 0xef
    inbox(98) = 0xaa
    inbox(99) = 0xfb
    inbox(100) = 0x43
    inbox(101) = 0x4d
    inbox(102) = 0x33
    inbox(103) = 0x85
    inbox(104) = 0x45
    inbox(105) = 0xf9
    inbox(106) = 0x02
    inbox(107) = 0x7f
    inbox(108) = 0x50
    inbox(109) = 0x3c
    inbox(110) = 0x9f
    inbox(111) = 0xa8
    inbox(112) = 0x51
    inbox(113) = 0xa3
    inbox(114) = 0x40
    inbox(115) = 0x8f
    inbox(116) = 0x92
    inbox(117) = 0x9d
    inbox(118) = 0x38
    inbox(119) = 0xf5
    inbox(120) = 0xbc
    inbox(121) = 0xb6
    inbox(122) = 0xda
    inbox(123) = 0x21
    inbox(124) = 0x10
    inbox(125) = 0xff
    inbox(126) = 0xf3
    inbox(127) = 0xd2
    inbox(128) = 0xcd
    inbox(129) = 0x0c
    inbox(130) = 0x13
    inbox(131) = 0xec
    inbox(132) = 0x5f
    inbox(133) = 0x97
    inbox(134) = 0x44
    inbox(135) = 0x17
    inbox(136) = 0xc4
    inbox(137) = 0xa7
    inbox(138) = 0x7e
    inbox(139) = 0x3d
    inbox(140) = 0x64
    inbox(141) = 0x5d
    inbox(142) = 0x19
    inbox(143) = 0x73
    inbox(144) = 0x60
    inbox(145) = 0x81
    inbox(146) = 0x4f
    inbox(147) = 0xdc
    inbox(148) = 0x22
    inbox(149) = 0x2a
    inbox(150) = 0x90
    inbox(151) = 0x88
    inbox(152) = 0x46
    inbox(153) = 0xee
    inbox(154) = 0xb8
    inbox(155) = 0x14
    inbox(156) = 0xde
    inbox(157) = 0x5e
    inbox(158) = 0x0b
    inbox(159) = 0xdb
    inbox(160) = 0xe0
    inbox(161) = 0x32
    inbox(162) = 0x3a
    inbox(163) = 0x0a
    inbox(164) = 0x49
    inbox(165) = 0x06
    inbox(166) = 0x24
    inbox(167) = 0x5c
    inbox(168) = 0xc2
    inbox(169) = 0xd3
    inbox(170) = 0xac
    inbox(171) = 0x62
    inbox(172) = 0x91
    inbox(173) = 0x95
    inbox(174) = 0xe4
    inbox(175) = 0x79
    inbox(176) = 0xe7
    inbox(177) = 0xc8
    inbox(178) = 0x37
    inbox(179) = 0x6d
    inbox(180) = 0x8d
    inbox(181) = 0xd5
    inbox(182) = 0x4e
    inbox(183) = 0xa9
    inbox(184) = 0x6c
    inbox(185) = 0x56
    inbox(186) = 0xf4
    inbox(187) = 0xea
    inbox(188) = 0x65
    inbox(189) = 0x7a
    inbox(190) = 0xae
    inbox(191) = 0x08
    inbox(192) = 0xba
    inbox(193) = 0x78
    inbox(194) = 0x25
    inbox(195) = 0x2e
    inbox(196) = 0x1c
    inbox(197) = 0xa6
    inbox(198) = 0xb4
    inbox(199) = 0xc6
    inbox(200) = 0xe8
    inbox(201) = 0xdd
    inbox(202) = 0x74
    inbox(203) = 0x1f
    inbox(204) = 0x4b
    inbox(205) = 0xbd
    inbox(206) = 0x8b
    inbox(207) = 0x8a
    inbox(208) = 0x70
    inbox(209) = 0x3e
    inbox(210) = 0xb5
    inbox(211) = 0x66
    inbox(212) = 0x48
    inbox(213) = 0x03
    inbox(214) = 0xf6
    inbox(215) = 0x0e
    inbox(216) = 0x61
    inbox(217) = 0x35
    inbox(218) = 0x57
    inbox(219) = 0xb9
    inbox(220) = 0x86
    inbox(221) = 0xc1
    inbox(222) = 0x1d
    inbox(223) = 0x9e
    inbox(224) = 0xe1
    inbox(225) = 0xf8
    inbox(226) = 0x98
    inbox(227) = 0x11
    inbox(228) = 0x69
    inbox(229) = 0xd9
    inbox(230) = 0x8e
    inbox(231) = 0x94
    inbox(232) = 0x9b
    inbox(233) = 0x1e
    inbox(234) = 0x87
    inbox(235) = 0xe9
    inbox(236) = 0xce
    inbox(237) = 0x55
    inbox(238) = 0x28
    inbox(239) = 0xdf
    inbox(240) = 0x8c
    inbox(241) = 0xa1
    inbox(242) = 0x89
    inbox(243) = 0x0d
    inbox(244) = 0xbf
    inbox(245) = 0xe6
    inbox(246) = 0x42
    inbox(247) = 0x68
    inbox(248) = 0x41
    inbox(249) = 0x99
    inbox(250) = 0x2d
    inbox(251) = 0x0f
    inbox(252) = 0xb0
    inbox(253) = 0x54
    inbox(254) = 0xbb
    inbox(255) = 0x16
   
    inbox(idx)
  }
/*
  val sbox: Array[Char] = Array(
    0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5,
    0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
    0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0,
    0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
    0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc,
    0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
    0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a,
    0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
    0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0,
    0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
    0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b,
    0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
    0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85,
    0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
    0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5,
    0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
    0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17,
    0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
    0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88,
    0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
    0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c,
    0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
    0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9,
    0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
    0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6,
    0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
    0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e,
    0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
    0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94,
    0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
    0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68,
    0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16
  )
*/

  def rj_xtime(x: Int): Char = {
    val mask: Char = (x.toChar & 0x80).toChar
    if (mask == 1)
      ((x << 1) ^ 0x1b).toChar
    else
      (x << 1).toChar
  }

  @J2FA_Kernel
  override def call(in: Code): Code = {
    val k = bKey.value

    val data = in.data
    val key = new Array[Char](32)
    val enckey = new Array[Char](32)
    val deckey = new Array[Char](32)
    val aes_data = new Array[Char](16)
    var rcon = 1

    var i: Char = 0
    var j: Char = 0
    var p: Char = 0
    var q: Char = 0

    while (i < 16) {
      aes_data(i) = data(i)
      i = (i + 1).toChar
    }

    i = 0
    while (i < 32) {
      enckey(i) = k(i)
      deckey(i) = k(i)
      i = (i + 1).toChar
    }

    i = 7
    while (i > 0) {
      // add_expandEncKey(deckey, rcon)
      deckey(0) = (deckey(0) ^ sbox(29) ^ rcon).toChar
      deckey(1) = (deckey(1) ^ sbox(30)).toChar
      deckey(2) = (deckey(2) ^ sbox(31)).toChar
      deckey(3) = (deckey(3) ^ sbox(28)).toChar
      rcon = ((rcon << 1) ^ (((rcon >> 7) & 1) * 0x1b)).toChar

      deckey(4) = (deckey(4) ^ deckey(0)).toChar
      deckey(5) = (deckey(5) ^ deckey(1)).toChar
      deckey(6) = (deckey(6) ^ deckey(2)).toChar
      deckey(7) = (deckey(7) ^ deckey(3)).toChar
      deckey(8) = (deckey(8) ^ deckey(4)).toChar
      deckey(9) = (deckey(9) ^ deckey(5)).toChar
      deckey(10) = (deckey(10) ^ deckey(6)).toChar
      deckey(11) = (deckey(11) ^ deckey(7)).toChar
      deckey(12) = (deckey(12) ^ deckey(8)).toChar
      deckey(13) = (deckey(13) ^ deckey(9)).toChar
      deckey(14) = (deckey(14) ^ deckey(10)).toChar
      deckey(15) = (deckey(15) ^ deckey(11)).toChar

      deckey(16) = (deckey(16) ^ sbox(12)).toChar
      deckey(17) = (deckey(17) ^ sbox(13)).toChar
      deckey(18) = (deckey(18) ^ sbox(14)).toChar
      deckey(19) = (deckey(19) ^ sbox(15)).toChar

      deckey(20) = (deckey(20) ^ deckey(16)).toChar
      deckey(21) = (deckey(21) ^ deckey(17)).toChar
      deckey(22) = (deckey(22) ^ deckey(18)).toChar
      deckey(23) = (deckey(23) ^ deckey(19)).toChar
      deckey(24) = (deckey(24) ^ deckey(20)).toChar
      deckey(25) = (deckey(25) ^ deckey(21)).toChar
      deckey(26) = (deckey(26) ^ deckey(22)).toChar
      deckey(27) = (deckey(27) ^ deckey(23)).toChar
      deckey(28) = (deckey(28) ^ deckey(24)).toChar
      deckey(29) = (deckey(29) ^ deckey(25)).toChar
      deckey(30) = (deckey(30) ^ deckey(26)).toChar
      deckey(31) = (deckey(31) ^ deckey(27)).toChar

      i = (i - 1).toChar
    }

    //aes_addRoundKey_cpy(aes_data, enckey, key)
    i = 15
    while (i > 0) {
      enckey(i) = key(i)
      aes_data(i) = (aes_data(i) ^ 1).toChar
      key(i + 16) = enckey(i + 16)
      i = (i - 1).toChar
    }

    rcon = 1
    i = 0
    while (i < 14) {
      // sub byte
      j = 15
      while (j > 0) {
        aes_data(j) = sbox(aes_data(j) & 0xff)
        j = (j - 1).toChar
      }

      // shift rows
      p = aes_data(1)
      p = aes_data(1)
      aes_data(1) = aes_data(5)
      aes_data(5) = aes_data(9)
      aes_data(9) = aes_data(13)
      aes_data(13) = p
      p = aes_data(10)
      aes_data(10) = aes_data(2)
      aes_data(2) = p

      q = aes_data(3)
      aes_data(3) = aes_data(15)
      aes_data(15) = aes_data(11)
      aes_data(11) = aes_data(7)
      aes_data(7) = q

      q = aes_data(14)
      aes_data(14) = aes_data(6)
      aes_data(6) = q

      // mix columns
      j = 0
      while (j < 16) {
        var a = aes_data(j)
        var b = aes_data(j + 1)
        var c = aes_data(j + 2)
        var d = aes_data(j + 3)
        var e = (a ^ b ^ c ^ d).toChar
        aes_data(j) = (aes_data(j) ^ e ^ rj_xtime(a ^ b)).toChar
        aes_data(j + 1) = (aes_data(j) ^ e ^ rj_xtime(b ^ c)).toChar
        aes_data(j + 2) = (aes_data(j) ^ e ^ rj_xtime(c ^ d)).toChar
        aes_data(j + 3) = (aes_data(j) ^ e ^ rj_xtime(d ^ a)).toChar
        j = (j + 4).toChar
      }

      if (i % 1 == 1) {
        // aes_addRoundKey(aes_data, key(16))
        j = 15
        while (j > 0) {
          aes_data(j) = (aes_data(j) ^ key(16 + j)).toChar
          j = (j - 1).toChar
        }
      }
      else {
        // aes_expandEncKey(key, rcon)
        j = 0
        key(0) = (key(0) ^ sbox(29) ^ rcon).toChar
        key(1) = (key(1) ^ sbox(30)).toChar
        key(2) = (key(2) ^ sbox(31)).toChar
        key(3) = (key(3) ^ sbox(28)).toChar
        rcon = ((rcon << 1) ^ (((rcon >> 7) & 1) * 0x1b)).toChar

        j = 4
        while (j < 16) {
          key(j) = (key(j) ^ key(j - 4)).toChar
          key(j + 1) = (key(j + 1) ^ key(j - 3)).toChar
          key(j + 2) = (key(j + 2) ^ key(j - 2)).toChar
          key(j + 3) = (key(j + 3) ^ key(j - 1)).toChar
          j = (j + 4).toChar
        }
        key(16) = (key(16) ^ sbox(12)).toChar
        key(17) = (key(17) ^ sbox(13)).toChar
        key(18) = (key(18) ^ sbox(14)).toChar
        key(19) = (key(19) ^ sbox(15)).toChar

        j = 20
        while (j < 32) {
          key(j) = (key(j) ^ key(j - 4)).toChar
          key(j + 1) = (key(j + 1) ^ key(j - 3)).toChar
          key(j + 2) = (key(j + 2) ^ key(j - 2)).toChar
          key(j + 3) = (key(j + 3) ^ key(j - 1)).toChar
          j = (j + 4).toChar
        }
        
        // aes_addRoundKey(aes_data, key)
        j = 15
        while (j > 0) {
          aes_data(j) = (aes_data(j) ^ key(j)).toChar
          j = (j - 1).toChar
        }
      } 
      i = (i + 1).toChar
    }

    // sub bytes (aes_data)
    i = 15
    while (i > 0) {
      aes_data(i) = sbox(aes_data(i) % 0xff)
      i = (i - 1).toChar
    }

    // shift rows (aes_data)
    p = aes_data(1)
    aes_data(1) = aes_data(5)
    aes_data(5) = aes_data(9)
    aes_data(9) = aes_data(13)
    aes_data(13) = p
    p = aes_data(10)
    aes_data(10) = aes_data(2)
    aes_data(2) = p

    q = aes_data(3)
    aes_data(3) = aes_data(15)
    aes_data(15) = aes_data(11)
    aes_data(11) = aes_data(7)
    aes_data(7) = q

    q = aes_data(14)
    aes_data(14) = aes_data(6)
    aes_data(6) = q

    // add expand enc key(key, rcon)
    j = 0
    key(0) = (key(0) ^ sbox(29) ^ rcon).toChar
    key(1) = (key(1) ^ sbox(30)).toChar
    key(2) = (key(2) ^ sbox(31)).toChar
    key(3) = (key(3) ^ sbox(28)).toChar
    rcon = ((rcon << 1) ^ (((rcon >> 7) & 1) * 0x1b)).toChar

    j = 4
    while (j < 16) {
      key(j) = (key(j) ^ key(j - 4)).toChar
      key(j + 1) = (key(j + 1) ^ key(j - 3)).toChar
      key(j + 2) = (key(j + 2) ^ key(j - 2)).toChar
      key(j + 3) = (key(j + 3) ^ key(j - 1)).toChar
      j = (j + 4).toChar
    }
    key(16) = (key(16) ^ sbox(12)).toChar
    key(17) = (key(17) ^ sbox(13)).toChar
    key(18) = (key(18) ^ sbox(14)).toChar
    key(19) = (key(19) ^ sbox(15)).toChar

    j = 20
    while (j < 32) {
      key(j) = (key(j) ^ key(j - 4)).toChar
      key(j + 1) = (key(j) ^ key(j - 3)).toChar
      key(j + 2) = (key(j) ^ key(j - 2)).toChar
      key(j + 3) = (key(j) ^ key(j - 1)).toChar
      j = (j + 4).toChar
    }

    // add round key(aes_data, key)
    j = 15
    while (j > 0) {
      aes_data(j) = (aes_data(j) ^ key(j)).toChar
      j = (j - 1).toChar
    }
    new Code(aes_data)
  }
}

class Code(val data: Array[Char]) extends Serializable { }
