package com.axzy.taqueria

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import com.axzy.taqueria.ui.KitchenScreen
import com.axzy.taqueria.ui.LicenseGate
import com.axzy.taqueria.ui.RoleScreen
import com.axzy.taqueria.ui.TaqueriaTheme
import com.axzy.taqueria.ui.WaiterScreen
import com.axzy.taqueria.ui.ScreenScreen

class MainActivity : ComponentActivity() {
    private val localNetworkRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) app.restartNetwork()
    }

    private fun requestLocalNetwork(app: App) {
        if (Build.VERSION.SDK_INT < 37) return
        if (checkSelfPermission(LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) return
        localNetworkRequest.launch(LOCAL_NETWORK)
    }

    private companion object {
        const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }

    /** Si el admin deja la app (o se apaga la pantalla), cocina vuelve a taquero. */
    override fun onStop() {
        super.onStop()
        app.lockAdmin()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        val app = app
        requestLocalNetwork(app)
        setContent {
            TaqueriaTheme {
                when (app.role) {
                    null -> RoleScreen(onPick = app::applyRole)
                    Role.KITCHEN -> LicenseGate(app.license, onExit = { app.applyRole(null) }) { notice ->
                        KitchenScreen(app, notice)
                    }
                    Role.WAITER -> WaiterScreen(app, onExit = { app.applyRole(null) })
                    Role.SCREEN -> ScreenScreen(app, onExit = { app.applyRole(null) })
                }
            }
        }
    }
}
