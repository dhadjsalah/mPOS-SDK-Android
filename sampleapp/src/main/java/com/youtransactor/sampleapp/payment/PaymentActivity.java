/*
 * Copyright (C) 2011-2020, YouTransactor. All Rights Reserved.
 * <p/>
 * Use of this product is contingent on the existence of an executed license
 * agreement between YouTransactor or one of its sublicensee, and your
 * organization, which specifies this software's terms of use. This software
 * is here defined as YouTransactor Intellectual Property for the purposes
 * of determining terms of use as defined within the license agreement.
 */
package com.youtransactor.sampleapp.payment;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.youTransactor.uCube.TLV;
import com.youTransactor.uCube.Tools;
import com.youTransactor.uCube.api.UCubeAPI;
import com.youTransactor.uCube.api.UCubeLibPaymentServiceListener;
import com.youTransactor.uCube.api.UCubePaymentRequest;
import com.youTransactor.uCube.payment.CardReaderType;
import com.youTransactor.uCube.payment.Currency;
import com.youTransactor.uCube.payment.EMVPaymentStateMachine;
import com.youTransactor.uCube.payment.PaymentContext;
import com.youTransactor.uCube.payment.PaymentState;
import com.youTransactor.uCube.payment.TransactionType;
import com.youTransactor.uCube.rpc.Constants;
import com.youtransactor.sampleapp.R;
import com.youtransactor.sampleapp.UIUtils;
import com.youtransactor.sampleapp.YTProduct;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import static com.youtransactor.sampleapp.SetupActivity.YT_PRODUCT;

public class PaymentActivity extends AppCompatActivity {

    public static final String TAG = PaymentActivity.class.getName();

    /* Device */
    private YTProduct ytProduct = YTProduct.uCubeTouch;

    private Button doPaymentBtn;
    private Button cancelPaymentBtn;
    private EditText cardWaitTimeoutFld;
    private Spinner trxTypeChoice;
    private EditText amountFld;
    private Spinner currencyChooser;
    private Switch forceOnlinePINBtn;
    private Switch forceAuthorisationBtn;
    private Switch amountSrcSwitch;
    private Switch contactOnlySwitch;
    private Switch displayResultSwitch;
    private TextView trxResultFld;
    private LinearLayout progressSection;
    private TextView progressMessage;
    private ProgressBar progressBar;
    int progress = 0;
    private static final int STEP = 10;

    EMVPaymentStateMachine emvPaymentStateMachine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_payment);

        if (getIntent() != null) {
            if (getIntent().hasExtra(YT_PRODUCT))
                ytProduct = YTProduct.valueOf(getIntent().getStringExtra(YT_PRODUCT));
        }

        initView();
    }

    private void initView() {

        doPaymentBtn = findViewById(R.id.doPaymentBtn);
        cancelPaymentBtn = findViewById(R.id.cancelPaymentBtn);
        cardWaitTimeoutFld = findViewById(R.id.cardWaitTimeoutFld);
        trxResultFld = findViewById(R.id.trxResultFld);
        trxTypeChoice = findViewById(R.id.trxTypeChoice);
        amountFld = findViewById(R.id.amountFld);
        currencyChooser = findViewById(R.id.currencyChooser);
        forceOnlinePINBtn = findViewById(R.id.forceOnlinePINBtn);
        amountSrcSwitch = findViewById(R.id.amountSrcBtn);
        contactOnlySwitch = findViewById(R.id.contactOnlyBtn);
        forceAuthorisationBtn = findViewById(R.id.forceAuthorisationBtn);
        displayResultSwitch = findViewById(R.id.displayResultOnUCubeBtn);
        progressSection = findViewById(R.id.progress_section);
        progressMessage = findViewById(R.id.progress_msg);
        progressBar = findViewById(R.id.progress_bar);

        amountFld.setText(getString(R.string._1_00));
        trxTypeChoice.setAdapter(new TransactionTypeAdapter());

        final CurrencyAdapter currencyAdapter = new CurrencyAdapter();
        currencyAdapter.add(UCubePaymentRequest.CURRENCY_EUR);
        currencyAdapter.add(UCubePaymentRequest.CURRENCY_USD);

        currencyChooser.setAdapter(currencyAdapter);
        currencyChooser.setSelection(0);

        amountSrcSwitch.setOnClickListener(v -> amountFld.setEnabled(!amountSrcSwitch.isChecked()));

        doPaymentBtn.setOnClickListener(v -> startPayment());

        cancelPaymentBtn.setOnClickListener(v -> cancelPayment());
    }

    private void startPayment() {

        UCubePaymentRequest uCubePaymentRequest = preparePaymentRequest();

        //update UI
        String msg;
        if (uCubePaymentRequest.getAmount() > 0) {
            msg = getString(
                    R.string.payment_progress_with_amount,
                    uCubePaymentRequest.getAmount(),
                    uCubePaymentRequest.getCurrency().getLabel()
            );
        } else {
            msg = getString(
                    R.string.payment_progress_without_amount,
                    uCubePaymentRequest.getCurrency().getLabel()
            );
        }

        doPaymentBtn.setVisibility(View.GONE);
        cancelPaymentBtn.setVisibility(View.VISIBLE);
        trxResultFld.setText("");
        progressSection.setVisibility(View.VISIBLE);
        progressMessage.setText(msg);
        progress = 0;
        progressBar.setProgress(progress);

        try {
            emvPaymentStateMachine = UCubeAPI.pay(this, uCubePaymentRequest,
                    new UCubeLibPaymentServiceListener() {

                        @Override
                        public void onProgress(PaymentState state, PaymentContext context) {
                            // todo No RPC call here

                            Log.d(TAG, " Payment progress : " + state);

                            displayProgress(state);

                            if (state == PaymentState.KSN_AVAILABLE) {
                                Log.d(TAG, "KSN : " + Arrays.toString(context.sredKsn));
                                return;
                            }

                            if (state == PaymentState.SMC_PROCESS_TRANSACTION) {
                                Log.d(TAG, "init data : " + Arrays.toString(context.transactionInitData));
                            }
                        }

                        @Override
                        public void onFinish(PaymentContext context) {
                            if(context == null) {
                                UIUtils.showMessageDialog(PaymentActivity.this, getString(R.string.payment_failed));
                                return;
                            }

                            Log.d(TAG, "payment finish status : " + context.paymentStatus);

                            //update UI
                            doPaymentBtn.setVisibility(View.VISIBLE);
                            cancelPaymentBtn.setVisibility(View.GONE);
                            progressSection.setVisibility(View.INVISIBLE);

                            parsePaymentResponse(context);

                        }
                    });

        } catch (Exception e) {

            e.printStackTrace();

            //update UI
            doPaymentBtn.setVisibility(View.VISIBLE);
            cancelPaymentBtn.setVisibility(View.GONE);
            progressSection.setVisibility(View.INVISIBLE);
        }
    }

    private UCubePaymentRequest preparePaymentRequest() {
        int timeout = Integer.parseInt(cardWaitTimeoutFld.getText().toString());

        Currency currency = (Currency) currencyChooser.getSelectedItem();

        TransactionType trxType = (TransactionType) trxTypeChoice.getSelectedItem();

        boolean forceOnlinePin = forceOnlinePINBtn.isChecked(); // only for NFC & MSR

        boolean forceAuthorisation = forceAuthorisationBtn.isChecked();

        boolean contactOnly = contactOnlySwitch.isChecked();

        boolean displayResultOnUCube = displayResultSwitch.isChecked();

        double amount = -1;

        if (!amountSrcSwitch.isChecked()) {
            try {
                amount = Double.parseDouble(amountFld.getText().toString());
            } catch (Exception e) {
                amountSrcSwitch.setChecked(true);
            }
        }

        ResourceBundle msgBundle = null;
        Bundle altMsgBundle = null;

        try {
            msgBundle = new PropertyResourceBundle(getResources().openRawResource(R.raw.ucube_strings));

        } catch (IOException ignore) {
            altMsgBundle = new Bundle();

            // common messages to nfc & smc transaction
            altMsgBundle.putString("LBL_prepare_context", "Preparing context");
            altMsgBundle.putString("LBL_authorization", "Authorization processing");

            // smc messages
            altMsgBundle.putString("LBL_smc_initialization", "initialization processing");
            altMsgBundle.putString("LBL_smc_risk_management", "risk management processing");
            altMsgBundle.putString("LBL_smc_finalization", "finalization processing");
            altMsgBundle.putString("LBL_smc_remove_card", "Remove card, please");

            //nfc messages
            altMsgBundle.putString("LBL_nfc_complete", "complete processing");
            altMsgBundle.putString("LBL_wait_online_pin_process", "online pin processing");
            altMsgBundle.putString("LBL_wait_card", "Insert card");

            /*  Payment status messages*/
            altMsgBundle.putString("LBL_approved", "Approved"); // returned by the application
            altMsgBundle.putString("LBL_declined", "Declined"); // returned by the application
            altMsgBundle.putString("LBL_unsupported_card", "Unsupported card"); // returned by the application
            altMsgBundle.putString("LBL_cancelled", "Cancelled"); // terminal or application
            altMsgBundle.putString("LBL_error", "Error"); // returned by the application
            altMsgBundle.putString("LBL_no_card_detected", "No card detected");  // returned by the application
            altMsgBundle.putString("LBL_wrong_activated_reader", "wrong activated reader");  // returned by the application
            // nfc specific error status
            altMsgBundle.putString("LBL_try_other_interface", "Try other interface"); // returned by terminal
            altMsgBundle.putString("LBL_end_application", "End application"); // returned by terminal
            altMsgBundle.putString("LBL_failed", "Failed"); // returned by terminal
            altMsgBundle.putString("LBL_wrong_nfc_outcome", "wrong activated reader"); // returned by the application
            // smc specific error status
            altMsgBundle.putString("LBL_wrong_cryptogram_value", "wrong cryptogram"); // returned by the application
            altMsgBundle.putString("LBL_missing_required_cryptogram", "missing required cryptogram"); // returned by the application


            // Global configuration of messages layout
            altMsgBundle.putString("GLOBAL_LBL_xposition", "FF");
            altMsgBundle.putString("GLOBAL_LBL_yposition", "0C");
            altMsgBundle.putString("GLOBAL_LBL_font_id", "00");
        }

        List<CardReaderType> readerList = new ArrayList<>();

        readerList.add(CardReaderType.ICC);

        if (!contactOnly)
            readerList.add(CardReaderType.NFC);

        return new UCubePaymentRequest.Builder()
                .setAmount(amount)
                .setCurrency(currency)
                .setTransactionDate(new Date())
                .setDisplayResult(displayResultOnUCube)
                .setReaderList(readerList)
                .setForceOnlinePin(forceOnlinePin)
                .setForceAuthorisation(forceAuthorisation)
                .setAuthorizationTask(new AuthorizationTask(this))
                .setRiskManagementTask(new RiskManagementTask(this))
                .setCardWaitTimeout(timeout)
                .setTransactionType(trxType)
                .setSystemFailureInfo(false)
                .setSystemFailureInfo2(false)
                .setAltMsgBundle(altMsgBundle)
                .setMsgBundle(msgBundle)
                .setPreferredLanguageList(Collections.singletonList("en")) // each language represented by 2 alphabetical characters according to ISO 639
                .setAuthorizationPlainTags(0x50, 0x8A, 0x8F, 0x9F09, 0x9F17, 0x9F35, 0x5F28, 0x9F0A)
                .setAuthorizationSecuredTags(0x56, 0x57, 0x5A, 0x5F34, 0x5F20, 0x5F24, 0x5F30,
                        0x9F0B, 0x9F6B, 0x9F08, 0x9F68, 0x5F2C, 0x5F2E)
                .setFinalizationSecuredTags(0x56, 0x57, 0x5A, 0x5F34, 0x5F20, 0x5F24, 0x5F30,
                        0x9F0B, 0x9F6B, 0x9F08, 0x9F68, 0x5F2C, 0x5F2E)
                .setFinalizationPlainTags(0x50, 0x8A, 0x8F, 0x9F09, 0x9F17, 0x9F35, 0x5F28, 0x9F0A)
                .build();
    }

    private void displayProgress(PaymentState state) {

        String msg = state.name() + "...";

        progress = progress + STEP;
        if (progress > 200)
            progress = 200;

        progressBar.setProgress(progress);

        progressMessage.setText(msg);
    }

    private void parsePaymentResponse(@NonNull PaymentContext context) {
        Log.d(TAG, "Payment status : " + context.paymentStatus);

        trxResultFld.setText(context.paymentStatus.name());

        /* uCube info */
        byte[] ucubeFirmware = TLV.parse(context.uCubeInfos).get(Constants.TAG_FIRMWARE_VERSION);
        if (ucubeFirmware != null)
            Log.d(TAG, "uCube firmware version: " + Tools.parseVersion(ucubeFirmware));

        Log.d(TAG, "Used Interface: " + CardReaderType.getLabel(context.activatedReader));

        Log.d(TAG, "amount: " + context.amount);
        Log.d(TAG, "currency: " + context.currency.getLabel());
        Log.d(TAG, "tx date: " + context.transactionDate);
        Log.d(TAG, "tx type: " + context.transactionType.getLabel());

        if (context.selectedApplication != null) {
            Log.d(TAG, "app ID: " + context.selectedApplication.getLabel());
            Log.d(TAG, "app version: " + context.applicationVersion);
        }

        Log.d(TAG, "system failure log1: " + Tools.bytesToHex(context.systemFailureInfo));
        Log.d(TAG, "system failure log2: " + Tools.bytesToHex(context.systemFailureInfo2));

        if (context.finalizationPlainTagsValues != null) {

            for (Integer tag : context.finalizationPlainTagsValues.keySet())
                Log.d(TAG, String.format("Plain Tag : 0x%x : %s", tag, Tools.bytesToHex(context.finalizationPlainTagsValues.get(tag))));

        }
        if (context.finalizationSecuredTagsValues != null)
            Log.d(TAG, "secure tag block: " + Tools.bytesToHex(context.finalizationSecuredTagsValues));

    }

    private void cancelPayment() {
        Log.d(TAG, "Try to cancel current Payment");

        if (emvPaymentStateMachine != null && emvPaymentStateMachine.isRunning()) {
            emvPaymentStateMachine.cancel();
        }
    }
}
