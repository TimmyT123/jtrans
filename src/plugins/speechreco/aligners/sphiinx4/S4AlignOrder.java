/*
This source code is copyrighted by Christophe Cerisara, CNRS, France.

It is licensed under the terms of the INRIA Cecill-C licence, as described in:
http://www.cecill.info/licences/Licence_CeCILL-C_V1-en.html
*/

package plugins.speechreco.aligners.sphiinx4;

public class S4AlignOrder {
	final public static S4AlignOrder terminationOrder = new S4AlignOrder(-1, -1);
	
	boolean isBlocViterbi;
	int mot1, mot2=-1, tr1, tr2=-1;
	public AlignementEtat alignWords, alignPhones, alignStates;
	// contient l'alignement des mots
	
	public S4AlignOrder(int firstWord, int firstFrame, int lastMot, int lastFrame) {
		isBlocViterbi = false;
		mot1=firstWord; mot2=lastMot;
		tr1=firstFrame; tr2=lastFrame;
	}
	
	public S4AlignOrder(int firstWord, int firstFrame) {
		isBlocViterbi = true;
		mot1=firstWord;
		tr1=firstFrame;
	}
	
	public boolean isBlocViterbi() {return isBlocViterbi;}
	
	public int getFirstMot() {return mot1;}
	public int getLastMot() {return mot2;}
	public int getFirstFrame() {return tr1;}
	public int getLastFrame() {return tr2;}
}
