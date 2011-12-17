package jscheme;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Vector;

public class JavaDotMethod extends Procedure {
	private boolean DEBUG = false;
	
	static Vector<String> imports = new Vector<String>();

	static {
		imports.add("");
		imports.add("java.lang.");
	}

	public static void addImport(String pkg) {
		imports.add(pkg+".");
	}
	
	String method;

	public JavaDotMethod(String method) {
		this.method = method;
	}

	protected boolean isMatch(Class<?>[] types, Object[] objs) {
		if (DEBUG) System.out.println ("isMatch: " + types.length + " =?= " + objs.length);
		
		if (types.length != objs.length) {
			return false;
		}

		for (int i = 0; i < objs.length; i++) {
			
			if (DEBUG) System.out.println ("isMatch: types[" + i + "]" + types[i].toString() );
			if (DEBUG) System.out.println ("isMatch: objs[" + i + "].getClass" + objs[i].getClass().toString() + " ----- " + method);
			
			if (types[i].isAssignableFrom(objs[i].getClass())) {
				continue;
			} else if (types[i].isAssignableFrom(String.class)  // This is where a Scheme string in char[] is replaced with a Java string.
					&& objs[i].getClass() == char[].class) {
				continue;
			} else if (types[i].isAssignableFrom(int.class)
					&& objs[i].getClass() == Double.class) {
				continue;
			} else if (types[i].isAssignableFrom(float.class)
					&& objs[i].getClass() == Double.class) {
				continue;
			} else if (types[i].isAssignableFrom(double.class)
					&& objs[i].getClass() == Double.class) {
				continue;
			} else if (types[i] == int.class 
					&& objs[i].getClass() == Integer.class) {
				continue;
			}
			//throw new RuntimeException("Bailing on: "+types[i]+"<->"+objs[i].getClass());
			return false;
		}
		return true;
	}

	protected Object[] coerce(Class<?>[] types, Object[] objs) {
		Object[] res = new Object[objs.length];
		for (int i = 0; i < res.length; i++) {
			if (types[i].isAssignableFrom(objs[i].getClass())) {
				res[i] = objs[i];
			} else if (types[i].isAssignableFrom(String.class)  // This is where a Scheme string in char[] is replaced with a Java string.
					&& objs[i].getClass() == char[].class) {
				res[i] = new String((char[]) objs[i]);
			} else if (types[i].isAssignableFrom(int.class)
					&& objs[i].getClass() == Double.class) {
				res[i] = new Integer(((Double) objs[i]).intValue());
			} else if (types[i].isAssignableFrom(float.class)
					&& objs[i].getClass() == Double.class) {
				res[i] = new Float(((Float) objs[i]).floatValue());
			} else if (types[i].isAssignableFrom(double.class)
					&& objs[i].getClass() == Double.class) {
				res[i] = (Double) objs[i];
			} else if (types[i] == int.class &&
					   objs[i].getClass() == Integer.class) {
				res[i] = objs[i];
			}
			else {
				throw new RuntimeException("Bailing on: "+types[i]+"<a>"+objs[i].getClass());
			}
		}
		return res;
	}

	protected Object[] getArgs(Object args) {
		int n = length(args);
		Object[] as = new Object[n];
		for (int i = 0; i < n; i++) {
			as[i] = first(args);
			args = rest(args);
		}
		return as;
	}

	protected Object doConstructor(Scheme interpreter, Object args)
			throws ClassNotFoundException, InstantiationException,
			InvocationTargetException, IllegalAccessException {
		Object[] as = getArgs(args);
		String className = method.substring(0, method.length() - 1);
		
		if (DEBUG) System.out.println ("doConstructor: className " + className);
		
		Class<?> clazz = forName(className);
		Constructor<?>[] cs = clazz.getConstructors();
		
		if (DEBUG) System.out.println ("doConstructor: cs.length " + cs.length);
		
		for (int i = 0; i < cs.length; i++) {
			if (isMatch(cs[i].getParameterTypes(), as)) {
				
				// Access check
				interpreter.checkAccess( cs[i], coerce(cs[i].getParameterTypes(), as)); 
				
				// Interfaces to Java
				return cs[i].newInstance(coerce(cs[i].getParameterTypes(), as)); 
			}
		}
		error("Couldn't find matching constructor.");
		return null;
	}

	protected Object doMethod(Scheme interpreter, Object target, Object args)
			throws InvocationTargetException, IllegalAccessException {
		String name = method.substring(1);
		Class<?> clazz = target.getClass();

		return doMethod(interpreter, name, target, clazz, args);
	}

	protected Object doMethod(Scheme interpreter, String name, Object target,
			Class<?> clazz, Object args) throws InvocationTargetException,
			IllegalAccessException {
		Object[] as = getArgs(args);
		Method[] ms = clazz.getMethods();
		for (int i = 0; i < ms.length; i++) {
			if (ms[i].getName().equals(name)
					&& isMatch(ms[i].getParameterTypes(), as)) {
				
				// Access check
				interpreter.checkAccess( ms[i], target, coerce(ms[i].getParameterTypes(), as)); 
				
				// Interfaces to Java
				return ms[i].invoke(target, coerce(ms[i].getParameterTypes(),  
						as));
			}
		}
		error("Couldn't find matching method");
		return null;
	}

	protected Object doStaticMethod(Scheme interpreter, Object args)
			throws InvocationTargetException, IllegalAccessException,
			ClassNotFoundException {
		int ix = method.lastIndexOf(".");
		String className = method.substring(0, ix);
		String methodName = method.substring(ix + 1);

		return doMethod(interpreter, methodName, null, forName(className), args);
	}

	protected Object doField(Scheme interpreter, String name, Class<?> clazz,
			Object target, Object args) throws IllegalAccessException {
		Field[] fs = clazz.getFields();
		for (int i = 0; i < fs.length; i++) {
			if (fs[i].getName().equals(name)) {
				
				// Access check
				interpreter.checkAccess( fs[i], target ); 
				
				// Interfaces to Java
				return fs[i].get(target);                                      
			}
		}
		error("No such field: " + method);
		return null;
	}

	protected Class<?> forName(String nme) 
		throws ClassNotFoundException
	{
		String name;
		for (String imprt : imports)
		{
			if (DEBUG) System.out.println ("forName: " + imprt);
			if (DEBUG) System.out.println ("       : " + nme);
			
			name = imprt + nme;
			
			if (DEBUG) System.out.println ("Class.forName : " + name);
			try {
				if (name.indexOf('$') == -1) {
					return Class.forName(name);
				}
				else {
					int ix = name.indexOf('$');
					String className = name.substring(0, ix);
					
					if (DEBUG) System.out.println ("className: " + className);
					
					Class<?> clazz = Class.forName(className);
					Class<?> res = forName(clazz, name);
					
					if (res == null) {
						throw new ClassNotFoundException(name);
					}
					else {
						return res;
					}
				}
			}
			catch (ClassNotFoundException ex) {}
		}
		throw new ClassNotFoundException(nme);
	}

	protected Class<?> forName(Class<?> parent, String name) {
		Class<?>[] cs = parent.getClasses();
		if (name.indexOf('$') == -1) {
			for (int i = 0; i < cs.length; i++) {
				if (cs[i].getName().equals(name)) {
					return cs[i];
				}
			}
		} else {
			int ix = name.indexOf('$');
			String className = name.substring(0, ix);
			String internalName = name.substring(ix + 1);
			for (int i = 0; i < cs.length; i++) {
				if (cs[i].getName().equals(className)) {
					return forName(cs[i], internalName);
				}
			}
		}
		return null;
	}

	protected Object applyInternal(Scheme interpreter, Object args) {
		try {
			if (method.equals("import"))
			{
				addImport(new String((char[])first(args)));
				return Scheme.TRUE;
			}
			else if (method.endsWith(".")) {
				return doConstructor(interpreter, args);
			} else if (method.startsWith(".")) {
				Object target = first(args);
				args = rest(args);
				if (method.endsWith("$")) {
					String name = method.substring(1, method.length() - 1);
					return doField(interpreter, name, target.getClass(),
							target, args);
				} else {
					return doMethod(interpreter, target, args);
				}
			} else if (method.endsWith("$")) {
				int ix = method.lastIndexOf('.');
				String className = method.substring(0, ix);
				String fieldName = method
						.substring(ix + 1, method.length() - 1);
				return doField(interpreter, fieldName, forName(className),
						null, args);
			} else if (method.endsWith(".class")) {
				String className = method.substring(0, method.length() - 6);
				return forName(className);
			} else if (method.indexOf(".") != -1) {
				return doStaticMethod(interpreter, args);
			} else {
				error("Unknown: " + method);
			}
		}
		catch (InvocationTargetException ex) {
			error("Exception: "+ex.getTargetException());
		} 
		catch (Exception ex) {
			error("Exception: " + ex);
		}
		return null;
	}

	protected Object interpret(Object o) 
	{
		if (o instanceof String) {
			String s = (String)o;
			char[] res = new char[s.length()];
			s.getChars(0, res.length, res, 0);
			return res;
		}
		else if (o instanceof Number) {
			return num(o);		
		}
		else {
			return o;
		}
	}
	
	@Override
	public Object apply(Scheme interpreter, Object args) {
		Object res = applyInternal(interpreter, args);
		if (res != null) {
			res = interpret(res);
		}
		return res;
	}
}
