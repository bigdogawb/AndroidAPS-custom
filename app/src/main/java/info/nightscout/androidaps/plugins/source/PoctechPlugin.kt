package info.nightscout.androidaps.plugins.source

import android.content.Intent
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.interfaces.BgSourceInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.utils.JsonHelper.safeGetString
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONArray
import org.json.JSONException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PoctechPlugin @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sp: SP
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginName(R.string.poctech)
    .preferencesId(R.xml.pref_bgsource)
    .description(R.string.description_source_poctech)
), BgSourceInterface {

    override fun advancedFilteringSupported(): Boolean {
        return false
    }

    override fun handleNewData(intent: Intent) {
        if (!isEnabled(PluginType.BGSOURCE)) return
        val bundle = intent.extras ?: return
        val bgReading = BgReading()
        val data = bundle.getString("data")
        aapsLogger.debug(LTag.BGSOURCE, "Received Poctech Data $data")
        try {
            val jsonArray = JSONArray(data)
            aapsLogger.debug(LTag.BGSOURCE, "Received Poctech Data size:" + jsonArray.length())
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                bgReading.value = json.getDouble("current")
                bgReading.direction = json.getString("direction")
                bgReading.date = json.getLong("date")
                bgReading.raw = json.getDouble("raw")
                if (safeGetString(json, "units", Constants.MGDL) == "mmol/L") bgReading.value = bgReading.value * Constants.MMOLL_TO_MGDL
                val isNew = MainApp.getDbHelper().createIfNotExists(bgReading, "Poctech")
                if (isNew && sp.getBoolean(R.string.key_dexcomg5_nsupload, false)) {
                    NSUpload.uploadBg(bgReading, "AndroidAPS-Poctech")
                }
                if (isNew && sp.getBoolean(R.string.key_dexcomg5_xdripupload, false)) {
                    NSUpload.sendToXdrip(bgReading)
                }
            }
        } catch (e: JSONException) {
            aapsLogger.error("Exception: ", e)
        }
    }
}