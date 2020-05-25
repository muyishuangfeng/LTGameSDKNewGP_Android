package com.sdk.ltgame.ltgoogleplay;

import android.app.Activity;
import android.util.Log;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchaseHistoryRecord;
import com.android.billingclient.api.PurchaseHistoryResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.gentop.ltgame.ltgamesdkcore.common.Target;
import com.gentop.ltgame.ltgamesdkcore.impl.OnRechargeListener;
import com.gentop.ltgame.ltgamesdkcore.model.RechargeResult;
import com.sdk.ltgame.ltnet.manager.LoginRealizeManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GooglePlayHelper implements PurchasesUpdatedListener {
    private static final String TAG = GooglePlayHelper.class.getSimpleName();
    private BillingClient mBillingClient;
    //订单号
    private String mOrderID;
    private String mProductID;
    private int mPayTest = 0;
    private WeakReference<Activity> mActivityRef;
    private int mRechargeTarget;
    private OnRechargeListener mListener;
    //自定义参数
    private Map<String, Object> mParams;
    private String mConsume = "0";
    //商品
    private String mSku;
    private List<Purchase> mList = new ArrayList<>();


    GooglePlayHelper(Activity activity, String productID,
                     String sku, Map<String, Object> mParams,
                     OnRechargeListener mListener) {
        this.mActivityRef = new WeakReference<>(activity);
        this.mSku = sku;
        this.mProductID = productID;
        this.mParams = mParams;
        this.mRechargeTarget = Target.RECHARGE_GOOGLE;
        this.mListener = mListener;
    }


    /**
     * 初始化
     */
    void init() {
        mBillingClient = BillingClient.newBuilder(mActivityRef.get()).setListener(this)
                .enablePendingPurchases()
                .build();
        mBillingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                Log.e(TAG, billingResult.getResponseCode() + "==onBillingSetupFinished==");
                billingResult.getDebugMessage();
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    mConsume = "1";
                    getLTOrderID(mParams);
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
            }
        });
    }


    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> list) {
        if (list != null && list.size() > 0) {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                for (Purchase purchase : list) {
                    mList.add(purchase);
                    uploadToServer(purchase.getPurchaseToken());
                }
            }
        } else {
            switch (billingResult.getResponseCode()) {
                case BillingClient.BillingResponseCode.SERVICE_TIMEOUT: {//服务连接超时
                    mListener.onState(mActivityRef.get(), RechargeResult.failOf("-3"));
                    break;
                }
                case BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED: {
                    mListener.onState(mActivityRef.get(), RechargeResult.failOf("-2"));
                    break;
                }
                case BillingClient.BillingResponseCode.SERVICE_DISCONNECTED: {//服务未连接
                    mListener.onState(mActivityRef.get(), RechargeResult.failOf("-1"));
                    break;
                }
                case BillingClient.BillingResponseCode.USER_CANCELED: {//取消
                    mListener.onState(mActivityRef.get(), RechargeResult.failOf("1"));
                    break;
                }
                case BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE: {//服务不可用
                    mListener.onState(mActivityRef.get(), RechargeResult.failOf("2"));
                    break;
                }
                case BillingClient.BillingResponseCode.BILLING_UNAVAILABLE: {//购买不可用
                    mListener.onState(mActivityRef.get(), RechargeResult.failOf("3"));
                    break;
                }
                case BillingClient.BillingResponseCode.ITEM_UNAVAILABLE: {//商品不存在
                    mListener.onState(mActivityRef.get(), RechargeResult.failOf("4"));
                    break;
                }
                case BillingClient.BillingResponseCode.DEVELOPER_ERROR: {//提供给 API 的无效参数
                    mListener.onState(mActivityRef.get(), RechargeResult.failOf("5"));
                    break;
                }
                case BillingClient.BillingResponseCode.ERROR: {//错误
                    mListener.onState(mActivityRef.get(), RechargeResult.failOf("6"));
                    break;
                }
                case BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED: {//未消耗掉
                    queryHistory();
                    break;
                }
                case BillingClient.BillingResponseCode.ITEM_NOT_OWNED: {//不可购买
                    mListener.onState(mActivityRef.get(), RechargeResult.failOf("8"));
                    break;
                }
            }
        }


    }

    /**
     * 购买
     */
    private void recharge() {
        if (mBillingClient.isReady()) {
            List<String> skuList = new ArrayList<>();
            skuList.add(mSku);
            SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
            params.setSkusList(skuList).setType(BillingClient.SkuType.INAPP);
            mBillingClient.querySkuDetailsAsync(params.build(),
                    new SkuDetailsResponseListener() {
                        @Override
                        public void onSkuDetailsResponse(BillingResult billingResult,
                                                         List<SkuDetails> skuDetailsList) {
                            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                                    && skuDetailsList != null) {
                                for (SkuDetails skuDetails : skuDetailsList) {
                                    String sku = skuDetails.getSku();
                                    if (mSku.equals(sku)) {
                                        BillingFlowParams purchaseParams =
                                                BillingFlowParams.newBuilder()
                                                        .setSkuDetails(skuDetails)
                                                        .build();
                                        mBillingClient.launchBillingFlow(mActivityRef.get(), purchaseParams);
                                    }
                                }
                            }

                        }
                    });

        }


    }


    /**
     * 消耗
     */
    private void consume(String purchaseToken, final RechargeResult result) {
        ConsumeParams consumeParams =
                ConsumeParams.newBuilder()
                        .setPurchaseToken(purchaseToken)
                        .build();
        mBillingClient.consumeAsync(consumeParams, new ConsumeResponseListener() {
            @Override
            public void onConsumeResponse(BillingResult billingResult, String s) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.e(TAG, billingResult.getResponseCode() + "==consumeAsync==" + s);
                    mListener.onState(mActivityRef.get(), RechargeResult.successOf(result.getResultModel()));
                } else {
                    if (mList != null) {
                        for (int i = 0; i < mList.size(); i++) {
                            consume2(mList.get(i).getPurchaseToken());
                        }
                    }
                }
            }
        });

    }

    /**
     * 消耗
     */
    private void consume2(String purchaseToken) {
        ConsumeParams consumeParams =
                ConsumeParams.newBuilder()
                        .setPurchaseToken(purchaseToken)
                        .build();
        mBillingClient.consumeAsync(consumeParams, new ConsumeResponseListener() {

            @Override
            public void onConsumeResponse(BillingResult billingResult, String s) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    if (mConsume.equals("1")) {
                        recharge();
                    } else {
                        mActivityRef.get().finish();
                    }

                    if (mList != null && mList.size() > 0) {
                        mList.clear();
                    }
                }
            }
        });

    }

    /**
     * 补单操作
     */
    private void queryHistory() {
        mBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP,
                new PurchaseHistoryResponseListener() {
                    @Override
                    public void onPurchaseHistoryResponse(BillingResult billingResult, List<PurchaseHistoryRecord> list) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                                && list != null) {
                            for (final PurchaseHistoryRecord purchase : list) {
                                consume2(purchase.getPurchaseToken());
                            }
                        } else {
                            mActivityRef.get().finish();
                        }
                    }

                });


    }

    /**
     * 获取乐推订单ID
     *
     * @param params 集合
     */
    private void getLTOrderID(Map<String, Object> params) {
        LoginRealizeManager.createOrder(mActivityRef.get(), mProductID, params, new OnRechargeListener() {

            @Override
            public void onState(Activity activity, RechargeResult result) {
                if (result != null) {
                    if (result.getResultModel() != null) {
                        if (result.getResultModel().getData() != null) {
                            if (!result.getResultModel().getResult().equals("NO")) {
                                if (result.getResultModel().getData().getLt_order_id() != null) {
                                    mOrderID = result.getResultModel().getData().getLt_order_id();
                                    recharge();
                                }
                            } else {
                                mListener.onState(mActivityRef.get(),
                                        RechargeResult.failOf(result.getResultModel().getMsg()));
                                mActivityRef.get().finish();
                                activity.finish();
                            }

                        }

                    }
                }
            }

        });
    }

    /**
     * 上传到服务器验证
     */
    private void uploadToServer(final String purchaseToken) {
        LoginRealizeManager.googlePlay(mActivityRef.get(),
                purchaseToken, mOrderID, 1, new OnRechargeListener() {

                    @Override
                    public void onState(Activity activity, RechargeResult result) {
                        if (result != null) {
                            if (result.getResultModel() != null) {
                                consume(purchaseToken, result);
                                mConsume = "2";
                            }

                        }

                    }

                });
    }

    /**
     * 释放
     */
    void release() {
        if (mBillingClient.isReady()) {
            mBillingClient.endConnection();
        }
    }

}

