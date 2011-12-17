package jscheme;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import jscheme.AccessControlException;

/**
 * This class represents a hook interface to accessing java from Scheme interpreter. See
 * 
 * @author Kwanghoon Choi, kwanghoon.choi@yonsei.ac.kr Copyright 2011
 *
 **/

public interface Hook {
	public void setSupervisorMode (boolean b);
	
	public void checkAccess  (Constructor<?> cs, Object[] args)
			throws AccessControlException;
	public void checkAccess (Field field, Object callingobj)
			throws AccessControlException;
	public void checkAccess (Method method, Object callingobj, Object[] args)
			throws AccessControlException;
}