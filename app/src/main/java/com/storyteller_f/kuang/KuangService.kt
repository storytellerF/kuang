package com.storyteller_f.kuang

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dalvik.system.DexClassLoader
import io.ktor.server.engine.*
import io.ktor.server.netty.*

class KuangService : Service() {
    private var binder: Kuang? = null
    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "onBind() called with: intent = $intent")
        return Kuang().also {
            binder = it
        }
    }

    override fun onCreate() {
        super.onCreate()
        val s = "foreground"
        val channel = NotificationChannelCompat.Builder(s, NotificationManagerCompat.IMPORTANCE_MIN).apply {
            setName("running")
            setSound(null, null)
        }.build()
        val from = NotificationManagerCompat.from(this)
        if (from.getNotificationChannel(s) == null)
            from.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, s).apply {
            setSound(null)
        }.build()
        startForeground(foreground_notification_id, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        binder?.stop()
    }

    class Kuang : Binder() {
        private var server: ApplicationEngine? = null
        fun start(context: Context) {
            try {

                this.server = embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
                    val listFiles = context.filesDir.listFiles { _, name ->
                        name.endsWith(".jar")
                    }
                    val classLoader = javaClass.classLoader
                    println("当前classLoader $classLoader")
                    listFiles?.forEach {
                        val dexClassLoader = DexClassLoader(it.absolutePath, null, null, classLoader)
                        val className = dexClassLoader.getResourceAsStream("kcon")?.bufferedReader()?.readText() ?: return@forEach
                        println(className)
                        val serverClass = dexClassLoader.loadClass(className)
                        val declaredField = serverClass.getField("application")
                        val newInstance = serverClass.getConstructor().newInstance()
                        println("外部jar classLoader ${serverClass.classLoader}")
                        declaredField.set(newInstance, this)
                        try {
                            serverClass.getMethod("start").apply {
                                invoke(newInstance)
                            }
                        } catch (e: Exception) {
                            serverClass.getMethod("start", ClassLoader::class.java).apply {
                                invoke(newInstance, dexClassLoader)
                            }
                        }
                    }

                }.start(wait = false)
            } catch (th: Throwable) {
                Log.e(TAG, "start: ${th.localizedMessage}", th)
            }

        }

        fun stop() {
            server?.stop()
        }

        fun restart(context: Context) {
            stop()
            start(context)
            Toast.makeText(context, "restarted", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "KuangService"
        private const val foreground_notification_id = 10
    }
}