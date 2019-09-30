/*
 * Copyright 2019 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.eclair.wallet.activities;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import androidx.databinding.DataBindingUtil;
import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.security.GeneralSecurityException;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivitySecuritySettingsBinding;
import fr.acinq.eclair.wallet.fragments.PinDialog;
import fr.acinq.eclair.wallet.utils.Constants;
import fr.acinq.eclair.wallet.utils.WalletUtils;

public class SecuritySettingsActivity extends EclairActivity implements EclairActivity.EncryptSeedCallback {

  private final Logger log = LoggerFactory.getLogger(SecuritySettingsActivity.class);
  private SharedPreferences.OnSharedPreferenceChangeListener securityPrefsListener;
  private ActivitySecuritySettingsBinding mBinding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_security_settings);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);

    mBinding.paymentPinSwitchWrapper.setOnClickListener(view -> {
      final boolean isPinDefined = isPinRequired();
      if (isPinDefined && mBinding.paymentPinSwitch.isChecked()) {
        disablePinSensitiveAction();
      } else if (!isPinDefined && !mBinding.paymentPinSwitch.isChecked()) {
        getApplicationContext().getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, MODE_PRIVATE).edit()
          .putBoolean(Constants.SETTING_ASK_PIN_FOR_SENSITIVE_ACTIONS, true).apply();
      } else {
        mBinding.paymentPinSwitch.setChecked(isPinRequired());
      }
    });

    securityPrefsListener = (sharedPreferences, s) -> mBinding.paymentPinSwitch.setChecked(isPinRequired());
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (checkInit()) {
      mBinding.paymentPinSwitch.setChecked(isPinRequired());
      getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(securityPrefsListener);
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(securityPrefsListener);
  }

  /**
   * Disable PIN verification for sensitive actions. User has to verify the PIN first.
   */
  private void disablePinSensitiveAction() {
    final PinDialog removePinDialog = new PinDialog(SecuritySettingsActivity.this, R.style.FullScreenDialog, new PinDialog.PinDialogCallback() {
      @Override
      public void onPinConfirm(final PinDialog dialog, final String pinValue) {
        if (isPinCorrect(pinValue, dialog)) {
          getApplicationContext().getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, MODE_PRIVATE).edit()
            .putBoolean(Constants.SETTING_ASK_PIN_FOR_SENSITIVE_ACTIONS, false).apply();
        } else {
          Toast.makeText(getApplicationContext(), getString(R.string.security_password_update_failure), Toast.LENGTH_SHORT).show();
        }
      }

      @Override
      public void onPinCancel(final PinDialog dialog) {
      }
    });
    removePinDialog.show();
  }

  public void changePassword(View view) {
    new PinDialog(SecuritySettingsActivity.this, R.style.FullScreenDialog, new PinDialog.PinDialogCallback() {
      @Override
      public void onPinConfirm(final PinDialog dialog, final String pinValue) {
        dialog.dismiss();
        try {
          final File datadir = new File(getFilesDir(), Constants.ECLAIR_DATADIR);
          final byte[] seed = WalletUtils.readSeedFile(datadir, pinValue);
          encryptWallet(SecuritySettingsActivity.this, true, datadir, seed);
        } catch (GeneralSecurityException e) {
          Toast.makeText(getApplicationContext(), R.string.security_password_update_failure, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
          log.error("failed to read seed");
          Toast.makeText(getApplicationContext(), R.string.seed_read_general_failure, Toast.LENGTH_SHORT).show();
        }
      }

      @Override
      public void onPinCancel(PinDialog dialog) {
      }
    }).show();
  }

  @Override
  public void onEncryptSeedFailure(String message) {
    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
  }

  @Override
  public void onEncryptSeedSuccess() {
    Toast.makeText(getApplicationContext(), getString(R.string.security_password_update_success), Toast.LENGTH_SHORT).show();
  }
}
