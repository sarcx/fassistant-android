package dev.todor.fassistant

import android.app.admin.DeviceAdminReceiver

/**
 * Only there so a dedicated phone can make this app device owner over ADB:
 *   adb shell dpm set-device-owner dev.todor.fassistant/.AdminReceiver
 *
 * That blocks the user from uninstalling or force-stopping it. It is not a background-launch
 * exemption — the overlay permission is what allows the reopen.
 */
class AdminReceiver : DeviceAdminReceiver()
