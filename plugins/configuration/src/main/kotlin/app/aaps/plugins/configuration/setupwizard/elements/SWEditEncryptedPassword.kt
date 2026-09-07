package app.aaps.plugins.configuration.setupwizard.elements

import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.ui.R
import app.aaps.core.ui.extensions.scanForActivity
import app.aaps.core.ui.extensions.toVisibility
import javax.inject.Inject

class SWEditEncryptedPassword @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    rxBus: RxBus,
    preferences: Preferences,
    passwordCheck: PasswordCheck,
    private val cryptoUtil: CryptoUtil
) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    private var validator: (String) -> Boolean = String::isNotEmpty
    private var updateDelay = 0L

    override fun generateDialog(layout: LinearLayout) {
        val context = layout.context
        val isPasswordSet = preferences.getIfExists(StringKey.ProtectionMasterPassword).isNullOrEmpty().not()

        val createdEditText = EditText(context).apply {
            id = View.generateViewId()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            maxLines = 1
            visibility = isPasswordSet.not().toVisibility()
        }

        val createdEditText2 = EditText(context).apply {
            id = View.generateViewId()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            maxLines = 1
            visibility = isPasswordSet.not().toVisibility()
        }

        val createdL: TextView? = label?.let {
            TextView(context).apply {
                id = View.generateViewId()
                setText(it)
                setTypeface(typeface, Typeface.BOLD)
            }
        }

        val createdC: TextView? = comment?.let {
            TextView(context).apply {
                id = View.generateViewId()
                setText(it)
                setTypeface(typeface, Typeface.ITALIC)
                visibility = isPasswordSet.not().toVisibility()
            }
        }

        val createdC2 = TextView(context).apply {
            id = View.generateViewId()
            setText(R.string.confirm)
            visibility = isPasswordSet.not().toVisibility()
        }

        val button = Button(context)
        button.setText(R.string.unlock_settings)
        button.setOnClickListener {
            context.scanForActivity()?.let { activity ->
                passwordCheck.queryPassword(activity, R.string.master_password, StringKey.ProtectionMasterPassword, {
                    button.visibility = View.GONE
                    createdEditText.visibility = View.VISIBLE
                    createdEditText2.visibility = View.VISIBLE
                    createdL?.visibility = View.VISIBLE
                    createdC?.visibility = View.VISIBLE
                    createdC2.visibility = View.VISIBLE
                })
            }
        }
        button.visibility = isPasswordSet.toVisibility()
        layout.addView(button)

        createdL?.let { layout.addView(it) }
        createdC?.let { layout.addView(it) }
        layout.addView(createdEditText)
        layout.addView(createdC2)
        layout.addView(createdEditText2)

        super.generateDialog(layout)
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                preferences.remove(preference as StringPreferenceKey)
                scheduleChange(updateDelay)
                if (validator.invoke(createdEditText.text.toString()) && validator.invoke(createdEditText2.text.toString()) && createdEditText.text.toString() == createdEditText2.text.toString())
                    save(s.toString(), updateDelay)
            }

            override fun afterTextChanged(s: Editable) {}
        }
        createdEditText.addTextChangedListener(watcher)
        createdEditText2.addTextChangedListener(watcher)
    }

    fun preference(preference: StringKey): SWEditEncryptedPassword {
        this.preference = preference
        return this
    }

    override fun save(value: CharSequence, updateDelay: Long) {
        preferences.put(preference as StringPreferenceKey, cryptoUtil.hashPassword(value.toString()))
        scheduleChange(updateDelay)
    }
}
