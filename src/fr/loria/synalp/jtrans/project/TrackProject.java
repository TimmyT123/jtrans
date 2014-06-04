package fr.loria.synalp.jtrans.project;

import fr.loria.synalp.jtrans.align.Aligner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TrackProject extends Project {
	public List<List<Phrase>> tracks = new ArrayList<>();

	@Override
	public List<Token> getTokens(int speaker) {
		ArrayList<Token> res = new ArrayList<>();
		for (Phrase phrase: tracks.get(speaker)) {
			res.addAll(phrase);
		}
		return res;
	}

	@Override
	public Iterator<Phrase> phraseIterator(int speaker) {
		return tracks.get(speaker).iterator();
	}

	@Override
	public void align(Aligner aligner)
			throws IOException, InterruptedException
	{
		clearAlignment();

		for (int i = 0; i < speakerCount(); i++) {
			Iterator<Phrase> itr = phraseIterator(i);

			while (itr.hasNext()) {
				Phrase phrase = itr.next();

				if (!phrase.isFullyAligned()) {
					align(aligner,
							phrase.getInitialAnchor(),
							phrase.getFinalAnchor(),
							phrase,
							false); // never concatenate.
					// If we did concatenate, we'd have to start over a new
					// concatenated timeline on the next for iteration anyway.
				}
			}
		}
	}

	public void addTrack(String name, List<Phrase> newTrack) {
		final int speakerID = tracks.size();

		speakerNames.add(name);
		tracks.add(newTrack);
		assert tracks.size() == speakerNames.size();

		for (Phrase phrase: newTrack) {
			for (Token token: phrase) {
				token.setSpeaker(speakerID);
			}
		}
	}
}
