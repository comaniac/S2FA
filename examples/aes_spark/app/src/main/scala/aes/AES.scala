import java.io.{File, PrintWriter, IOException}
import scala.io.Source
import scala.util.Random
import org.apache.spark.SparkContext._
import org.apache.spark.{SparkConf, SparkContext}

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

	val key = Source.fromFile(keyPath)
	  .getLines()
	  .next
	  .split(" ")
	  .map(e => e.toInt.toByte)

	  // Preprocess
	  val rdd = sc.textFile(filePath).map(line => line.split(" ").map(e => e.toInt.toByte))

	  val t0 = System.nanoTime
	  val trans = rdd.map(data => {

		val sbox = new Array[Byte](256)
		sbox(0) = 0x63.toByte
		sbox(1) = 0x7c.toByte
		sbox(2) = 0x77.toByte
		sbox(3) = 0x7b.toByte
		sbox(4) = 0xf2.toByte
		sbox(5) = 0x6b.toByte
		sbox(6) = 0x6f.toByte
		sbox(7) = 0xc5.toByte
		sbox(8) = 0x30.toByte
		sbox(9) = 0x01.toByte
		sbox(10) = 0x67.toByte
		sbox(11) = 0x2b.toByte
		sbox(12) = 0xfe.toByte
		sbox(13) = 0xd7.toByte
		sbox(14) = 0xab.toByte
		sbox(15) = 0x76.toByte
		sbox(16) = 0xca.toByte
		sbox(17) = 0x82.toByte
		sbox(18) = 0xc9.toByte
		sbox(19) = 0x7d.toByte
		sbox(20) = 0xfa.toByte
		sbox(21) = 0x59.toByte
		sbox(22) = 0x47.toByte
		sbox(23) = 0xf0.toByte
		sbox(24) = 0xad.toByte
		sbox(25) = 0xd4.toByte
		sbox(26) = 0xa2.toByte
		sbox(27) = 0xaf.toByte
		sbox(28) = 0x9c.toByte
		sbox(29) = 0xa4.toByte
		sbox(30) = 0x72.toByte
		sbox(31) = 0xc0.toByte
		sbox(32) = 0xb7.toByte
		sbox(33) = 0xfd.toByte
		sbox(34) = 0x93.toByte
		sbox(35) = 0x26.toByte
		sbox(36) = 0x36.toByte
		sbox(37) = 0x3f.toByte
		sbox(38) = 0xf7.toByte
		sbox(39) = 0xcc.toByte
		sbox(40) = 0x34.toByte
		sbox(41) = 0xa5.toByte
		sbox(42) = 0xe5.toByte
		sbox(43) = 0xf1.toByte
		sbox(44) = 0x71.toByte
		sbox(45) = 0xd8.toByte
		sbox(46) = 0x31.toByte
		sbox(47) = 0x15.toByte
		sbox(48) = 0x04.toByte
		sbox(49) = 0xc7.toByte
		sbox(50) = 0x23.toByte
		sbox(51) = 0xc3.toByte
		sbox(52) = 0x18.toByte
		sbox(53) = 0x96.toByte
		sbox(54) = 0x05.toByte
		sbox(55) = 0x9a.toByte
		sbox(56) = 0x07.toByte
		sbox(57) = 0x12.toByte
		sbox(58) = 0x80.toByte
		sbox(59) = 0xe2.toByte
		sbox(60) = 0xeb.toByte
		sbox(61) = 0x27.toByte
		sbox(62) = 0xb2.toByte
		sbox(63) = 0x75.toByte
		sbox(64) = 0x09.toByte
		sbox(65) = 0x83.toByte
		sbox(66) = 0x2c.toByte
		sbox(67) = 0x1a.toByte
		sbox(68) = 0x1b.toByte
		sbox(69) = 0x6e.toByte
		sbox(70) = 0x5a.toByte
		sbox(71) = 0xa0.toByte
		sbox(72) = 0x52.toByte
		sbox(73) = 0x3b.toByte
		sbox(74) = 0xd6.toByte
		sbox(75) = 0xb3.toByte
		sbox(76) = 0x29.toByte
		sbox(77) = 0xe3.toByte
		sbox(78) = 0x2f.toByte
		sbox(79) = 0x84.toByte
		sbox(80) = 0x53.toByte
		sbox(81) = 0xd1.toByte
		sbox(82) = 0x00.toByte
		sbox(83) = 0xed.toByte
		sbox(84) = 0x20.toByte
		sbox(85) = 0xfc.toByte
		sbox(86) = 0xb1.toByte
		sbox(87) = 0x5b.toByte
		sbox(88) = 0x6a.toByte
		sbox(89) = 0xcb.toByte
		sbox(90) = 0xbe.toByte
		sbox(91) = 0x39.toByte
		sbox(92) = 0x4a.toByte
		sbox(93) = 0x4c.toByte
		sbox(94) = 0x58.toByte
		sbox(95) = 0xcf.toByte
		sbox(96) = 0xd0.toByte
		sbox(97) = 0xef.toByte
		sbox(98) = 0xaa.toByte
		sbox(99) = 0xfb.toByte
		sbox(100) = 0x43.toByte
		sbox(101) = 0x4d.toByte
		sbox(102) = 0x33.toByte
		sbox(103) = 0x85.toByte
		sbox(104) = 0x45.toByte
		sbox(105) = 0xf9.toByte
		sbox(106) = 0x02.toByte
		sbox(107) = 0x7f.toByte
		sbox(108) = 0x50.toByte
		sbox(109) = 0x3c.toByte
		sbox(110) = 0x9f.toByte
		sbox(111) = 0xa8.toByte
		sbox(112) = 0x51.toByte
		sbox(113) = 0xa3.toByte
		sbox(114) = 0x40.toByte
		sbox(115) = 0x8f.toByte
		sbox(116) = 0x92.toByte
		sbox(117) = 0x9d.toByte
		sbox(118) = 0x38.toByte
		sbox(119) = 0xf5.toByte
		sbox(120) = 0xbc.toByte
		sbox(121) = 0xb6.toByte
		sbox(122) = 0xda.toByte
		sbox(123) = 0x21.toByte
		sbox(124) = 0x10.toByte
		sbox(125) = 0xff.toByte
		sbox(126) = 0xf3.toByte
		sbox(127) = 0xd2.toByte
		sbox(128) = 0xcd.toByte
		sbox(129) = 0x0c.toByte
		sbox(130) = 0x13.toByte
		sbox(131) = 0xec.toByte
		sbox(132) = 0x5f.toByte
		sbox(133) = 0x97.toByte
		sbox(134) = 0x44.toByte
		sbox(135) = 0x17.toByte
		sbox(136) = 0xc4.toByte
		sbox(137) = 0xa7.toByte
		sbox(138) = 0x7e.toByte
		sbox(139) = 0x3d.toByte
		sbox(140) = 0x64.toByte
		sbox(141) = 0x5d.toByte
		sbox(142) = 0x19.toByte
		sbox(143) = 0x73.toByte
		sbox(144) = 0x60.toByte
		sbox(145) = 0x81.toByte
		sbox(146) = 0x4f.toByte
		sbox(147) = 0xdc.toByte
		sbox(148) = 0x22.toByte
		sbox(149) = 0x2a.toByte
		sbox(150) = 0x90.toByte
		sbox(151) = 0x88.toByte
		sbox(152) = 0x46.toByte
		sbox(153) = 0xee.toByte
		sbox(154) = 0xb8.toByte
		sbox(155) = 0x14.toByte
		sbox(156) = 0xde.toByte
		sbox(157) = 0x5e.toByte
		sbox(158) = 0x0b.toByte
		sbox(159) = 0xdb.toByte
		sbox(160) = 0xe0.toByte
		sbox(161) = 0x32.toByte
		sbox(162) = 0x3a.toByte
		sbox(163) = 0x0a.toByte
		sbox(164) = 0x49.toByte
		sbox(165) = 0x06.toByte
		sbox(166) = 0x24.toByte
		sbox(167) = 0x5c.toByte
		sbox(168) = 0xc2.toByte
		sbox(169) = 0xd3.toByte
		sbox(170) = 0xac.toByte
		sbox(171) = 0x62.toByte
		sbox(172) = 0x91.toByte
		sbox(173) = 0x95.toByte
		sbox(174) = 0xe4.toByte
		sbox(175) = 0x79.toByte
		sbox(176) = 0xe7.toByte
		sbox(177) = 0xc8.toByte
		sbox(178) = 0x37.toByte
		sbox(179) = 0x6d.toByte
		sbox(180) = 0x8d.toByte
		sbox(181) = 0xd5.toByte
		sbox(182) = 0x4e.toByte
		sbox(183) = 0xa9.toByte
		sbox(184) = 0x6c.toByte
		sbox(185) = 0x56.toByte
		sbox(186) = 0xf4.toByte
		sbox(187) = 0xea.toByte
		sbox(188) = 0x65.toByte
		sbox(189) = 0x7a.toByte
		sbox(190) = 0xae.toByte
		sbox(191) = 0x08.toByte
		sbox(192) = 0xba.toByte
		sbox(193) = 0x78.toByte
		sbox(194) = 0x25.toByte
		sbox(195) = 0x2e.toByte
		sbox(196) = 0x1c.toByte
		sbox(197) = 0xa6.toByte
		sbox(198) = 0xb4.toByte
		sbox(199) = 0xc6.toByte
		sbox(200) = 0xe8.toByte
		sbox(201) = 0xdd.toByte
		sbox(202) = 0x74.toByte
		sbox(203) = 0x1f.toByte
		sbox(204) = 0x4b.toByte
		sbox(205) = 0xbd.toByte
		sbox(206) = 0x8b.toByte
		sbox(207) = 0x8a.toByte
		sbox(208) = 0x70.toByte
		sbox(209) = 0x3e.toByte
		sbox(210) = 0xb5.toByte
		sbox(211) = 0x66.toByte
		sbox(212) = 0x48.toByte
		sbox(213) = 0x03.toByte
		sbox(214) = 0xf6.toByte
		sbox(215) = 0x0e.toByte
		sbox(216) = 0x61.toByte
		sbox(217) = 0x35.toByte
		sbox(218) = 0x57.toByte
		sbox(219) = 0xb9.toByte
		sbox(220) = 0x86.toByte
		sbox(221) = 0xc1.toByte
		sbox(222) = 0x1d.toByte
		sbox(223) = 0x9e.toByte
		sbox(224) = 0xe1.toByte
		sbox(225) = 0xf8.toByte
		sbox(226) = 0x98.toByte
		sbox(227) = 0x11.toByte
		sbox(228) = 0x69.toByte
		sbox(229) = 0xd9.toByte
		sbox(230) = 0x8e.toByte
		sbox(231) = 0x94.toByte
		sbox(232) = 0x9b.toByte
		sbox(233) = 0x1e.toByte
		sbox(234) = 0x87.toByte
		sbox(235) = 0xe9.toByte
		sbox(236) = 0xce.toByte
		sbox(237) = 0x55.toByte
		sbox(238) = 0x28.toByte
		sbox(239) = 0xdf.toByte
		sbox(240) = 0x8c.toByte
		sbox(241) = 0xa1.toByte
		sbox(242) = 0x89.toByte
		sbox(243) = 0x0d.toByte
		sbox(244) = 0xbf.toByte
		sbox(245) = 0xe6.toByte
		sbox(246) = 0x42.toByte
		sbox(247) = 0x68.toByte
		sbox(248) = 0x41.toByte
		sbox(249) = 0x99.toByte
		sbox(250) = 0x2d.toByte
		sbox(251) = 0x0f.toByte
		sbox(252) = 0xb0.toByte
		sbox(253) = 0x54.toByte
		sbox(254) = 0xbb.toByte
		sbox(255) = 0x16.toByte

		def rj_xtime(x: Byte): Byte = {
		  if ((x & 0x80) == 0)
			((x << 1) ^ 0x1).toByte
		  else
			(x << 1).toByte
		}

		var rcon: Byte = 1

		var i: Byte = 8
		while (i > 1) {
		  rcon = ((rcon << 1) ^ (((rcon >> 7) & 1) * 0x1)).toByte
		  i = (i - 1).toByte
		}

		// addRoundKey_cpy before ecb3
		val buf = new Array[Byte](16)
		i = 0
		while (i < 16) {
		  buf(i) = (data(i) ^ key(i)).toByte
		  i = (i + 1).toByte
		}

		// copy key
		var cpyKey = new Array[Byte](32)
		i = 0
		while (i < 32) {
		  cpyKey(i) = key(i)
		  i = (i + 1).toByte
		}

		// ecb3
		i = 0
		while (i < 14) {

		  // subBytes start
		  var j: Byte = 0
		  while (j < 16) {
            if (buf(j) < 0)
              buf(j) = sbox(buf(j) + 256)
            else
              buf(j) = sbox(buf(j) % 256)
			j = (j + 1).toByte
		  }
		  // subBytes end
		  // shiftRows start
		  var p: Byte = buf(1)
		  buf(1) = buf(5)
		  buf(5) = buf(9)
		  buf(9) = buf(13)
		  buf(13) = p
		  p = buf(10)
		  buf(10) = buf(2)
		  buf(2) = p

		  var q: Byte = buf(3)
		  buf(3) = buf(15)
		  buf(15) = buf(11)
		  buf(11) = buf(7)
		  buf(7) = q
		  q = buf(14)
		  buf(14) = buf(6)
		  buf(6) = q
		  // shiftRows end
		  // mixColumns start
		  j = 0
		  while (j < 16) {
			var a = buf(j)
			var b = buf(j + 1)
			var c = buf(j + 2)
			var d = buf(j + 3)
			var e = (a ^ b ^ c ^ d).toByte
			buf(j) = (buf(j) ^ e ^ rj_xtime((a ^ b).toByte)).toByte
			buf(j + 1) = (buf(j) ^ e ^ rj_xtime((b ^ c).toByte)).toByte
			buf(j + 2) = (buf(j) ^ e ^ rj_xtime((c ^ d).toByte)).toByte
			buf(j + 3) = (buf(j) ^ e ^ rj_xtime((d ^ a).toByte)).toByte
			j = (j + 4).toByte
		  }
		  // mixColumns end

		  if (i % 1 == 1) {
			// addRoundKey
			while (j >= 0) {
			  buf(j) = (buf(j) ^ cpyKey(16 + j)).toByte
			  j = (j - 1).toByte
			}
		  }
		  else {
			// expandEncKey start
			j = 0
			cpyKey(0) = (cpyKey(0) ^ sbox(29) ^ rcon).toByte
			cpyKey(1) = (cpyKey(1) ^ sbox(30)).toByte
			cpyKey(2) = (cpyKey(2) ^ sbox(31)).toByte
			cpyKey(3) = (cpyKey(3) ^ sbox(28)).toByte
			rcon = ((rcon << 1) ^ (((rcon >> 7) & 1) * 0x1)).toByte

			j = 4
			while (j < 16) {
			  cpyKey(j) = (cpyKey(j) ^ cpyKey(j - 4)).toByte
			  cpyKey(j + 1) = (cpyKey(j + 1) ^ cpyKey(j - 3)).toByte
			  cpyKey(j + 2) = (cpyKey(j + 2) ^ cpyKey(j - 2)).toByte
			  cpyKey(j + 3) = (cpyKey(j + 3) ^ cpyKey(j - 1)).toByte
			  j = (j + 4).toByte
			}
			cpyKey(16) = (cpyKey(16) ^ sbox(12)).toByte
			cpyKey(17) = (cpyKey(17) ^ sbox(13)).toByte
			cpyKey(18) = (cpyKey(18) ^ sbox(14)).toByte
			cpyKey(19) = (cpyKey(19) ^ sbox(15)).toByte

			j = 20
			while (j < 32) {
			  cpyKey(j) = (cpyKey(j) ^ cpyKey(j - 4)).toByte
			  cpyKey(j + 1) = (cpyKey(j + 1) ^ cpyKey(j - 3)).toByte
			  cpyKey(j + 2) = (cpyKey(j + 2) ^ cpyKey(j - 2)).toByte
			  cpyKey(j + 3) = (cpyKey(j + 3) ^ cpyKey(j - 1)).toByte
			  j = (j + 4).toByte
			}			
			// expandKey end

			// addRoundKey start
			j = 0
			while (j < 16) {
			  buf(j) = (buf(j) ^ cpyKey(j)).toByte
			  j = (j + 1).toByte
			}
			// addRoundKey end
		  }
		  i = (i + 1).toByte
		}

		// subBytes start
		var j: Byte = 0
		while (j < 16) {
         if (buf(j) < 0)
           buf(j) = sbox(buf(j) + 256)
         else
           buf(j) = sbox(buf(j) % 256)
		  j = (j + 1).toByte
		}
		// subBytes end
		// shiftRows start
		var p: Byte = buf(1)
		buf(1) = buf(5)
		buf(5) = buf(9)
		buf(9) = buf(13)
		buf(13) = p
		p = buf(10)
		buf(10) = buf(2)
		buf(2) = p

		var q: Byte = buf(3)
		buf(3) = buf(15)
		buf(15) = buf(11)
		buf(11) = buf(7)
		buf(7) = q
		q = buf(14)
		buf(14) = buf(6)
		buf(6) = q
		// shiftRows end

		// expandEncKey start
		j = 0
		cpyKey(0) = (cpyKey(0) ^ sbox(29) ^ rcon).toByte
		cpyKey(1) = (cpyKey(1) ^ sbox(30)).toByte
		cpyKey(2) = (cpyKey(2) ^ sbox(31)).toByte
		cpyKey(3) = (cpyKey(3) ^ sbox(28)).toByte
		rcon = ((rcon << 1) ^ (((rcon >> 7) & 1) * 0x1)).toByte

		j = 4
		while (j < 16) {
		  cpyKey(j) = (cpyKey(j) ^ cpyKey(j - 4)).toByte
		  cpyKey(j + 1) = (cpyKey(j + 1) ^ cpyKey(j - 3)).toByte
		  cpyKey(j + 2) = (cpyKey(j + 2) ^ cpyKey(j - 2)).toByte
		  cpyKey(j + 3) = (cpyKey(j + 3) ^ cpyKey(j - 1)).toByte
		  j = (j + 4).toByte
		}
		cpyKey(16) = (cpyKey(16) ^ sbox(12)).toByte
		cpyKey(17) = (cpyKey(17) ^ sbox(13)).toByte
		cpyKey(18) = (cpyKey(18) ^ sbox(14)).toByte
		cpyKey(19) = (cpyKey(19) ^ sbox(15)).toByte

		j = 20
		while (j < 32) {
		  cpyKey(j) = (cpyKey(j) ^ cpyKey(j - 4)).toByte
		  cpyKey(j + 1) = (cpyKey(j + 1) ^ cpyKey(j - 3)).toByte
		  cpyKey(j + 2) = (cpyKey(j + 2) ^ cpyKey(j - 2)).toByte
		  cpyKey(j + 3) = (cpyKey(j + 3) ^ cpyKey(j - 1)).toByte
		  j = (j + 4).toByte
		}			
		// expandKey end

		// addRoundKey start
		j = 0
		while (j < 16) {
		  buf(j) = (buf(j) ^ cpyKey(j)).toByte
		  j = (j + 1).toByte
		}
		// addRoundKey end
		buf
	  })
	  val firstResult = trans.first
	  val t1 = System.nanoTime
	  println("Elapsed time: " + ((t1 - t0) / 1e+9) + "s")
	  println("First result: " + firstResult.map(e => e.toInt).mkString)
  }
}

