package com.asamm.locus.client.api

import com.asamm.locus.client.model.TimelineResponseJSON
import com.asamm.locus.client.model.TopResponseJSON
import kotlinx.serialization.SerialName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface AnalyticsApi {

    /**
     * enum for parameter period
     */
    enum class Period_analyticsApiUsageTimelineGet(val value: kotlin.String) {
        @SerialName(value = "current_billing_period")
        currentBillingPeriod("current_billing_period"),
        @SerialName(value = "last_billing_period")
        lastBillingPeriod("last_billing_period"),
        @SerialName(value = "past_30_days")
        past30Days("past_30_days"),
        @SerialName(value = "past_90_days")
        past90Days("past_90_days"),
        @SerialName(value = "past_12_billing_periods")
        past12BillingPeriods("past_12_billing_periods"),
        @SerialName(value = "past_12_months")
        past12Months("past_12_months"),
        @SerialName(value = "all")
        all("all")
    }


    /**
     * enum for parameter classifier
     */
    enum class Classifier_analyticsApiUsageTimelineGet(val value: kotlin.String) {
        @SerialName(value = "services")
        services("services"),
        @SerialName(value = "api_keys")
        apiKeys("api_keys"),
        @SerialName(value = "service_credentials")
        serviceCredentials("service_credentials")
    }


    /**
     * enum for parameter group
     */
    enum class Group_analyticsApiUsageTimelineGet(val value: kotlin.String) {
        @SerialName(value = "request")
        request("request"),
        @SerialName(value = "session")
        session("session"),
        @SerialName(value = "export")
        export("export")
    }


    /**
     * enum for parameter format
     */
    enum class Format_analyticsApiUsageTimelineGet(val value: kotlin.String) {
        @SerialName(value = "json")
        json("json"),
        @SerialName(value = "csv")
        csv("csv")
    }

    /**
     * Get usage analytics timeline
     *
     * Responses:
     *  - 200: Usage analytics timeline response, further description is within each media type schema.
     *  - 400: - `period_not_found`: Selected time period does not exist (for example last_billing_period of new account) - `account_over_limit`: Account currently has too many entities (api_keys or service_credentials) to be queried. - `response_too_large`: There were too much data in the result (could be historical).
     *
     * @param period - billing periods are based on account subscription - for classifiers api_keys and service_credentials are **supported only daily periods**: current_billing_period, last_billing_period, past_30_days, past_90_days
     * @param classifier classifies datasets
     * @param group group ID filter, **mandatory for CSV format** (optional)
     * @param format response media type (optional, default to json)
     * @return [TimelineResponseJSON]
     */
    @GET("analytics/api_usage/timeline")
    suspend fun analyticsApiUsageTimelineGet(
        @Query("period") period: Period_analyticsApiUsageTimelineGet,
        @Query("classifier") classifier: Classifier_analyticsApiUsageTimelineGet,
        @Query("group") group: Group_analyticsApiUsageTimelineGet? = null,
        @Query("format") format: Format_analyticsApiUsageTimelineGet? = Format_analyticsApiUsageTimelineGet.json,
    ): Response<TimelineResponseJSON>


    /**
     * enum for parameter period
     */
    enum class Period_analyticsApiUsageTopGet(val value: kotlin.String) {
        @SerialName(value = "current_billing_period")
        currentBillingPeriod("current_billing_period"),
        @SerialName(value = "last_billing_period")
        lastBillingPeriod("last_billing_period"),
        @SerialName(value = "past_30_days")
        past30Days("past_30_days"),
        @SerialName(value = "past_90_days")
        past90Days("past_90_days"),
        @SerialName(value = "past_12_billing_periods")
        past12BillingPeriods("past_12_billing_periods"),
        @SerialName(value = "past_12_months")
        past12Months("past_12_months"),
        @SerialName(value = "all")
        all("all")
    }


    /**
     * enum for parameter classifier
     */
    enum class Classifier_analyticsApiUsageTopGet(val value: kotlin.String) {
        @SerialName(value = "services")
        services("services"),
        @SerialName(value = "api_keys")
        apiKeys("api_keys"),
        @SerialName(value = "service_credentials")
        serviceCredentials("service_credentials")
    }


    /**
     * enum for parameter group
     */
    enum class Group_analyticsApiUsageTopGet(val value: kotlin.String) {
        @SerialName(value = "request")
        request("request"),
        @SerialName(value = "session")
        session("session"),
        @SerialName(value = "export")
        export("export")
    }


    /**
     * enum for parameter format
     */
    enum class Format_analyticsApiUsageTopGet(val value: kotlin.String) {
        @SerialName(value = "json")
        json("json"),
        @SerialName(value = "csv")
        csv("csv")
    }

    /**
     * Get top usage analytics
     *
     * Responses:
     *  - 200: Usage analytics top response, further description is within each media type schema.
     *  - 400: - `period_not_found`: Selected time period does not exist (for example last_billing_period of new account)
     *
     * @param period - billing periods are based on account subscription - for classifiers api_keys and service_credentials are **supported only daily periods**: current_billing_period, last_billing_period, past_30_days, past_90_days
     * @param classifier classifies datasets
     * @param group group ID filter, **mandatory for CSV format** (optional)
     * @param format response media type (optional, default to json)
     * @param limit number of top items within response group (optional, default to 10)
     * @return [TopResponseJSON]
     */
    @GET("analytics/api_usage/top")
    suspend fun analyticsApiUsageTopGet(
        @Query("period") period: Period_analyticsApiUsageTopGet,
        @Query("classifier") classifier: Classifier_analyticsApiUsageTopGet,
        @Query("group") group: Group_analyticsApiUsageTopGet? = null,
        @Query("format") format: Format_analyticsApiUsageTopGet? = Format_analyticsApiUsageTopGet.json,
        @Query("limit") limit: kotlin.Int? = 10,
    ): Response<TopResponseJSON>

}
