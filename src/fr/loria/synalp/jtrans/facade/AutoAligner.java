package fr.loria.synalp.jtrans.facade;

import edu.cmu.sphinx.frontend.FloatData;
import fr.loria.synalp.jtrans.elements.Word;
import fr.loria.synalp.jtrans.speechreco.s4.S4mfccBuffer;
import fr.loria.synalp.jtrans.utils.ProgressDisplay;
import fr.loria.synalp.jtrans.viterbi.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


/**
 * Base front-end for alignment algorithms.
 * Its main aim is to align a sequence of words to a chunk of audio data.
 */
public abstract class AutoAligner {

	protected final StatePool pool;
	protected List<AlignmentScorer> scorers;
	protected final List<FloatData> data;
	protected final File audio;
	protected final ProgressDisplay progress;
	protected Runnable refinementIterationHook;


	/**
	 * Compute alignment likelihood in each call to align().
	 */
	public static boolean COMPUTE_LIKELIHOODS = false;


	/**
	 * Refine the baseline alignment with Metropolis-Hastings after completing
	 * Viterbi.
	 */
	public static boolean METROPOLIS_HASTINGS_POST_PROCESSING = false;


	public AutoAligner(File audio, ProgressDisplay progress) {
		this.progress = progress;
		this.audio = audio;

		data = S4mfccBuffer.getAllData(audio);
		pool = new StatePool();
	}

	public void setScorers(int speakers) {
		if (!COMPUTE_LIKELIHOODS && !METROPOLIS_HASTINGS_POST_PROCESSING) {
			return;
		}

		scorers = new ArrayList<>(speakers);
		float[][] dataArray = S4mfccBuffer.to2DArray(data);

		for (int i = 0; i < speakers; i++) {
			scorers.add(new AlignmentScorer(dataArray, pool, i));
		}
	}


	public int getFrameCount() {
		return data.size();
	}


	/**
	 * Sets a hook to be run after every iteration of the Metropolis-Hastings
	 * refinement process.
	 */
	public void setRefinementIterationHook(Runnable r) {
		refinementIterationHook = r;
	}


	public int boundCheckLength(int startFrame, int endFrame) {
		assert startFrame <= endFrame;
		assert startFrame >= 0;
		assert endFrame >= 0;
		assert endFrame < data.size();
		return endFrame - startFrame + 1;
	}


	/**
	 * Align words between startWord and endWord.
	 * @param endFrame last frame to analyze
	 */
	public void align(
			final List<Word> words,
			final int startFrame,
			final int endFrame)
			throws IOException, InterruptedException
	{
		if (progress != null) {
			progress.setIndeterminateProgress("Setting up state graph...");
		}

		// String representations of each word (used to build StateGraph)
		final String[] wordStrings = new String[words.size()];

		final int[] wordSpeakers = new int[words.size()];

		// Space-separated string of words (used as identifier for cache files)
		final String text;

		// build wordStrings and text
		StringBuilder textBuilder = new StringBuilder();
		for (int i = 0; i < words.size(); i++) {
			Word w = words.get(i);
			wordStrings[i] = w.toString();
			wordSpeakers[i] = w.getSpeaker();
			textBuilder.append(w.toString()).append(" ");
		}
		text = textBuilder.toString();

		final StateGraph graph = new StateGraph(pool, wordStrings, wordSpeakers);
		graph.setProgressDisplay(progress);

		// Cache wrapper class for getTimeline()
		class TimelineFactory implements Cache.ObjectFactory {
			public int[] make() {
				try {
					return getTimeline(graph, text, startFrame, endFrame);
				} catch (IOException ex) {
					ex.printStackTrace();
					return null;
				} catch (InterruptedException ex) {
					ex.printStackTrace();
					return null;
				}
			}
		}

		int[] timeline = (int[])Cache.cachedObject(
				getTimelineCacheDirectoryName(),
				"timeline",
				new TimelineFactory(),
				audio, text, startFrame, endFrame);

		if (COMPUTE_LIKELIHOODS) {
			assert scorers != null;
			if (progress != null) {
				progress.setIndeterminateProgress("Computing likelihood...");
			}

			graph.setWordAlignments(words, timeline, startFrame);

			for (Word w: words) {
				scorers.get(w.getSpeaker())
						.learn(w, graph, timeline, startFrame);
			}
		}

		if (METROPOLIS_HASTINGS_POST_PROCESSING) {
			if (progress != null) {
				progress.setIndeterminateProgress("Metropolis-Hastings...");
			}

			assert scorers != null;

			throw new RuntimeException("(re)IMPLEMENT ME!");
			/*
			final TransitionRefinery refinery = new TransitionRefinery(
					graph, timeline, scorers.get(speaker));

			while (!refinery.hasPlateaued()) {
				timeline = refinery.step();

				if (refinementIterationHook != null) {
					graph.setWordAlignments(words, timeline, startFrame);
					refinementIterationHook.run();
				}
			}
			*/
		}

		graph.setWordAlignments(words, timeline, startFrame);
	}


	/**
	 * Aligns a StateGraph and produces an HMM state timeline.
	 * @param text space-separated words (mainly useful as an identifier for
	 *             cache files)
	 * @return a frame-by-frame timeline of HMM states
	 */
	protected abstract int[] getTimeline(
			StateGraph graph,
			String text,
			int startFrame,
			int endFrame)
			throws IOException, InterruptedException;


	/**
	 * Returns a name for the directory containing cached timelines produced
	 * by/for this aligner. Different alignment algorithms should not share the
	 * same cache!
	 */
	protected String getTimelineCacheDirectoryName() {
		final String suffix = "Aligner";
		String name = getClass().getSimpleName();
		assert name.endsWith(suffix);
		name = name.substring(0, name.length() - suffix.length());
		return "timeline_" + name.toLowerCase();
	}


	public void printScores() {
		for (int i = 0; i < scorers.size(); i++) {
			AlignmentScorer scorer = scorers.get(i);
			double sum = 0;

			scorer.finishLearning();
			int effectiveFrames = scorer.score();
			sum += AlignmentScorer.sum(scorer.getLikelihoods());

			System.out.println("Overall likelihood track " + i + "\tFrames: "
							+ effectiveFrames + "/" + getFrameCount()
							+ "\tSum: "
							+ sum
							+ "\tNormalized: "
							+ (sum / effectiveFrames)
			);
		}
	}

}
