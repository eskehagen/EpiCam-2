import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.util.TypedValue
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.TextView

object SettingsDialogHelper {

    /// Show pin code dialog
    fun showPinDialog(activity: Activity, onSuccess: () -> Unit) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Indtast PIN-kode"
        }

        val prefs = activity.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val savedPin = prefs.getString("PIN_CODE", "1234")

        AlertDialog.Builder(activity)
            .setTitle("PIN-kode krævet")
            .setView(input)
            .setPositiveButton("OK") { dialog, _ ->
                val enteredPin = input.text.toString()
                if (enteredPin == savedPin) {
                    onSuccess()
                } else {
                    Toast.makeText(activity, "Forkert PIN-kode", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Annuller") { dialog, _ -> dialog.cancel() }
            .show()
    }

    /// Show settings menu dialog
    fun showSettingsDialog(activity: Activity) {
        val prefs = activity.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        // Label + input til BluetoothLampName
        val bluetoothLabel = TextView(activity).apply {
            text = "Bluetooth Lamp Name"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }

        val bluetoothNameInput = EditText(activity).apply {
            hint = "Name of ESP32 lamp/bluetooth module"
            setText(prefs.getString("BLUETOOTH_NAME", "ESP32"))
        }

        // Label + input til Epicam ID
        val epicamLabel = TextView(activity).apply {
            text = "Epicam ID"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }

        val epicamIdInput = EditText(activity).apply {
            hint = "Uniq identification number for the Epicam"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getString("EPICAM_ID", "1"))
        }

        // Label + input til Pinkode
        val pinLabel = TextView(activity).apply {
            text = "PIN-kode"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }

        val pinInput = EditText(activity).apply {
            hint = "4-digit admin pin code"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setText(prefs.getString("PIN_CODE", "1234"))
        }

        layout.apply {
            addView(bluetoothLabel)
            addView(bluetoothNameInput)
            addView(epicamLabel)
            addView(epicamIdInput)
            addView(pinLabel)
            addView(pinInput)
        }

        AlertDialog.Builder(activity)
            .setTitle("Indstillinger")
            .setView(layout)
            .setPositiveButton("Gem") { _, _ ->
                prefs.edit().apply {
                    putString("BLUETOOTH_NAME", bluetoothNameInput.text.toString())
                    putString("EPICAM_ID", epicamIdInput.text.toString())
                    putString("PIN_CODE", pinInput.text.toString())
                    apply()
                }
                Toast.makeText(activity, "Indstillinger gemt", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Luk", null)
            .show()
    }

}

/// Configuration helper object for app settings
object AppConfig {
    fun getBluetoothName(context: Context): String {
        return context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getString("BLUETOOTH_NAME", "ESP32") ?: "ESP32"
    }

    fun getEpicamId(context: Context): Int {
        return context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getString("EPICAM_ID", "1")?.toIntOrNull() ?: 1
    }
}