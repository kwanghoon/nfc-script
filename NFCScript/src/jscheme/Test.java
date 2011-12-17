
package jscheme;

class A {

	public static void main (String[] args) throws MyEx {
		try {
			throw new MyEx();
		}
		catch(MyEx e) {
			throw new MyEx();
		}
		catch(Exception myex) {
			
		}
	}
}

class MyEx extends Exception {
	String msg;
}