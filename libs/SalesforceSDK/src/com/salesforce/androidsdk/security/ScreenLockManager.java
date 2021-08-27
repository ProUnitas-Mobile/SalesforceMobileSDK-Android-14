/*
 * Copyright (c) 2021-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.security;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.util.EventsObservable;

import java.util.List;

/**
 * Manages if the app should be locked.
 *
 * @author bpage
 */
public class ScreenLockManager {

    public static final String MOBILE_POLICY_PREF = "mobile_policy";
    public static final String SCREEN_LOCK = "screen_lock";

    private boolean shouldLock = true;

    public void storeMobilePolicyForOrg(UserAccount account, boolean screenLockRequired) {
        Context ctx = SalesforceSDKManager.getInstance().getAppContext();
        SharedPreferences accountSharedPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF
                + account.getOrgLevelFilenameSuffix(), Context.MODE_PRIVATE);
        accountSharedPrefs.edit().putBoolean(SCREEN_LOCK, screenLockRequired).apply();

        SharedPreferences globalPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF, Context.MODE_PRIVATE);
        if (screenLockRequired) {
            globalPrefs.edit().putBoolean(SCREEN_LOCK, true).apply();
        }
    }

    public boolean onResume() {
        boolean locked = shouldLock && readMobilePolicy();
        if (locked) {
            lock();
        }

        // If locked, do nothing - when the app gets unlocked we will be back here.
        return !locked;
    }

    public void onPause() {
        setShouldLock(true);
    }

    public void setShouldLock(boolean shouldLock) {
        this.shouldLock = shouldLock;
    }

    public void reset() {
        Context ctx = SalesforceSDKManager.getInstance().getAppContext();
        SharedPreferences globalPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF, Context.MODE_PRIVATE);
        globalPrefs.edit().remove(SCREEN_LOCK).apply();
    }

    public void cleanUp(UserAccount account) {
        // CleanUp and remove Lock for account.
        Context ctx = SalesforceSDKManager.getInstance().getAppContext();
        SharedPreferences accountPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF
                + account.getOrgLevelFilenameSuffix(), Context.MODE_PRIVATE);
        accountPrefs.edit().remove(SCREEN_LOCK).apply();

        // Determine if any other users still need ScreenLock.
        List<UserAccount> accounts = SalesforceSDKManager.getInstance()
                .getUserAccountManager().getAuthenticatedUsers();
        accounts.remove(account);
        for (UserAccount mAccount : accounts) {
            accountPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF
                    + mAccount.getOrgLevelFilenameSuffix(), Context.MODE_PRIVATE);
            if (accountPrefs.getBoolean(SCREEN_LOCK, false)) {
                return;
            }
        }

        if (!readMobilePolicy()) {
            reset();
        }
    }

    private boolean readMobilePolicy() {
        Context ctx = SalesforceSDKManager.getInstance().getAppContext();
        SharedPreferences sharedPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF, Context.MODE_PRIVATE);
        return sharedPrefs.getBoolean(SCREEN_LOCK, false);
    }

    private void lock() {
        Context ctx = SalesforceSDKManager.getInstance().getAppContext();
        Intent intent = new Intent(ctx, SalesforceSDKManager.getInstance().getScreenLockActivity());
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (ctx instanceof Activity) {
            ((Activity) ctx).startActivityForResult(intent, PasscodeManager.PASSCODE_REQUEST_CODE);
        } else {
            ctx.startActivity(intent);
        }
        EventsObservable.get().notifyEvent(EventsObservable.EventType.AppLocked);
    }
}
