package cn.xor7.xiaohei.icu.utils

import cn.xor7.xiaohei.icu.listeners.InstantReplayListener
import cn.xor7.xiaohei.icu.listeners.SimpleRecordListener
import cn.xor7.xiaohei.icu.listeners.anticheat.AntiCheatListener
import cn.xor7.xiaohei.icu.listeners.anticheat.GrimACListener
import cn.xor7.xiaohei.icu.listeners.anticheat.LightAntiCheatListener
import cn.xor7.xiaohei.icu.listeners.anticheat.MatrixListener
import cn.xor7.xiaohei.icu.listeners.anticheat.NegativityListener
import cn.xor7.xiaohei.icu.listeners.anticheat.SpartanListener
import cn.xor7.xiaohei.icu.listeners.anticheat.ThemisListener
import cn.xor7.xiaohei.icu.listeners.anticheat.VulcanListener
import cn.xor7.xiaohei.icu.plugin
import org.bukkit.event.Listener
import kotlin.reflect.KClass

private val listeners = mutableMapOf<KClass<out Listener>, Any>()

@Suppress("UNCHECKED_CAST")
fun <T : Listener> registerListener(listener: T) {
    val type = listener::class as KClass<T>
    listeners[type] = listener
    plugin.server.pluginManager.registerEvents(listener, plugin)
}

fun <T : Listener> registerListenerIfAbsent(type: KClass<T>, factory: () -> T) {
    if (listeners.containsKey(type)) return
    registerListener(factory())
}

fun registerOrUpdateListeners() {
    registerListenerIfAbsent(SimpleRecordListener::class, ::SimpleRecordListener)

    if (module.instantReplay.enable) {
        registerListenerIfAbsent(InstantReplayListener::class, ::InstantReplayListener)
    }

    module.recordSuspicious.apply {
        if (enable) registerListenerIfAbsent(AntiCheatListener::class, ::AntiCheatListener)
        else return

        if (grim) registerListenerIfAbsent(GrimACListener::class, ::GrimACListener)
        if (lightAntiCheat) registerListenerIfAbsent(LightAntiCheatListener::class, ::LightAntiCheatListener)
        if (matrix) registerListenerIfAbsent(MatrixListener::class, ::MatrixListener)
        if (negativity) registerListenerIfAbsent(NegativityListener::class, ::NegativityListener)
        if (spartan) registerListenerIfAbsent(SpartanListener::class, ::SpartanListener)
        if (themis) registerListenerIfAbsent(ThemisListener::class, ::ThemisListener)
        if (vulcan) registerListenerIfAbsent(VulcanListener::class, ::VulcanListener)
    }
}