package com.mem.mqos.utils

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.lang.Exception
import java.lang.StringBuilder
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*
import kotlin.experimental.and


//import org.apache.http.conn.util.InetAddressUtils;

//import org.apache.http.conn.util.InetAddressUtils;
object Utils {
  /**
   * Returns MAC address of the given interface name.
   * @param interfaceName eth0, wlan0 or NULL=use first interface
   * @return  mac address or empty string
   */
  fun getMACAddress(interfaceName: String?): String {
    try {
      val interfaces: List<NetworkInterface> =
        Collections.list(NetworkInterface.getNetworkInterfaces())
      for (intf in interfaces) {
        if (interfaceName != null) {
          if (!intf.name.equals(interfaceName, ignoreCase = true)) continue
        }
        val mac = intf.hardwareAddress ?: return ""
        val buf = StringBuilder()
        for (aMac in mac) buf.append(String.format("%02X:", aMac))
        if (buf.isNotEmpty()) buf.deleteCharAt(buf.length - 1)
        return buf.toString()
      }
    } catch (ignored: Exception) {
    } // for now eat exceptions
    return ""
    /*try {
            // this is so Linux hack
            return loadFileAsString("/sys/class/net/" +interfaceName + "/address").uppercase().trim();
        } catch (IOException ex) {
            return null;
        }*/
  }

  /**
   * Get IP address from first non-localhost interface
   * @param useIPv4   true=return ipv4, false=return ipv6
   * @return  address or empty string
   */
  fun getIPAddress(useIPv4: Boolean): String {
    try {
      val interfaces: List<NetworkInterface> =
        Collections.list(NetworkInterface.getNetworkInterfaces())
      for (intf in interfaces) {
        val addrs: List<InetAddress> = Collections.list(intf.inetAddresses)
        for (addr in addrs) {
          if (!addr.isLoopbackAddress) {
            val sAddr = addr.hostAddress
            //boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
            val isIPv4 = sAddr.indexOf(':') < 0
            if (useIPv4) {
              if (isIPv4) return sAddr
            } else {
              if (!isIPv4) {
                val delim = sAddr.indexOf('%') // drop ip6 zone suffix
                return if (delim < 0) sAddr.uppercase() else sAddr.substring(0, delim)
                  .uppercase()
              }
            }
          }
        }
      }
    } catch (ignored: Exception) {
    } // for now eat exceptions
    return ""
  }
}