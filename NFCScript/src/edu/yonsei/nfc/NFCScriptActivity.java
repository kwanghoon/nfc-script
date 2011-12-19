package edu.yonsei.nfc;

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

public class NFCScriptActivity extends FragmentActivity {
	boolean DEBUG = false;

	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.e("NFCScript", "[NFCScriptActivity] BEGINS.");
    	
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        // int permission = checkAccess(android.content.Intent.ACTION_VIEW);
        // System.out.println ("permission=" + permission);
        String url_str = resolveIntent(getIntent());
        url_str = "http://192.168.0.15/~khchoi/nfc.scm";
        
        if (url_str != null) {
       	try {
        		URL url = new URL (url_str);
        		InputStream is_from_url = url.openStream();
        		
        		String policy =  // Load a user-defined policy onto the policy String.
        				"(define (true3 x y z) #t)\n" +
        				"(define (true2 a d) #t)\n" +
        				"(define (checkIntent a d)" + 
        				"		  (cond ((and (string=? a \"android.intent.action.VIEW\")" +
        				"		              (string=? d \"http://www.naver.com\")) #f)" +
        				"		        (else #t)))" +
						"(define policy (cons true3 (cons true3 (cons true3 (cons checkIntent ())))))\n" +
						"\n";      		
        		String str = 
        				"(define cache ())\n" +
        				"\n" +
        				"(define (addToACList pkgname)\n" +
        				"   (set! cache (addToACList_ cache pkgname)))\n" +
        				"\n" +
        				"(define (addToACList_ cache pkgname)\n" +
        				"   (cond ((null? cache)\n" +
        				"            (cons\n" +
        				"               pkgname cache))\n" +
        				"         (else\n" +
        				"            (letrec\n" +
        				"                 ((h  (car cache))\n" +
        				"                  (h1 (car h))\n" +
        				"                  (t  (cdr cache)))\n" +
        				"                 (cond ((equal? h1 pkgname) (cons h t))\n" +
        				"                       (else (cons h (addToACList_ t pkgname))))))))\n" +
        				"                       \n" +
        				"(define (Use pcs)\n" +
        				"   (cond ((null? pcs) (edu.yonsei.nfc.Policy.checkCache cache))\n" +
        				"         (else \n" +
        				"            (letrec\n" +
        				"                 ((h (car pcs))\n" +
        				"                  (t (cdr pcs))\n" +
        				"                  (a (addToACList h))\n" +
        				"                  (b (Use t)))\n" +
        				"                 ()))))\n" +
        				"\n";
        		
        		String prog = policy + str;
                
                TextView tv = (TextView)findViewById(R.id.scripttext);
        		tv.setText(prog);
        		
        		InputStream is_from_string = new StringBufferInputStream(prog);
        		SequenceInputStream is = new SequenceInputStream(is_from_string, is_from_url);
        		startScheme(new InputPort(is));
        		
        	} catch (AccessControlException e) {
        		Log.i ("NFCScript", "[Exception] : " + e.toString() );
    		} catch (IOException e) {
    			Log.i ("NFCScript", "[Exception] : " + e.toString() );
    		}
        }
        
      
        Log.e("NFCScript", "[NFCScriptActivity] ENDS.");      
        
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

	public static Scheme getScheme() {
		if (s == null) {
			Policy p = new Policy();
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
		
		java.lang.Object ret = scheme.load(inputPort);
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

















