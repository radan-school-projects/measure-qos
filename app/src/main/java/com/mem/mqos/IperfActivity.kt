package com.mem.mqos

import android.os.Bundle
import com.mem.mqos.databinding.ActivityIperfBinding

class IperfActivity : DrawerBaseActivity() {
  private lateinit var activityIperfBinding: ActivityIperfBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    activityIperfBinding = ActivityIperfBinding.inflate(layoutInflater)
    setContentView(activityIperfBinding.root)
    this.allocateActivityTitle("${getString(R.string.app_name)}: iperf")
  }
}