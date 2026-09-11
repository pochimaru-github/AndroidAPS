package app.aaps.plugins.sync.tidepool.comm

import app.aaps.plugins.sync.tidepool.messages.DatasetReplyMessage
import app.aaps.plugins.sync.tidepool.messages.UploadReplyMessage
import okhttp3.RequestBody
import retrofit2.Call

const val SESSION_TOKEN_HEADER: String = "x-tidepool-session-token"

interface TidepoolApiService {

    fun deleteDataSet(token: String, id: String): Call<DatasetReplyMessage>

    fun getOpenDataSets(
        token: String,
        id: String,
        clientName: String,
        size: Int
    ): Call<List<DatasetReplyMessage>>

    fun getDataSet(token: String, id: String): Call<DatasetReplyMessage>

    fun openDataSet(token: String, id: String, body: RequestBody): Call<DatasetReplyMessage>

    fun doUpload(token: String, id: String, body: RequestBody): Call<UploadReplyMessage>

    fun closeDataSet(token: String, id: String, body: RequestBody): Call<DatasetReplyMessage>
}
