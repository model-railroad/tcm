package com.alflabs.tcm

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.alflabs.tcm.record.GrabberThread
import com.alflabs.tcm.util.ILogger
import org.bytedeco.javacv.FFmpegLogCallback
import java.net.URL


class MainActivity : AppCompatActivity() {

    private lateinit var grabberThread: GrabberThread
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var statusTxt: TextView

    companion object {
        const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FFmpegLogCallback.set()

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

        val url : String = "rtsp://U:P@IP:554/stream2"
        grabberThread = GrabberThread(getLogger(), url)
    }

    fun onStartButton() {
        Log.d(TAG, "@@ on START button")
        addStatus("## Start")
        grabberThread.start()
    }

    fun onStopButton() {
        Log.d(TAG, "@@ on STOP button")
        addStatus("## Stop")
        grabberThread.stop()
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
