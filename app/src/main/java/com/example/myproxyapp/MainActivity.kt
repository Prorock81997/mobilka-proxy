package com.example.myproxyapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var btnToggle: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etHost = findViewById(R.id.etHost)
        etPort = findViewById(R.id.etPort)
        etUser = findViewById(R.id.etUser)
        etPass = findViewById(R.id.etPass)
        btnToggle = findViewById(R.id.btnToggle)
        tvStatus = findViewById(R.id.tvStatus)

        val prefs = getSharedPreferences("proxy_prefs", Context.MODE_PRIVATE)
        etHost.setText(prefs.getString("host", ""))
        etPort.setText(prefs.getString("port", ""))
        etUser.setText(prefs.getString("user", ""))
        etPass.setText(prefs.getString("pass", ""))

        updateUi()

        btnToggle.setOnClickListener {
            if (ProxyVpnService.isConnected) {
                val stopIntent = Intent(this, ProxyVpnService::class.java).apply { action = "STOP" }
                startService(stopIntent)
                btnToggle.postDelayed({ updateUi() }, 300)
            } else {
                val host = etHost.text.toString().trim()
                val portStr = etPort.text.toString().trim()
                if (host.isEmpty() || portStr.isEmpty()) {
                    Toast.makeText(this, "Введите сервер и порт", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                prefs.edit()
                    .putString("host", host)
                    .putString("port", portStr)
                    .putString("user", etUser.text.toString())
                    .putString("pass", etPass.text.toString())
                    .apply()

                val vpnIntent = VpnService.prepare(this)
                if (vpnIntent != null) {
                    startActivityForResult(vpnIntent, 100)
                } else {
                    onActivityResult(100, Activity.RESULT_OK, null)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            val serviceIntent = Intent(this, ProxyVpnService::class.java).apply {
                putExtra("HOST", etHost.text.toString().trim())
                putExtra("PORT", etPort.text.toString().trim().toIntOrNull() ?: 8080)
                putExtra("USER", etUser.text.toString().trim())
                putExtra("PASS", etPass.text.toString())
            }
            startService(serviceIntent)
            btnToggle.postDelayed({ updateUi() }, 500)
        }
    }

    private fun updateUi() {
        if (ProxyVpnService.isConnected) {
            btnToggle.text = "Отключить"
            tvStatus.text = "Статус: Подключено"
        } else {
            btnToggle.text = "Подключить прокси"
            tvStatus.text = "Статус: Отключено"
        }
    }
}
