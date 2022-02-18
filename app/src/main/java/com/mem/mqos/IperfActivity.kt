package com.mem.mqos

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.widget.Toast
import com.mem.mqos.databinding.ActivityIperfBinding
import com.mem.mqos.databinding.ResultRowGenericBinding
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.*

class IperfActivity : DrawerBaseActivity() {
  private lateinit var activityIperfBinding: ActivityIperfBinding

  private val iperfName = "iperf39"
  private var iperfPath: String = ""

  private var myHandler = MyHandler(WeakReference(this), Looper.getMainLooper())

  private var mThread: Thread? = null
  private var isThreadRunning = false
  private var errorMessage = ""

  private val queue: Queue<String> = ArrayDeque()

  companion object {
    private const val IPERF = 102
    private const val STOP = 101
    private const val START = 100
  }

  private fun updateText() {
    if (queue.size == 0) return

    val str = queue.remove()
    //Log.i("queue remove", str)

    val resultRowBinding = ResultRowGenericBinding.inflate(layoutInflater)
    resultRowBinding.tvRandom.text = str

    val resultRowView = resultRowBinding.root
    activityIperfBinding.tableOutput.addView(resultRowView)
  }

  private fun toggleIperf(on: Boolean) {
    if (errorMessage != "") {
      Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
      Log.i("errorMessage", errorMessage)
    }

    if (on) {
      //activityIperfBinding.btnStart.text = resources.getString(R.string.btn_stop)
      activityIperfBinding.btnStart.text = "STOP"
    } else {
      mThread = null
      //activityIperfBinding.btnStart.text = resources.getString(R.string.btn_start)
      activityIperfBinding.btnStart.text = "START"
    }

    errorMessage = ""
    activityIperfBinding.btnStart.isClickable = true
  }
  
  private class MyHandler(private val outerClass: WeakReference<IperfActivity>, looper: Looper):
    Handler(looper) {
    override fun handleMessage(msg: Message) {
      super.handleMessage(msg)
      when (msg.what) {
        IPERF -> outerClass.get()?.updateText()
        STOP -> outerClass.get()?.toggleIperf(false)
        START -> outerClass.get()?.toggleIperf(true)
      }
    }
  }

  internal inner class IperfProcess: Runnable {
    override fun run() {
      val cmd = mutableListOf(iperfPath, "-c", "192.168.43.231", "-t", "4")
      //val cmd = mutableListOf(iperfPath, "-s")
      //val cmd = mutableListOf("ping", "-c", "5", "192.168.43.231")

      cmd.add("--forceflush")

      val builder = ProcessBuilder()
      builder.command(cmd)

      //var process: Proces
      //Thread({
      //  val process = builder.start()
      //})
      val process = builder.start()
      Log.i("STATUS", "process started!!!")

      val stdInput = process.inputStream.bufferedReader()
      //val stdInput = BufferedReader(InputStreamReader(process.inputStream))

      val messageStart = Message()
      messageStart.what = START
      myHandler.sendMessage(messageStart)
      //Log.i("STATUS", "START MESSAGE SENT")
      //Log.i("isThreadRunning", "$isThreadRunning")

      while (isThreadRunning) {
        Log.i("Loop", "one iteration")
        //val stdInput = process.inputStream.bufferedReader()
        val currentStr = stdInput.readLine() ?: break //break // wrap into try catch block later

        //queue.add(currentStr)
        //Log.i("currentStr", currentStr)
        queue.add(currentStr)

        val messageIperf = Message()
        messageIperf.what = IPERF
        myHandler.sendMessage(messageIperf)
      }
      if (isThreadRunning) {
        errorMessage = process.errorStream.bufferedReader().readLine() ?: ""
      }

      val messageStop = Message()
      messageStop.what = STOP
      myHandler.sendMessage(messageStop)

      process.destroy()
    }
  }

  private fun triggerToggleIperf() {
    activityIperfBinding.btnStart.isClickable = false

    val doEnable = mThread == null
    isThreadRunning = doEnable

    if (doEnable) {
      mThread = Thread(IperfProcess())
      mThread?.start()
    }
  }

  private fun setupIperf() {
    iperfPath = "${filesDir.path}/$iperfName"

    if (!File(iperfPath).exists()) {
      // copy iperf bin (IperfIn) to IperfOut
      val iperfFile = assets.open(iperfName)
      val iperfDestination = FileOutputStream(iperfPath)
      iperfFile.copyTo(iperfDestination)

      // change permissions of iperf bin file
      Runtime.getRuntime().exec("chmod 755 $iperfPath")
      // change permissions on files '/data/data/com.mem.mqos/files directory
      // because iperf is gonna need to write some stuffs there when it works
      Runtime.getRuntime().exec("chmod 777 ${filesDir.path}")
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    activityIperfBinding = ActivityIperfBinding.inflate(layoutInflater)
    setContentView(activityIperfBinding.root)
    allocateActivityTitle("${getString(R.string.app_name)}: iperf")

    setupIperf()

    activityIperfBinding.btnStart.setOnClickListener {
      triggerToggleIperf()
      //execCommand()
    }
  }
}