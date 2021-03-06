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

import android.app.Activity;
import android.app.AlertDialog;

import com.youTransactor.uCube.ITaskMonitor;
import com.youTransactor.uCube.TaskEvent;
import com.youTransactor.uCube.Tools;
import com.youTransactor.uCube.log.LogManager;
import com.youTransactor.uCube.payment.task.IAuthorizationTask;
import com.youTransactor.uCube.payment.PaymentContext;

public class AuthorizationTask implements IAuthorizationTask {

    private Activity activity;
    private byte[] authResponse;
    private ITaskMonitor monitor;
    private PaymentContext paymentContext;
    private AlertDialog alertDialog;

    public AuthorizationTask(Activity activity) {
        this.activity = activity;
    }

    @Override
    public byte[] getAuthorizationResponse() {
        return authResponse;
    }

    @Override
    public PaymentContext getContext() {
        return paymentContext;
    }

    @Override
    public void setContext(PaymentContext context) {
        this.paymentContext = context;
    }

    @Override
    public void execute(ITaskMonitor monitor) {
        this.monitor = monitor;

        if (paymentContext.authorizationSecuredTagsValues != null)
            LogManager.d("authorization secured tags " + Tools.bytesToHex(paymentContext.authorizationSecuredTagsValues));

        if (paymentContext.authorizationPlainTagsValues != null) {
            for (Integer tag :
                    paymentContext.authorizationPlainTagsValues.keySet()) {
                LogManager.d("authorization Plain tag : " + tag + " value : " + Tools.bytesToHex(paymentContext.authorizationPlainTagsValues.get(tag)));
            }
        }

        //todo here you can call the host

        activity.runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);

            builder.setCancelable(true);
            builder.setTitle("Authorization response");

            builder.setItems(new String[]{"Approved", "Declined", "Unable to go online"}, (dialog, which) -> {
                dialog.dismiss();
                end(which);
            });

            alertDialog = builder.create();
            alertDialog.show();
        });
    }

    @Override
    public void cancel() {
        if(alertDialog != null && alertDialog.isShowing())
            alertDialog.dismiss();

        new Thread(() -> monitor.handleEvent(TaskEvent.CANCELLED)).start();
    }


    private void end(int choice) {
        switch (choice) {
            case 0:
                this.authResponse = new byte[]{(byte) 0x8A, 0x02, 0x30, 0x30};
                break;

            case 1:
                this.authResponse = new byte[]{(byte) 0x8A, 0x02, 0x30, 0x35};
                break;

            case 2:
                this.authResponse = new byte[]{(byte) 0x8A, 0x02, 0x39, 0x38};
                break;
        }


        new Thread(() -> monitor.handleEvent(TaskEvent.SUCCESS)).start();
    }

}
