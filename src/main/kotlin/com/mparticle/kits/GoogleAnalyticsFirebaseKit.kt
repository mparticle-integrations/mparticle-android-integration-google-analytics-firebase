package com.mparticle.kits

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.mparticle.MPEvent
import com.mparticle.MParticle
import com.mparticle.MParticle.EventType
import com.mparticle.commerce.CommerceEvent
import com.mparticle.commerce.Product
import com.mparticle.consent.ConsentState
import com.mparticle.identity.MParticleUser
import com.mparticle.internal.Logger
import com.mparticle.kits.KitIntegration.CommerceListener
import com.mparticle.kits.KitIntegration.IdentityListener
import java.math.BigDecimal

class GoogleAnalyticsFirebaseKit : KitIntegration(), KitIntegration.EventListener, IdentityListener,
    CommerceListener, KitIntegration.UserAttributeListener {
    override fun getName(): String = KIT_NAME

    @Throws(IllegalArgumentException::class)
    public override fun onKitCreate(
        map: Map<String, String>,
        context: Context
    ): List<ReportingMessage> {
        Logger.info("$name Kit relies on a functioning instance of Firebase Analytics. If your Firebase Analytics instance is not configured properly, this Kit will not work")
        return emptyList()
    }

    override fun setOptOut(b: Boolean): List<ReportingMessage> = emptyList()

    override fun leaveBreadcrumb(s: String): List<ReportingMessage> = emptyList()

    override fun logError(s: String, map: Map<String, String>): List<ReportingMessage> = emptyList()

    override fun logException(
        e: Exception,
        map: Map<String, String>,
        s: String
    ): List<ReportingMessage> = emptyList()

    override fun logEvent(mpEvent: MPEvent): List<ReportingMessage> {
        getFirebaseEventName(mpEvent)?.let {
            FirebaseAnalytics.getInstance(context)
                .logEvent(it, toBundle(mpEvent.customAttributeStrings))
        }
        return listOf(ReportingMessage.fromEvent(this, mpEvent))
    }

    override fun logScreen(s: String, map: Map<String, String>): List<ReportingMessage> {
        val activity = currentActivity.get()
        if (activity != null) {
            FirebaseAnalytics.getInstance(context)
                .setCurrentScreen(activity, standardizeName(s, true), null)
            return listOf(
                ReportingMessage(
                    this,
                    ReportingMessage.MessageType.SCREEN_VIEW,
                    System.currentTimeMillis(),
                    null
                )
            )
        }
        return emptyList()
    }

    override fun logLtvIncrease(
        bigDecimal: BigDecimal,
        bigDecimal1: BigDecimal,
        s: String,
        map: Map<String, String>
    ): List<ReportingMessage> = emptyList()

    override fun logEvent(commerceEvent: CommerceEvent): List<ReportingMessage> {
        val instance = FirebaseAnalytics.getInstance(context)
        if (commerceEvent.productAction == null) {
            return emptyList()
        }
        val bundle = getCommerceEventBundle(commerceEvent)
            .bundle
        val eventName: String = when (commerceEvent.productAction) {
            Product.ADD_TO_CART -> FirebaseAnalytics.Event.ADD_TO_CART
            Product.ADD_TO_WISHLIST -> FirebaseAnalytics.Event.ADD_TO_WISHLIST
            Product.CHECKOUT -> FirebaseAnalytics.Event.BEGIN_CHECKOUT
            Product.PURCHASE -> FirebaseAnalytics.Event.PURCHASE
            Product.REFUND -> FirebaseAnalytics.Event.REFUND
            Product.REMOVE_FROM_CART -> FirebaseAnalytics.Event.REMOVE_FROM_CART
            Product.CLICK -> FirebaseAnalytics.Event.SELECT_CONTENT
            Product.CHECKOUT_OPTION ->  FirebaseAnalytics.Event.SET_CHECKOUT_OPTION
            Product.DETAIL -> FirebaseAnalytics.Event.VIEW_ITEM
            else -> return emptyList()
        }
        instance.logEvent(eventName, bundle)
        return listOf(ReportingMessage.fromEvent(this, commerceEvent))
    }

    override fun onIdentifyCompleted(
        mParticleUser: MParticleUser,
        filteredIdentityApiRequest: FilteredIdentityApiRequest
    ) {
        setUserId(mParticleUser)
    }

    override fun onLoginCompleted(
        mParticleUser: MParticleUser,
        filteredIdentityApiRequest: FilteredIdentityApiRequest
    ) {
        setUserId(mParticleUser)
    }

    override fun onLogoutCompleted(
        mParticleUser: MParticleUser,
        filteredIdentityApiRequest: FilteredIdentityApiRequest
    ) {
        setUserId(mParticleUser)
    }

    override fun onModifyCompleted(
        mParticleUser: MParticleUser,
        filteredIdentityApiRequest: FilteredIdentityApiRequest
    ) {
        setUserId(mParticleUser)
    }

    override fun onUserIdentified(mParticleUser: MParticleUser) {}
    private fun setUserId(user: MParticleUser?) {
        var userId: String? = null
        if (user != null) {
            if (USER_ID_CUSTOMER_ID_VALUE.equals(settings[USER_ID_FIELD_KEY], true)) {
                userId = user.userIdentities[MParticle.IdentityType.CustomerId]
            } else if (USER_ID_EMAIL_VALUE.equals(settings[USER_ID_FIELD_KEY], true)) {
                userId = user.userIdentities[MParticle.IdentityType.Email]
            } else if (USER_ID_MPID_VALUE.equals(settings[USER_ID_FIELD_KEY], true)) {
                userId = user.id.toString()
            }
        }
        if (!KitUtils.isEmpty(userId)) {
            FirebaseAnalytics.getInstance(context).setUserId(userId)
        }
    }

    fun getFirebaseEventName(event: MPEvent): String? {
        if (event.eventType == EventType.Search) {
            return FirebaseAnalytics.Event.SEARCH
        }
        return if (event.isScreenEvent) {
            FirebaseAnalytics.Event.VIEW_ITEM
        } else standardizeName(event.eventName, true)
    }

    fun toBundle(mapIn: Map<String, String>?): Bundle {
        var map = mapIn
        val bundle = Bundle()
        map = standardizeAttributes(map, true)
        if (map != null) {
            for ((key, value) in map) {
                bundle.putString(key, value)
            }
        }
        return bundle
    }

    fun getCommerceEventBundle(commerceEvent: CommerceEvent): PickyBundle {
        val pickyBundle = getTransactionAttributesBundle(commerceEvent)
        var currency = commerceEvent.currency
        if (currency == null) {
            Logger.info(CURRENCY_FIELD_NOT_SET)
            currency = USD
        }
        return pickyBundle
            .putString(FirebaseAnalytics.Param.CURRENCY, currency)
            .putBundleList(FirebaseAnalytics.Param.ITEMS, getProductBundles(commerceEvent))
            .putString(FirebaseAnalytics.Event.SET_CHECKOUT_OPTION, commerceEvent.checkoutOptions)
            .putInt(FirebaseAnalytics.Event.CHECKOUT_PROGRESS, commerceEvent.checkoutStep)
    }

    fun getProductBundles(commerceEvent: CommerceEvent): Array<Bundle?> {
        val products = commerceEvent.products
        if (products != null) {
            val bundles = arrayOfNulls<Bundle>(products.size)
            var i = 0
            for (product in products) {
                val bundle = getBundle(product)
                    .putString(FirebaseAnalytics.Param.CURRENCY, commerceEvent.currency)
                bundles[i] = bundle.bundle
                i++
            }
            return bundles
        }
        return arrayOfNulls(0)
    }

    fun getTransactionAttributesBundle(commerceEvent: CommerceEvent): PickyBundle {
        val pickyBundle = PickyBundle()
        val transactionAttributes = commerceEvent.transactionAttributes
        return if (commerceEvent.transactionAttributes == null) {
            pickyBundle
        } else pickyBundle
            .putString(
                FirebaseAnalytics.Param.TRANSACTION_ID,
                transactionAttributes?.id
            )
            .putDouble(
                FirebaseAnalytics.Param.VALUE,
                transactionAttributes?.revenue
            )
            .putDouble(
                FirebaseAnalytics.Param.TAX,
                transactionAttributes?.tax
            )
            .putDouble(
                FirebaseAnalytics.Param.SHIPPING,
                transactionAttributes?.shipping
            )
            .putString(
                FirebaseAnalytics.Param.COUPON,
                transactionAttributes?.couponCode
            )
    }

    fun getBundle(product: Product): PickyBundle {
        return PickyBundle()
            .putLong(FirebaseAnalytics.Param.QUANTITY, product.quantity.toLong())
            .putString(FirebaseAnalytics.Param.ITEM_ID, product.sku)
            .putString(FirebaseAnalytics.Param.ITEM_NAME, product.name)
            .putString(FirebaseAnalytics.Param.ITEM_CATEGORY, product.category)
            .putDouble(FirebaseAnalytics.Param.PRICE, product.unitPrice)
    }

    private fun getValue(commerceEvent: CommerceEvent): Double? {
        var value = 0.0
        val products = commerceEvent.products ?: return null
        for (product in products) {
            value += product.quantity * product.unitPrice
        }
        return value
    }

    override fun onIncrementUserAttribute(
        key: String,
        incrementedBy: Number,
        value: String,
        filteredMParticleUser: FilteredMParticleUser
    ) {
        standardizeName(key, false)?.let {
            FirebaseAnalytics.getInstance(context).setUserProperty(
                it, value
            )
        }
    }

    override fun onRemoveUserAttribute(key: String, filteredMParticleUser: FilteredMParticleUser) {
        standardizeName(key, false)?.let {
            FirebaseAnalytics.getInstance(context).setUserProperty(
                it, null
            )
        }
    }

    /**
     * We are going to ignore Lists here, since Firebase only supports String "user property" values
     */
    override fun onSetUserAttribute(
        key: String,
        value: Any,
        filteredMParticleUser: FilteredMParticleUser
    ) {
        if (value is String) {
            standardizeName(key, false)?.let {
                FirebaseAnalytics.getInstance(context).setUserProperty(
                    it, standardizeValue(value, false)
                )
            }
        }
    }

    override fun onSetUserTag(s: String, filteredMParticleUser: FilteredMParticleUser) {}
    override fun onSetUserAttributeList(
        s: String,
        list: List<String>,
        filteredMParticleUser: FilteredMParticleUser
    ) {
    }

    override fun onSetAllUserAttributes(
        userAttributes: Map<String, String>,
        userAttributeLists: Map<String, List<String>>,
        filteredMParticleUser: FilteredMParticleUser
    ) {
        var userAttributes: Map<String, String>? = userAttributes
        userAttributes = standardizeAttributes(userAttributes, false)
        if (userAttributes != null) {
            for ((key, value) in userAttributes) {
                FirebaseAnalytics.getInstance(context).setUserProperty(key, value)
            }
        }
    }

    override fun supportsAttributeLists(): Boolean {
        return false
    }

    override fun onConsentStateUpdated(
        consentState: ConsentState,
        consentState1: ConsentState,
        filteredMParticleUser: FilteredMParticleUser
    ) {
    }

    fun standardizeAttributes(
        attributes: Map<String, String>?,
        event: Boolean
    ): Map<String, String>? {
        if (attributes == null) {
            return null
        }
        val attributeCopy = HashMap<String, String>()
        for ((key, value) in attributes) {
            attributeCopy[standardizeName(key, event)!!] = standardizeValue(value, event)
        }
        return attributeCopy
    }

    fun standardizeValue(valueIn: String?, event: Boolean): String {
        var value = valueIn ?: return ""
        if (event) {
            if (value.length > eventValMaxLength) {
                value = value.substring(0, eventValMaxLength)
            }
        } else {
            if (value.length > userAttributeValMaxLength) {
                value = value.substring(0, userAttributeValMaxLength)
            }
        }
        return value
    }

    fun standardizeName(nameIn: String?, event: Boolean): String? {
        var name = nameIn ?: return null
        name = name.replace("[^a-zA-Z0-9_\\s]".toRegex(), " ")
        name = name.replace("[\\s]+".toRegex(), "_")
        for (forbiddenPrefix in forbiddenPrefixes) {
            if (name.startsWith(forbiddenPrefix)) {
                name = name.replaceFirst(forbiddenPrefix.toRegex(), "")
            }
        }
        while (name.isNotEmpty() && !Character.isLetter(name.toCharArray()[0])) {
            name = name.substring(1)
        }
        if (event) {
            if (name.length > eventMaxLength) {
                name = name.substring(0, eventMaxLength)
            }
        } else {
            if (name.length > userAttributeMaxLength) {
                name = name.substring(0, userAttributeMaxLength)
            }
        }
        return name
    }

    class PickyBundle {
        val bundle = Bundle()
        fun putString(key: String?, value: String?): PickyBundle {
            if (value != null) {
                bundle.putString(key, value)
            }
            return this
        }

        fun putDouble(key: String?, value: Double?): PickyBundle {
            if (value != null) {
                bundle.putDouble(key, value)
            }
            return this
        }

        fun putLong(key: String?, value: Long?): PickyBundle {
            if (value != null) {
                bundle.putLong(key, value)
            }
            return this
        }

        fun putInt(key: String?, value: Int?): PickyBundle {
            if (value != null) {
                bundle.putInt(key, value)
            }
            return this
        }

        fun putBundleList(key: String?, value: Array<Bundle?>?): PickyBundle {
            if (value != null) {
                bundle.putParcelableArray(key, value)
            }
            return this
        }
    }

    companion object {
        const val USER_ID_FIELD_KEY = "userIdField"
        const val USER_ID_CUSTOMER_ID_VALUE = "customerId"
        const val USER_ID_EMAIL_VALUE = "email"
        const val USER_ID_MPID_VALUE = "mpid"
        private val forbiddenPrefixes = arrayOf("google_", "firebase_", "ga_")
        private const val CURRENCY_FIELD_NOT_SET = "Currency field required by Firebase was not set, defaulting to 'USD'"
        private const val USD = "USD"
        private const val eventMaxLength = 40
        private const val userAttributeMaxLength = 24
        private const val eventValMaxLength = 100
        private const val userAttributeValMaxLength = 36
        private const val KIT_NAME = "Google Analytics for Firebase"
    }
}