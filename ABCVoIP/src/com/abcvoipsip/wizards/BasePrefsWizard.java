/**
 * Copyright (C) 2010-2012 Regis Montoya (aka r3gis - www.r3gis.fr)
 * This file is part of CSipSimple.
 *
 *  CSipSimple is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  If you own a pjsip commercial license you can also redistribute it
 *  and/or modify it under the terms of the GNU Lesser General Public License
 *  as an android library.
 *
 *  CSipSimple is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with CSipSimple.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.abcvoipsip.wizards;

import java.util.List;

import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.abcvoipsip.R;
import com.abcvoipsip.api.SipManager;
import com.abcvoipsip.api.SipProfile;
import com.abcvoipsip.db.DBProvider;
import com.abcvoipsip.models.Filter;
import com.abcvoipsip.ui.account.AccountFilters;
import com.abcvoipsip.ui.prefs.GenericPrefs;
import com.abcvoipsip.utils.Log;
import com.abcvoipsip.utils.PreferencesWrapper;
import com.abcvoipsip.wizards.WizardUtils.WizardInfo;

public class BasePrefsWizard extends GenericPrefs {

	
	
	public static final int SAVE_MENU = Menu.FIRST + 1;
	public static final int TRANSFORM_MENU = Menu.FIRST + 2;
	public static final int FILTERS_MENU = Menu.FIRST + 3;
	public static final int DELETE_MENU = Menu.FIRST + 4;

	public static final int CHOOSE_WIZARD = 0;
	public static final int MODIFY_FILTERS = 1;
	private static final String THIS_FILE = "Base Prefs wizard";

	protected SipProfile account = null;
	private Button saveButton;
	private String wizardId = "";
	private WizardIface wizard = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Get back the concerned account and if any set the current (if not a
		// new account is created)
		Intent intent = getIntent();
		long accountId = intent.getLongExtra(SipProfile.FIELD_ID, SipProfile.INVALID_ID);

		// TODO : ensure this is not null...
		setWizardId(intent.getStringExtra(SipProfile.FIELD_WIZARD));

		account = SipProfile.getProfileFromDbId(this, accountId, DBProvider.ACCOUNT_FULL_PROJECTION);

		super.onCreate(savedInstanceState);

		// Bind buttons to their actions
		Button bt = (Button) findViewById(R.id.cancel_bt);
		bt.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				setResult(RESULT_CANCELED, getIntent());
				finish();
			}
		});

		saveButton = (Button) findViewById(R.id.save_bt);
		saveButton.setEnabled(false);
		saveButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				saveAndFinish();
			}
		});
		wizard.fillLayout(account);
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateDescriptions();
		updateValidation();
	}

	private boolean setWizardId(String wId) {
		if (wizardId == null) {
			return setWizardId(WizardUtils.EXPERT_WIZARD_TAG);
		}

		WizardInfo wizardInfo = WizardUtils.getWizardClass(wId);
		if (wizardInfo == null) {
			if (!wizardId.equals(WizardUtils.EXPERT_WIZARD_TAG)) {
				return setWizardId(WizardUtils.EXPERT_WIZARD_TAG);
			}
			return false;
		}

		try {
			wizard = (WizardIface) wizardInfo.classObject.newInstance();
		} catch (IllegalAccessException e) {
			Log.e(THIS_FILE, "Can't access wizard class", e);
			if (!wizardId.equals(WizardUtils.EXPERT_WIZARD_TAG)) {
				return setWizardId(WizardUtils.EXPERT_WIZARD_TAG);
			}
			return false;
		} catch (InstantiationException e) {
			Log.e(THIS_FILE, "Can't access wizard class", e);
			if (!wizardId.equals(WizardUtils.EXPERT_WIZARD_TAG)) {
				return setWizardId(WizardUtils.EXPERT_WIZARD_TAG);
			}
			return false;
		}
		wizardId = wId;
		wizard.setParent(this);

		return true;
	}

	@Override
	protected void beforeBuildPrefs() {
		// Use our custom wizard view
		setContentView(R.layout.wizard_prefs_base);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		updateDescriptions();
		updateValidation();
	}

	/**
	 * Update validation state of the current activity.
	 * It will check if wizard can be saved and if so 
	 * will enable button
	 */
	public void updateValidation() {
		saveButton.setEnabled(wizard.canSave());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, SAVE_MENU, Menu.NONE, R.string.save).setIcon(android.R.drawable.ic_menu_save);
		if (account.id != SipProfile.INVALID_ID) {
			menu.add(Menu.NONE, TRANSFORM_MENU, Menu.NONE, R.string.choose_wizard).setIcon(android.R.drawable.ic_menu_edit);
			menu.add(Menu.NONE, FILTERS_MENU, Menu.NONE, R.string.filters).setIcon(R.drawable.ic_menu_filter);
			menu.add(Menu.NONE, DELETE_MENU, Menu.NONE, R.string.delete_account).setIcon(android.R.drawable.ic_menu_delete);
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.findItem(SAVE_MENU).setVisible(wizard.canSave());

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case SAVE_MENU:
			saveAndFinish();
			return true;
		case TRANSFORM_MENU:
			startActivityForResult(new Intent(this, WizardChooser.class), CHOOSE_WIZARD);
			return true;
		case DELETE_MENU:
			if (account.id != SipProfile.INVALID_ID) {
				getContentResolver().delete(ContentUris.withAppendedId(SipProfile.ACCOUNT_ID_URI_BASE, account.id), null, null);
				setResult(RESULT_OK, getIntent());
				finish();
			}
			return true;
		case FILTERS_MENU:
			if (account.id != SipProfile.INVALID_ID) {
				Intent it = new Intent(this, AccountFilters.class);
				it.putExtra(SipProfile.FIELD_ID, account.id);
				startActivityForResult(it, MODIFY_FILTERS);
				return true;
			}
			break;
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == CHOOSE_WIZARD && resultCode == RESULT_OK && data != null && data.getExtras() != null) {
			String wizardId = data.getStringExtra(WizardUtils.ID);
			if (wizardId != null) {
				saveAccount(wizardId);
				setResult(RESULT_OK, getIntent());
				finish();
			}
		}
	}

	/**
	 * Save account and end the activity
	 */
	private void saveAndFinish() {
		saveAccount();
		Intent intent = getIntent();
		setResult(RESULT_OK, intent);
		finish();
	}

	/*
	 * Save the account with current wizard id
	 */
	private void saveAccount() {
		saveAccount(wizardId);
	}
	
	/**
	 * Save the account with given wizard id
	 * @param wizardId the wizard to use for account entry
	 */
	private void saveAccount(String wizardId) {
		boolean needRestart = false;

		PreferencesWrapper prefs = new PreferencesWrapper(this);
		account = wizard.buildAccount(account);
		account.wizard = wizardId;
		if (account.id == SipProfile.INVALID_ID) {
			// This account does not exists yet
			wizard.setDefaultParams(prefs);
			Uri uri = getContentResolver().insert(SipProfile.ACCOUNT_URI, account.getDbContentValues());
			
			// After insert, add filters for this wizard 
			account.id = ContentUris.parseId(uri);
			List<Filter> filters = wizard.getDefaultFilters(account);
			if (filters != null) {
				for (Filter filter : filters) {
					// Ensure the correct id if not done by the wizard
					filter.account = (int) account.id;
					getContentResolver().insert(SipManager.FILTER_URI, filter.getDbContentValues());
				}
			}
			// Check if we have to restart
			needRestart = wizard.needRestart();

		} else {
			// TODO : should not be done there but if not we should add an
			// option to re-apply default params
			wizard.setDefaultParams(prefs);
			getContentResolver().update(ContentUris.withAppendedId(SipProfile.ACCOUNT_ID_URI_BASE, account.id), account.getDbContentValues(), null, null);
		}

		// Mainly if global preferences were changed, we have to restart sip stack 
		if (needRestart) {
			Intent intent = new Intent(SipManager.ACTION_SIP_REQUEST_RESTART);
			sendBroadcast(intent);
		}
	}

	@Override
	protected int getXmlPreferences() {
		return wizard.getBasePreferenceResource();
	}

	@Override
	protected void updateDescriptions() {
		wizard.updateDescriptions();
	}

	@Override
	protected String getDefaultFieldSummary(String fieldName) {
		return wizard.getDefaultFieldSummary(fieldName);
	}

}
