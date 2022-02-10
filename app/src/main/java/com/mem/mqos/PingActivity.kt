package com.mem.mqos

import android.os.Bundle
import com.mem.mqos.databinding.ActivityPingBinding

class PingActivity : DrawerBaseActivity() {
  private lateinit var activityPingBinding: ActivityPingBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    activityPingBinding = ActivityPingBinding.inflate(layoutInflater)
    setContentView(activityPingBinding.root)
    this.allocateActivityTitle("${getString(R.string.app_name)}: ping")
  }
}