package fr.loria.synalp.jtrans.facade;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class LinearBridge
		implements Iterator<AnchorSandwich[]>
{
	private final int nTracks;
	private final AnchorSandwichIterator[] sandwichIterators;
	private final AnchorSandwich[] currentSandwiches;


	public LinearBridge(List<Track> tracks) {
		nTracks = tracks.size();
		sandwichIterators = new AnchorSandwichIterator[nTracks];
		currentSandwiches = new AnchorSandwich[nTracks];

		for (int i = 0; i < nTracks; i++) {
			sandwichIterators[i] = tracks.get(i).sandwichIterator();
			currentSandwiches[i] = sandwichIterators[i].next();
		}
	}


	@Override
	public boolean hasNext() {
		for (Iterator i: sandwichIterators) {
			if (!i.hasNext()) {
				return false;
			}
		}

		return true;
	}


	@Override
	public AnchorSandwich[] next() {
		AnchorSandwich[] simultaneous = new AnchorSandwich[nTracks];
		AnchorSandwich earliest = null;

		// Find track containing the earliest upcoming anchor
		for (int i = 0; i < nTracks; i++) {
			AnchorSandwich sandwich = currentSandwiches[i];

			if (sandwich == null) {
				continue;
			}

			if (earliest == null) {
				simultaneous[i] = sandwich;
				earliest = sandwich;
			} else {
				int cmp = sandwich.compareTo(earliest);

				if (cmp < 0) {
					Arrays.fill(simultaneous, 0, i, null);
					simultaneous[i] = sandwich;
					earliest = sandwich;
				} else if (cmp == 0) {
					simultaneous[i] = sandwich;
				}
			}
		}

		for (int i = 0; i < nTracks; i++) {
			if (simultaneous[i] == null) {
				;
			} else if (!sandwichIterators[i].hasNext()) {
				currentSandwiches[i] = null;
			} else {
				currentSandwiches[i] = sandwichIterators[i].next();
			}
		}

		return simultaneous;
	}


	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
}
