package fr.loria.synalp.jtrans.project;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * Piece of text sandwiched between two anchors.
 */
public class Phrase
		extends AbstractList<Element>
		implements Comparable<Phrase>
{
	private final List<Element> elements;
	private final Anchor initialAnchor;
	private final Anchor finalAnchor;

	public Phrase(Anchor initialAnchor, Anchor finalAnchor, List<Element> elements) {
		this.elements = elements;
		this.initialAnchor = initialAnchor;
		this.finalAnchor = finalAnchor;
	}

	public Anchor getInitialAnchor() {
		return initialAnchor;
	}

	public Anchor getFinalAnchor() {
		return finalAnchor;
	}

	public List<Word> getWords() {
		List<Word> words = new ArrayList<Word>();

		for (Element el: elements) {
			if (el instanceof Word) {
				words.add((Word)el);
			}
		}

		return words;
	}


	public String getSpaceSeparatedWords() {
		StringBuilder sb = new StringBuilder();
		String prefix = "";

		for (Element el: elements) {
			if (el instanceof Word) {
				sb.append(prefix).append(el.toString());
				prefix = " ";
			}
		}

		return sb.toString();
	}


	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		String prefix = "";

		for (Element el: elements) {
			sb.append(prefix).append(el);
			prefix = " ";
		}

		return sb.toString();
	}


	public boolean isFullyAligned() {
		for (Element el: elements) {
			if (el instanceof Word && !((Word) el).isAligned()) {
				return false;
			}
		}

		return true;
	}


	@Override
	public Element get(int index) {
		return elements.get(index);
	}

	@Override
	public int size() {
		return elements.size();
	}


	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Phrase)) {
			return false;
		}

		Phrase as = (Phrase)o;

		return as.initialAnchor.equals(initialAnchor) &&
				as.finalAnchor.equals(finalAnchor) &&
				super.equals(o);
	}


	@Override
	public int compareTo(Phrase o) {
		Anchor myIA = getInitialAnchor();
		Anchor theirIA = o.getInitialAnchor();

		if (myIA != null) {
			return myIA.compareTo(theirIA);
		}

		if (theirIA != null) {
			return theirIA.compareTo(null);
		}

		return 0;
	}
}
