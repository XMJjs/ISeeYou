package cn.xor7.xiaohei.icu.utils

import org.bukkit.entity.Player
import java.io.File
import java.util.UUID.randomUUID

fun createRecordFile(path: String, player: Player): File {
    val recordPath: String = path
        .replace($$"${name}", player.name)
        .replace($$"${uuid}", player.uniqueId.toString())

    val recordDir = File(recordPath)
    if (!recordDir.exists() && !recordDir.mkdirs()) throw RuntimeException("Error when create record directory: $recordPath")

    val suffix = randomUUID().toString().substring(0, 8)
    return recordDir.resolve("${getNowDateString()}-$suffix.mcpr")
}