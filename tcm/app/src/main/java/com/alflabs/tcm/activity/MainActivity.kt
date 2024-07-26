package com.alflabs.tcm.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.alflabs.tcm.R
import com.alflabs.tcm.app.AppPrefsValues
import com.alflabs.tcm.record.GrabberThread
import com.alflabs.tcm.util.ILogger
import org.bytedeco.javacv.FFmpegLogCallback


class MainActivity : AppCompatActivity() {

    private var grabberThread: GrabberThread? = null
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


        val startBtn = findViewById<Button>(R.id.start_btn)
        val stopBtn = findViewById<Button>(R.id.stop_btn)
        val prefsBtn = findViewById<ImageButton>(R.id.prefs_btn)
        statusTxt = findViewById(R.id.status_text)

        startBtn.setOnClickListener { onStartButton() }
        stopBtn.setOnClickListener { onStopButton() }
        prefsBtn.setOnClickListener { onPrefsButton() }

        addStatus("\n@@ ABIs: ${android.os.Build.SUPPORTED_ABIS.toList()}")

        try {
            FFmpegLogCallback.setLevel(AV_LOG_TRACE)    // Warning: very verbose in logcat
            FFmpegLogCallback.set()
        } catch (t: Throwable) {
            addStatus("ERROR: $t")
        }
    }

    private fun onPrefsButton() {
        val i = Intent(this, PrefsActivity::class.java)
        startActivity(i)
    }

    private fun onStartButton() {
        Log.d(TAG, "@@ on START button")
        addStatus("## Start")

        try {
            val prefs = AppPrefsValues(this)
            val url = prefs.camerasUrl1()
            grabberThread = GrabberThread(getLogger(), url)
        } catch (t: Throwable) {
            addStatus("ERROR: $t")
        }

        grabberThread?.start()
    }

    private fun onStopButton() {
        Log.d(TAG, "@@ on STOP button")
        addStatus("## Stop")
        grabberThread?.stop()
        grabberThread = null
    }

    private fun addStatus(s : String) {
        Log.d(TAG, "Status: $s")
        statusTxt.text = statusTxt.text.toString() + s + "\n"
    }

    private fun getLogger() : ILogger {
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
