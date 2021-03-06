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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;

import com.abcvoipsip.R;
import com.abcvoipsip.api.SipProfile;
import com.abcvoipsip.utils.CustomDistribution;
import com.abcvoipsip.wizards.impl.*;


public class WizardUtils {
	
	
	public static class WizardInfo {
		public String label;
		public String id;
		public int icon;
		public int priority=99;
		public Locale[] countries;
		public boolean isGeneric = false;
		public boolean isWorld = false;
		public Class<?> classObject;
		
		public WizardInfo(String aId, String aLabel, int aIcon, int aPriority, Locale[] aCountries, boolean aIsGeneric, boolean aIsWorld, Class<?> aClassObject) {
			id = aId;
			label = aLabel;
			icon = aIcon;
			priority = aPriority;
			countries = aCountries;
			isGeneric = aIsGeneric;
			isWorld = aIsWorld;
			classObject = aClassObject;
		}
	};
	
	private static boolean initDone = false;
	
    public static final String LABEL = "LABEL";
    public static final String ICON = "ICON";
    public static final String ID = "ID";
    public static final String LANG_DISPLAY = "DISPLAY";
    public static final String PRIORITY = "PRIORITY";
    public static final String PRIORITY_INT = "PRIORITY_INT";
    
    public static final String EXPERT_WIZARD_TAG = "EXPERT";
    public static final String LOCAL_WIZARD_TAG = "LOCAL";
    
    
    
    private static HashMap<String, WizardInfo> WIZARDS_DICT;
    
    private static class WizardPrioComparator implements Comparator<Map<String, Object>> {
		

		@Override
		public int compare(Map<String, Object> infos1, Map<String, Object> infos2) {
			if (infos1 != null && infos2 != null) {
			    if((Boolean) infos1.get(PRIORITY_INT)) {
    				Integer w1 = (Integer) infos1.get(PRIORITY);
    				Integer w2 = (Integer) infos2.get(PRIORITY);
    				//Log.d(THIS_FILE, "Compare : "+w1+ " vs "+w2);
    				if (w1 > w2) {
    					return -1;
    				}
    				if (w1 < w2) {
    					return 1;
    				}
			    }else {
			        String name1 = (String) infos1.get(LABEL);
			        String name2 = (String) infos2.get(LABEL);
			        return name1.compareToIgnoreCase(name2);
			    }
			}
			return 0;
		}
    }
    
    private static Locale locale(String isoCode) {
    	String[] codes = isoCode.split("_");
    	if(codes.length == 2) {
    		return new Locale(codes[0].toLowerCase(), codes[1].toUpperCase());
    	}else if(codes.length == 1){
    		return new Locale(codes[0].toLowerCase());
    	}
    	Log.e("WizardUtils", "Invalid locale "+isoCode);
    	return null;
    }
    
	
    /**
     * Initialize wizards list
     */
	private static void initWizards() {
		WIZARDS_DICT = new HashMap<String, WizardInfo>();
		
		//Generic
		WIZARDS_DICT.put("BASIC", new WizardInfo("BASIC", "Basic", 
				R.drawable.ic_wizard_basic, 50, 
				new Locale[] {}, true, false, 
				Basic.class));
		WIZARDS_DICT.put("ADVANCED", new WizardInfo("ADVANCED", "Advanced", 
				R.drawable.ic_wizard_advanced, 10, 
				new Locale[] {}, true, false, 
				Advanced.class));
		WIZARDS_DICT.put(WizardUtils.EXPERT_WIZARD_TAG, new WizardInfo(WizardUtils.EXPERT_WIZARD_TAG, "Expert", 
				R.drawable.ic_wizard_expert, 5, 
				new Locale[] {}, true, false, 
				Expert.class));
		WIZARDS_DICT.put(WizardUtils.LOCAL_WIZARD_TAG, new WizardInfo(WizardUtils.LOCAL_WIZARD_TAG, "Local", 
				R.drawable.ic_wizard_expert, 1, 
				new Locale[] {}, true, false, 
				Local.class));
		
		if(CustomDistribution.distributionWantsOtherProviders()) {

            WIZARDS_DICT.put("ABCVOIP", new WizardInfo("ABCVOIP", "ABC-VoIP", 
                    R.drawable.ic_wizard_abcvoip, 18, 
                    new Locale[]{  }, false, true, 
                    AbcVoip.class));
            
		} else {
			WizardInfo info = CustomDistribution.getCustomDistributionWizard();
			WIZARDS_DICT.put(info.id, info);
		}
		initDone = true;
	}
	
	private static Map<String, Object> wizardInfoToMap(WizardInfo infos, boolean usePriorityInt) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put(LABEL, infos.label);
		map.put(ID, infos.id);
		map.put(ICON, infos.icon);
		map.put(PRIORITY, infos.priority);
		map.put(PRIORITY_INT, usePriorityInt);
		return map;
	}
	
    
	//Ok, what could have be done is declaring an interface but not able with static fields
	// I'll later check whether this is more interesting to declare an interface or an info class
	// used to declare wizards
	public static WizardInfo getWizardClassInfos(Class<?> wizard) {
		Method method;
		try {
			method = wizard.getMethod("getWizardInfo", (Class[]) null);
			return (WizardInfo) method.invoke(null, (Object[]) null);
		} catch (Exception e) {
			//Generic catch : we are not interested in more details
			e.printStackTrace();
		} 
		return null;
	}
   
	public static HashMap<String, WizardInfo> getWizardsList(){
		if(!initDone){
			initWizards();
		}
		return WIZARDS_DICT;
	}
	
	
	public static WizardInfo getWizardClass(String wizardId) {
		if(!initDone){
			initWizards();
		}
		return WIZARDS_DICT.get(wizardId);
	}
	
	public static int getWizardIconRes(String wizardId) {
		// Update account image
		WizardInfo wizard_infos = WizardUtils.getWizardClass(wizardId);
		if (wizard_infos != null) {
			if(!wizard_infos.isGeneric) {
				return wizard_infos.icon;
			}
		}
		return R.drawable.ic_launcher_phone;
	}
	

	public static Bitmap getWizardBitmap(Context ctxt, SipProfile account) {
		if(account.icon == null) {
			Resources r = ctxt.getResources();
			BitmapDrawable bd = ((BitmapDrawable) r.getDrawable(WizardUtils.getWizardIconRes(account.wizard)));
			account.icon = bd.getBitmap();
		}
		return account.icon;
	}


	public static ArrayList<HashMap<String, String>> getWizardsGroups(Context context) {
		ArrayList<HashMap<String, String>> result = new ArrayList<HashMap<String, String>>();
		HashMap<String, String> m;
		
		//Local
		m = new HashMap<String, String>();
		
	//	m.put("lang", Locale.getDefault().getCountry());
		m.put(LANG_DISPLAY, Locale.getDefault().getDisplayCountry());
		result.add(m);
		
		//Generic
		m = new HashMap<String, String>();
	//	m.put("lang", "generic");
		m.put(LANG_DISPLAY, context.getString(R.string.generic_wizards_text));
		result.add(m);
		
		if(CustomDistribution.distributionWantsOtherProviders()) {
			//World
			m = new HashMap<String, String>();
		//	m.put("lang", "world");
			m.put(LANG_DISPLAY, context.getString(R.string.world_wide_providers_text));
			result.add(m);
			
			//Others
			m = new HashMap<String, String>();
		//	m.put("lang", "others");
			m.put(LANG_DISPLAY, context.getString(R.string.other_country_providers_text));
			result.add(m);
		}
		
		return result;
	}


	public static ArrayList<ArrayList<Map<String, Object>>> getWizardsGroupedList() {
		ArrayList<Map<String, Object>> locale_list = new ArrayList<Map<String, Object>>();
		ArrayList<Map<String, Object>> generic_list = new ArrayList<Map<String, Object>>();
		ArrayList<Map<String, Object>> world_list = new ArrayList<Map<String, Object>>();
		ArrayList<Map<String, Object>> others_list = new ArrayList<Map<String, Object>>();
		
		Set<Entry<String, WizardInfo>> wizards = getWizardsList().entrySet();
		for( Entry<String, WizardInfo> wizard : wizards) {
			boolean found = false;
			
			for (Locale country : wizard.getValue().countries) {
				if(country != null) {
					if(country.getCountry().equals(Locale.getDefault().getCountry())) {
						found = true;
						locale_list.add(wizardInfoToMap(wizard.getValue(), true));
						break;
					}else if(country.getCountry().equalsIgnoreCase("")) {
						if(country.getLanguage().equals(Locale.getDefault().getLanguage())) {
							found = true;
							locale_list.add(wizardInfoToMap(wizard.getValue(), true));
							break;
						}
					}
				}
			}
			if(!found) {
				if(wizard.getValue().isGeneric) {
					generic_list.add(wizardInfoToMap(wizard.getValue(), true));
					found = true;
				}else if(wizard.getValue().isWorld) {
					world_list.add(wizardInfoToMap(wizard.getValue(), false));
					found = true;
				}
			}
			if(!found) {
				others_list.add(wizardInfoToMap(wizard.getValue(), false));
			}
		}
		
		WizardPrioComparator comparator = new WizardPrioComparator();
		Collections.sort(locale_list, comparator);
		Collections.sort(generic_list, comparator);
		Collections.sort(world_list, comparator);
		Collections.sort(others_list, comparator);
		
		ArrayList<ArrayList<Map<String, Object>>> result = new ArrayList<ArrayList<Map<String,Object>>>();
		result.add(locale_list);
		result.add(generic_list);
		result.add(world_list);
		result.add(others_list);
		return result;
	}

	
	
	
}
