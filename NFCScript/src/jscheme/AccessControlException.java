package jscheme;

/**
 * This class represents an exception that happens when it is not allowed to access
 * protected java resources from Scheme interpreter. See
 * 
 * @author Kwanghoon Choi, kwanghoon.choi@yonsei.ac.kr Copyright 2011
 *
 **/

public class AccessControlException extends RuntimeException {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1025936374346063890L;
	/**
	 * 
	 */
	
	public String msg;
	public AccessControlException (String msg) {
		this.msg = msg;
	}
}