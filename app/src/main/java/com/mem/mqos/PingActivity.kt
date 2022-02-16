package com.mem.mqos

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.widget.Toast
import com.mem.mqos.databinding.ActivityPingBinding
import com.mem.mqos.databinding.PingSequenceRowBinding
import com.mem.mqos.databinding.ResultRowGenericBinding
import java.lang.ref.WeakReference
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

class PingActivity : DrawerBaseActivity() {
  private lateinit var activityPingBinding: ActivityPingBinding

  private val regexes = arrayOf(
    //"(?<size>[0-9]+) bytes from (?<ip>[0-9.]+): icmp_seq=(?<seq>[0-9]+) ttl=(?<ttl>[0-9]+)(?: time=(?<rtt>[0-9.]+) (?<rttmetric>\\w+))?",
    "(?<size>[0-9]+?) bytes from (?<ip>[0-9.]+?): icmp_seq=(?<seq>[0-9]+?) ttl=(?<ttl>[0-9]+) time=(?<rtt>[0-9.]+?) (?<rttmetric>\\w+)",
    "(?<trans>[0-9]+?) packets transmitted, (?<rec>[0-9]+?) received, (?<loss>[0-9]+?)% packet loss, time (?<time>[0-9]+?)ms",
    "rtt min/avg/max/mdev = (?<min>[0-9.]+?)/(?<avg>[0-9.]+?)/(?<max>[0-9.]+?)/(?<mdev>[0-9.]+?) ms"
  )
  private var myHandler = MyHandler(WeakReference(this), Looper.getMainLooper())

  private var mThread: Thread? = null
  private var isThreadRunning = false
  private var errorMessage = ""

  //class IMQElt(val str: String, val rgx: String)
  val queue = ArrayDeque<Any>()
  //val queue: Queue<String> = ArrayDeque()
  //val specialQueue: Queue<IMQElt> = ArrayDeque()

  companion object {
    private const val PING = 102
    private const val STOP = 101
    private const val START = 100
  }

  private class MyHandler(private val outerClass: WeakReference<PingActivity>, looper: Looper):
    Handler(looper) {
    override fun handleMessage(msg: Message) {
      super.handleMessage(msg)
      when (msg.what) {
        PING -> outerClass.get()?.updateText()
        STOP -> outerClass.get()?.togglePing(false)
        START -> outerClass.get()?.togglePing(true)
      }
    }
  }

  private fun updateText() {
    if (queue.size == 0) return
    //if (specialQueue.size == 0) return

    val element = queue.remove()

    //var view: View

    if (element is String) {
      val resultRowBinding = ResultRowGenericBinding.inflate(layoutInflater)
      resultRowBinding.tvRandom.text = element
      val view = resultRowBinding.root
      activityPingBinding.tableOutput.addView(view)
    }
    if (element is PingSequenceRow) {
      val pingSequenceRowBinding = PingSequenceRowBinding.inflate(layoutInflater)
      pingSequenceRowBinding.tvSeqN.text = element.seq
      pingSequenceRowBinding.tvSize.text = element.size
      pingSequenceRowBinding.tvTtl.text = element.ttl
      pingSequenceRowBinding.tvRtt.text = element.rtt
      val view = pingSequenceRowBinding.root
      activityPingBinding.tableOutput.addView(view)
    }

    activityPingBinding.scrollView.post {
      activityPingBinding.scrollView.fullScroll(View.FOCUS_DOWN)
    }

    //resultRowBinding.rowText.text = processedStr

    //val resultRowView = resultRowBinding.root
    //activityPingBinding.tableOutput.addView(view)
  }

  private fun togglePing(on: Boolean) {
    if (errorMessage != "") {
      Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
      //Log.i("errorMessage", errorMessage)
    }

    if (on) {
      //activityPingBinding.btnStart.text = resources.getString(R.string.btn_stop)
      activityPingBinding.btnStart.text = "STOP"

    } else {
      mThread = null
      //activityPingBinding.btnStart.text = resources.getString(R.string.btn_start)
      activityPingBinding.btnStart.text = "RUN"
    }

    errorMessage = ""
    activityPingBinding.btnStart.isClickable = true
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    activityPingBinding = ActivityPingBinding.inflate(layoutInflater)
    setContentView(activityPingBinding.root)
    this.allocateActivityTitle("${getString(R.string.app_name)}: ping")

    activityPingBinding.btnStart.setOnClickListener {
      //Log.i("PING BUTTON", "PING CLICKED!")
      triggerTogglePing()
      //testRegex()
    }
  }

  private fun triggerTogglePing() {
    activityPingBinding.btnStart.isClickable = false

    val doEnable = mThread == null
    isThreadRunning = doEnable

    if (doEnable) {
      mThread = Thread(PingProcess())
      mThread?.start()
    }
  }

  private fun parsePingString(s: String, rgx: String): Matcher {
    val re = Pattern.compile(
      rgx,
      Pattern.CASE_INSENSITIVE.or(Pattern.DOTALL))
    return re.matcher(s)
  }

  class PingSequenceRow(var seq: String, var size: String, var ttl: String, var rtt: String)
  //open class PingSequenceRow(open var seq: String, open var size: String, open var ttl: String, open var rtt: String)
  //class PingTableHeader(var seq: String = "Seq #", var size: String = "Size", var ttl: String = "TTL", var rtt: String = "RTT"): PingSequenceRow()
  //class PingTableHeader(override var seq: String = "Seq #", override var size: String = "Size", override var ttl: String = "TTL", override var rtt: String = "RTT"):
  //  PingSequenceRow(seq, size, ttl, rtt)

  internal inner class PingProcess: Runnable {
    override fun run() {
      val cmd = mutableListOf("ping", "-c", "5", "8.8.8.8")

      val cmdStr = "-> ${cmd.joinToString(" ")}"

      // ==
      //queue.add(cmdStr)
      //val toAddToQueue = mutableListOf(cmdStr, PingTableHeader())
      val pingTableHeader = PingSequenceRow("Seq #", "Size", "TTL", "RTT")
      val toAddToQueue = mutableListOf(cmdStr, pingTableHeader)
      queue.addAll(toAddToQueue)

      while (queue.size > 0) {
        val messagePing = Message()
        messagePing.what = PING
        myHandler.sendMessage(messagePing)
      }

      val builder = ProcessBuilder()
      builder.command(cmd)

      val process = builder.start()
      val stdInput = process.inputStream.bufferedReader()

      val messageStart = Message()
      messageStart.what = START
      myHandler.sendMessage(messageStart)

      while (isThreadRunning) {
        val currentStr = stdInput.readLine() ?: break // wrap into try catch block later
        //Log.i("currentStr", currentStr)

        // === newly added code ===
        //var isStrOk = false
        var pattern = ""
        for (rgx in regexes) {
          if (rgx.toRegex().find(currentStr) !== null) {
            //isStrOk = true
            pattern = rgx
            break
          }
        }
        //if (!isStrOk && pattern !== "") continue
        if (pattern == "") continue

        //further processing
        //var processedStr = ""
        val matcher = parsePingString(currentStr, pattern)
        matcher.find()//necessary

        when (pattern) {
          regexes[0] -> {
            //"(?<size>[0-9]+?) bytes from (?<ip>[0-9.]+?): icmp_seq=(?<seq>[0-9]+?) ttl=(?<ttl>[0-9]+) time=(?<rtt>[0-9.]+?) (?<rttmetric>\\w+)",
            val seq = matcher.group(3)
            val size = matcher.group(1)
            val ttl = matcher.group(4)
            val rtt = "${matcher.group(5)}ms"
            val element = PingSequenceRow(seq, size, ttl, rtt)
            //processedStr = "ping n $seq rtt: $rtt ms"
            queue.add(element);
          }
          regexes[1] -> {
            val loss = matcher.group(3)
            val processedStr = "taux de perte: $loss%"
            queue.add(processedStr)
          }
          regexes[2] -> {
            val avg = matcher.group(2)
            val processedStr = "rtt moyenne: ${avg}ms"
            queue.add(processedStr)
          }
          else -> {} // nothing
        }
        // === newly added code ===

        //queue.add(processedStr)
        //queue.add(currentStr)
        //specialQueue.add(IMQElt(currentStr, pattern))

        val messagePing = Message()
        messagePing.what = PING
        myHandler.sendMessage(messagePing)
      }
      if (isThreadRunning) {
        errorMessage =
//           try {
          process.errorStream.bufferedReader().readLine() ?: ""//"no error"
//         }
//         catch (e: Throwable) {
//           e.message.toString()
////           e.toString()
////           "somthung wrong"
//         }
//           ?: "___________"
      }

      val messageStop = Message()
      messageStop.what = STOP
      myHandler.sendMessage(messageStop)

      process.destroy()

      // ===
      queue.add("------------------------------")
      val messagePing = Message()
      messagePing.what = PING
      myHandler.sendMessage(messagePing)
    }
  }
}