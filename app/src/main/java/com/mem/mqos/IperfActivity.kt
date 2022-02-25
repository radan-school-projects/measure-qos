package com.mem.mqos

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.mem.mqos.databinding.*
import com.mem.mqos.db.AppDatabase
import com.mem.mqos.db.IperfCommandEntity
import com.mem.mqos.utils.Utils
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

class IperfActivity : DrawerBaseActivity() {
  //private lateinit var db: AppDatabase

  private lateinit var activityIperfBinding: ActivityIperfBinding
  private lateinit var iperfSettingsDialogBinding: IperfSettingsDialogBinding

  var iperfProcess: Process? = null

  private val iperfName = "iperf39"
  private var iperfPath: String = ""

  private var myHandler = MyHandler(WeakReference(this), Looper.getMainLooper())

  private var mThread: Thread? = null
  private var isThreadRunning = false
  private var errorMessage = ""

  //private val queue: Queue<String> = ArrayDeque()
  private val queue = ArrayDeque<Any>()

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

    //val str = queue.remove()
    val element = queue.remove()
    //Log.i("queue remove", str)

    if (element is IperfTableRowClientTcpSeq) {
      val  binding = IperfRowClientTcpSeqBinding.inflate(layoutInflater)
      binding.tvTransfer.text = element.transfer
      binding.tvBitrate.text = element.bitrate
      binding.tvRetr.text = element.retr
      binding.tvCwnd.text = element.cwnd
      activityIperfBinding.tableOutput.addView(binding.root)
    }
    if (element is IperfTableRowClientTcpFin) {
      val binding = IperfRowClientTcpFinBinding.inflate(layoutInflater)
      binding.tvTransfer.text = element.transfer
      binding.tvBitrate.text = element.bitrate
      binding.tvRetr.text = element.retr
      activityIperfBinding.tableOutput.addView(binding.root)
    }
    if (element is IperfTableRowServerTcp) {
      val binding = IperfRowServerTcpBinding.inflate(layoutInflater)
      binding.tvTransfer.text = element.transfer
      binding.tvBitrate.text = element.bitrate
      activityIperfBinding.tableOutput.addView(binding.root)
    }
    if (element is IperfTableRowClientUdpSeq) {
      val binding = IperfRowClientUdpSeqBinding.inflate(layoutInflater)
      binding.tvTransfer.text = element.transfer
      binding.tvBitrate.text = element.bitrate
      binding.tvDatagramsTotal.text = element.datagramsTotal
      activityIperfBinding.tableOutput.addView(binding.root)
    }
    if (element is IperfTableRowClientUdpFin) {
      val binding = IperfRowClientUdpFinBinding.inflate(layoutInflater)
      binding.tvTransfer.text = element.transfer
      binding.tvBitrate.text = element.bitrate
      binding.tvJitter.text = element.jitter
      //val str = "${element.datagramsLost}/${element.datagramsTotal} (${element.lossRate}%)"
      binding.tvDatagrams.text = element.datagramsText
      activityIperfBinding.tableOutput.addView(binding.root)
    }
    if (element is IperfTableRowServerUdp) {
      val binding = IperfRowServerUdpBinding.inflate(layoutInflater)
      binding.tvTransfer.text = element.transfer
      binding.tvBitrate.text = element.bitrate
      binding.tvJitter.text = element.jitter
      binding.tvDatagrams.text = "${element.datagramsLost}/${element.datagramsTotal} (${element.lossRate}%)"
      activityIperfBinding.tableOutput.addView(binding.root)
    }
    if (element is String) {
      val binding = ResultRowGenericBinding.inflate(layoutInflater)
      binding.tvRandom.text = element
      activityIperfBinding.tableOutput.addView(binding.root)
    }

    //val resultRowBinding = ResultRowGenericBinding.inflate(layoutInflater)
    //resultRowBinding.tvRandom.text = str

    //val resultRowView = resultRowBinding.root
    //activityIperfBinding.tableOutput.addView(resultRowView)

    activityIperfBinding.scrollView.post {
      activityIperfBinding.scrollView.fullScroll(View.FOCUS_DOWN)
    }
  }

  private fun toggleIperf(on: Boolean) {
    if (errorMessage != "") {
      Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
      //Log.i("errorMessage", errorMessage)
    }

    if (on) {
      //activityIperfBinding.btnStart.text = resources.getString(R.string.btn_stop)
      activityIperfBinding.btnStart.text = getString(R.string.stop_btn_text)
    } else {
      //Runtime.getRuntime().exec("kill -9 \$(pidof /data/user/0/com.mem.mqos/files/iperf39)")
      //mThread?.interrupt()
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

  val dotString = "- - - - - - - - - - - - - - - - - - - - - - - - -"
  private val regexesClientTcp =  arrayOf(
    "\\[  (?<a>[0-9]+)\\]   (?<b>[0-9.]+)-(?<c>[0-9.]+)   sec  (?<d>[0-9.]+) MBytes  (?<e>[0-9.]+) Mbits/sec    (?<f>[0-9]+)    (?<g>[0-9]+) KBytes",
    "\\[  (?<a>[0-9]+)\\]   (?<b>[0-9.]+)-(?<c>[0-9.]+)   sec  (?<d>[0-9.]+) MBytes  (?<e>[0-9.]+) Mbits/sec    (?<f>[0-9]+)             sender",
    dotString
  )
  private val regexesServerTcp = arrayOf(
    "\\[  (?<a>[0-9]+)\\]   (?<b>[0-9.]+)-(?<c>[0-9.]+)   sec  (?<d>[0-9.]+) MBytes  (?<e>[0-9.]+) Mbits/sec",
    "\\[  (?<a>[0-9]+)\\]   (?<b>[0-9.]+)-(?<c>[0-9.]+)   sec  (?<d>[0-9.]+) MBytes  (?<e>[0-9.]+) Mbits/sec                  receiver"
  )
  private val regexesClientUdp =  arrayOf(//+++
    "\\[  (?<a>[0-9]+)\\]   (?<b>[0-9.]+)-(?<c>[0-9.]+)   sec   (?<d>[0-9]+) KBytes  (?<e>[0-9.]+) Mbits/sec  (?<f>[0-9.]+) ms  (?<g>[0-9]+)/(?<h>[0-9]+) \\((?<i>[0-9]+)%\\)  receiver",
    "\\[  (?<a>[0-9]+)\\]   (?<b>[0-9.]+)-(?<c>[0-9.]+)   sec   (?<d>[0-9]+) KBytes  (?<e>[0-9.]+) Mbits/sec  (?<f>[0-9.]+) ms  (?<g>[0-9]+)/(?<h>[0-9]+) \\((?<i>[0-9]+)%\\)  sender",
    "\\[  (?<a>[0-9]+)\\]   (?<b>[0-9.]+)-(?<c>[0-9.]+)   sec   (?<d>[0-9]+) KBytes  (?<e>[0-9.]+) Mbits/sec  (?<f>[0-9]+)",
    dotString
  )
  private val regexesServerUdp =  arrayOf(
    //"[  5]   0.00-5.00   sec   641 KBytes  1.05 Mbits/sec  0.244 ms  0/453 (0%)  receiver",
    "\\[  (?<a>[0-9]+)\\]   (?<b>[0-9.]+)-(?<c>[0-9.]+)   sec   (?<d>[0-9]+) KBytes  (?<e>[0-9.]+) Mbits/sec  (?<f>[0-9.]+) ms  (?<g>[0-9]+)/(?<h>[0-9]+) \\((?<i>[0-9]+)%\\)  receiver",
    "\\[  (?<a>[0-9]+)\\]   (?<b>[0-9.]+)-(?<c>[0-9.]+)   sec   (?<d>[0-9]+) KBytes  (?<e>[0-9.]+) Mbits/sec  (?<f>[0-9.]+) ms  (?<g>[0-9]+)/(?<h>[0-9]+) \\((?<i>[0-9]+)%\\)",
  )
  private val regexesServer = arrayOf(
    regexesServerTcp[0],
    regexesServerTcp[1],
    regexesServerUdp[1],
    regexesServerUdp[0],
    dotString
  )

  class IperfTableRowClientTcpSeq(var transfer: String, var bitrate: String, var retr: String, var cwnd: String)
  class IperfTableRowClientTcpFin(var transfer: String, var bitrate: String, var retr: String)
  private val thTcpClientSeq = IperfTableRowClientTcpSeq("Transfer", "Bitrate", "Retr", "Cwnd")
  private val thTcpClientFin = IperfTableRowClientTcpFin("Transfer", "Bitrate", "Retr")

  class IperfTableRowServerTcp(var transfer: String, var bitrate: String)
  private val thTcpServer = IperfTableRowServerTcp("Transfer", "Bitrate")

  class IperfTableRowClientUdpSeq(var transfer: String, var bitrate: String, var datagramsTotal: String)
  class IperfTableRowClientUdpFin(var transfer: String, var bitrate: String, var jitter: String, var datagramsLost: String, var datagramsTotal: String, var lossRate: String) {
    var datagramsText = ""

    init {
      this@IperfTableRowClientUdpFin.datagramsText = "${datagramsLost}/${datagramsTotal} (${lossRate}%)"
    }
  }
  private val thUdpClientSeq = IperfTableRowClientUdpSeq("Transfer", "Bitrate", "Total Datagrams")
  private val thUdpClientFin = IperfTableRowClientUdpFin("Transfer", "Bitrate", "Jitter", "replace this", "", "")
  // Lost/total datagrams

  class IperfTableRowServerUdp(var transfer: String, var bitrate: String, var jitter: String, var datagramsLost: String, var datagramsTotal: String, var lossRate: String)
  //class IperfTableRowServerUdpSeq(var transfer: String, var bitrate: String, var jitter: String, var datagramsLost: String, var datagramsTotal: String, var lossRate: String)
  //class IperfTableRowServerUdpFin()

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
        MODES.SERVER -> cmd.addAll(arrayOf("-s"))
      }

      val cmd1 = cmd.toMutableList()
      cmd1[0] = "iperf3"
      val cmdStr = cmd1.joinToString(" ")

      queue.add("-> $cmdStr")
      val myMessage = Message()
      myMessage.what = IPERF
      myHandler.sendMessage(myMessage)

      // put in a thread ?
      //db.iperfCommandDao().insertAll(IperfCommandEntity(cmdStr))

      val regexes = if (!params.isUdp)  {
        when (params.mode) {
          MODES.CLIENT -> regexesClientTcp
          MODES.SERVER -> regexesServer
          //MODES.SERVER -> regexesServerTcp
        }
      } else {
        when (params.mode) {
          MODES.CLIENT -> regexesClientUdp
          MODES.SERVER -> regexesServer
          //MODES.SERVER -> regexesServerUdp
        }
      }

      cmd.add("--forceflush")
      val builder = ProcessBuilder()
      builder.command(cmd)

      val process = builder.start()
      iperfProcess = process

      val stdInput = process.inputStream.bufferedReader()

      val messageStart = Message()
      messageStart.what = START
      myHandler.sendMessage(messageStart)

      var iteratorSeq = 0

      while (isThreadRunning) {
        //val currentStr = stdInput.readLine() ?: break // wrap into try catch block later
        val currentStr0 = try {
          stdInput.readLine()
        } catch (e: Throwable) {
          break
        } ?: break
        val currentStr = currentStr0.trim()
        //Log.i("Current Str", currentStr)

        var pattern = ""
        for (rgx in regexes) {
          if (rgx.toRegex().find(currentStr) !== null) {
            pattern = rgx
            break
          }
        }

        //Log.i("okay?", (regexes.contentEquals(regexesServerUdp)).toString())
        //Log.i("PATTERN", pattern)
        if (pattern == "") continue

        val matcher = parseIperfString(currentStr, pattern)
        matcher.find()

        //queue.add(currentStr)
        when (pattern) {
          dotString -> {
            queue.add("--------------------------------")
          }
          regexesClientTcp[0] -> {// Tcp client Seq
            iteratorSeq++
            if (iteratorSeq <= 1) {
              queue.add(thTcpClientSeq)
              val messageIperfAA0 = Message()
              messageIperfAA0.what = IPERF
              myHandler.sendMessage(messageIperfAA0)
            }

            //"\\[  (?<a>[0-9]+)\\]   (?<b>[0-9.]+)-(?<c>[0-9.]+)   sec  (?<d>[0-9.]+) MBytes  (?<e>[0-9.]+) Mbits/sec    (?<f>[0-9]+)    (?<g>[0-9]+) KBytes",
            val transfer = "" + matcher.group(4)
            val bitrate = "" + matcher.group(5)
            val retr = "" + matcher.group(6)
            val cwnd = "" + matcher.group(7)
            queue.add(IperfTableRowClientTcpSeq(transfer, bitrate, retr, cwnd))
          }
          regexesClientTcp[1] -> {// tcp client fin
            queue.add(thTcpClientFin)
            val messageIperfAA0 = Message()
            messageIperfAA0.what = IPERF
            myHandler.sendMessage(messageIperfAA0)

            val transfer = "" + matcher.group(4)
            val bitrate = "" + matcher.group(5)
            val retr = "" + matcher.group(6)
            queue.add(IperfTableRowClientTcpFin(transfer, bitrate, retr))
          }
          regexesServerTcp[0] -> {// tcp server seq
            iteratorSeq++
            if (iteratorSeq <= 1) {
              queue.add(thTcpServer)
              val messageIperfAA0 = Message()
              messageIperfAA0.what = IPERF
              myHandler.sendMessage(messageIperfAA0)
            }

            val transfer = "" + matcher.group(4)
            val bitrate = "" + matcher.group(5)
            queue.add(IperfTableRowServerTcp(transfer, bitrate))
          }
          regexesServerTcp[1] -> {// tcp server fin
            iteratorSeq = 0
            queue.add(thTcpServer)
            val messageIperfAA0 = Message()
            messageIperfAA0.what = IPERF
            myHandler.sendMessage(messageIperfAA0)

            val transfer = "" + matcher.group(4)
            val bitrate = "" + matcher.group(5)
            queue.add(IperfTableRowServerTcp(transfer, bitrate))
          }
          regexesClientUdp[2] -> {// udp client seq
            iteratorSeq++
            if (iteratorSeq <= 1) {
              queue.add(thUdpClientSeq)
              val messageIperfAA0 = Message()
              messageIperfAA0.what = IPERF
              myHandler.sendMessage(messageIperfAA0)
            }

            val transfer = "" + matcher.group(4)
            val bitrate = "" + matcher.group(5)
            val datagramsTotal = "" + matcher.group(6)
            queue.add(IperfTableRowClientUdpSeq(transfer, bitrate, datagramsTotal))
          }
          regexesClientUdp[1] -> {// udp client fin
            queue.add(thUdpClientFin)
            val messageIperfAA0 = Message()
            messageIperfAA0.what = IPERF
            myHandler.sendMessage(messageIperfAA0)

            val transfer = "" + matcher.group(4)
            val bitrate = "" + matcher.group(5)
            val jitter = "" + matcher.group(6)
            val datagramsLost = "" + matcher.group(7)
            val datagramsTotal = "" + matcher.group(8)
            val lossRate = "" + matcher.group(9)
            queue.add(IperfTableRowClientUdpFin(transfer, bitrate, jitter, datagramsLost, datagramsTotal, lossRate))
          }
          regexesClientUdp[0] -> { continue }
          regexesServerUdp[1] -> {
            val transfer = "" + matcher.group(4)
            val bitrate = "" + matcher.group(5)
            val jitter = "" + matcher.group(6)
            val datagramsLost = "" + matcher.group(7)
            val datagramsTotal = "" + matcher.group(8)
            val lossRate = "" + matcher.group(9)
            queue.add(IperfTableRowServerUdp(transfer, bitrate, jitter, datagramsLost, datagramsTotal, lossRate))
          }
          regexesServerUdp[0] -> {
            val transfer = "" + matcher.group(4)
            val bitrate = "" + matcher.group(5)
            val jitter = "" + matcher.group(6)
            val datagramsLost = "" + matcher.group(7)
            val datagramsTotal = "" + matcher.group(8)
            val lossRate = "" + matcher.group(9)
            queue.add(IperfTableRowServerUdp(transfer, bitrate, jitter, datagramsLost, datagramsTotal, lossRate))
          }
          //else -> { continue }
        }

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

  private fun parseIperfString(s: String, rgx: String): Matcher {
    val re = Pattern.compile(
      rgx,
      Pattern.CASE_INSENSITIVE.or(Pattern.DOTALL))
    return re.matcher(s)
  }

  private fun triggerToggleIperf() {
    activityIperfBinding.btnStart.isClickable = false

    val doEnable = mThread == null
    isThreadRunning = doEnable

    if (doEnable) {
      mThread = Thread(IperfProcess())
      mThread?.start()
    } else {
      //Runtime.getRuntime().exec("kill -9 \$(pidof /data/user/0/com.mem.mqos/files/iperf39)")

      //toggleIperf(false)
      //iperfProcess?.destroy()

      mThread = null
      activityIperfBinding.btnStart.text = getString(R.string.run_btn_text)
      activityIperfBinding.btnStart.isClickable = true
      iperfProcess?.destroy()
    }
    //***
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

    thUdpClientFin.datagramsText = "Lost/Total Datagrams"
    setupIperf()
    syncServerText()

    //db = AppDatabase(this)
    //populateTableView()

    activityIperfBinding.btnStart.setOnClickListener {
      triggerToggleIperf()
      //testIperfRegex()
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

  //private fun testIperfRegex() {
  //  //val currentStr = "[  5]   2.00-3.00   sec  6.52 MBytes  54.7 Mbits/sec    0    359 KBytes"
  //  //val strRegex = "\\[  (?<a>[0-9]+)\\]   (?<b>[0-9.]+)-(?<c>[0-9.]+)   sec  (?<d>[0-9.]+) MBytes  (?<e>[0-9.]+) Mbits/sec    (?<f>[0-9]+)    (?<g>[0-9]+) KBytes"
  //  //val currentStr = "[  5]   0.00-3.00   sec  20.1 MBytes  56.3 Mbits/sec    0             sender"
  //  //val strRegex = "\\[  (?<a>[0-9]+)\\]   (?<b>[0-9.]+)-(?<c>[0-9.]+)   sec  (?<d>[0-9.]+) MBytes  (?<e>[0-9.]+) Mbits/sec    (?<f>[0-9]+)             sender"
  //
  //  val currentStr = "[  5]   0.00-3.00   sec   385 KBytes  1.05 Mbits/sec  0.000 ms  0/272 (0%)  sender"
  //  val strRegex = "\\[  (?<a>[0-9]+)\\]   (?<b>[0-9.]+)-(?<c>[0-9.]+)   sec   (?<d>[0-9]+) KBytes  (?<e>[0-9.]+) Mbits/sec  (?<f>[0-9.]+) ms  (?<g>[0-9]+)/(?<h>[0-9]+) \\((?<i>[0-9]+)%\\)  sender"
  //
  //
  //  val resFind = strRegex.toRegex().find(currentStr)?.value
  //  Log.i("the", resFind ?: "null")
  //
  //  val re = Pattern.compile(
  //    strRegex,
  //    Pattern.CASE_INSENSITIVE.or(Pattern.DOTALL))
  //  val resMatch = re.matcher(currentStr)
  //  val a = resMatch.find()
  //  Log.i("What", a.toString())
  //}
}