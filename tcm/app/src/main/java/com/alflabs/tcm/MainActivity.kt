package com.alflabs.tcm

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.alflabs.tcm.record.GrabberThread
import com.alflabs.tcm.util.ILogger
import org.bytedeco.javacv.FFmpegLogCallback


class MainActivity : AppCompatActivity() {

    private var grabberThread: GrabberThread? = null
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var statusTxt: TextView

    companion object {
        const val TAG = "MainActivity"

        // From https://www.ffmpeg.org/doxygen/4.0/group__lavu__log__constants.html
        const val AV_LOG_TRACE = 56
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }


        startBtn = findViewById(R.id.start_btn)
        stopBtn = findViewById(R.id.stop_btn)
        statusTxt = findViewById(R.id.status_text)

        startBtn.setOnClickListener { onStartButton() }
        stopBtn.setOnClickListener { onStopButton() }

        addStatus("\n@@ ABIs: ${android.os.Build.SUPPORTED_ABIS.toList()}")

        try {
            FFmpegLogCallback.setLevel(AV_LOG_TRACE)    // Warning: very verbose in logcat
            FFmpegLogCallback.set()
        } catch (t: Throwable) {
            addStatus("ERROR: $t")
        }
    }

    fun onStartButton() {
        Log.d(TAG, "@@ on START button")
        addStatus("## Start")

        try {
            val U = ""
            val P = ""
            val url = "rtsp://$U:$P@192.168.3.128:554/stream2"
            grabberThread = GrabberThread(getLogger(), url)
        } catch (t: Throwable) {
            addStatus("ERROR: $t")
        }

        grabberThread?.start()
    }

    fun onStopButton() {
        Log.d(TAG, "@@ on STOP button")
        addStatus("## Stop")
        grabberThread?.stop()
        grabberThread = null
    }

    fun addStatus(s : String) {
        Log.d(TAG, "Status: $s")
        statusTxt.text = statusTxt.text.toString() + s + "\n"
    }

    fun getLogger() : ILogger {
        return object : ILogger {
            override fun log(msg: String?) {
                statusTxt.post {
                    addStatus("$msg\n")
                }

            }

            override fun log(tag: String?, msg: String?) {
                statusTxt.post {
                    addStatus("$tag : $msg\n")
                }
            }
        }
    }

}
