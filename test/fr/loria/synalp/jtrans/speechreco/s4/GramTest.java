/*
This source code is copyrighted by Christophe Cerisara, CNRS, France.

It is licensed under the terms of the INRIA Cecill-C licence, as described in:
http://www.cecill.info/licences/Licence_CeCILL-C_V1-en.html
*/

package fr.loria.synalp.jtrans.speechreco.s4;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;

import edu.cmu.sphinx.linguist.acoustic.Unit;
import edu.cmu.sphinx.linguist.acoustic.UnitManager;
import edu.cmu.sphinx.linguist.dictionary.Dictionary;
import edu.cmu.sphinx.linguist.dictionary.Pronunciation;
import edu.cmu.sphinx.linguist.dictionary.Word;
import edu.cmu.sphinx.linguist.dictionary.WordClassification;
import edu.cmu.sphinx.linguist.language.grammar.GrammarNode;
import edu.cmu.sphinx.util.props.PropertyException;
import edu.cmu.sphinx.util.props.PropertySheet;

import fr.loria.synalp.jtrans.utils.PrintStreamProgressDisplay;
import junit.framework.TestCase;

public class GramTest extends TestCase {
	public void test() throws Exception {
			class MyPronunc extends Pronunciation {
				public MyPronunc(Unit[] units, String tag, WordClassification wc, float prob) {
					super(units,tag,wc,prob);
				}
				public void setWord(Word w) {
					super.setWord(w);
				}
			}
			
			Dictionary dictionary=new Dictionary() {
				@Override
				public void newProperties(PropertySheet ps) throws PropertyException {}
				
				@Override
				public Word getWord(String text) {
					boolean isFiller = text.equals("SIL");
					UnitManager umgr = HMMModels.getUnitManager();
					System.out.println("DICO GETWORD "+text+" "+isFiller);
					Unit[] units = {umgr.getUnit(text, isFiller)};
					MyPronunc[] pr = {new MyPronunc(units, null, null, 1)};
					Word w = new Word(text, pr, isFiller);
					pr[0].setWord(w);
					return w;
				}
				
				@Override
				public Word getSilenceWord() {
					return null;
				}
				
				@Override
				public Word getSentenceStartWord() {
					return null;
				}
				
				@Override
				public Word getSentenceEndWord() {
					return null;
				}
				
				@Override
				public WordClassification[] getPossibleWordClassifications() {
					return null;
				}
				
				@Override
				public Word[] getFillerWords() {
					return null;
				}
				
				@Override
				public void deallocate() {}
				
				@Override
				public void allocate() throws IOException {}
			};
		
		PhoneticForcedGrammar gram = new PhoneticForcedGrammar();
			String[] words = {"bonjour","il"};
			gram.setWords(Arrays.asList(words), new PrintStreamProgressDisplay());
			GrammarNode g = gram.getGram();
			g.dump();
	}
}
