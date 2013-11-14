package facade;

import java.io.*;
import java.util.*;

import plugins.applis.SimpleAligneur.Aligneur;
import plugins.signalViewers.spectroPanel.SpectroControl;
import plugins.speechreco.aligners.sphiinx4.AlignementEtat;
import plugins.speechreco.aligners.sphiinx4.S4AlignOrder;
import plugins.speechreco.aligners.sphiinx4.S4ForceAlignBlocViterbi;
import plugins.text.ListeElement;
import plugins.text.TexteEditor;
import plugins.text.elements.Element;
import plugins.text.elements.Element_Ancre;
import plugins.text.elements.Element_Mot;
import utils.ProgressDialog;

import javax.sound.sampled.*;

public class JTransAPI {
	/**
	 * Align words between anchors using linear interpolation (a.k.a.
	 * "equialign") instead of proper Sphinx alignment (batchAlign).
	 * Setting this flag to `true` yields very fast albeit inaccurate results.
	 */
	private static final boolean USE_LINEAR_ALIGNMENT = false;

	/**
	 * Target audio format. Any input audio files that do not match this format
	 * will be converted to it before being processed.
	 */
	private static final AudioFormat SUITABLE_AUDIO_FORMAT =
			new AudioFormat(16000, 16, 1, true, false);
	
	public static int getNbWords() {
		if (elts==null) return 0;
		initmots();
		return mots.size();
	}
	public static boolean isBruit(int mot) {
		initmots();
		Element_Mot m = elts.getMot(mot);
		return m.isBruit;
	}

	/**
	 * Return an audio file in a suitable format for JTrans. If the original
	 * file isn't in the right format, convert it and cache it.
	 */
	public static File suitableAudioFile(final File original) {
		AudioFormat af;

		try {
			 af = AudioSystem.getAudioFileFormat(original).getFormat();
		} catch (UnsupportedAudioFileException ex) {
			ex.printStackTrace();
			return original;
		} catch (IOException ex) {
			ex.printStackTrace();
			return original;
		}

		if (af.matches(SUITABLE_AUDIO_FORMAT)) {
			System.out.println("suitableAudioFile: no conversion needed!");
			return original;
		}

		System.out.println("suitableAudioFile: need conversion, trying to get one from the cache");

		Cache.FileFactory factory = new Cache.FileFactory() {
			public void write(File f) throws IOException {
				System.out.println("suitableAudioFile: no cache found... creating one");

				AudioInputStream originalStream;
				try {
					 originalStream = AudioSystem.getAudioInputStream(original);
				} catch (UnsupportedAudioFileException ex) {
					ex.printStackTrace();
					throw new Error("Unsupported audio file; should've been caught above!");
				}

				AudioSystem.write(
						AudioSystem.getAudioInputStream(SUITABLE_AUDIO_FORMAT, originalStream),
						AudioFileFormat.Type.WAVE,
						f);
			}
		};

		return Cache.cachedFile("converted.wav", factory, original);
	}

	private static S4AlignOrder createS4AlignOrder(int motdeb, int trdeb, int motfin, int trfin) {
		S4AlignOrder order = new S4AlignOrder(motdeb, trdeb, motfin, trfin);
		try {
			s4blocViterbi.input2process.put(order);
			synchronized(order) {
				order.wait();
				// TODO ce thread ne sort jamais d'ici si sphinx plante
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		if (!order.isEmpty())
			order.adjustOffset();

		return order;
	}

	/**
	 * Align words between startWord and endWord using Sphinx.
	 * Slow, but accurate.
	 *
	 * The resulting S4AlignOrder objects may be cached to save time.
	 *
	 * It is not merged into the main alignment (use mergeOrder() for that).
	 */
	public static S4AlignOrder partialBatchAlign(final int startWord, final int startFrame, final int endWord, final int endFrame) {
		System.out.println("batch align "+startWord+"-"+endWord+" "+startFrame+":"+endFrame);

		if (s4blocViterbi==null) {
			String[] amots = new String[mots.size()];
			for (int i=0;i<mots.size();i++) {
				amots[i] = mots.get(i).getWordString();
			}
			s4blocViterbi = S4ForceAlignBlocViterbi.getS4Aligner(aligneur.convertedAudioFile.getAbsolutePath());
			s4blocViterbi.setMots(amots);
		}

		return (S4AlignOrder)Cache.cachedObject(
				String.format("%05d_%05d_%05d_%05d.order", startWord, startFrame, endWord, endFrame),
				new Cache.ObjectFactory() {
					public Object make() {
						return createS4AlignOrder(startWord, startFrame, endWord, endFrame);
					}
				},
				aligneur.originalAudioFile,
				edit.getText());
	}

	/**
	 * Merge an S4AlignOrder into the main alignment.
	 */
	public static void mergeOrder(S4AlignOrder order, int startWord, int endWord) {
		if (order.alignWords != null) {
			System.out.println("================================= ALIGN FOUND");
			System.out.println(order.alignWords.toString());

			String[] alignedWords = new String[1 + endWord - startWord];
			for (int i = 0; i < 1+endWord-startWord; i++)
				alignedWords[i] = mots.get(i + startWord).getWordString();
			int[] wordSegments = order.alignWords.matchWithText(alignedWords);

			// Merge word segments into the main word alignment
			int firstSegment = alignementWords.merge(order.alignWords);

			// Adjust posInAlign for word elements (Element_Mot)
			for (int i = 0; i < wordSegments.length; i++) {
				int idx = wordSegments[i];

				// Offset if we have a valid segment index
				if (idx >= 0)
					idx += firstSegment;

				mots.get(i + startWord).posInAlign = idx;
			}
			elts.refreshIndex();

			// Merge phoneme segments into the main phoneme alignment
			alignementPhones.merge(order.alignPhones);
		} else {
			System.out.println("================================= ALIGN FOUND null");
			// TODO
		}
	}

	/**
	 * Align words between startWord and endWord using linear interpolation in
	 * the main alignment.
	 * Very fast, but inaccurate.
	 */
	public static void linearAlign(int startWord, int startFrame, int endWord, int endFrame) {
		float frameDelta = ((float)(endFrame-startFrame))/((float)(endWord-startWord+1));
		float currEndFrame = startFrame + frameDelta;

		assert frameDelta >= 1f:
				"can't align on fractions of frames! (frameDelta=" + frameDelta + ")";

		for (int i = startWord; i <= endWord; i++) {
			int newseg = alignementWords.addRecognizedSegment(
					mots.get(i).getWordString(), startFrame, (int)currEndFrame, null, null);

			alignementWords.setSegmentSourceEqui(newseg);
			mots.get(i).posInAlign = newseg;

			startFrame = (int)currEndFrame;
			currEndFrame += frameDelta;
		}
	}

	/**
	 * Align all words until `word`.
	 * 
	 * @param word number of the last word to align
	 * @param startFrame can be < 0, in which case use the last aligned word.
	 * @param endFrame
	 */
	public static void setAlignWord(int word, int startFrame, int endFrame) {
		assert endFrame >= 0;
		// TODO: detruit les segments recouvrants

		int lastAlignedWord = getLastMotPrecAligned(word);
		int startWord;

		System.out.println("setalign "+word+" "+startFrame+" "+endFrame+" "+lastAlignedWord);

		// Find first word to align (startWord) and adjust startFrame if needed.
		if (lastAlignedWord >= 0) {
			startWord = lastAlignedWord + 1;

			// Lagging behind the alignment - wait for a couple more words
			if (startWord > word)
				return;

			if (startFrame < 0) {
				// Start aligning at the end frame of the last aligned word.
				int lastAlignedWordSeg = mots.get(lastAlignedWord).posInAlign;
				startFrame = alignementWords.getSegmentEndFrame(lastAlignedWordSeg);
			}
		} else {
			// Nothing is aligned yet; start aligning from the beginning.
			startWord = 0;
			startFrame = 0;
		}

		if (startWord < word) {
			// There are unaligned words before `word`; align them.
			if (USE_LINEAR_ALIGNMENT) {
				linearAlign(startWord, startFrame, word, endFrame);
			} else {
				S4AlignOrder order = partialBatchAlign(startWord, startFrame, word, endFrame);
				mergeOrder(order, startWord, word);
			}
		} else {
			// Only one word to align; create a new manual segment.
			int newseg = alignementWords.addRecognizedSegment(
					elts.getMot(word).getWordString(), startFrame, endFrame, null, null);
			alignementWords.setSegmentSourceManu(newseg);
			elts.getMot(word).posInAlign = newseg;
		}

		// Ensure everything has been aligned
		lastAlignedWord = getLastMotPrecAligned(word);
		if (lastAlignedWord < word) {
			// Incomplete alignment. Avoid offsetting the entire track by
			// force-aligning missing words onto the last successful alignment.

			System.out.println("setalign: incomplete alignment! last: "
					+ lastAlignedWord + ", word: " + word);

			int fakeAlign = mots.get(lastAlignedWord).posInAlign;
			for (int w = lastAlignedWord+1; w <= word; w++)
				mots.get(w).posInAlign = fakeAlign;

			// Don't underline the parts we just forced
			word = lastAlignedWord;
		}

		// TODO: phonetiser et aligner auto les phonemes !!

		// Update GUI
		if (edit != null) {
			edit.colorizeAlignedWords(startWord, word);
			edit.repaint();
		}
	}

	public static void setAlignWord(int mot, float secdeb, float secfin) {
		int curdebfr = SpectroControl.second2frame(secdeb);
		int curendfr = SpectroControl.second2frame(secfin);
		setAlignWord(mot, curdebfr, curendfr);
	}
	private static void setSilenceSegment(int curdebfr, int curendfr, AlignementEtat al) {
		// detruit tous les segments existants deja a cet endroit
		ArrayList<Integer> todel = new ArrayList<Integer>();
		clearAlignFromFrame(curdebfr);
		for (int i=0;i<al.getNbSegments();i++) {
			int d=al.getSegmentDebFrame(i);
			if (d>=curendfr) break;
			int f=al.getSegmentEndFrame(i);
			if (f<curdebfr) continue;
			// il y a intersection
			if (d>=curdebfr&&f<=curendfr) {
				// ancient segment inclu dans nouveau
				todel.add(i);
			} else {
				// TODO: faire les autres cas d'intersection
			}
		}
		for (int i=todel.size()-1;i>=0;i--) al.delSegment(todel.get(i));
		int newseg=al.addRecognizedSegment("SIL", curdebfr, curendfr, null, null);
		al.setSegmentSourceManu(newseg);
	}
	public static void setSilenceSegment(float secdeb, float secfin) {
		int curdebfr = SpectroControl.second2frame(secdeb);
		int curendfr = SpectroControl.second2frame(secfin);
		setSilenceSegment(curdebfr, curendfr, alignementWords);
		setSilenceSegment(curdebfr, curendfr, alignementPhones);
	}
	public static void clearAlignFromFrame(int fr) {
		// TODO
		throw new Error("clearAlignFromFrame: IMPLEMENT ME!");
	}
	
	// =========================
	// variables below are duplicate (point to) of variables in the mess of the rest of the code...
	private static ListeElement elts =  null;
	public static AlignementEtat alignementWords = null;
	public static AlignementEtat alignementPhones = null;
	public static TexteEditor edit = null;
	public static Aligneur aligneur = null;
	public static S4ForceAlignBlocViterbi s4blocViterbi = null;
	
	public static void setElts(ListeElement e) {
		elts=e;
		mots = elts.getMots();
	}
	
	// =========================
	private static List<Element_Mot> mots = null;
	
	// =========================
	private static void initmots() {
		if (elts!=null)
			if (mots==null) {
				mots=elts.getMots();
			}
	}
	private static int getLastMotPrecAligned(int midx) {
		initmots();
		for (int i=midx;i>=0;i--) {
			if (mots.get(i).posInAlign>=0) return i;
		}
		return -1;
	}

	public static void loadTRS(String trsfile, ProgressDialog progress) {
		progress.setMessage("Parsing TRS markup...");

		TRSLoader trs = null;
		try {
			trs = new TRSLoader(trsfile);
		} catch (Exception e) {
			System.err.println("TRS loader failed!");
			e.printStackTrace();
			// TODO handle failure gracefully -IJ
		}

		TexteEditor zonetexte = TexteEditor.getTextEditor();
		zonetexte.setEditable(false);
		zonetexte.setText(trs.buffer.toString());
		zonetexte.setListeElement(trs.elements);
		zonetexte.highlightNonTextSegments(trs.nonText);

		// Align words between the previous anchor and the current one.
		progress.setMessage("Aligning...");
		float alignFrom = 0;
		int word = -1;
		for (int i = 0; i < elts.size(); i++) {
			Element e = elts.get(i);

			if (e instanceof Element_Mot) {
				word++;
			} else if (e instanceof Element_Ancre) {
				Element_Ancre anchor = (Element_Ancre)e;
				if (word >= 0)
					setAlignWord(word, alignFrom, anchor.seconds);
				alignFrom = anchor.seconds;
			}

			progress.setProgress((i+1) / (float)elts.size());
		}

		// Align end of file.
		if (word >= 0)
			setAlignWord(word, alignFrom, trs.lastEnd);

		progress.setMessage("Finishing up...");
		progress.setIndeterminate(true);

		aligneur.caretSensible = true;

		// force la construction de l'index
		alignementWords.clearIndex();
		alignementWords.getSegmentAtFrame(0);
		alignementPhones.clearIndex();
		alignementPhones.getSegmentAtFrame(0);
		elts.refreshIndex();
	}
}
