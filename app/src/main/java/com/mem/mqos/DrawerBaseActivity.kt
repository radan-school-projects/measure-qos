package com.mem.mqos

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import com.google.android.material.navigation.NavigationView

open class DrawerBaseActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
  private lateinit var drawerLayout: DrawerLayout

  override fun setContentView(view: View?) {
    @SuppressLint("InflateParams")
    drawerLayout = layoutInflater.inflate(R.layout.activity_drawer_base, null) as DrawerLayout
    val container = drawerLayout.findViewById<FrameLayout>(R.id.activityContainer)
    container.addView(view)

    super.setContentView(drawerLayout)

    val toolbar = drawerLayout.findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)

    val navigationView = drawerLayout.findViewById<NavigationView>(R.id.nav_view)
    navigationView.setNavigationItemSelectedListener(this)

    val toggle = ActionBarDrawerToggle(this, drawerLayout as DrawerLayout?, toolbar, R.string.menu_drawer_open, R.string.menu_drawer_close)
    drawerLayout.addDrawerListener(toggle)
    toggle.syncState()
  }

  override fun onNavigationItemSelected(item: MenuItem): Boolean {
    drawerLayout.closeDrawer(GravityCompat.START)
    when (item.itemId) {
      R.id.iperf_menu_link -> {
        startActivity(Intent(this, IperfActivity::class.java))
        overridePendingTransition(0, 0)
      }
      R.id.ping_menu_link -> {
        startActivity(Intent(this, PingActivity::class.java))
        overridePendingTransition(0, 0)
      }
    }
    return false
  }

  protected fun allocateActivityTitle(title: String) {
    supportActionBar?.title = title
  }
}