package fr.loria.synalp.jtrans.markup;

import fr.loria.synalp.jtrans.elements.*;
import fr.loria.synalp.jtrans.facade.Project;
import fr.loria.synalp.jtrans.facade.Track;
import fr.loria.synalp.jtrans.utils.FileUtils;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Parser for raw transcription text.
 */
public class RawTextLoader implements MarkupLoader {
	/**
	 * Substitute junk characters with ones that JTrans can handle.
	 */
	public static String normalizeText(String text) {
		return text
				.replace('\u2019', '\'')            // smart quotes
				.replace("\r\n", "\n")              // Windows CRLF
				.replace('\r', '\n')                // remaining non-Unix linebreaks
				.replace('\u00a0', ' ')             // non-breaking spaces
				.replaceAll("[\"=/]", " ")          // junk punctuation marks
				.replaceAll("\'(\\S)", "\' $1")     // add space after apostrophes glued to a word
		;
	}

	/**
	 * Creates elements from a string according to the regular expressions
	 * defined in typeList.
	 */
	public static ElementList parseString(String normedText, List<ElementType> typeList) {
		class NonTextSegment implements Comparable<NonTextSegment> {
			public int start, end, type;

			public NonTextSegment(int start, int end, int type) {
				this.start = start;
				this.end = end;
				this.type = type;
			}

			public int compareTo(NonTextSegment other) {
				if (start > other.start) return 1;
				if (start < other.start) return -1;
				return 0;
			}
		}

		ElementList listeElts = new ElementList();
		ArrayList<NonTextSegment> nonText = new ArrayList<NonTextSegment>();

		for (int type = 0; type < typeList.size(); type++) {
			for (Pattern pat: typeList.get(type).getPatterns()) {
				Matcher mat = pat.matcher(normedText);
				while (mat.find())
					nonText.add(new NonTextSegment(mat.start(), mat.end(), type));
			}
		}
		Collections.sort(nonText);

		// Turn the non-text segments into Elements
		int prevEnd = 0;
		for (NonTextSegment seg: nonText) {
			int start = seg.start;
			int end = seg.end;
			if (prevEnd > start) {
				//cas entrecroisé : {-----------[---}-------]
				//on deplace de façon à avoir : {--------------}[-------]
				if (end > prevEnd) start = prevEnd;

				//cas imbriqué : {------[---]----------}
				//on ne parse pas l'imbriqué
				else continue;
			}

			// Line right before
			if (start > prevEnd) {
				String line = normedText.substring(prevEnd, start);
				parserListeMot(line, prevEnd, listeElts, normedText);
			}

			// Create the actual element
			String sub = normedText.substring(start, end);
			listeElts.add(new Comment(sub, seg.type));

			prevEnd = end;
		}

		// Line after the last element
		if (normedText.length() > prevEnd) {
			String line = normedText.substring(prevEnd);
			parserListeMot(line, prevEnd, listeElts, normedText);
		}

		return listeElts;
	}


	private static void parserListeMot(String ligne, int precfin, ElementList listeElts, String text) {
		int index = 0;
		int debutMot;
		//on parcourt toute la ligne
		while(index < ligne.length()){

			//on saute les espaces
			while(index < ligne.length() &&
					Character.isWhitespace(ligne.charAt(index))){
				index++;
			}

			debutMot =  index;
			//on avance jusqu'au prochain espace

			while((index < ligne.length()) && (!Character.isWhitespace(ligne.charAt(index)))){
				index++;
			}

			if (index > debutMot){
				listeElts.add(new Word(text.substring(debutMot + precfin, index + precfin)));
			}
		}
	}

	@Override
	public Project parse(File file) throws ParsingException, IOException {
		Project project = new Project();
		BufferedReader reader = FileUtils.openFileAutoCharset(file);

		// Add default speaker
		Track track = new Track("L1");
		project.tracks.add(track);

		track.elts.add(new Anchor(0));

		while (true) {
			String line = reader.readLine();
			if (line == null)
				break;
			line = normalizeText(line.trim());
			track.elts.addAll(parseString(line, project.types));
		}

		reader.close();

		return project;
	}

	@Override
	public String getFormat() {
		return "Raw Text";
	}
}
