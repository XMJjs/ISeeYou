package cn.xor7.xiaohei.icu.listeners

import cn.xor7.xiaohei.icu.plugin
import cn.xor7.xiaohei.icu.utils.createPhotographer
import cn.xor7.xiaohei.icu.utils.createRecordFile
import cn.xor7.xiaohei.icu.utils.module
import cn.xor7.xiaohei.icu.utils.removePhotographer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.leavesmc.leaves.entity.photographer.Photographer
import java.util.*
import java.util.UUID.randomUUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean

lateinit var instantReplayListener: InstantReplayListener

class InstantReplayListener : Listener {
    private val instantReplayPhotographers =
        ConcurrentHashMap<UUID, ConcurrentLinkedDeque<InstantReplaySegment>>()
    private val discardedSegments =
        ConcurrentHashMap<UUID, InstantReplaySegment>()
    private val creatingPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    init {
        instantReplayListener = this
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(
            plugin,
            {
                retryDiscardedSegments()
                if (!module.instantReplay.enable) return@runAtFixedRate
                Bukkit.getOnlinePlayers()
                    .filter(Player::isOnline)
                    .forEach { player ->
                        player.scheduler.run(
                            plugin,
                            { triggerOneInstantReplayPhotographer(player) },
                            {},
                        )
                    }
            },
            1, module.instantReplay.intervalTicks,
        )
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (!module.instantReplay.enable) return
        val player = event.player
        if (!instantReplayPhotographers[player.uniqueId].isNullOrEmpty()) return
        triggerOneInstantReplayPhotographer(player)
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val playerId = event.player.uniqueId
        instantReplayPhotographers.remove(playerId)?.forEach { segment ->
            stopDiscardedSegment(segment)
        }
    }

    fun triggerInstantReplaySave(player: Player, completed: (Int) -> Unit) {
        if (!module.instantReplay.enable) {
            completed(0)
            return
        }

        val segments = instantReplayPhotographers[player.uniqueId]
        val segment = segments?.pollFirst()
        if (segment == null) {
            completed(0)
            return
        }

        if (!segment.stopping.compareAndSet(false, true)) {
            segments.addFirst(segment)
            completed(0)
            return
        }

        segment.photographer.scheduler.run(
            plugin,
            {
                if (segment.photographer.removePhotographer()) {
                    val minutes =
                        ((System.currentTimeMillis() - segment.startedAtMillis) / 60_000L)
                            .coerceAtLeast(1L)
                            .coerceAtMost(module.instantReplay.length.toLong())
                            .toInt()
                    completed(minutes)
                } else {
                    segment.stopping.set(false)
                    requeueSegment(player.uniqueId, segment)
                    completed(0)
                }
            },
            {
                segment.stopping.set(false)
                requeueSegment(player.uniqueId, segment)
                completed(0)
            },
        )
    }

    private fun triggerOneInstantReplayPhotographer(player: Player) {
        if (!creatingPlayers.add(player.uniqueId)) return
        var photographer: Photographer? = null
        try {
            val createdPhotographer = createOneInstantReplayPhotographer(player)
            photographer = createdPhotographer
            val recordFile = createRecordFile(module.instantReplay.path, player)

            createdPhotographer.setRecordFile(recordFile)
            createdPhotographer.setFollowPlayer(player)
            val segment = InstantReplaySegment(
                photographer = createdPhotographer,
                startedAtMillis = System.currentTimeMillis(),
            )
            instantReplayPhotographers
                .computeIfAbsent(player.uniqueId) { ConcurrentLinkedDeque() }
                .addLast(segment)

            createdPhotographer.scheduler.runDelayed(
                plugin,
                {
                    val segments = instantReplayPhotographers[player.uniqueId]
                    if (
                        segments?.remove(segment) == true &&
                        segment.stopping.compareAndSet(false, true)
                    ) {
                        if (!createdPhotographer.removePhotographer(false)) {
                            segment.stopping.set(false)
                            discardedSegments[createdPhotographer.uniqueId] = segment
                        } else if (segments.isEmpty()) {
                            instantReplayPhotographers.remove(player.uniqueId, segments)
                        }
                    }
                },
                {},
                module.instantReplay.lengthTicks,
            )
        } catch (e: Exception) {
            photographer?.let {
                stopDiscardedSegment(
                    InstantReplaySegment(
                        photographer = it,
                        startedAtMillis = System.currentTimeMillis(),
                    ),
                )
            }
            throw e
        } finally {
            creatingPlayers.remove(player.uniqueId)
        }
    }

    private fun requeueSegment(playerId: UUID, segment: InstantReplaySegment) {
        val segments = instantReplayPhotographers[playerId]
        if (segments != null) {
            segments.addFirst(segment)
        } else {
            stopDiscardedSegment(segment)
        }
    }

    private fun stopDiscardedSegment(segment: InstantReplaySegment) {
        discardedSegments[segment.photographer.uniqueId] = segment
        if (!segment.stopping.compareAndSet(false, true)) return

        segment.photographer.scheduler.run(
            plugin,
            {
                if (segment.photographer.removePhotographer(false)) {
                    discardedSegments.remove(segment.photographer.uniqueId, segment)
                } else {
                    segment.stopping.set(false)
                }
            },
            { segment.stopping.set(false) },
        )
    }

    private fun retryDiscardedSegments() {
        discardedSegments.values.forEach { segment ->
            stopDiscardedSegment(segment)
        }
    }

    private fun createOneInstantReplayPhotographer(player: Player): Photographer =
        createPhotographer(
            randomUUID().toString().replace("-", "").substring(0, 16),
            player.location,
        ) ?: throw RuntimeException(
            "Error when create instant replay photographer for player: {name:${player.name},UUID:${player.uniqueId}}",
        )
}

data class InstantReplaySegment(
    val photographer: Photographer,
    val startedAtMillis: Long,
    val stopping: AtomicBoolean = AtomicBoolean(false),
)