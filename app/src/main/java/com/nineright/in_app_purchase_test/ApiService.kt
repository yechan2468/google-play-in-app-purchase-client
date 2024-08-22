package com.nineright.in_app_purchase_test

import retrofit2.Call
import retrofit2.http.POST

interface ApiService {
    @POST("receipt-validation")
    fun validateReceipt(): Call<DataModel>
}