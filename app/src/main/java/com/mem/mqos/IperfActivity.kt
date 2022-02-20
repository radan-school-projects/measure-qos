package com.mem.mqos

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.mem.mqos.databinding.ActivityIperfBinding
import com.mem.mqos.databinding.IperfSettingsDialogBinding
import com.mem.mqos.databinding.ResultRowGenericBinding
import com.mem.mqos.utils.Utils
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.*

class IperfActivity : DrawerBaseActivity() {
  private lateinit var activityIperfBinding: ActivityIperfBinding
  private lateinit var iperfSettingsDialogBinding: IperfSettingsDialogBinding

  private val iperfName = "iperf39"
  private var iperfPath: String = ""

  private var myHandler = MyHandler(WeakReference(this), Looper.getMainLooper())

  private var mThread: Thread? = null
  private var isThreadRunning = false
  private var errorMessage = ""

  private val queue: Queue<String> = ArrayDeque()
  //private val queue = ArrayDeque<Any>()

  companion object {
    private const val IPERF = 102
    private const val STOP = 101
    private const val START = 100
  }

  enum class MODES {
    CLIENT, SERVER
  }

  object Params {
    var mode: MODES = MODES.CLIENT
    var isUdp = false
    var server = "192.168.43.231"
    var interval = 1.0
    var time = 5
  }

  private var params = Params

  private fun syncServerText() {
    activityIperfBinding.btnConfig.text = params.server
  }

  private fun updateText() {
    if (queue.size == 0) return

    val str = queue.remove()
    //Log.i("queue remove", str)

    val resultRowBinding = ResultRowGenericBinding.inflate(layoutInflater)
    resultRowBinding.tvRandom.text = str

    val resultRowView = resultRowBinding.root
    activityIperfBinding.tableOutput.addView(resultRowView)

    activityIperfBinding.scrollView.post {
      activityIperfBinding.scrollView.fullScroll(View.FOCUS_DOWN)
    }
  }

  private fun toggleIperf(on: Boolean) {
    if (errorMessage != "") {
      Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
      Log.i("errorMessage", errorMessage)
    }

    if (on) {
      //activityIperfBinding.btnStart.text = resources.getString(R.string.btn_stop)
      activityIperfBinding.btnStart.text = getString(R.string.stop_btn_text)
    } else {
      mThread = null
      //activityIperfBinding.btnStart.text = resources.getString(R.string.btn_start)
      activityIperfBinding.btnStart.text = getString(R.string.run_btn_text)
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
      val cmd = mutableListOf(iperfPath)
      //val cmd = mutableListOf(iperfPath, "-c", "192.168.43.231", "-t", "4")
      //val cmd = mutableListOf(iperfPath, "-s")

      when (params.mode) {
        MODES.CLIENT -> {
          val udpOption = if (params.isUdp) "-u" else ""
          cmd.addAll(arrayOf(
            "-c", params.server, udpOption,
            "-t", params.time.toString(),
            "-i", params.interval.toString()
          ))
        }
        MODES.SERVER -> cmd.addAll(arrayOf("-s", "-1"))
      }

      val cmd1 = cmd.toMutableList()
      cmd1[0] = "iperf3"
      queue.add(cmd1.joinToString(" "))
      val myMessage = Message()
      myMessage.what = IPERF
      myHandler.sendMessage(myMessage)

      cmd.add("--forceflush")

      val builder = ProcessBuilder()
      builder.command(cmd)

      val process = builder.start()

      val stdInput = process.inputStream.bufferedReader()

      val messageStart = Message()
      messageStart.what = START
      myHandler.sendMessage(messageStart)

      while (isThreadRunning) {
        val currentStr = stdInput.readLine() ?: break //break // wrap into try catch block later

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

    syncServerText()
    setupIperf()

    activityIperfBinding.btnStart.setOnClickListener {
      triggerToggleIperf()
    }

    activityIperfBinding.btnConfig.setOnClickListener {
      showSettingsPopup()
    }

    activityIperfBinding.btnClearOutput.setOnClickListener {
      activityIperfBinding.tableOutput.removeAllViews()
    }
  }

  private fun showSettingsPopup() {
    val builder = AlertDialog.Builder(this)

    iperfSettingsDialogBinding = IperfSettingsDialogBinding.inflate(layoutInflater)
    val dialogView = iperfSettingsDialogBinding.root

    iperfSettingsDialogBinding.iperfCible.setText(params.server)
    iperfSettingsDialogBinding.checkBoxUdp.isChecked = params.isUdp
    iperfSettingsDialogBinding.iperfInterval.setText(params.interval.toString())
    iperfSettingsDialogBinding.iperfTime.setText(params.time.toString())

    when (params.mode) {
      MODES.SERVER -> {
        iperfSettingsDialogBinding.iperfModeServerRadio.isChecked = true
        iperfSettingsDialogBinding.checkBoxUdp.isChecked = false
        iperfSettingsDialogBinding.checkBoxUdp.isEnabled = false
        iperfSettingsDialogBinding.iperfTime.isEnabled = false
        iperfSettingsDialogBinding.iperfInterval.isEnabled = false

        //params.server = Utils.getIPAddress(true)
        iperfSettingsDialogBinding.iperfCible.setText(Utils.getIPAddress(true))

        iperfSettingsDialogBinding.iperfCible.isEnabled = false
      }
      MODES.CLIENT -> {
        iperfSettingsDialogBinding.iperfModeClientRadio.isChecked = true
        iperfSettingsDialogBinding.checkBoxUdp.isEnabled = true
        iperfSettingsDialogBinding.iperfCible.isEnabled = true
      }
    }

    iperfSettingsDialogBinding.iperfModeRadioGroup.setOnCheckedChangeListener {
      _, checkedId ->
        when (checkedId) {
          R.id.iperfModeServerRadio -> {
            //params.mode = MODES.SERVER
            iperfSettingsDialogBinding.checkBoxUdp.isChecked = false
            iperfSettingsDialogBinding.checkBoxUdp.isEnabled = false
            iperfSettingsDialogBinding.iperfTime.isEnabled = false
            iperfSettingsDialogBinding.iperfInterval.isEnabled = false

            //params.server = Utils.getIPAddress(true)
            iperfSettingsDialogBinding.iperfCible.setText(Utils.getIPAddress(true))

            iperfSettingsDialogBinding.iperfCible.isEnabled = false
          }
          R.id.iperfModeClientRadio -> {
            //params.mode = MODES.CLIENT
            iperfSettingsDialogBinding.checkBoxUdp.isEnabled = true
            //iperfSettingsDialogBinding.iperfCible.setText("8.8.8.8")
            iperfSettingsDialogBinding.iperfCible.isEnabled = true
            iperfSettingsDialogBinding.iperfInterval.isEnabled = true
            iperfSettingsDialogBinding.iperfTime.isEnabled = true
          }
        }
    }

    builder.setView(dialogView)
    builder.setPositiveButton("OK") {
        _, _ ->
      params.mode = when (iperfSettingsDialogBinding.iperfModeRadioGroup.checkedRadioButtonId) {
        R.id.iperfModeClientRadio -> MODES.CLIENT
        R.id.iperfModeServerRadio -> MODES.SERVER
        else -> MODES.CLIENT
      }
      params.isUdp = iperfSettingsDialogBinding.checkBoxUdp.isChecked
      params.server = iperfSettingsDialogBinding.iperfCible.text.toString()
      params.interval = iperfSettingsDialogBinding.iperfInterval.text.toString().toDouble()
      params.time = iperfSettingsDialogBinding.iperfTime.text.toString().toInt()
      syncServerText()
    }

    builder.show()
  }
}