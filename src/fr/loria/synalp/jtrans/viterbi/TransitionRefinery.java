package fr.loria.synalp.jtrans.viterbi;

import edu.cmu.sphinx.util.LogMath;
import fr.loria.synalp.jtrans.speechreco.s4.HMMModels;
import fr.loria.synalp.jtrans.speechreco.s4.S4mfccBuffer;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;

/**
 * Refines an HMM state timeline by shifting transitions with the
 * Metropolis-Hastings algorithm.
 */
public class TransitionRefinery {

	private final float[][] data;

	private int[] timeline;
	private double[] lhd;
	private double cLhd;

	private Random random;
	private StateGraph graph;
	private LogMath log = HMMModels.getLogMath();

	int rejectionStreak = 0;
	int rejections = 0;
	int acceptances = 0;
	int luckyAcceptances = 0;
	int iterations;
	private PrintWriter plot;


	/**
	 * Maximum number of rejected steps in a row before aborting the
	 * Metropolis-Hastings refinement.
	 */
	public static final int METROPOLIS_REJECTION_STREAK_CAP = 500;


	/** Proposal acceptance/rejection status */
	private enum Accept {
		/** Accepted on merit alone. Proposal better than original. */
		MERIT,

		/** Accepted on luck. Proposal worse than original, but got randomly
		 * accepted anyway. */
		LUCK,

		/** Rejected. Proposal worse than original. */
		REJECTED,
	}


	/**
	 * @param baseline Baseline alignment (as found e.g. with viterbi()).
	 *                 Will be modified!
	 * @param frameOffset Frame offset for the MFCC buffer *only*. *Not*
	 *                    applicable to timeline!
	 */
	public TransitionRefinery(
			int[] baseline,
			StateGraph graph,
			S4mfccBuffer mfcc,
			int frameOffset)
	{
		timeline = new int[baseline.length];
		System.arraycopy(baseline, 0, timeline, 0, timeline.length);
		this.graph = graph;

		data = StateGraph.getData(baseline.length, mfcc, frameOffset);
		random = new Random();

		lhd = graph.alignmentLikelihood(baseline, data);
		for (int i = 0; i < baseline.length; i++) {
			cLhd += lhd[i];
		}
	}


	/**
	 * Finds the next transition between two states.
	 * @param offset start searching at this frame
	 * @param timeline HMM state timeline
	 * @return the number of the frame preceding the transition
	 */
	public static int nextTransition(int offset, int[] timeline) {
		final int upper = timeline.length - 2;
		while (offset < upper &&
				!(timeline[offset+1] != timeline[offset] &&
						timeline[offset+1] == timeline[offset+2]))
		{
			offset++;
		}
		return offset >= upper? -1: offset;
	}


	/**
	 * Refines an HMM state timeline with the Metropolis-Hastings algorithm.
	 */
	public int[] step() throws IOException {
		iterations++;

		if (plot == null) {
			final String plotName = "likelihood_" + System.currentTimeMillis() + ".txt";
			plot = new PrintWriter(new BufferedWriter(new FileWriter(plotName)));
			System.err.println("Plot: " + plotName);
		}

		Accept status = metropolisHastings();

		if (status == Accept.REJECTED) {
			rejections++;
			rejectionStreak++;
		} else {
			acceptances++;
			if (status == Accept.LUCK) {
				luckyAcceptances++;
			}
			rejectionStreak = 0;
		}


		plot.println(cLhd);

		if (hasPlateaued() || iterations % 100 == 0) {
			plot.flush();
			System.err.println(String.format(
					"Rejections: %d, Acceptances: %d (of which %d lucky) (%f%%)",
					rejections, acceptances, luckyAcceptances,
					100f * acceptances / (rejections+acceptances)));
		}

		return timeline;
	}


	public boolean hasPlateaued() {
		return rejectionStreak >= METROPOLIS_REJECTION_STREAK_CAP;
	}


	/**
	 * Refines a random transition in the timeline.
	 */
	private Accept metropolisHastings() {
		int trans = -1;
		while (trans < 0) {
			// don't use last 2 values (see nextTransition())
			trans = nextTransition(random.nextInt(timeline.length-2), timeline);
		}
		assert trans < timeline.length - 1;

		// Shift transition
		int backup = timeline[trans+1];
		timeline[trans+1] = timeline[trans];

		double[] newLhd = graph.alignmentLikelihood(timeline, data);

		double newCLhd = 0;
		for (int i = 0; i < timeline.length; i++) {
			newCLhd += newLhd[i];
		}

		System.out.println("============= TRANSITION CHANGED AT FRAME " + trans + "=============");
		System.out.println("=====CUMULATIVE: old: " + cLhd + " new: " + newCLhd);
		for (int i = 0; i < timeline.length; i++) {
			double diff = newLhd[i] - lhd[i];
			if (diff != 0) {
				System.out.println(
						"[FRAME " + i + "]" +
								"\tnew " + newLhd[i] +
								"\told " + lhd[i] +
								"\tdiff " + diff
				);
			}
		}

		boolean accept = newCLhd > cLhd;
		final Accept status;

		if (accept) {
			status = Accept.MERIT;
		} else {
			double ratio = log.logToLinear((float) (newCLhd - cLhd));
			assert ratio >= 0 && ratio <= 1;
			double dice = random.nextDouble();
			accept = dice <= ratio;
			status = accept? Accept.LUCK: Accept.REJECTED;

			System.out.println(String.format(
					"Dice: %.5f, Ratio: %.20f, newLhd: %.10f, oldLhd: %.10f, logdiff:%.10f",
					dice, ratio, newCLhd, cLhd, newCLhd - cLhd));
		}

		if (!accept) {
			timeline[trans+1] = backup;
		} else {
			lhd = newLhd;
			cLhd = newCLhd;
		}

		System.out.println("Acceptance status: " + status);

		return status;
	}

}
