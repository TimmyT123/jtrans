/*
This source code is copyrighted by Christophe Cerisara, CNRS, France.

It is licensed under the terms of the INRIA Cecill-C licence, as described in:
http://www.cecill.info/licences/Licence_CeCILL-C_V1-en.html
*/

package plugins.text;

import java.awt.Color;
import java.awt.Font;
import java.util.concurrent.PriorityBlockingQueue;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.text.*;


import facade.Project;
import plugins.text.elements.Element;
import plugins.text.elements.Element_Mot;

/**
 * permet de selectionner les differents elements pour interpreter le texte:
 * locuteurs, recouvrements, bruits, etc.
 * 
 * En + du texte, on trouve des elements speciaux, qui sont:
 * - locuteur
 * - commentaires
 * - etc
 * 
 * Pour definir les elements "speciaux", il faut ajouter des expressions regulieres
 */
public class TexteEditor extends JTextPane {

	public static final String DEFAULT_FONT_NAME = Font.SANS_SERIF;
	public static final int DEFAULT_FONT_SIZE = 13;
	
	//----------------------------------------------------------
	//------------------ Private Fields ------------------------
	//----------------------------------------------------------
	
	//----------- Objects externes -------------
	private Project project;

	//---------- State Flag ---------------
	public boolean textChanged;
	
	//-------- R�f�rences de travail ---------
	public Element_Mot lastSelectedWord=null;
	public Element_Mot lastSelectedWord2=null;

	// THREAD dont le role est de colorier le texte
	// pour arreter ce thread, il faut appeler colorieur.interrupt()
	class Coloriage extends Thread {
		public Coloriage() {
			super("coloriageThread");
		}
		public void run() {
			try {
				for (;;) {
					ColoriageEvent e = colorOrders.take();
					if (e==ColoriageEvent.endofthread) break;
					e.colorie(TexteEditor.this);
					e.coloriageDone.put(true);
				}
			} catch (InterruptedException e) {}
			System.out.println("fin du process de coloriage");
		}
	}
	Coloriage colorieur = null;
	public final PriorityBlockingQueue<ColoriageEvent> colorOrders = new PriorityBlockingQueue<ColoriageEvent>();
	
	//----------------------------------------------------------------
	//------------------ Constructor --------------------------------
	//---------------------------------------------------------------
	public TexteEditor(Project project) {
		super();

		setFont(new Font(DEFAULT_FONT_NAME, Font.PLAIN, DEFAULT_FONT_SIZE));

		this.project = project;
		
		textChanged = false;
		colorieur = new Coloriage();
		colorieur.start();
	}//Constructor

	private static final AttributeSet ALIGNED_STYLE = new SimpleAttributeSet() {{
		addAttribute(StyleConstants.CharacterConstants.Foreground, new Color(0x2018a8));
		addAttribute(StyleConstants.CharacterConstants.Italic, true);
	}};

	/**
	 * Sets text from the list of elements and highlights non-text segments
	 * according to the color defined in their respective types.
	 * @see Project#render()
	 */
	public void setTextFromElements() {
		// Create a new style for each type
		AttributeSet[] attr = new AttributeSet[project.types.size()];
		for (int i = 0; i < project.types.size(); i++) {
			SimpleAttributeSet sas = new SimpleAttributeSet();
			sas.addAttribute(StyleConstants.ColorConstants.Background, project.types.get(i).getColor());
			sas.addAttribute(StyleConstants.ColorConstants.Foreground, Color.gray);
			attr[i] = sas;
		}

		// Create a new document instead of using this instance's document to avoid
		// triggering any listeners, which makes the styling process much faster
		StyledDocument doc = new DefaultStyledDocument();

		try {
			doc.insertString(0, project.render(), null);
		} catch (BadLocationException ex) {
			JOptionPane.showMessageDialog(this, ex.toString(), "BadLocationException", JOptionPane.ERROR_MESSAGE);
		}

		// Apply styles
		for (Element el: project.elts) {
			int type = el.getType();			
			if (type >= 0 && type < project.types.size())
				doc.setCharacterAttributes(el.start, el.end - el.start, attr[type], true);
		}

		setStyledDocument(doc);
	}

	public void degrise() {
		try {
			if (lastSelectedWord != null) {
				if (lastSelectedWord2 != null) {
					ColoriageEvent e = new ColoriageEvent(lastSelectedWord, lastSelectedWord2, Color.white, true, 10);
					colorOrders.add(e);
					e.waitForColoriageDone();
				} else {
					ColoriageEvent e = new ColoriageEvent(lastSelectedWord, lastSelectedWord, Color.white, true, 10);
					colorOrders.add(e);
					e.waitForColoriageDone();
				}
			}
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		lastSelectedWord2=null;
		lastSelectedWord=null;
	}
	/**
	 * on a un acces concurrent au textPane, par le thread du PlayerListerner (qui doit avoir la priorite !)
	 * et par le thread de l'AutoAligner.
	 * 
	 * J'utilise donc une BlockingPriorityQueue pour les evenements de coloriage
	 * et une SynchronizedQueue pour que l'appelant bloque jusqu'a ce que son coloriage ait ete fait
	 * 
	 * @param mot
	 */
	public void griseMot(Element_Mot mot) {
		if (mot==null) return;

		// un seul mot a la fois peut etre grise
		degrise();
		
		// le degrisage est legerement prioritaire, meme si on attend en fait qu'il soit termine !
		ColoriageEvent e = new ColoriageEvent(mot, mot, Color.LIGHT_GRAY, true, 9);
		colorOrders.add(e);
		try {
			e.waitForColoriageDone();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		lastSelectedWord=mot;
	}

	/**
	 * Colorizes range of words with the "aligned" style.
	 * Does not touch non-word elements.
	 * This method does not care whether the word is actually aligned
	 * (posInAlign) or not.
	 *
	 * @param fromWord number of the first word (in a list containing only
	 *                    words) to colorize. This is NOT an element index!
	 * @param toWord number of the last word to colorize (inclusive). This is
	 *               NOT an element index!
	 */
	public void colorizeWords(int fromWord, int toWord) {
		int word = -1;
		int fromCh = -1;
		int toCh = -1;

		for (int i = 0; i < project.elts.size() && word < toWord; i++) {
			Element el = project.elts.get(i);

			if (el instanceof Element_Mot) {
				word++;
				if (word >= fromWord) {
					toCh = el.end;
					if (fromCh < 0)
						fromCh = el.start;
				}
			} else {
				if (fromCh > 0)
					colorizeAlignedChars(fromCh, toCh);
				fromCh = -1;
			}
		}

		if (fromCh > 0)
			colorizeAlignedChars(fromCh, toCh);
	}

	/**
	 * Colorizes all aligned words with the "aligned" style.
	 * This method only colorizes words that are aligned properly (posInAlign).
	 */
	public void colorizeAllAlignedWords() {
		int fromCh = -1;
		int toCh = -1;

		for (int i = 0; i < project.elts.size(); i++) {
			Element el = project.elts.get(i);

			if (el instanceof Element_Mot && ((Element_Mot) el).posInAlign > 0) {
				toCh = el.end;
				if (fromCh < 0)
					fromCh = el.start;
			} else {
				if (fromCh > 0)
					colorizeAlignedChars(fromCh, toCh);
				fromCh = -1;
			}
		}

		if (fromCh > 0)
			colorizeAlignedChars(fromCh, toCh);
	}

	/**
	 * Colorize range of characters with the "aligned" style
	 * @param from first character to colorize
	 * @param to last character to colorize (exclusive)
	 */
	private void colorizeAlignedChars(int from, int to) {
		getStyledDocument().setCharacterAttributes(from, to-from, ALIGNED_STYLE, true);
	}

	public void griseMotsRed(Element_Mot mot, Element_Mot mot2) {
		degrise();
		lastSelectedWord=mot;
		lastSelectedWord2=mot2;
		if (lastSelectedWord==null) return;
		if (lastSelectedWord2==null) return;
		setCaretPosition((lastSelectedWord.start + lastSelectedWord.end)/2);
		select(lastSelectedWord.start, mot2.end);

		setSelectedTextColor(Color.MAGENTA);
		repaint();
		/*
		AttributeSet a = getCharacterAttributes();
		AttributeSet b = styler.addAttribute(a, StyleConstants.ColorConstants.Background, Color.MAGENTA);
		setCharacterAttributes(b, true);
		*/
	}

	public String getMot(Element_Mot mot) {
		try {
			return getText(mot.start, mot.end-mot.start);
		} catch (BadLocationException e) {
			e.printStackTrace();
			return null;
		}
	}

	public int getIndiceMotUnderCaret(CaretEvent e) {
		return project.elts.getIndiceElementAtTextPosi(e.getDot());
	}

	public ListeElement getListeElement() {
		return project.elts;
	}

	/**
	 * Load text/elements into the text editor and colorize the relevant parts.
	 */
	public void setProject(Project project) {
		this.project = project;
		setEditable(false);
		setTextFromElements();
		colorizeAllAlignedWords();
	}
}//class TextEditor
