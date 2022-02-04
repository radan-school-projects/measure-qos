package com.mem.mqos

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class DrawerBaseActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_drawer_base)
  }
}