package com.androidtvopen.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLanSender {

    fun normalizeMac(input: String): String? {
        val hexOnly = input.uppercase().replace(Regex("[^0-9A-F]"), "")
        if (hexOnly.length != 12) return null
        return hexOnly.chunked(2).joinToString(":")
    }

    fun parseMacBytes(macAddress: String): ByteArray {
        val normalized = normalizeMac(macAddress)
            ?: throw IllegalArgumentException("Geçersiz MAC adresi formatı: $macAddress")
        val parts = normalized.split(":")
        return ByteArray(6) { i ->
            parts[i].toInt(16).toByte()
        }
    }

    fun buildMagicPacket(macBytes: ByteArray): ByteArray {
        val packet = ByteArray(6 + 16 * macBytes.size)
        for (i in 0 until 6) {
            packet[i] = 0xFF.toByte()
        }
        for (i in 0 until 16) {
            System.arraycopy(macBytes, 0, packet, 6 + i * macBytes.size, macBytes.size)
        }
        return packet
    }

    suspend fun sendWakeOnLan(
        macAddress: String,
        ipAddress: String = "",
        broadcastIp: String = "255.255.255.255",
        port: Int = 9
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val normalizedMac = normalizeMac(macAddress)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Geçerli bir 12 haneli MAC adresi girin (Örn: E0:51:D8:1C:50:DB)")
                )

            val macBytes = parseMacBytes(normalizedMac)
            val magicBytes = buildMagicPacket(macBytes)

            val targetIps = linkedSetOf<String>()
            if (broadcastIp.isNotBlank()) {
                targetIps.add(broadcastIp.trim())
            }
            if (ipAddress.isNotBlank()) {
                val trimmedIp = ipAddress.trim()
                val parts = trimmedIp.split(".")
                if (parts.size == 4) {
                    targetIps.add("${parts[0]}.${parts[1]}.${parts[2]}.255")
                    targetIps.add(trimmedIp)
                }
            }
            targetIps.add("255.255.255.255")

            val targetPorts = linkedSetOf(port, 9, 7).filter { it in 1..65535 }

            var sentCount = 0
            DatagramSocket().use { socket ->
                socket.broadcast = true
                repeat(3) { burst ->
                    for (targetIp in targetIps) {
                        try {
                            val address = InetAddress.getByName(targetIp)
                            for (p in targetPorts) {
                                val packet = DatagramPacket(magicBytes, magicBytes.size, address, p)
                                socket.send(packet)
                                sentCount++
                            }
                        } catch (_: Exception) {
                        }
                    }
                    if (burst < 2) {
                        delay(60)
                    }
                }
            }

            if (sentCount == 0) {
                Result.failure(Exception("Magic Packet gönderilemedi. Ağ bağlantısını kontrol edin."))
            } else {
                Result.success("Magic Packet gönderildi ($normalizedMac -> ${targetIps.first()}:$port)")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
