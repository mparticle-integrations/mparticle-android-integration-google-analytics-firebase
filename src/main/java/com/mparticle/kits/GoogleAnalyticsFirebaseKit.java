package com.mparticle.kits;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.mparticle.MPEvent;
import com.mparticle.MParticle;
import com.mparticle.commerce.CommerceEvent;
import com.mparticle.commerce.Product;
import com.mparticle.commerce.TransactionAttributes;
import com.mparticle.consent.ConsentState;
import com.mparticle.identity.MParticleUser;
import com.mparticle.internal.Logger;
import com.mparticle.internal.MPUtility;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GoogleAnalyticsFirebaseKit extends KitIntegration implements KitIntegration.EventListener, KitIntegration.IdentityListener, KitIntegration.CommerceListener, KitIntegration.UserAttributeListener {
    final static String USER_ID_FIELD_KEY = "userIdField";
    final static String USER_ID_CUSTOMER_ID_VALUE = "customerId";
    final static String USER_ID_EMAIL_VALUE = "email";
    final static String USER_ID_MPID_VALUE = "mpid";

    private static String[] forbiddenPrefixes = new String[]{"google_", "firebase_", "ga_"};
    private static int eventMaxLength = 40;
    private static int userAttributeMaxLength = 24;

    private static int eventValMaxLength = 100;
    private static int userAttributeValMaxLength = 36;

    @Override
    public String getName() {
        return "Google Analytics for Firebase";
    }

    @Override
    protected List<ReportingMessage> onKitCreate(Map<String, String> map, Context context) throws IllegalArgumentException {
        Logger.info(getName() + " Kit relies on a functioning instance of Firebase Analytics. If your Firebase Analytics instance is not configured properly, this Kit will not work");
        return null;
    }

    @Override
    public List<ReportingMessage> setOptOut(boolean b) {
        return null;
    }

    @Override
    public List<ReportingMessage> leaveBreadcrumb(String s) {
        return null;
    }

    @Override
    public List<ReportingMessage> logError(String s, Map<String, String> map) {
        return null;
    }

    @Override
    public List<ReportingMessage> logException(Exception e, Map<String, String> map, String s) {
        return null;
    }

    @Override
    public List<ReportingMessage> logEvent(MPEvent mpEvent) {
        FirebaseAnalytics.getInstance(getContext())
                .logEvent(getFirebaseEventName(mpEvent), toBundle(mpEvent.getInfo()));

        return Collections.singletonList(ReportingMessage.fromEvent(this, mpEvent));
    }

    @Override
    public List<ReportingMessage> logScreen(String s, Map<String, String> map) {
        Activity activity = getCurrentActivity().get();
        if (activity != null) {
            FirebaseAnalytics.getInstance(getContext()).setCurrentScreen(activity, s, null);
            return Collections.singletonList(new ReportingMessage(this, ReportingMessage.MessageType.SCREEN_VIEW, System.currentTimeMillis(), null));
        }
        return null;
    }

    @Override
    public List<ReportingMessage> logLtvIncrease(BigDecimal bigDecimal, BigDecimal bigDecimal1, String s, Map<String, String> map) {
        return null;
    }

    @Override
    public List<ReportingMessage> logEvent(CommerceEvent commerceEvent) {
        FirebaseAnalytics instance = FirebaseAnalytics.getInstance(getContext());
        String eventName;
        Bundle bundle;
        if (commerceEvent == null || commerceEvent.getProductAction() == null) {
            return null;
        }
        switch (commerceEvent.getProductAction()) {
            case Product.ADD_TO_CART:
                eventName = FirebaseAnalytics.Event.ADD_TO_CART;
                for (Bundle lBundle: getProductBundles(commerceEvent)) {
                    instance.logEvent(eventName, lBundle);
                }
                break;
            case Product.ADD_TO_WISHLIST:
                eventName = FirebaseAnalytics.Event.ADD_TO_WISHLIST;
                for (Bundle lBundle: getProductBundles(commerceEvent)) {
                    instance.logEvent(eventName, lBundle);
                }
                break;
            case Product.CHECKOUT:
                eventName = FirebaseAnalytics.Event.BEGIN_CHECKOUT;
                Double value = getValue(commerceEvent);
                bundle = getTransactionAttributesBundle(commerceEvent)
                        .putDouble(FirebaseAnalytics.Param.VALUE, value)
                        .putString(FirebaseAnalytics.Param.CURRENCY, commerceEvent.getCurrency())
                        .getBundle();
                instance.logEvent(eventName, bundle);
                break;
            case Product.PURCHASE:
                eventName = FirebaseAnalytics.Event.ECOMMERCE_PURCHASE;
                value = getValue(commerceEvent);
                bundle = getTransactionAttributesBundle(commerceEvent)
                        .putDouble(FirebaseAnalytics.Param.VALUE, value)
                        .putString(FirebaseAnalytics.Param.CURRENCY, commerceEvent.getCurrency())
                        .getBundle();
                instance.logEvent(eventName, bundle);
                break;
            case Product.REFUND:
                eventName = FirebaseAnalytics.Event.PURCHASE_REFUND;
                value = getValue(commerceEvent);
                bundle = getTransactionAttributesBundle(commerceEvent)
                        .putDouble(FirebaseAnalytics.Param.VALUE, value)
                        .putString(FirebaseAnalytics.Param.CURRENCY, commerceEvent.getCurrency())
                        .getBundle();
                instance.logEvent(eventName, bundle);
                break;
            case Product.REMOVE_FROM_CART:
                eventName = FirebaseAnalytics.Event.REMOVE_FROM_CART;
                for (Bundle lBundle: getProductBundles(commerceEvent)) {
                    instance.logEvent(eventName, lBundle);
                }
                break;
            case Product.CLICK:
                eventName = FirebaseAnalytics.Event.SELECT_CONTENT;
                for (Bundle lBundle: getProductBundles(commerceEvent)) {
                    instance.logEvent(eventName, lBundle);
                }
                break;
            case Product.CHECKOUT_OPTION:
                eventName = FirebaseAnalytics.Event.SET_CHECKOUT_OPTION;
                bundle = new PickyBundle()
                        .putString(FirebaseAnalytics.Event.SET_CHECKOUT_OPTION, commerceEvent.getCheckoutOptions())
                        .putInt(FirebaseAnalytics.Event.CHECKOUT_PROGRESS, commerceEvent.getCheckoutStep())
                        .getBundle();
                instance.logEvent(eventName, bundle);
                break;
            case Product.DETAIL:
                eventName = FirebaseAnalytics.Event.VIEW_ITEM;
                for (Bundle lBundle: getProductBundles(commerceEvent)) {
                    instance.logEvent(eventName, lBundle);
                }
                break;
            case Product.REMOVE_FROM_WISHLIST:
                eventName = Product.REMOVE_FROM_WISHLIST;
                for (Bundle lBundle: getProductBundles(commerceEvent)) {
                    instance.logEvent(eventName, lBundle);
                }
                break;
            default:
                return null;
        }
        return Collections.singletonList(ReportingMessage.fromEvent(this, commerceEvent));
    }

    @Override
    public void onIdentifyCompleted(MParticleUser mParticleUser, FilteredIdentityApiRequest filteredIdentityApiRequest) {
        setUserId(mParticleUser);
    }

    @Override
    public void onLoginCompleted(MParticleUser mParticleUser, FilteredIdentityApiRequest filteredIdentityApiRequest) {
        setUserId(mParticleUser);
    }

    @Override
    public void onLogoutCompleted(MParticleUser mParticleUser, FilteredIdentityApiRequest filteredIdentityApiRequest) {
        setUserId(mParticleUser);
    }

    @Override
    public void onModifyCompleted(MParticleUser mParticleUser, FilteredIdentityApiRequest filteredIdentityApiRequest) {
        setUserId(mParticleUser);
    }

    @Override
    public void onUserIdentified(MParticleUser mParticleUser) {

    }

    private void setUserId(MParticleUser user) {
        String userId = null;
        if (USER_ID_CUSTOMER_ID_VALUE.equalsIgnoreCase(getSettings().get(USER_ID_FIELD_KEY))) {
            userId = user.getUserIdentities().get(MParticle.IdentityType.CustomerId);
        } else if (USER_ID_EMAIL_VALUE.equalsIgnoreCase(getSettings().get(USER_ID_FIELD_KEY))) {
            userId = user.getUserIdentities().get(MParticle.IdentityType.Email);
        } else if (user != null && USER_ID_MPID_VALUE.equalsIgnoreCase(getSettings().get(USER_ID_FIELD_KEY))) {
            userId = Long.toString(user.getId());
        }
        if (!KitUtils.isEmpty(userId)) {
            FirebaseAnalytics.getInstance(getContext()).setUserId(userId);
        }
        }

    String getFirebaseEventName(MPEvent event) {
        switch (event.getEventType()) {
            case Search:
                return FirebaseAnalytics.Event.SEARCH;
        }
        if (event.isScreenEvent()) {
            return FirebaseAnalytics.Event.VIEW_ITEM;
        }
        return standardizeName(event.getEventName(), true);
    }

    Bundle toBundle(Map<String, String> map) {
        Bundle bundle = new Bundle();
        map = standardizeAttributes(map, true);
        if (map != null) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                bundle.putString(entry.getKey(), entry.getValue());
            }
        }
        return bundle;
    }

    List<Bundle> getProductBundles(CommerceEvent commerceEvent) {
        List<Bundle> bundles = new ArrayList<>();
        List<Product> products = commerceEvent.getProducts();
        if (products == null) {
            return bundles;
        }
        for (Product product: products) {
            PickyBundle bundle = getBundle(product)
                    .putString(FirebaseAnalytics.Param.CURRENCY, commerceEvent.getCurrency());
            bundles.add(bundle.getBundle());
        }
        return bundles;
    }

    PickyBundle getTransactionAttributesBundle(CommerceEvent commerceEvent) {
        PickyBundle pickyBundle = new PickyBundle();
        TransactionAttributes transactionAttributes = commerceEvent.getTransactionAttributes();
        if (commerceEvent.getTransactionAttributes() == null) {
            return pickyBundle;
        }
        return pickyBundle
                .putString(FirebaseAnalytics.Param.ITEM_ID, transactionAttributes.getId())
                .putDouble(FirebaseAnalytics.Param.TAX, transactionAttributes.getTax())
                .putDouble(FirebaseAnalytics.Param.SHIPPING, transactionAttributes.getShipping())
                .putString(FirebaseAnalytics.Param.COUPON, transactionAttributes.getCouponCode());
    }

    PickyBundle getBundle(Product product) {
        return new PickyBundle()
                .putDouble(FirebaseAnalytics.Param.QUANTITY, product.getQuantity())
                .putString(FirebaseAnalytics.Param.ITEM_ID, product.getSku())
                .putString(FirebaseAnalytics.Param.ITEM_NAME, product.getName())
                .putString(FirebaseAnalytics.Param.ITEM_CATEGORY, product.getCategory())
                .putDouble(FirebaseAnalytics.Param.VALUE, product.getUnitPrice());
    }

    private Double getValue(CommerceEvent commerceEvent) {
        double value = 0;
        List<Product> products = commerceEvent.getProducts();
        if (products == null) {
            return null;
        }
        for (Product product: products) {
            value += product.getQuantity() * product.getUnitPrice();
        }
        return value;
    }

    @Override
    public void onIncrementUserAttribute(String key, int incrementedBy, String value, FilteredMParticleUser filteredMParticleUser) {
        FirebaseAnalytics.getInstance(getContext()).setUserProperty(standardizeName(key, false), value);
    }

    @Override
    public void onRemoveUserAttribute(String key, FilteredMParticleUser filteredMParticleUser) {
        FirebaseAnalytics.getInstance(getContext()).setUserProperty(standardizeName(key, false), null);
    }

    /**
     * We are going to ignore Lists here, since Firebase only supports String "user property" values
     */
    @Override
    public void onSetUserAttribute(String key, Object value, FilteredMParticleUser filteredMParticleUser) {
        if (value instanceof String) {
            FirebaseAnalytics.getInstance(getContext()).setUserProperty(standardizeName(key, false), standardizeValue((String)value, false));
        }
    }

    @Override
    public void onSetUserTag(String s, FilteredMParticleUser filteredMParticleUser) {

    }

    @Override
    public void onSetUserAttributeList(String s, List<String> list, FilteredMParticleUser filteredMParticleUser) {

    }

    @Override
    public void onSetAllUserAttributes(Map<String, String> userAttributes, Map<String, List<String>> userAttributeLists, FilteredMParticleUser filteredMParticleUser) {
        userAttributes = standardizeAttributes(userAttributes, false);
        for (Map.Entry<String, String> entry: userAttributes.entrySet()) {
            FirebaseAnalytics.getInstance(getContext()).setUserProperty(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public boolean supportsAttributeLists() {
        return false;
    }

    @Override
    public void onConsentStateUpdated(ConsentState consentState, ConsentState consentState1, FilteredMParticleUser filteredMParticleUser) {

    }

    Map<String, String> standardizeAttributes(Map<String, String> attributes, boolean event) {
        if (attributes == null) {
            return null;
        }
        Map<String, String> attributeCopy = new HashMap<>();
        for (Map.Entry<String, String> entry: attributes.entrySet()) {
            attributeCopy.put(standardizeName(entry.getKey(), event), standardizeValue(entry.getValue(), event));
        }
        return attributeCopy;
    }

    final String standardizeValue(String value, boolean event) {
        if (value == null) {
            return value;
        }
        if (event) {
            if (value.length() > eventValMaxLength) {
                value = value.substring(0, eventValMaxLength);
            }
        } else {
            if (value.length() > userAttributeValMaxLength) {
                value = value.substring(0, userAttributeValMaxLength);
            }
        }
        return value;
    }

    final String standardizeName(String name, boolean event) {
        if (name == null) {
            return null;
        }
        name = name.replace(" ", "_");
        name = name.replaceAll("[^a-zA-Z0-9_" +
                "]", "");

        for(String forbiddenPrefix: forbiddenPrefixes) {
            if (name.startsWith(forbiddenPrefix)) {
                name = name.replaceFirst(forbiddenPrefix, "");
            }
        }

        while(name.length() > 0 && !Character.isLetter(name.toCharArray()[0])) {
            name = name.substring(1);
        }
        if (event) {
            if (name.length() > eventMaxLength) {
                name = name.substring(0, eventMaxLength);
            }
        } else {
            if (name.length() > userAttributeMaxLength) {
                name = name.substring(0, userAttributeMaxLength);
            }
        }
        return name;
    }

    class PickyBundle {
        private Bundle bundle = new Bundle();

        PickyBundle putString(String key, String value) {
            if (value != null) {
                bundle.putString(key, value);
            }
            return this;
        }

        PickyBundle putDouble(String key, Double value) {
            if (value != null) {
                bundle.putDouble(key, value);
            }
            return this;
        }

        PickyBundle putInt(String key, Integer value) {
            if (value != null) {
                bundle.putInt(key, value);
            }
            return this;
        }

        Bundle getBundle() {
            return bundle;
        }
    }
}
