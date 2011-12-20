package edu.yonsei.nfc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;

import java.lang.reflect.Field;
import java.net.URL;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.StringBufferInputStream;
import java.io.StringReader;

import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import jscheme.Environment;
import jscheme.InputPort;
import jscheme.Scheme;
import jscheme.AccessControlException;

/**
 * This class represents an NFC tag reader handling special NFC URIs to Scheme
 * programs, e.g., "U" "http://localhost/nfctag.scm". The name of Scheme programs
 * is assumed to end with ".scm".
 *  
 * This is version 0.1.
 *      
 * @author Kwanghoon Choi, kwanghoon.choi@yonsei.ac.kr Copyright 2011
 *           
 **/

public class NFCScriptActivity extends Activity {
	boolean DEBUG = false;

	private Policy p = new Policy();
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.e("NFCScript", "[NFCScriptActivity] BEGINS.");
    	
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        String url_str = resolveIntent(getIntent());
        
//        if (url_str != null) {
        	try {
        		InputStream is_from_url =
        				new URL ("http://192.168.0.15/~khchoi/nfc.scm").openStream();
        		InputStream is_from_userpolicy = new URL("http://192.168.0.15/~khchoi/user_policy.scm").openStream();
        		InputStream is_from_string = new StringBufferInputStream(p.getCacheLib());
        		
        		SequenceInputStream is = new SequenceInputStream(is_from_string,
        									new SequenceInputStream (is_from_userpolicy, is_from_url));
        		startScheme(new InputPort(is));
       		}
       		catch (IOException e) {
    			Log.i ("NFCScript", "[Exception] : " + e.toString() );
       		}
//        }
      
        Log.e("NFCScript", "[NFCScriptActivity] ENDS.");
        finish();
    }
    
    private String resolveIntent(Intent intent) {
        // Parse the intent
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
            Log.e("NFCScript", "ACTION_TAG_DISCOVERED intent arrived" + intent);
            
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] msgs;
            if (rawMsgs != null) {
                msgs = new NdefMessage[rawMsgs.length];
                
                if (DEBUG) System.out.println ("resolveIntent: rawMsgs.length=" + rawMsgs.length);
                for (int i = 0; i < rawMsgs.length; i++) {
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }
                
                for (int i = 0; i < rawMsgs.length; i++) {
                	NdefRecord[] recs = msgs[i].getRecords();
                	
                	for (int j = 0; j < recs.length; j++) {
                		byte[] type = recs[i].getType();
                		byte[] payload = recs[i].getPayload();
                		String str_type = new String (type);
                		String str_payload = new String (payload );
                		
                		if (DEBUG) System.out.println ("resolveIntent: type=" + str_type + " payload=" + str_payload);
                		
                		if ( type.length == 1 && (type[i] == 'U' || type[i] == 'u') && str_payload.endsWith (".scm") ) {
                			if (DEBUG) System.out.println("resolveIntent : found! " + str_payload);
                			return str_payload;
                		}
                	}
                }
                
            } 
        } else {
            Log.e("NFCScript", "Unknown intent " + intent);
        }
        
        return null;
    }    
    
	static Scheme s;

	public Scheme getScheme() {
		if (s == null) {
			s = new Scheme(new String[0], p); // Creating a Scheme interpreter with a hook interface
			p.setScheme(s);
		}
		return s;
	}
	
	public void startScheme(InputPort inputPort) {
		Scheme scheme = getScheme();
		
		Environment e = scheme.getGlobalEnvironment();
		
		//addResourceFields(R.layout.class, "layout", e);
		//addResourceFields(R.drawable.class, "drawable", e);
		//addResourceFields(R.id.class, "id", e);
		
		//addResourceFields (Uri.class, "Uri", e);
		//addResourceFields (Intent.class, "Intent", e);
		
		e.define("context".intern(), this);
		
		java.lang.Object ret;
		try {
			ret = scheme.load(inputPort);
		}
		catch(AccessControlException exn) {
    		ret = "exception (AC)";
		}
		catch (RuntimeException exn) {
			
    		Log.i ("NFCScript", "[Exception] : " + exn.toString() );
    		Toast.makeText(this, exn.toString(), 10000).show();
    		ret = "exception";
    		
    		if (exn instanceof AccessControlException) {
    			AccessControlException eexn = (AccessControlException)exn;
    			Log.i ("NFCScript", "[Exception] : " + eexn.msg );
        		Toast.makeText(this, eexn.msg, 1000).show();
        		ret = "exception";
    		}
		}
		
		Log.i("NFCScriptActivity:", scheme.SchemeLog);
		if (DEBUG) System.out.println ("NFCScript Result is " + ret.toString());
	}
	
	protected void addResourceFields(Class<?> clazz, String name, Environment e) {
		Field[] fs = clazz.getFields();
		for (Field f : fs) {
			if (f.getType() == int.class) {
				try {
					e.define(("r-"+name+"-"+f.getName()).intern(), f.get(null));
				} catch (IllegalAccessException ex) {
				}
			}
		}
	}
}

















