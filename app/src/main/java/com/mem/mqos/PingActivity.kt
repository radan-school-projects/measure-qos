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
import com.mem.mqos.db.*
import java.lang.ref.WeakReference
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

class PingActivity : DrawerBaseActivity() {
  //===Database
  private lateinit var db: AppDatabase //**
  //val db = Room.databaseBuilder(
  //  applicationContext,
  //  AppDatabase::class.java, "todo-list.db"
  //).build()
  //===

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

  class PingSequenceRow(var seq: String, var size: String, var ttl: String, var rtt: String)
  private val pingTableHeader = PingSequenceRow("Seq #", "Size", "TTL", "RTT")

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

  internal inner class PingProcess: Runnable {
    override fun run() {
      val cmd = mutableListOf("ping", "-c", "5", "8.8.8.8")

      val cmdStrA = cmd.joinToString(" ")
      val cmdStrB = "-> $cmdStrA"

      ////var latestCommandRan: CommandEntity? = null
      //var latestCommandRanEntity = CommandEntity(value = cmdStrA)
      //Thread {
      //  //db.pingCommandDao().insertAll(CommandEntity(value = cmdStrA))
      //  db.pingCommandDao().insertAll(latestCommandRanEntity)
      //  // ???
      //  latestCommandRanEntity = db.pingCommandDao().getWithLatestId()[0] // ??
      //}.start()
      ////db.pingCommandDao().insertAll(CommandEntity(value = cmdStrA))

      db.pingCommandDao().insertAll(CommandEntity(cmdStrA))
      val latestCommandRanEntity = db.pingCommandDao().getWithLatestId()[0] // ??
      db.pingFinalResultDao().insertAll(PingFinalResultEntity(latestCommandRanEntity.id, null, null)) //***

      // ==
      //queue.add(cmdStr)
      //val toAddToQueue = mutableListOf(cmdStr, PingTableHeader())
      //val pingTableHeader = PingSequenceRow("Seq #", "Size", "TTL", "RTT")
      val toAddToQueue = mutableListOf(cmdStrB, pingTableHeader)
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
            val seq = "" + matcher.group(3)
            val size = "" + matcher.group(1)
            val ttl = "" + matcher.group(4)
            val rtt = "${matcher.group(5)}ms"
            val element = PingSequenceRow(seq, size, ttl, rtt)
            queue.add(element)

            //db.pingSequenceResultDao().insertAll(PingSequenceResultEntity(seq, size, ttl, rtt, latestCommandRanEntity.id)) //***
            Thread {
              db.pingSequenceResultDao().insertAll(PingSequenceResultEntity(seq, size, ttl, rtt, latestCommandRanEntity.id)) //***
            }.start()
          }
          regexes[1] -> {
            val loss = matcher.group(3)
            val processedStr = "taux de perte: $loss%"
            queue.add(processedStr)

            //db.pingFinalResultDao().updateLossRateWhereCommandId(latestCommandRanEntity.id, loss) //***
            Thread {
              db.pingFinalResultDao().updateLossRateWhereCommandId(latestCommandRanEntity.id, loss) //***
            }.start()
          }
          regexes[2] -> {
            val avg = matcher.group(2)
            val processedStr = "rtt moyenne: ${avg}ms"
            queue.add(processedStr)

            //db.pingFinalResultDao().updateAverageRttWhereCommandId(latestCommandRanEntity.id, avg) //***
            Thread {
              db.pingFinalResultDao().updateAverageRttWhereCommandId(latestCommandRanEntity.id, avg) //***
            }.start()
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

  private fun triggerTogglePing() {
    activityPingBinding.btnStart.isClickable = false

    val doEnable = mThread == null
    isThreadRunning = doEnable

    if (doEnable) {
      mThread = Thread(PingProcess())
      mThread?.start()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    activityPingBinding = ActivityPingBinding.inflate(layoutInflater)
    setContentView(activityPingBinding.root)
    allocateActivityTitle("${getString(R.string.app_name)}: ping")

    db = AppDatabase(this)
    //populateTableView()
    populateTableView2()

    activityPingBinding.btnStart.setOnClickListener {
      //Log.i("PING BUTTON", "PING CLICKED!")
      triggerTogglePing()
      //testRegex()
    }

    activityPingBinding.btnClearOutput.setOnClickListener {
      //clearTableView()
      Thread {
        db.pingCommandDao().deleteAll()
        db.pingSequenceResultDao().deleteAll()
        db.pingFinalResultDao().deleteAll()
      }.start()
      activityPingBinding.tableOutput.removeAllViews()
    }
  }

  private fun parsePingString(s: String, rgx: String): Matcher {
    val re = Pattern.compile(
      rgx,
      Pattern.CASE_INSENSITIVE.or(Pattern.DOTALL))
    return re.matcher(s)
  }

  //private fun clearTableView() {
  //  activityPingBinding.tableOutput.removeAllViews()
  //}

  private fun populateTableView2() {
    Thread {
      val allJoins = db.pingJoinDao().joinAllPing()

      //val pingTableHeader = PingSequenceRow("Seq #", "Size", "TTL", "RTT")

      var lastCmdId = 0

      for (index in allJoins.indices) {
        val element = allJoins[index]

        if (element.id != lastCmdId) {
          lastCmdId = element.id

          val arr0 = mutableListOf("-> ${element.value}", pingTableHeader)
          queue.addAll(arr0)

          for (i in arr0) {
            val messagePing0 = Message()
            messagePing0.what = PING
            myHandler.sendMessage(messagePing0)
          }
        }

        queue.add(PingSequenceRow(element.seq, element.size, element.ttl, element.rtt))
        val messagePing1 = Message()
        messagePing1.what = PING
        myHandler.sendMessage(messagePing1)

        if ((index >= (allJoins.size - 1)) || (allJoins[index + 1].id != element.id)) {
          val arr = arrayOf(
            "taux de perte: ${element.loss_rate}%",
            "rtt moyenne: ${element.average_rtt}ms",
            "------------------------------"
          )
          queue.addAll(arr)
          for (i in arr) {
            val messagePing2 = Message()
            messagePing2.what = PING
            myHandler.sendMessage(messagePing2)
          }
        }
      }

      //while (queue.size > 0) {
      //  val messagePing2 = Message()
      //  messagePing2.what = PING
      //  myHandler.sendMessage(messagePing2)
      //}
    }.start()
  }

  private fun populateTableView() {
    // db read all commands and sor them by ascending order: from the smaller to greatest id
    Thread {
      val commandsRan = db.pingCommandDao().getAll()
      //Log.i("COMMANDS RAN", JS)
      //if (commandsRan.isEmpty()) return@Thread

      //val pingTableHeader = PingSequenceRow("Seq #", "Size", "TTL", "RTT")

      //val messagePing = Message()
      //messagePing.what = PING

      for (command in commandsRan) {
        val arr0 = arrayOf("-> ${command.value}", pingTableHeader)
        queue.addAll(arr0)

        for (i in arr0) {
          val messagePing = Message()
          messagePing.what = PING
          myHandler.sendMessage(messagePing)
        }

        val sequenceRows = db.pingSequenceResultDao().getAllWithCommandId(command.id) //***

        //***
        for (row in sequenceRows) {
          val rowObj = PingSequenceRow(row.seq, row.size, row.ttl, row.rtt)
          queue.add(rowObj)

          val messagePing2 = Message()
          messagePing2.what = PING
          myHandler.sendMessage(messagePing2)
        }

        val finalRes = db.pingFinalResultDao().getAllWithCommandId(command.id)
        val arr = arrayOf(
          "taux de perte: ${finalRes[0].lossRate}%",
          "rtt moyenne: ${finalRes[0].averageRtt}ms",
          "------------------------------"
        )
        queue.addAll(arr)

        for (i in arr) {
          val messagePing3 = Message()
          messagePing3.what = PING
          myHandler.sendMessage(messagePing3)
        }
      }
    }.start()
  }
}