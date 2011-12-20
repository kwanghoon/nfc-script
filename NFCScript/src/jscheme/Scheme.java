package jscheme;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
//import java.util.HashSet;

//import android.app.AlertDialog;
//import android.app.Dialog;
//import android.content.DialogInterface;
//import android.os.Bundle;
//import android.support.v4.app.DialogFragment;
//import android.support.v4.app.FragmentActivity;
//import android.util.Log;

/**
 * This class represents a Scheme interpreter. See
 * http://www.norvig.com/jscheme.html for more documentation. This is version
 * 1.4.
 * 
 * This class is extended with hooking methods to intercept accessing Java
 * constructors, methods, and fields from the Scheme interpreter.
 * 
 * @author Peter Norvig, peter@norvig.com http://www.norvig.com Copyright 1998
 *         Peter Norvig, see http://www.norvig.com/license.html
 *         
 * @author Kwanghoon Choi, kwanghoon.choi@yonsei.ac.kr Copyright 2011
 *           
 **/

public class Scheme extends SchemeUtils {
	
	public static String SchemeLog="";

	InputPort input = new InputPort(System.in);
	PrintWriter output = new PrintWriter(System.out, true);
	Environment globalEnvironment = new Environment();
	
	Hook hook;

	public Environment getGlobalEnvironment() {
		return globalEnvironment;
	}
	
	/**
	 * Create a Scheme interpreter and load an array of files into it. Also load
	 * SchemePrimitives.CODE.
	 **/
	public Scheme(String[] files) {
		Primitive.installPrimitives(globalEnvironment);
		try {
			load(new InputPort(new StringReader(SchemePrimitives.CODE)));
			for (int i = 0; i < (files == null ? 0 : files.length); i++) {
				load(files[i]);
			}
		}
		catch (RuntimeException e) {
			;
		}
	}
	
	public Scheme(String[] files, Hook hook) {
		this(files);
		this.hook = hook;
	}

	// ////////////// Main Loop

	/**
	 * Create a new Scheme interpreter, passing in the command line args as
	 * files to load, and then enter a read eval write loop.
	 **/
	public static void main(String[] files) {
		new Scheme(files).readEvalWriteLoop();
	}

	/**
	 * Prompt, read, eval, and write the result. Also sets up a catch for any
	 * RuntimeExceptions encountered.
	 **/
	public void readEvalWriteLoop() {
		Object x;
		for (;;) {
			try {
				output.print("> ");
				output.flush();
				if (InputPort.isEOF(x = input.read()))
					return;
				write(eval(x), output, true);
				output.println();
				output.flush();
			}
			catch (RuntimeException e) {
				;
			}
		}
	}

	/** Eval all the expressions in a file. Calls load(InputPort). **/
	public Object load(Object fileName) {
		String name = stringify(fileName, false);
		try {
			return load(new InputPort(new FileInputStream(name)));
		} catch (IOException e) {
			return error("can't load " + name);
		}
	}

	/** Eval all the expressions coming from an InputPort. **/
	public Object load(InputPort in) {
		Object x = null;
		for (;;) {
			if (InputPort.isEOF(x = in.read()))
				return TRUE;
			eval(x);
		}
	}

	// ////////////// Evaluation

	/** Evaluate an object, x, in an environment. **/
	public Object eval(Object x, Environment env) {
		// The purpose of the while loop is to allow tail recursion.
		// The idea is that in a tail recursive position, we do "x = ..."
		// and loop, rather than doing "return eval(...)".
		while (true) {
			if (x instanceof String) { // VARIABLE
				String s = (String) x;
				if (s.indexOf('.') != -1 || s.equals("import")) {
					return new JavaDotMethod(s);
				} else {
					return env.lookup(s);
				}
			} else if (!(x instanceof Pair)) { // CONSTANT
				return x;
			} else {
				Object fn = first(x);
				Object args = rest(x);
				if (fn == "quote") { // QUOTE
					return first(args);
				} else if (fn == "begin") { // BEGIN
					for (; rest(args) != null; args = rest(args)) {
						eval(first(args), env);
					}
					x = first(args);
				} else if (fn == "define") { // DEFINE
					if (first(args) instanceof Pair)
						return env.define(first(first(args)), eval(cons(
								"lambda", cons(rest(first(args)), rest(args))),
								env));
					else
						return env.define(first(args), eval(second(args), env));
				} else if (fn == "set!") { // SET!
					return env.set(first(args), eval(second(args), env));
				} else if (fn == "if") { // IF
					x = (truth(eval(first(args), env))) ? second(args)
							: third(args);
				} else if (fn == "cond") { // COND
					x = reduceCond(args, env);
				} else if (fn == "lambda") { // LAMBDA
					return new Closure(first(args), rest(args), env);
				} else if (fn == "macro") { // MACRO
					return new Macro(first(args), rest(args), env);
				} else { // PROCEDURE CALL:
					fn = eval(fn, env);
					if (fn instanceof Macro) { // (MACRO CALL)
						x = ((Macro) fn).expand(this, (Pair) x, args);
					} else if (fn instanceof Closure) { // (CLOSURE CALL)
						Closure f = (Closure) fn;
						x = f.body;
						env = new Environment(f.parms, evalList(args, env),
								f.env);
					} else { // (OTHER PROCEDURE CALL)
						return Procedure.proc(fn).apply(this,
								evalList(args, env));
					}
				}
			}
		}
	}

	/** Eval in the global environment. **/
	public Object eval(Object x) {
		return eval(x, this.globalEnvironment);
	}

	/** Evaluate each of a list of expressions. **/
	Pair evalList(Object list, Environment env) {
		if (list == null)
			return null;
		else if (!(list instanceof Pair)) {
			error("Illegal arg list: " + list);
			return null;
		} else
			return cons(eval(first(list), env), evalList(rest(list), env));
	}

	/**
	 * Reduce a cond expression to some code which, when evaluated, gives the
	 * value of the cond expression. We do it that way to maintain tail
	 * recursion.
	 **/
	Object reduceCond(Object clauses, Environment env) {
		Object result = null;
		for (;;) {
			if (clauses == null)
				return FALSE;
			Object clause = first(clauses);
			clauses = rest(clauses);
			if (first(clause) == "else"
					|| truth(result = eval(first(clause), env)))
				if (rest(clause) == null)
					return list("quote", result);
				else if (second(clause) == "=>")
					return list(third(clause), list("quote", result));
				else
					return cons("begin", rest(clause));
		}
	}
	
	// (c) 2011 Kwanghoon Choi
	public void checkAccess (Constructor<?> cs, Object[] args)
			throws AccessControlException
	{
		hook.checkAccess(cs,  args);	
	}
	
	public void checkAccess (Field field, Object callingobj)
			throws AccessControlException
	{
		hook.checkAccess (field, callingobj);
	}
	
	public void checkAccess (Method method, Object callingobj, Object[] args)
			throws AccessControlException
	{
		hook.checkAccess(method, callingobj, args);
		
//		{
//			Object context = env.lookup("context");
//			if (context instanceof FragmentActivity) {
//				FragmentActivity fa = (FragmentActivity)context;
//				DialogFragment newFragment = new MyAlertDialogFragment();
//				newFragment.setCancelable(false);
//				newFragment.show(fa.getSupportFragmentManager(), "dialog");
//			}
//		}
	}
	
	// Check Constructors Under User Policy
	public void checkConstructor (String pkg, String clz, String con) {
		Environment env = getGlobalEnvironment();
		
		// Check the user-defined policy
		hook.setSupervisorMode(true);
		{
			Object policy = env.lookup("policy");
			Object p_con = jscheme.SchemeUtils.first(policy);
			System.out.println("checkConstructor : p_con instanceof Clsoure="
					+ (p_con instanceof Closure));
			
			if (p_con instanceof Closure) {
				Closure f = (Closure) p_con;
				Object x = f.body;
				Object as = SchemeUtils.cons(pkg.toCharArray(),
								SchemeUtils.cons(clz.toCharArray(),
										SchemeUtils.cons(con.toCharArray(), null)));
				Environment e = new Environment(f.parms, evalList(as, env), f.env);
				Object r = eval (x,e);
				
				if (r != SchemeUtils.TRUE) {
					hook.setSupervisorMode(false);
					throw new AccessControlException
								("The policy fun (CON) evaluates to Non-TRUE.");
				}
			}
			else {
				hook.setSupervisorMode(false);
				throw new AccessControlException
							("The policy fun (CON) is not an instance of Closure.");
			}
		}
		hook.setSupervisorMode(false);
		
		System.out.println("check Constructor: pass");		
	}
	
	// Check Methods Under User Policy
	public void checkMethod (String pkg, String clz, String mth) {
		
		Environment env = getGlobalEnvironment();
		
		// Check the user-defined policy
		hook.setSupervisorMode(true);
		{
			Object policy = env.lookup("policy");
			Object p_method = jscheme.SchemeUtils.first(
								jscheme.SchemeUtils.rest(
										jscheme.SchemeUtils.rest(policy)));
			System.out.println("checkMethod : p_method instanceof Clsoure="
							+ (p_method instanceof Closure));
		
			if (p_method instanceof Closure) {
				Closure f = (Closure) p_method;
				Object x = f.body;
				Object as = SchemeUtils.cons(pkg.toCharArray(),
								SchemeUtils.cons(clz.toCharArray(),
										SchemeUtils.cons(mth.toCharArray(), null)));
				Environment e = new Environment(f.parms, evalList(as, env), f.env);
				Object r = eval (x,e);
			
				if (r != SchemeUtils.TRUE) {
					hook.setSupervisorMode(false);
					throw new AccessControlException
								("The policy fun (METHOD) evaluates to Non-TRUE.");
				}
			}
			else {
				hook.setSupervisorMode(false);
				throw new AccessControlException
							("The policy fun (METHOD) is not an instance of Closure.");
			}
		}
		hook.setSupervisorMode(false);
		
		System.out.println("check Method : pass");
	}
	
	// Check Fields Under User Policy
	public void checkField (String pkg, String clz, String fld) {
		Environment env = getGlobalEnvironment();
		
		// Check the user-defined policy
		hook.setSupervisorMode(true);
		{
			Object policy = env.lookup("policy");
			Object p_field = jscheme.SchemeUtils.first(
							jscheme.SchemeUtils.rest(policy));
			System.out.println("checkField : p_field instanceof Clsoure="
					+ (p_field instanceof Closure));
			
			if (p_field instanceof Closure) {
				Closure f = (Closure) p_field;
				Object x = f.body;
				Object as = SchemeUtils.cons(pkg.toCharArray(),
								SchemeUtils.cons(clz.toCharArray(),
										SchemeUtils.cons(fld.toCharArray(), null)));
				Environment e = new Environment(f.parms, evalList(as, env), f.env);
				Object r = eval (x,e);
				
				if (r != SchemeUtils.TRUE) {
					hook.setSupervisorMode(false);
					throw new AccessControlException
								("The policy fun (FIELD) evaluates to Non-TRUE.");
				}
			}
			else {
				hook.setSupervisorMode(false);
				throw new AccessControlException
							("The policy fun (FIELD) is not an instance of Closure.");
			}
		}
		hook.setSupervisorMode(false);
		
		System.out.println("checkField : pass");		
	}
	
	// Check Actions Under User Policy
	public void checkAction (String action, String data) {
		System.out.println("checkAction:" + action + ", " + data);
		
		Environment env = getGlobalEnvironment();
		
		// Check the user-defined policy
		hook.setSupervisorMode(true);
		{
			Object policy = env.lookup("policy");
			Object p_act = jscheme.SchemeUtils.first(
								jscheme.SchemeUtils.rest(
										jscheme.SchemeUtils.rest(
												jscheme.SchemeUtils.rest(policy))));
			System.out.println("checkAction : p_act instanceof Clsoure="
					+ (p_act instanceof Closure));
			
			if (p_act instanceof Closure) {
				Closure f = (Closure) p_act;
				Object x = f.body;
				Object as = SchemeUtils.cons(action.toCharArray(),
								SchemeUtils.cons(data.toCharArray(), null));
				Environment e = new Environment(f.parms, evalList(as, env), f.env);
				Object r = eval (x,e);
				
				if (r != SchemeUtils.TRUE) {
					hook.setSupervisorMode(false);
					throw new AccessControlException
								("The policy fun (FIELD) evaluates to Non-TRUE.");
				}
			}
			else {
				hook.setSupervisorMode(false);
				throw new AccessControlException
							("The policy fun (FIELD) is not an instance of Closure.");
			}
		}
		hook.setSupervisorMode(false);
		
		System.out.println("checkAction : pass");			
	}
	
	public boolean checkCache (String packageName, String className) {
		Environment env = getGlobalEnvironment();
		Object list = env.lookup("cache");
		
		boolean flag = false;
		String ff = "";
			
		while (list != null) {
			Object f = jscheme.SchemeUtils.first(list);
			
			if (f != null) {
				ff = new String ((char[])f);
				
				if (ff.equals(packageName)) {
					flag = true;
					break;
				}
			}
			list = jscheme.SchemeUtils.rest(list);
		}
		
		if (flag)
			SchemeLog += ("checkCache: " + ff + "    flag=" + flag);
		else
			SchemeLog += ("flag=" + flag + "(" + packageName + "," + className + ")");
		
		return flag;
	}

//	// For Test
//	public class MyAlertDialogFragment extends DialogFragment {
//		public Dialog onCreateDialog(Bundle savedInstaceState) {
//			return new AlertDialog.Builder(getActivity())
//				.setMessage("Are you sure you want to exit?")
//				.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
//					public void onClick(DialogInterface dialog, int id) {
//						Log.i("NFCScriptActivity", "Yes");
//						dialogflag = false;
//					}
//				})
//				.setNegativeButton("No", new DialogInterface.OnClickListener() {
//					public void onClick(DialogInterface dialog, int id) {
//						Log.i("NFCScriptActivity", "No");
//						dialogflag = false;
//					}
//				})
//				.create();
//		}
//	}
	
}
