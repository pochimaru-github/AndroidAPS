package app.aaps.plugins.sync.tidepool.comm

import app.aaps.plugins.sync.tidepool.messages.DatasetReplyMessage
import app.aaps.plugins.sync.tidepool.messages.UploadReplyMessage
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

const val SESSION_TOKEN_HEADER: String = "x-tidepool-session-token"

interface TidepoolApiService {

    @Headers(
        "User-Agent: AAPS-1.0",
        "X-Tidepool-Client-Name: aaps",
        "X-Tidepool-Client-Version: 0.2.0"
    )
    @DELETE("/v1/datasets/{dataSetId}")
    fun deleteDataSet(
        @Header("x-tidepool-session-token") token: String,
        @Path("dataSetId") id: String
    ): Call<DatasetReplyMessage>

    @GET("/v1/users/{userId}/data_sets")
    fun getOpenDataSets(
        @Header("x-tidepool-session-token") token: String,
        @Path("userId") id: String,
        @Query("client.name") clientName: String,
        @Query("size") size: Int
    ): Call<List<DatasetReplyMessage>>

    @GET("/v1/datasets/{dataSetId}")
    fun getDataSet(
        @Header("x-tidepool-session-token") token: String,
        @Path("dataSetId") id: String
    ): Call<DatasetReplyMessage>

    @POST("/v1/users/{userId}/data_sets")
    fun openDataSet(
        @Header("x-tidepool-session-token") token: String,
        @Path("userId") id: String,
        @Body body: RequestBody
    ): Call<DatasetReplyMessage>

    @POST("/v1/datasets/{sessionId}/data")
    fun doUpload(
        @Header("x-tidepool-session-token") token: String,
        @Path("sessionId") id: String,
        @Body body: RequestBody
    ): Call<UploadReplyMessage>

    @PUT("/v1/datasets/{sessionId}")
    fun closeDataSet(
        @Header("x-tidepool-session-token") token: String,
        @Path("sessionId") id: String,
        @Body body: RequestBody
    ): Call<DatasetReplyMessage>
}
