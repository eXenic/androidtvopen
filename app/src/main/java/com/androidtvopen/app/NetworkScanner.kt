package com.androidtvopen.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class LocalNetworkInfo(
    val localIp: String,
    val subnetPrefix: String,
    val broadcastIp: String,
    val interfaceName: String
)

object NetworkScanner {

    fun getLocalNetworkInfo(): LocalNetworkInfo? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val sorted = interfaces.sortedBy { iface ->
                val name = iface.name.lowercase()
                when {
                    name.startsWith("eth") -> 0
                    name.startsWith("wlan") -> 1
                    name.startsWith("ap") || name.startsWith("rndis") -> 2
                    else -> 3
                }
            }

            for (iface in sorted) {
                if (!iface.isUp || iface.isLoopback) continue
                for (interfaceAddress in iface.interfaceAddresses) {
                    val addr = interfaceAddress.address
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("169.254.")) continue
                        val parts = ip.split(".")
                        if (parts.size == 4) {
                            val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
                            val bcast = interfaceAddress.broadcast?.hostAddress ?: "$prefix.255"
                            return LocalNetworkInfo(
                                localIp = ip,
                                subnetPrefix = prefix,
                                broadcastIp = bcast,
                                interfaceName = iface.displayName ?: iface.name
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    suspend fun checkHostOnline(ipAddress: String, timeoutMs: Int = 650): Pair<Boolean, Long> =
        withContext(Dispatchers.IO) {
            if (ipAddress.isBlank()) return@withContext (false to -1L)
            val trimmed = ipAddress.trim()
            val start = System.currentTimeMillis()

            val tcpOnline = coroutineScope {
                val jobs = listOf(445, 135, 3389, 80, 22, 443, 139).map { port ->
                    async { isTcpHostAlive(trimmed, port, timeoutMs) }
                }
                jobs.awaitAll().any { it }
            }

            if (tcpOnline) {
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                return@withContext (true to elapsed)
            }

            try {
                val startReach = System.currentTimeMillis()
                val addr = InetAddress.getByName(trimmed)
                if (addr.isReachable(timeoutMs)) {
                    val elapsed = (System.currentTimeMillis() - startReach).coerceAtLeast(1L)
                    return@withContext (true to elapsed)
                }
            } catch (_: Exception) {
            }

            try {
                val startPing = System.currentTimeMillis()
                val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/ping", "-c", "1", "-W", "1", trimmed))
                val exitCode = proc.waitFor()
                if (exitCode == 0) {
                    val elapsed = (System.currentTimeMillis() - startPing).coerceAtLeast(1L)
                    return@withContext (true to elapsed)
                }
            } catch (_: Exception) {
            }

            false to -1L
        }

    suspend fun scanLocalNetwork(
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): List<DiscoveredPc> = withContext(Dispatchers.IO) {
        val info = getLocalNetworkInfo() ?: return@withContext emptyList()
        val prefix = info.subnetPrefix
        val semaphore = Semaphore(48)
        val completed = AtomicInteger(0)
        val onlineIps = ConcurrentHashMap.newKeySet<String>()

        coroutineScope {
            val probeJobs = (1..254).map { lastOctet ->
                async {
                    val ip = "$prefix.$lastOctet"
                    semaphore.withPermit {
                        if (ip != info.localIp) {
                            // Trigger ARP resolution via quick UDP + TCP probe
                            sendQuickUdpProbe(ip)
                            val alive = isTcpHostAlive(ip, 445, 220) ||
                                isTcpHostAlive(ip, 135, 220) ||
                                isTcpHostAlive(ip, 80, 220) ||
                                isTcpHostAlive(ip, 22, 220)
                            if (alive) {
                                onlineIps.add(ip)
                            }
                        }
                        val done = completed.incrementAndGet()
                        if (done % 16 == 0 || done == 254) {
                            onProgress?.invoke(done, 254)
                        }
                    }
                }
            }
            probeJobs.awaitAll()
        }

        val arpMap = readArpAndNeighborTable()
        val allIps = (arpMap.keys + onlineIps)
            .filter { it.startsWith("$prefix.") && it != info.localIp }
            .distinct()

        val results = coroutineScope {
            allIps.map { ip ->
                async {
                    val mac = arpMap[ip].orEmpty()
                    val isOnline = onlineIps.contains(ip) || (mac.isNotBlank() && checkHostOnline(ip, 300).first)
                    val host = resolveHostname(ip)
                    DiscoveredPc(
                        ip = ip,
                        mac = mac,
                        hostname = host,
                        isOnline = isOnline
                    )
                }
            }.awaitAll()
        }

        results
            .filter { it.mac.isNotBlank() || it.isOnline }
            .sortedWith(
                compareByDescending<DiscoveredPc> { it.mac.isNotBlank() }
                    .thenByDescending { it.isOnline }
                    .thenBy { ipSortKey(it.ip) }
            )
    }

    private fun ipSortKey(ip: String): Int {
        return ip.split(".").lastOrNull()?.toIntOrNull() ?: 999
    }

    private fun sendQuickUdpProbe(ip: String) {
        try {
            DatagramSocket().use { socket ->
                val data = byteArrayOf(0)
                val addr = InetAddress.getByName(ip)
                socket.send(DatagramPacket(data, data.size, addr, 9))
            }
        } catch (_: Exception) {
        }
    }

    private fun isTcpHostAlive(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
            }
            true
        } catch (e: ConnectException) {
            // Connection refused still means host is reachable on LAN!
            val msg = e.message?.lowercase().orEmpty()
            msg.contains("refused") || msg.contains("econnrefused")
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveHostname(ip: String): String {
        return try {
            val addr = InetAddress.getByName(ip)
            val name = addr.canonicalHostName
            if (name != null && name != ip) name else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun readArpAndNeighborTable(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        // 1. /proc/net/arp
        try {
            val file = File("/proc/net/arp")
            if (file.exists() && file.canRead()) {
                file.forEachLine { line ->
                    val cols = line.trim().split(Regex("\\s+"))
                    if (cols.size >= 4 && cols[0] != "IP") {
                        val ip = cols[0]
                        val mac = WakeOnLanSender.normalizeMac(cols[3])
                        if (mac != null && mac != "00:00:00:00:00:00") {
                            map[ip] = mac
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 2. ip neigh show fallback (works on newer Android where /proc/net/arp is restricted)
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("ip", "neigh", "show"))
            BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                reader.forEachLine { line ->
                    val tokens = line.trim().split(Regex("\\s+"))
                    val ip = tokens.firstOrNull() ?: return@forEachLine
                    val lladdrIdx = tokens.indexOf("lladdr")
                    if (lladdrIdx >= 0 && lladdrIdx + 1 < tokens.size) {
                        val mac = WakeOnLanSender.normalizeMac(tokens[lladdrIdx + 1])
                        if (mac != null && mac != "00:00:00:00:00:00") {
                            map[ip] = mac
                        }
                    }
                }
            }
            proc.waitFor()
        } catch (_: Exception) {
        }

        return map
    }
}
