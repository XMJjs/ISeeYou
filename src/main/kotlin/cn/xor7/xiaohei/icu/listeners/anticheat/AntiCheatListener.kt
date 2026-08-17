package cn.xor7.xiaohei.icu.listeners.anticheat

import cn.xor7.xiaohei.icu.plugin
import cn.xor7.xiaohei.icu.utils.createPhotographer
import cn.xor7.xiaohei.icu.utils.createRecordFile
import cn.xor7.xiaohei.icu.utils.getPhotographerName
import cn.xor7.xiaohei.icu.utils.module
import cn.xor7.xiaohei.icu.utils.removePhotographer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.leavesmc.leaves.entity.photographer.Photographer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

lateinit var antiCheatListener: AntiCheatListener

class AntiCheatListener : Listener {
    private val suspiciousPhotographers = ConcurrentHashMap<UUID, SuspiciousPhotographer>()

    init {
        antiCheatListener = this
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(
            plugin,
            {
                if (!module.recordSuspicious.enable) return@runAtFixedRate
                val current = System.currentTimeMillis()
                val recordSuspiciousMills = module.recordSuspicious.lengthMillis
                suspiciousPhotographers.entries.forEach { entry ->
                    val suspicious = entry.value
                    if (
                        current - suspicious.lastTagged.get() > recordSuspiciousMills &&
                        suspicious.stopping.compareAndSet(false, true)
                    ) {
                        suspicious.photographer.scheduler.run(
                            plugin,
                            {
                                if (suspicious.photographer.removePhotographer()) {
                                    suspiciousPhotographers.remove(entry.key, suspicious)
                                } else {
                                    suspicious.stopping.set(false)
                                }
                            },
                            { suspicious.stopping.set(false) },
                        )
                    }
                }
            },
            1, 20 * 60,
        )
    }

    fun onAntiCheatAction(player: Player) = player.scheduler.run(plugin, {
        val suspiciousPhotographer = suspiciousPhotographers[player.uniqueId]
        if (suspiciousPhotographer == null) {
            val photographer = createAntiCheatPhotographer(player)
            val recordFile = createRecordFile(module.recordSuspicious.path, player)

            photographer.setRecordFile(recordFile)
            photographer.setFollowPlayer(player)
            suspiciousPhotographers[player.uniqueId] = SuspiciousPhotographer(
                photographer = photographer,
                name = player.name,
                lastTagged = AtomicLong(System.currentTimeMillis()),
            )
        } else if (!suspiciousPhotographer.stopping.get()) {
            suspiciousPhotographer.lastTagged.set(System.currentTimeMillis())
        }
    }, {})

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onPlayerQuit(e: PlayerQuitEvent) {
        val playerId = e.player.uniqueId
        val suspicious = suspiciousPhotographers[playerId] ?: return
        if (!suspicious.stopping.compareAndSet(false, true)) return

        suspicious.photographer.scheduler.run(
            plugin,
            {
                if (suspicious.photographer.removePhotographer()) {
                    suspiciousPhotographers.remove(playerId, suspicious)
                } else {
                    suspicious.stopping.set(false)
                }
            },
            { suspicious.stopping.set(false) },
        )
    }

    private fun createAntiCheatPhotographer(player: Player): Photographer =
        createPhotographer(
            player.getPhotographerName("sus_"),
            player.location,
        ) ?: throw RuntimeException(
            "Error when create photographer for suspicious player: {name:${player.name},UUID:${player.uniqueId}}",
        )
}

data class SuspiciousPhotographer(
    val photographer: Photographer,
    val name: String,
    val lastTagged: AtomicLong,
    val stopping: AtomicBoolean = AtomicBoolean(false),
)