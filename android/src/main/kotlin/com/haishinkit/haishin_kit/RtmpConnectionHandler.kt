package com.haishinkit.haishin_kit

import android.os.Handler
import android.os.Looper
import com.haishinkit.event.Event
import com.haishinkit.event.IEventListener
import com.haishinkit.rtmp.RtmpConnection
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class RtmpConnectionHandler(
    private val plugin: HaishinKitPlugin
) : MethodChannel.MethodCallHandler, IEventListener,
    EventChannel.StreamHandler {
    companion object {
        private const val TAG = "RtmpConnection"
    }

    var instance: RtmpConnection? = RtmpConnection()
        private set

    private var channel: EventChannel
    private var eventSink: EventChannel.EventSink? = null

    @Volatile
    private var shouldSendSpeedStatistics = false

    private var previousTotalBytesOut: Long = 0
    private var previousTotalBytesIn: Long = 0

    init {
        instance?.addEventListener(Event.RTMP_STATUS, this)
        instance?.addEventListener(Event.IO_ERROR, this)
        channel = EventChannel(
            plugin.flutterPluginBinding.binaryMessenger,
            "com.haishinkit.eventchannel/${hashCode()}"
        )
        channel.setStreamHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "$TAG#connect" -> {
                val command = call.argument<String>("command") ?: ""
                instance?.connect(command)
                startSendSpeedStatistics()
                result.success(null)
            }
            "$TAG#close" -> {
                instance?.close()
                stopSendSpeedStatistics()
                result.success(null)
            }
            "$TAG#dispose" -> {
                eventSink?.endOfStream()
                instance?.dispose()
                instance = null
                plugin.onDispose(hashCode())
                result.success(null)
            }
        }
    }

    override fun handleEvent(event: Event) {
        val map = HashMap<String, Any?>()
        map["type"] = event.type
        map["data"] = event.data
        plugin.uiThreadHandler.post {
            eventSink?.success(map)
        }
    }

    fun startSendSpeedStatistics() {
        shouldSendSpeedStatistics = true
        Thread {
            while (eventSink != null && shouldSendSpeedStatistics) {
                val map = HashMap<String, Any?>()
                val data = HashMap<String, Any?>()
                data["code"] = "SpeedStatistics"
                // get current speed by minus previous total bytes
                val totalBytesOut = this.instance?.totalBytesOut ?: 0L
                val totalBytesIn = this.instance?.totalBytesIn ?: 0L
                data["outSpeedInByte"] = totalBytesOut?.minus(previousTotalBytesOut)
                data["inSpeedInByte"] = totalBytesIn?.minus(previousTotalBytesIn)
                previousTotalBytesOut = totalBytesOut
                previousTotalBytesIn = totalBytesIn
                map["data"] = data
                plugin.uiThreadHandler.post {
                    eventSink?.success(map)
                }
                try {
                    Thread.sleep(1000) // send every 1 second
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }.start()
    }

    fun stopSendSpeedStatistics(){
        shouldSendSpeedStatistics = false
        // reset bytes statistics
        previousTotalBytesOut = 0
        previousTotalBytesIn = 0
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
    }

    override fun onCancel(arguments: Any?) {
    }
}
