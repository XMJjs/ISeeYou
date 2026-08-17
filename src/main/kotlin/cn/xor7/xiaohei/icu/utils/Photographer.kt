package cn.xor7.xiaohei.icu.utils

import cn.xor7.xiaohei.icu.plugin
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.leavesmc.leaves.entity.photographer.Photographer
import org.leavesmc.leaves.replay.BukkitRecorderOption
import java.util.UUID
import java.util.UUID.randomUUID
import kotlin.collections.set

val photographers: MutableSet<UUID> = java.util.concurrent.ConcurrentHashMap.newKeySet()
val player2PhotographerMap = java.util.concurrent.ConcurrentHashMap<UUID, Photographer>()
val allPhotographers: Collection<Photographer>
    get() = Bukkit.getPhotographerManager().photographers

fun getPhotographer(id: String): Photographer? = Bukkit.getPhotographerManager().getPhotographer(id)

fun getPhotographer(uuid: UUID): Photographer? = Bukkit.getPhotographerManager().getPhotographer(uuid)

fun createPhotographer(
    id: String,
    location: Location,
    recorderOption: BukkitRecorderOption = BukkitRecorderOption()
): Photographer? = Bukkit.getPhotographerManager().createPhotographer(id, location, recorderOption).also {
    it?.let { photographers.add(it.uniqueId) }
}

fun Photographer.removePhotographer(save: Boolean = true): Boolean {
    return try {
        this.stopRecording(true, save)
        photographers.remove(this.uniqueId)
        player2PhotographerMap.entries.removeIf { it.value.uniqueId == this.uniqueId }
        true
    } catch (e: Exception) {
        plugin.logger.log(
            java.util.logging.Level.SEVERE,
            "Error while removing photographer ${this.name}; keeping it tracked for retry",
            e,
        )
        false
    }
}

fun removeAllPhotographers() = photographers.toSet().forEach {
    val photographer = getPhotographer(it)
    photographer?.removePhotographer(false)
}

fun Photographer.setFollow(player: Player) {
    player2PhotographerMap[player.uniqueId] = this
    this.setFollowPlayer(player)
}

fun Player.getRecordPhotographer(): Photographer? {
    return player2PhotographerMap[this.uniqueId]
}

fun Player.getPhotographerName(type: String = ""): String {
    val trimmedPlayerName = this.name
        .replace(".", "_")
        .replace("-", "_")
    val leftPart = (module.photographerPrefix + trimmedPlayerName).run {
        if (length > 6) substring(0, 6) else this
    }
    val rightPart = type + randomUUID().toString().replace("-", "")
    return "${leftPart}_${rightPart}".run {
        if (length > 16) substring(0, 16) else this
    }
}