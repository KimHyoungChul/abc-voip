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

package com.abcvoipsip.service;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.CallLog;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;

import com.abcvoipsip.R;
import com.abcvoipsip.api.SipCallSession;
import com.abcvoipsip.api.SipManager;
import com.abcvoipsip.api.SipMessage;
import com.abcvoipsip.api.SipProfile;
import com.abcvoipsip.api.SipProfileState;
import com.abcvoipsip.api.SipUri;
import com.abcvoipsip.utils.CustomDistribution;
import com.abcvoipsip.utils.Log;
import com.abcvoipsip.widgets.RegistrationNotification;

public class SipNotifications {

	private final NotificationManager notificationManager;
	private final Context context;
	private Notification inCallNotification;
	private Notification missedCallNotification;
	private Notification messageNotification;
	private Notification messageVoicemail;

	public static final int REGISTER_NOTIF_ID = 1;
	public static final int CALL_NOTIF_ID = REGISTER_NOTIF_ID + 1;
	public static final int CALLLOG_NOTIF_ID = REGISTER_NOTIF_ID + 2;
	public static final int MESSAGE_NOTIF_ID = REGISTER_NOTIF_ID + 3;
	public static final int VOICEMAIL_NOTIF_ID = REGISTER_NOTIF_ID + 4;
	public static final int REGISTER_FAILED_NOTIF_ID = REGISTER_NOTIF_ID + 5;

	private static boolean isInit = false;

	public SipNotifications(Context aContext) {
		context = aContext;
		notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		if (!isInit) {
			cancelAll();
			cancelCalls();
			isInit = true;
		}
	}

	// Foreground api

	private static final Class<?>[] SET_FG_SIG = new Class[] { boolean.class };
	private static final Class<?>[] START_FG_SIG = new Class[] { int.class, Notification.class };
	private static final Class<?>[] STOP_FG_SIG = new Class[] { boolean.class };
	private static final String THIS_FILE = "Notifications";

	private Method mSetForeground;
	private Method mStartForeground;
	private Method mStopForeground;
	private Object[] mSetForegroundArgs = new Object[1];
	private Object[] mStartForegroundArgs = new Object[2];
	private Object[] mStopForegroundArgs = new Object[1];

	private void invokeMethod(Method method, Object[] args) {
		try {
			method.invoke(context, args);
		} catch (InvocationTargetException e) {
			// Should not happen.
			Log.w(THIS_FILE, "Unable to invoke method", e);
		} catch (IllegalAccessException e) {
			// Should not happen.
			Log.w(THIS_FILE, "Unable to invoke method", e);
		}
	}

	/**
	 * This is a wrapper around the new startForeground method, using the older
	 * APIs if it is not available.
	 */
	private void startForegroundCompat(int id, Notification notification) {
		// If we have the new startForeground API, then use it.
		if (mStartForeground != null) {
			mStartForegroundArgs[0] = Integer.valueOf(id);
			mStartForegroundArgs[1] = notification;
			invokeMethod(mStartForeground, mStartForegroundArgs);
			return;
		}

		// Fall back on the old API.
		mSetForegroundArgs[0] = Boolean.TRUE;
		invokeMethod(mSetForeground, mSetForegroundArgs);
		notificationManager.notify(id, notification);
	}

	/**
	 * This is a wrapper around the new stopForeground method, using the older
	 * APIs if it is not available.
	 */
	private void stopForegroundCompat(int id) {
		// If we have the new stopForeground API, then use it.
		if (mStopForeground != null) {
			mStopForegroundArgs[0] = Boolean.TRUE;
			invokeMethod(mStopForeground, mStopForegroundArgs);
			return;
		}

		// Fall back on the old API. Note to cancel BEFORE changing the
		// foreground state, since we could be killed at that point.
		notificationManager.cancel(id);
		mSetForegroundArgs[0] = Boolean.FALSE;
		invokeMethod(mSetForeground, mSetForegroundArgs);
	}

	private boolean isServiceWrapper = false;

	public void onServiceCreate() {
		try {
			mStartForeground = context.getClass().getMethod("startForeground", START_FG_SIG);
			mStopForeground = context.getClass().getMethod("stopForeground", STOP_FG_SIG);
			isServiceWrapper = true;
			return;
		} catch (NoSuchMethodException e) {
			// Running on an older platform.
			mStartForeground = mStopForeground = null;
		}
		try {
			mSetForeground = context.getClass().getMethod("setForeground", SET_FG_SIG);
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException("OS doesn't have Service.startForeground OR Service.setForeground!");
		}
		isServiceWrapper = true;
	}

	public void onServiceDestroy() {
		// Make sure our notification is gone.
		cancelAll();
		cancelCalls();
	}

	// Announces

	// Register
	public synchronized void notifyRegisteredAccounts(ArrayList<SipProfileState> activeAccountsInfos, boolean showNumbers) {
		if (!isServiceWrapper) {
			Log.e(THIS_FILE, "Trying to create a service notification from outside the service");
			return;
		}
		
		int icon = R.drawable.ic_stat_sipok;
		CharSequence tickerText = context.getString(R.string.service_ticker_registered_text);
		long when = System.currentTimeMillis();

		Notification notification = new Notification(icon, tickerText, when);

		Intent notificationIntent = new Intent(SipManager.ACTION_SIP_DIALER);
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		RegistrationNotification contentView = new RegistrationNotification(context.getPackageName());
		contentView.clearRegistrations();
		contentView.addAccountInfos(context, activeAccountsInfos);

		// notification.setLatestEventInfo(context, contentTitle,
		// contentText, contentIntent);
		notification.contentIntent = contentIntent;
		notification.contentView = contentView;
		notification.flags = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR | Notification.FLAG_ONLY_ALERT_ONCE;
		if (showNumbers) {
			notification.number = activeAccountsInfos.size();
		}

		stopForegroundCompat(REGISTER_FAILED_NOTIF_ID);
		stopForegroundCompat(REGISTER_NOTIF_ID);
		
		startForegroundCompat(REGISTER_NOTIF_ID, notification);
	}

	public synchronized void notifyFailedRegisteredAccounts() {
		if (!isServiceWrapper) {
			Log.e(THIS_FILE, "Trying to create a service notification from outside the service");
			return;
		}
		
		int icon = R.drawable.ic_stat_sipok;
		CharSequence tickerText = context.getString(R.string.not_registered_text);
		long when = System.currentTimeMillis();

		Notification notification = new Notification(icon, tickerText, when);

		Intent notificationIntent = new Intent(SipManager.ACTION_SIP_DIALER);
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		
		RegistrationNotification contentView = new RegistrationNotification(context.getPackageName());
		contentView.clearRegistrations();
		
		//notification.setLatestEventInfo(context, "", "", contentIntent);
		
		notification.contentIntent = contentIntent;
		notification.contentView = contentView;
		notification.flags = Notification.FLAG_ONLY_ALERT_ONCE;
		
		stopForegroundCompat(REGISTER_FAILED_NOTIF_ID);
		stopForegroundCompat(REGISTER_NOTIF_ID);
		
		
		
		startForegroundCompat(REGISTER_FAILED_NOTIF_ID, notification);
	}
	
	// Calls
	public void showNotificationForCall(SipCallSession currentCallInfo2) {
		// This is the pending call notification
		// int icon = R.drawable.ic_incall_ongoing;
		int icon = android.R.drawable.stat_sys_phone_call;
		CharSequence tickerText = context.getText(R.string.ongoing_call);
		long when = System.currentTimeMillis();

		if (inCallNotification == null) {
			inCallNotification = new Notification(icon, tickerText, when);
			inCallNotification.flags = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
			// notification.flags = Notification.FLAG_FOREGROUND_SERVICE;
		}

		Intent notificationIntent = new Intent(SipManager.ACTION_SIP_CALL_UI);
		notificationIntent.putExtra(SipManager.EXTRA_CALL_INFO, currentCallInfo2);
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

		inCallNotification.setLatestEventInfo(context, context.getText(R.string.ongoing_call), currentCallInfo2.getRemoteContact(), contentIntent);

		notificationManager.notify(CALL_NOTIF_ID, inCallNotification);
	}

	public void showNotificationForMissedCall(ContentValues callLog) {
		int icon = android.R.drawable.stat_notify_missed_call;
		CharSequence tickerText = context.getText(R.string.missed_call);
		long when = System.currentTimeMillis();

		if (missedCallNotification == null) {
			missedCallNotification = new Notification(icon, tickerText, when);
			missedCallNotification.flags = Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_SHOW_LIGHTS | Notification.FLAG_AUTO_CANCEL;
			missedCallNotification.defaults |= Notification.DEFAULT_LIGHTS;
			missedCallNotification.defaults |= Notification.DEFAULT_SOUND;
		}

		Intent notificationIntent = new Intent(SipManager.ACTION_SIP_CALLLOG);
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

		missedCallNotification.setLatestEventInfo(context, context.getText(R.string.missed_call), callLog.getAsString(CallLog.Calls.NUMBER), contentIntent);

		notificationManager.notify(CALLLOG_NOTIF_ID, missedCallNotification);
	}

	public void showNotificationForMessage(SipMessage msg) {
		if (!CustomDistribution.supportMessaging()) {
			return;
		}
		// CharSequence tickerText = context.getText(R.string.instance_message);
		if (!msg.getFrom().equalsIgnoreCase(viewingRemoteFrom)) {
			String from = msg.getDisplayName();
			CharSequence tickerText = buildTickerMessage(context, from, msg.getBody());

			if (messageNotification == null) {
				messageNotification = new Notification(SipUri.isPhoneNumber(from) ? R.drawable.stat_notify_sms : android.R.drawable.stat_notify_chat, tickerText,
						System.currentTimeMillis());
				messageNotification.flags = Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_SHOW_LIGHTS | Notification.FLAG_AUTO_CANCEL;
				messageNotification.defaults |= Notification.DEFAULT_SOUND;
				messageNotification.defaults |= Notification.DEFAULT_LIGHTS;
			}

			Intent notificationIntent = new Intent(SipManager.ACTION_SIP_MESSAGES);
			notificationIntent.putExtra(SipMessage.FIELD_FROM, msg.getFrom());
			notificationIntent.putExtra(SipMessage.FIELD_BODY, msg.getBody());
			notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

			messageNotification.setLatestEventInfo(context, from, msg.getBody(), contentIntent);
			notificationManager.notify(MESSAGE_NOTIF_ID, messageNotification);
		}
	}

	public void showNotificationForVoiceMail(SipProfile acc, int numberOfMessages) {
		if (messageVoicemail == null) {
			messageVoicemail = new Notification(android.R.drawable.stat_notify_voicemail, context.getString(R.string.voice_mail), System.currentTimeMillis());
			messageVoicemail.flags = Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_SHOW_LIGHTS | Notification.FLAG_AUTO_CANCEL;
			messageVoicemail.defaults |= Notification.DEFAULT_SOUND;
			messageVoicemail.defaults |= Notification.DEFAULT_LIGHTS;
		}

		Intent notificationIntent = new Intent(Intent.ACTION_CALL);
		if(acc != null) {
    		notificationIntent.setData(Uri.fromParts("csip", acc.vm_nbr + "@" + acc.getDefaultDomain(), null));
			notificationIntent.putExtra(SipProfile.FIELD_ACC_ID, acc.id);
		}
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

		String messageText = "";
		if (acc != null) {
			messageText += acc.getProfileName() + " : ";
		}
		messageText += Integer.toString(numberOfMessages);

		messageVoicemail.setLatestEventInfo(context, context.getString(R.string.voice_mail), messageText, contentIntent);
		notificationManager.notify(VOICEMAIL_NOTIF_ID, messageVoicemail);
	}

	private static String viewingRemoteFrom = null;

	public void setViewingMessageFrom(String remoteFrom) {
		viewingRemoteFrom = remoteFrom;
	}

	protected static CharSequence buildTickerMessage(Context context, String address, String body) {
		String displayAddress = address;

		StringBuilder buf = new StringBuilder(displayAddress == null ? "" : displayAddress.replace('\n', ' ').replace('\r', ' '));
		buf.append(':').append(' ');

		int offset = buf.length();

		if (!TextUtils.isEmpty(body)) {
			body = body.replace('\n', ' ').replace('\r', ' ');
			buf.append(body);
		}

		SpannableString spanText = new SpannableString(buf.toString());
		spanText.setSpan(new StyleSpan(Typeface.BOLD), 0, offset, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

		return spanText;
	}

	// Cancels
	public final void cancelRegisters() {
		if (!isServiceWrapper) {
			Log.e(THIS_FILE, "Trying to cancel a service notification from outside the service");
			return;
		}
		stopForegroundCompat(REGISTER_NOTIF_ID);
	}

	public final void cancelCalls() {
		notificationManager.cancel(CALL_NOTIF_ID);
	}

	public final void cancelMissedCalls() {
		notificationManager.cancel(CALLLOG_NOTIF_ID);
	}

	public final void cancelMessages() {
		notificationManager.cancel(MESSAGE_NOTIF_ID);
	}

	public final void cancelVoicemails() {
		notificationManager.cancel(VOICEMAIL_NOTIF_ID);
	}

	public final void cancelAll() {
		// Do not cancel calls notification since it's possible that there is
		// still an ongoing call.
		if (isServiceWrapper) {
			cancelRegisters();
		}
		cancelMessages();
		cancelMissedCalls();
		cancelVoicemails();
	}

}
