package com.nomad.travel

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

object Launchpad {
    private val client = OkHttpClient()

    /** Call this when app starts! */
    fun verifyInstall(context: Context) {
        val referrerClient = InstallReferrerClient.newBuilder(context).build()
        referrerClient.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(code: Int) {
                if (code == InstallReferrerClient.InstallReferrerResponse.OK) {
                    val referrer = referrerClient.installReferrer.installReferrer
                    if (referrer.contains("launchpad")) {
                        sendToServer(referrer)
                    }
                    referrerClient.endConnection()
                }
            }

            override fun onInstallReferrerServiceDisconnected() {}
        })
    }

    private fun sendToServer(referrer: String) {
        val json = JSONObject().put("installReferrer", referrer)
        val request = Request.Builder()
            .url("https://apptesters.cc/api/verify-install")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }
}
