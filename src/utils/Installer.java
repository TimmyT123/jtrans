package utils;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;

import javax.swing.JOptionPane;

import utils.FileUtils;
import utils.WGETJava;

public class Installer {
	
	public static String getCurrentDir() {
		String path = Installer.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		try {
			String decodedPath = URLDecoder.decode(path, "UTF-8");
			return decodedPath;
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static void main(String args[]) {
		System.out.println("starting JTrans installer in curdir "+((new File(".")).getAbsolutePath()));
		if ((new File("culture.jtr")).exists())
			launchJTrans(args);
		else {
			try {
				WGETJava.DownloadFile(new URL("http://talc1.loria.fr/users/cerisara/jtrans/culture.wav"));
				WGETJava.DownloadFile(new URL("http://talc1.loria.fr/users/cerisara/jtrans/culture.txt"));
				WGETJava.DownloadFile(new URL("http://talc1.loria.fr/users/cerisara/jtrans/culture.jtr"));
				String[] ar = {"culture.jtr"};
				launchJTrans(ar);
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void launchJTrans(final String[] args) {
		if (!isAlreadyInstalled()) install();
		try {
			plugins.applis.SimpleAligneur.Aligneur.main(args);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void install() {
		File tf = new File(".");
		int rep = JOptionPane.showConfirmDialog(null, "First time run detected. OK for downloading all resources (400Mb) in "+tf.getAbsolutePath()+"?");
		if (rep==JOptionPane.OK_OPTION) {
			try {
				URL resurl = new URL("http://talc1.loria.fr/users/cerisara/jtrans/jtransres.zip");
				WGETJava.DownloadFile(resurl);
				FileUtils.unzip("jtransres.zip");
				System.out.println("installation successful");
				File f = new File("jtransres.zip");
				f.delete();
			} catch (Exception e) {
				JOptionPane.showMessageDialog(null, "error installing: "+e.toString());
			}
		}
	}
	public static boolean isAlreadyInstalled() {
		File f = new File("./res/acmod/ESTER2_Train_373f_a01_s01.f04.lexV02_alg01_ter.cd_2500.mdef");
		if (!f.exists()) return false;
		return true;
	}
}

