package edu.yonsei.nfc;

public class Util {
	public static String getPackageName (String full_path_clazz) {
		int index = full_path_clazz.lastIndexOf(".");
		if (index == -1) 
			return "";
		else
			return full_path_clazz.substring (6, index); // do not return "class " in the prefix
	}
	
	public static String getClassName (String full_path_clazz) {
		int index = full_path_clazz.lastIndexOf(".");
		if (index == -1) 
			return full_path_clazz;
		else
			return full_path_clazz.substring (index+1);
	}
}